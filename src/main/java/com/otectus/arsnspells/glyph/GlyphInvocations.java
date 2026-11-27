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
 *
 * <p><b>Cooldown (3.4.3).</b> Admission no longer keeps a second cast of a spell out while an
 * earlier one is still travelling or delayed, so Iron's cooldown is checked here, immediately before
 * the effect: an execution that arrives while its spell is on a cooldown this cast did not start (a
 * faster cast of the same spell landed first, or a native cast started it) is refused with the
 * cooldown message and gives back what it holds. A cooldown this cast started is its own: a split,
 * pierce, linger, wall or the same glyph again in the recipe still runs, as before. A native recast
 * and Iron's creative no-cooldown rule exempt an execution exactly as they exempt admission. The
 * check runs here only, never before a charged release or a channel pulse, which belong to a child
 * that already passed it.
 */
final class GlyphInvocations {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphInvocations.class);

    private GlyphInvocations() {}

    static void resolve(ImportedIronEffect effect, HitResult hit, Level world, LivingEntity shooter, SpellStats stats,
                        SpellContext context, SpellResolver resolver) {
        if (world.isClientSide()) return;
        ImportedGlyphDefinition definition = effect.definition();
        ServerPlayer player = GlyphCasters.realPlayer(shooter);
        if (player == null) {
            // A turret, rune, fake player or other non-player caster: no session, no payment, no free cast.
            trace(null, definition, "refused_non_player_caster");
            return;
        }
        GlyphCastSession session = GlyphSessions.forContext(context);
        if (session == null || !session.owner().equals(player.getUUID())) {
            // 3.4.3: a delivery that outlived its cast's lifetime is told so, not that it never had a cast.
            if (session == null && GlyphSessions.expiredFor(context, player.getUUID())) {
                int index = context.getCurrentIndex() - 1;
                record(player, definition, GlyphAdmission.requestedLevel(context.getSpell(), index, definition, player), -1,
                    "message.ars_n_spells.glyph.refused.expired");
                refuse(player, definition, "message.ars_n_spells.glyph.refused.expired");
                return;
            }
            refuse(player, definition, "message.ars_n_spells.glyph.refused.no_session");
            return;
        }
        int index = context.getCurrentIndex() - 1;
        GlyphCastSession.Occurrence occurrence = session.occurrence(index);
        if (occurrence == null || occurrence.definition != definition) {
            refuse(player, definition, "message.ars_n_spells.glyph.refused.no_session");
            return;
        }
        ResourceLocation castForm = context.getSpell().getCastMethod() == null ? null
            : context.getSpell().getCastMethod().getRegistryName();
        GlyphAvailability.Status status = GlyphAvailability.status(definition);
        if (!status.available()) {
            player.displayClientMessage(status.message(definition).copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        // 3.4.1 review CAST-03: the same authorization every later stage repeats, against the policy now.
        String refusal = GlyphExecutionPolicy.refusal(player, session, occurrence, world, hit, castForm);
        if (refusal != null) {
            // Refused before its effect: give back what this occurrence still holds, at once.
            GlyphSessions.payment(player, session, occurrence).releaseIfUnpaid();
            refuse(player, definition, refusal);
            return;
        }
        ValidatedCastTarget target = ValidatedCastTarget.of(world, hit);
        if (!targetStillValid(world, hit, target)) {
            refuse(player, definition, "message.ars_n_spells.glyph.refused.target_gone");
            return;
        }
        // 3.4.3: admission no longer keeps a second cast of this spell out while an earlier one is
        // unresolved, so the cooldown is enforced here, where the effect would run. The exemptions are
        // admission's own (GlyphAdmission): this cast's own cooldown, a native recast, Iron's creative rule.
        IronGlyphBridge bridge = IronGlyphBridges.get();
        ResourceLocation spellId = definition.spell();
        if (!session.started.contains(spellId) && !bridge.hasRecast(player, spellId)
                && !bridge.creativeNoCooldown(player) && bridge.onCooldown(player, spellId)) {
            GlyphSessions.payment(player, session, occurrence).releaseIfUnpaid();
            record(player, definition, occurrence.augmentedLevel, occurrence.nativeLevel, "message.ars_n_spells.glyph.refused.cooldown");
            refuse(player, definition, "message.ars_n_spells.glyph.refused.cooldown");
            return;
        }
        if (!session.budget().tryConsume()) {
            refuse(player, definition, "message.ars_n_spells.glyph.refused.budget");
            return;
        }
        ResourceLocation form = castForm;
        EmissionPayment payment = GlyphSessions.payment(player, session, occurrence);
        GlyphContinuation continuation = new GlyphContinuation(resolver, hit, world, session.id(), player.getUUID());
        GlyphInvocation invocation = new GlyphInvocation(player, definition, session.id(), index, occurrence.augmentedLevel,
            occurrence.nativeLevel, target, hit, form, payment, context.getCasterTool(), continuation);
        IronGlyphBridge.InvocationOutcome outcome;
        try {
            outcome = bridge.invoke(invocation);
        } catch (RuntimeException | LinkageError failure) {
            payment.releaseIfUnpaid();
            LOGGER.warn("[Glyph] {} failed for {}: {}", definition.key(), LogPrivacy.token(player.getUUID()), failure.toString());
            LOGGER.debug("[Glyph] failure", failure);
            refuse(player, definition, "message.ars_n_spells.glyph.refused.failed");
            return;
        }
        occurrence.emissions++;
        // 3.4.3: from here on a cooldown on this spell is this cast's own (an applied or failed effect, or a
        // child accepted into Iron's cast slot); a rejected or cancelled execution started nothing.
        if (outcome.status() != IronGlyphBridge.InvocationOutcome.Status.REJECTED
                && outcome.status() != IronGlyphBridge.InvocationOutcome.Status.CANCELED)
            session.started.add(spellId);
        com.otectus.arsnspells.casting.PaymentTimeline.record(player.getUUID(), -1, com.otectus.arsnspells.casting.PaymentTimeline.Kind.EMISSION,
            "glyph:" + session.id() + "/" + index, definition.key() + " " + outcome.status() + " final=" + outcome.finalLevel());
        GlyphSessions.record(player, new GlyphSessions.CastRecord(definition.spell(), definition.level(), occurrence.augmentedLevel,
            occurrence.nativeLevel, outcome.finalLevel(), payment.describe(), outcome.status().name().toLowerCase(java.util.Locale.ROOT)
                + (outcome.reasonKey() == null || outcome.reasonKey().isEmpty() ? "" : " " + outcome.reasonKey()),
            "execution", player.level().getGameTime()));
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
            case FAILED_AFTER_PAYMENT -> {
                // The effect had started and was paid for: nothing is refunded and the native
                // cooldown already applies, so a failure can never yield both an effect and a refund.
                refuse(player, definition, "message.ars_n_spells.glyph.refused.failed");
                trace(player, definition, "failed_after_payment");
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

    /** A refusal before any effect, as {@code /ans glyph last} lists it (3.4.3). */
    private static void record(ServerPlayer player, ImportedGlyphDefinition definition, int augmented, int nativeLevel, String key) {
        GlyphSessions.record(player, new GlyphSessions.CastRecord(definition.spell(), definition.level(), augmented, nativeLevel, -1,
            "nothing", "refused " + key, "execution", player.level().getGameTime()));
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
