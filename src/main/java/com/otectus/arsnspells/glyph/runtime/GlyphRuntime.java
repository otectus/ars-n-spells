package com.otectus.arsnspells.glyph.runtime;

import com.hollingsworth.arsnouveau.api.event.EventQueue;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAccelerate;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDecelerate;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDurationDown;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.glyph.IronGlyphBridge;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What happens when Ars resolves an imported glyph at a hit.
 *
 * <p>Per execution, in order:
 * <ol>
 *   <li>the cast must have been admitted ({@link GlyphSessions}); the glyph must still be
 *       available; the hit must be one the glyph's target accepts (a caster-only glyph resolves
 *       only on its caster, a living-target glyph only on another living entity, a block glyph only
 *       on a block); the cast's emission budget must not be spent;</li>
 *   <li>Iron's native conditions are checked again (learning, adventure mode, and the cooldown,
 *       except for further executions of an occurrence that already passed admission);</li>
 *   <li>instant effects run now through the Iron's bridge, which posts Iron's own cast events and
 *       asks for payment just before the effect: the occurrence's prepaid reservation if its price
 *       still matches, otherwise a fresh one;</li>
 *   <li>charged and continuous effects suspend the branch in a {@link GlyphContinuation}.</li>
 * </ol>
 * A refusal releases nothing that later executions of the same occurrence may still use; the
 * session releases unspent reservations when it closes.
 */
public final class GlyphRuntime {

    private GlyphRuntime() {
    }

    public static void resolve(ImportedIronEffect effect, HitResult hit, Level level, LivingEntity shooter,
                               SpellStats stats, SpellContext context, SpellResolver resolver) {
        if (level.isClientSide()) return;
        ServerPlayer player = GlyphSessions.supportedCaster(context);
        GlyphCastSession session = GlyphSessions.of(context);
        if (player == null || session == null || !player.getUUID().equals(session.owner)) {
            if (player != null) refuse(player, null, effect, Component.translatable("ars_n_spells.glyph.refused.not_admitted"));
            return;
        }
        VariantBehavior behavior = GlyphBehavior.current().variant(effect.key());
        if (!behavior.available()) {
            refuse(player, session, effect, Component.translatable("ars_n_spells.glyph.validation.dormant", effect.spellName(),
                Component.translatable(behavior.dormancy().translationKey())));
            return;
        }
        CatalogEntry entry = effect.entry();
        boolean selfHit = hit instanceof EntityHitResult e && e.getEntity() == player;
        Component targetRefusal = targetRefusal(effect, entry, hit, selfHit, GlyphBehavior.current().selfOnly(entry.spellId()));
        if (targetRefusal != null) {
            refuse(player, session, effect, targetRefusal);
            return;
        }
        if (session.emissions >= GlyphBehavior.current().settings().maxForeignEmissions()) {
            refuse(player, session, effect, Component.translatable("ars_n_spells.glyph.refused.emissions",
                GlyphBehavior.current().settings().maxForeignEmissions()));
            return;
        }
        GlyphCastSession.Token token = tokenFor(session, context, effect);
        boolean firstOfOccurrence = token != null && token.executions == 0;
        boolean sharedAdmission = token != null && token.executions > 0;
        IronGlyphBridge bridge = IronGlyphBridges.get();
        Component nativeRefusal = bridge.nativeRefusal(player, entry.spellId(),
            !sharedAdmission && !bridge.hasPendingRecast(player, entry.spellId()));
        if (nativeRefusal != null) {
            refuse(player, session, effect, nativeRefusal);
            return;
        }
        int level0 = GlyphSessions.effectiveLevel(effect, stats.getBuffCount(AugmentAmplify.INSTANCE),
            stats.getBuffCount(AugmentDampen.INSTANCE));
        int speed = stats.getBuffCount(AugmentAccelerate.INSTANCE) - stats.getBuffCount(AugmentDecelerate.INSTANCE);
        GlyphRequest request = new GlyphRequest(player, effect.key(), entry, hit, selfHit, level0,
            firstOfOccurrence || token == null, speed, session.id, UUID.randomUUID());

        if (entry.charged()) {
            GlyphPayments.Reservation held = holdFor(player, session, token, effect, level0);
            if (held == null && bridge.chargesMana(player)) return;
            int ticks = chargeTicks(bridge.effectiveCastTime(entry.spellId(), level0, player), speed);
            GlyphContinuation continuation = GlyphContinuation.charge(session, request, effect, hit, level, resolver, token, held, ticks);
            if (token != null && held == token.reservation) token.reservation = null; // the continuation owns it now
            start(session, context, continuation);
            bridge.chargeStarted(request);
            session.lastOutcome = GlyphOutcome.ACCEPTED_PENDING;
            GlyphSessions.trace(player, session, "charge_started", "spell", entry.spellId(), "level", level0, "ticks", ticks);
            GlyphSessions.recordOutcome(player, effect.key(), level0, GlyphOutcome.ACCEPTED_PENDING, "charge " + ticks + " ticks");
            player.displayClientMessage(Component.translatable("ars_n_spells.glyph.charging", effect.spellName(),
                String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        if (entry.channelled()) {
            int extraPulses = GlyphArithmetic.extraPulses(stats.getBuffCount(AugmentExtendTime.INSTANCE),
                stats.getBuffCount(AugmentDurationDown.INSTANCE));
            GlyphCastSession.Token useToken = token;
            GlyphChannel channel = bridge.startChannel(request, extraPulses,
                (cost, finalLevel) -> pay(player, session, useToken, effect, cost),
                cost -> payFresh(player, session, effect, cost));
            if (channel == null) {
                session.lastOutcome = GlyphOutcome.REJECTED;
                GlyphSessions.trace(player, session, "channel_refused", "spell", entry.spellId());
                return;
            }
            if (token != null) token.executions++;
            session.emissions++;
            GlyphContinuation continuation = GlyphContinuation.channel(session, request, effect, hit, level, resolver, channel);
            start(session, context, continuation);
            session.lastOutcome = GlyphOutcome.ACCEPTED_PENDING;
            GlyphSessions.trace(player, session, "channel_started", "spell", entry.spellId(), "level", level0);
            GlyphSessions.recordOutcome(player, effect.key(), level0, GlyphOutcome.ACCEPTED_PENDING, "channel");
            return;
        }
        executeNow(player, session, token, effect, request, null);
    }

    /** Hold the price of a charged execution until it completes: the prepaid token, or a fresh reservation. */
    @Nullable
    private static GlyphPayments.Reservation holdFor(ServerPlayer player, GlyphCastSession session,
                                                     @Nullable GlyphCastSession.Token token, ImportedIronEffect effect, int level) {
        IronGlyphBridge bridge = IronGlyphBridges.get();
        if (!bridge.chargesMana(player)) return null;
        if (token != null && token.prepaid()) return token.reservation;
        CostQuote quote = GlyphPayments.quote(player, bridge.manaCost(effect.key().ironSpellId(), level), session.rules);
        GlyphPayments.Shortage[] shortage = new GlyphPayments.Shortage[1];
        GlyphPayments.Reservation fresh = GlyphPayments.reserve(player, quote, "glyph:" + session.id + ":charge",
            GlyphSessions.MAX_SESSION_TICKS + 200, shortage);
        if (fresh == null) refuse(player, session, effect, GlyphSessions.shortageMessage(effect, shortage[0]));
        return fresh;
    }

    static int chargeTicks(int nativeTicks, int speed) {
        return GlyphArithmetic.chargeTicks(nativeTicks, speed);
    }

    private static void start(GlyphCastSession session, SpellContext context, GlyphContinuation continuation) {
        context.delay(continuation);
        EventQueue.getServerInstance().addEvent(continuation);
        GlyphSessions.register(continuation);
        GlyphSessions.pendingStarted(session);
    }

    /** A charge reached its cast time: run the effect on the held reservation. */
    static boolean completeCharge(GlyphContinuation continuation, @Nullable GlyphCastSession.Token token,
                                  @Nullable GlyphPayments.Reservation held) {
        ServerPlayer player = continuation.request.caster();
        GlyphRequest request = continuation.request;
        HitResult hit = request.hit();
        if (hit instanceof EntityHitResult entityHit && (!entityHit.getEntity().isAlive() || entityHit.getEntity().isRemoved()
                || entityHit.getEntity().level() != player.level())) {
            GlyphPayments.release(held);
            refuse(player, continuation.session, continuation.effect, Component.translatable("ars_n_spells.glyph.refused.target_gone"));
            return false;
        }
        Component nativeRefusal = IronGlyphBridges.get().nativeRefusal(player, request.key().ironSpellId(), false);
        if (nativeRefusal != null) {
            GlyphPayments.release(held);
            refuse(player, continuation.session, continuation.effect, nativeRefusal);
            return false;
        }
        return executeNow(player, continuation.session, token, continuation.effect, request, held);
    }

    /** Run an instant execution. {@code held} is a reservation already taken for it, if any. */
    private static boolean executeNow(ServerPlayer player, GlyphCastSession session, @Nullable GlyphCastSession.Token token,
                                      ImportedIronEffect effect, GlyphRequest request, @Nullable GlyphPayments.Reservation held) {
        IronGlyphBridge bridge = IronGlyphBridges.get();
        GlyphPayments.Reservation[] holding = {held};
        IronGlyphBridge.ExecutionResult result = bridge.execute(request, (cost, finalLevel) -> {
            GlyphPayments.Reservation own = holding[0];
            holding[0] = null;
            if (own != null) return settle(player, session, effect, cost, own);
            return pay(player, session, token, effect, cost);
        });
        if (holding[0] != null) GlyphPayments.release(holding[0]); // never reached the payment boundary
        if (result.applied()) {
            if (token != null) token.executions++;
            session.emissions++;
            if (request.applyCooldown()) GlyphSessions.cooldownCommitted(session, request.key().ironSpellId());
            session.lastOutcome = GlyphOutcome.EFFECT_APPLIED;
            session.record("applied " + request.key() + " at level " + request.effectiveLevel());
            GlyphSessions.recordOutcome(player, request.key(), request.effectiveLevel(), GlyphOutcome.EFFECT_APPLIED, hit(request.hit()));
            GlyphSessions.trace(player, session, "effect_applied", "spell", request.key().ironSpellId(),
                "level", request.effectiveLevel(), "hit", hit(request.hit()));
            return true;
        }
        refuse(player, session, effect, result.refusal() == null
            ? Component.translatable("ars_n_spells.glyph.refused.native") : result.refusal());
        return false;
    }

    /** Pay an execution: redeem the occurrence's prepaid reservation if its price matches, else pay fresh. */
    private static boolean pay(ServerPlayer player, GlyphCastSession session, @Nullable GlyphCastSession.Token token,
                               ImportedIronEffect effect, int finalIronsCost) {
        IronGlyphBridge bridge = IronGlyphBridges.get();
        if (!bridge.chargesMana(player)) {
            if (token != null && token.prepaid()) GlyphPayments.release(token.reservation);
            return true;
        }
        if (token != null && token.prepaid()) {
            GlyphPayments.Reservation prepaid = token.reservation;
            token.reservation = null;
            return settle(player, session, effect, finalIronsCost, prepaid);
        }
        return payFresh(player, session, effect, finalIronsCost);
    }

    /** Commit {@code held} when it covers exactly the final price; otherwise swap it for an exact one. */
    private static boolean settle(ServerPlayer player, GlyphCastSession session, ImportedIronEffect effect, int finalIronsCost,
                                  GlyphPayments.Reservation held) {
        CostQuote exact = GlyphPayments.quote(player, finalIronsCost, session.rules);
        if (sameLegs(held.quote(), exact)) {
            GlyphPayments.commit(held);
            return true;
        }
        GlyphPayments.release(held);
        return payFresh(player, session, effect, finalIronsCost);
    }

    private static boolean payFresh(ServerPlayer player, GlyphCastSession session, ImportedIronEffect effect, int ironsCost) {
        if (!IronGlyphBridges.get().chargesMana(player)) return true;
        CostQuote quote = GlyphPayments.quote(player, ironsCost, session.rules);
        GlyphPayments.Shortage[] shortage = new GlyphPayments.Shortage[1];
        GlyphPayments.Reservation fresh = GlyphPayments.reserve(player, quote, "glyph:" + session.id + ":exec", 200, shortage);
        if (fresh == null) {
            player.displayClientMessage(GlyphSessions.shortageMessage(effect, shortage[0]).copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        GlyphPayments.commit(fresh);
        return true;
    }

    static boolean sameLegs(CostQuote a, CostQuote b) {
        return GlyphArithmetic.sameLegs(a, b);
    }

    /**
     * The occurrence token this resolution belongs to. Ars resolves projectiles and delays against
     * the root recipe (same length, same part at the same index), so the index identifies the
     * occurrence; a context Ars rebuilt from a remainder does not, and its executions pay for
     * themselves rather than borrowing another occurrence's prepaid reservation.
     */
    @Nullable
    static GlyphCastSession.Token tokenFor(GlyphCastSession session, SpellContext context, ImportedIronEffect effect) {
        int index = context.getCurrentIndex() - 1;
        List<AbstractSpellPart> current = context.getSpell().unsafeList();
        List<AbstractSpellPart> root = session.rootSpell.unsafeList();
        if (index < 0 || index >= root.size() || current.size() != root.size() || root.get(index) != effect) return null;
        GlyphCastSession.Token token = session.tokens.get(index);
        return token != null && token.key.equals(effect.key()) ? token : null;
    }

    /** Why this hit is not one the glyph can resolve on, or null. */
    @Nullable
    static Component targetRefusal(ImportedIronEffect effect, CatalogEntry entry, HitResult hit, boolean selfHit, boolean selfOnly) {
        if (selfOnly && !selfHit) return Component.translatable("ars_n_spells.glyph.validation.self_only", effect.spellName());
        return switch (entry.target()) {
            case SELF -> selfHit ? null : Component.translatable("ars_n_spells.glyph.validation.self_only", effect.spellName());
            case ENTITY -> hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity && !selfHit ? null
                : Component.translatable("ars_n_spells.glyph.validation.living_target", effect.spellName());
            case ENTITY_OR_SELF -> hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity ? null
                : Component.translatable("ars_n_spells.glyph.validation.living_target", effect.spellName());
            case BLOCK -> hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? null
                : Component.translatable("ars_n_spells.glyph.validation.block_target", effect.spellName());
            case POSITION, ANY -> null;
        };
    }

    static void refuse(ServerPlayer player, @Nullable GlyphCastSession session, ImportedIronEffect effect, Component why) {
        player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
        GlyphSessions.recordOutcome(player, effect.key(), effect.key().baseLevel(), GlyphOutcome.REJECTED, GlyphSessions.detail(why));
        if (session != null) {
            session.lastOutcome = GlyphOutcome.REJECTED;
            session.record("refused " + effect.key() + ": " + why.getString());
            GlyphSessions.trace(player, session, "rejected", "spell", effect.key().ironSpellId(), "reason", why.getString());
        }
    }

    private static String hit(HitResult hit) {
        if (hit instanceof EntityHitResult e) return "entity#" + e.getEntity().getId();
        if (hit instanceof BlockHitResult b) return "block@" + b.getBlockPos().toShortString() + (b.getType() == HitResult.Type.MISS ? "(air)" : "");
        return "none";
    }
}
