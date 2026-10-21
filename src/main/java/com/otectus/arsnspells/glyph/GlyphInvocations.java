package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.spell.target.CastTargetKind;
import com.otectus.arsnspells.spell.target.ValidatedCastTarget;
import com.otectus.arsnspells.util.LogPrivacy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where Ars hands an imported glyph its target, once per execution.
 *
 * <p>Ars decides where and when the glyph resolves (the form, delays, splits, bursts); this
 * class decides whether this execution may run and then hands it to Iron's through the bridge.
 * An execution runs only for a real player, inside the session its root cast was admitted
 * with, at a target that is still in the caster's world, within the cast's emission budget,
 * and while the glyph is available. Anything else is refused without an effect or a payment.
 */
final class GlyphInvocations {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphInvocations.class);

    private GlyphInvocations() {}

    static void resolve(ImportedIronEffect effect, HitResult hit, Level world, LivingEntity shooter, SpellStats stats,
                        SpellContext context, SpellResolver resolver) {
        if (world.isClientSide()) return;
        ImportedGlyphDefinition definition = effect.definition();
        if (!(shooter instanceof ServerPlayer player) || shooter instanceof com.hollingsworth.arsnouveau.api.ANFakePlayer
                || player.isRemoved() || !player.isAlive()) {
            // A turret, rune or other non-player caster: no session, no payment, no free cast.
            trace(null, definition, "refused_non_player_caster");
            return;
        }
        GlyphCastSession session = GlyphSessions.forContext(context);
        if (session == null || !session.owner().equals(player.getUUID())) {
            refuse(player, definition, "message.ars_n_spells.glyph.refused.no_session");
            return;
        }
        int index = context.getCurrentIndex() - 1;
        GlyphCastSession.Occurrence occurrence = session.occurrence(index);
        if (occurrence == null || occurrence.definition != definition) {
            refuse(player, definition, "message.ars_n_spells.glyph.refused.no_session");
            return;
        }
        if (player.level() != world || !world.dimension().equals(session.dimension())) {
            refuse(player, definition, "message.ars_n_spells.glyph.refused.other_world");
            return;
        }
        GlyphAvailability.Status status = GlyphAvailability.status(definition);
        if (!status.available()) {
            player.displayClientMessage(status.message(definition).copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        ValidatedCastTarget target = ValidatedCastTarget.of(world, hit);
        if (!targetStillValid(world, hit, target)) {
            refuse(player, definition, "message.ars_n_spells.glyph.refused.target_gone");
            return;
        }
        if (!session.budget().tryConsume()) {
            refuse(player, definition, "message.ars_n_spells.glyph.refused.budget");
            return;
        }
        ResourceLocation form = context.getSpell().getCastMethod() == null ? null
            : context.getSpell().getCastMethod().getRegistryName();
        EmissionPayment payment = GlyphSessions.payment(player, session, occurrence);
        GlyphContinuation continuation = new GlyphContinuation(resolver, hit, world);
        GlyphInvocation invocation = new GlyphInvocation(player, definition, session.id(), index, occurrence.augmentedLevel,
            target, hit, form, payment, context.getCasterTool(), continuation);
        IronGlyphBridge.InvocationOutcome outcome;
        try {
            outcome = IronGlyphBridges.get().invoke(invocation);
        } catch (RuntimeException failure) {
            payment.releaseIfUnpaid();
            LOGGER.warn("[Glyph] {} failed for {}: {}", definition.key(), LogPrivacy.token(player.getUUID()), failure.toString());
            LOGGER.debug("[Glyph] failure", failure);
            refuse(player, definition, "message.ars_n_spells.glyph.refused.failed");
            return;
        }
        occurrence.emissions++;
        switch (outcome.status()) {
            case REJECTED, CANCELED -> {
                payment.releaseIfUnpaid();
                session.budget().refund();
                if (outcome.reasonKey() != null && !outcome.reasonKey().isEmpty())
                    refuse(player, definition, outcome.reasonKey());
                else LOGGER.debug("[Glyph] {} rejected for {} without a player message", definition.key(), LogPrivacy.token(player.getUUID()));
                trace(player, definition, "rejected " + outcome.reasonKey());
            }
            case ACCEPTED_PENDING -> {
                occurrence.childPending = true;
                continuation.suspend();
                trace(player, definition, "accepted_pending");
            }
            default -> trace(player, definition, outcome.status().name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static boolean targetStillValid(Level world, HitResult hit, ValidatedCastTarget target) {
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            return !entity.isRemoved() && entity.level() == world && world.getWorldBorder().isWithinBounds(entity.blockPosition());
        }
        if (target.kind() == CastTargetKind.BLOCK)
            return world.isLoaded(target.blockPos()) && world.getWorldBorder().isWithinBounds(target.blockPos());
        return world.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(hit.getLocation()));
    }

    private static void refuse(ServerPlayer player, ImportedGlyphDefinition definition, String key) {
        player.displayClientMessage(Component.translatable(key, definition.spellName() + " " + definition.levelNumeral())
            .withStyle(ChatFormatting.RED), true);
        LOGGER.debug("[Glyph] {} refused for {}: {}", definition.key(), LogPrivacy.token(player.getUUID()), key);
        trace(player, definition, "refused " + key);
    }

    private static void trace(ServerPlayer player, ImportedGlyphDefinition definition, String stage) {
        if (com.otectus.arsnspells.util.CrossCastTrace.enabled())
            LOGGER.info("[Glyph] player={} glyph={} stage={}", player == null ? "non-player" : LogPrivacy.token(player.getUUID()),
                definition.key(), stage);
    }
}
