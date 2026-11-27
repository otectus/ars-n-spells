package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.casting.QuoteService;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Admits an Ars cast that contains imported glyphs, before anything is spent.
 *
 * <p>Runs after Ars's cancellable {@code SpellCastEvent}, where ANS reserves the Ars leg, and
 * reserves the first execution of every imported glyph alongside it: the cast is admitted with
 * all of them funded or with none of them. What it checks: a real player is casting through a
 * player caster (never a turret, rune or fake player); no more imported glyphs than
 * {@code max_imported_effects_per_spell}; every one available, learned in Iron's where Iron's
 * requires it now, off its native cooldown (or holding a native recast) and not charging or
 * channelling in Iron's cast slot for another of the player's casts; its Amplified level within
 * Iron's live maximum; and fewer than {@code max_active_glyph_sessions_per_player} sessions still
 * holding something. Each refusal tells the caster why and costs nothing.
 *
 * <p><b>Repeated casts (3.4.3).</b> An earlier cast of the same Iron's spell that is only travelling
 * or delayed (a projectile that missed flies on for a minute) no longer refuses this one. What it
 * still holds for that spell is handed back before this cast is counted and funded, and its delivery
 * stays live: if it arrives, it pays at its effect boundary like any later execution, where Iron's
 * cooldown is checked too ({@code GlyphInvocations}). If this admission is refused after that point,
 * the earlier holds stay released; those deliveries pay on arrival.
 *
 * <p><b>Levels (3.4.1).</b> Each occurrence carries four levels: the glyph's base level; the Ars
 * level after Amplify and Dampen, which must not exceed Iron's live maximum (refused, never
 * clamped, so lowering the maximum cannot be bypassed with Amplify); Iron's own level for it
 * ({@code getLevelFor}: equipment affinity and {@code ModifySpellLevelEvent}), evaluated once here
 * and frozen; and, per execution, the level Iron's {@code SpellOnCastEvent} settles on, which the
 * payment, the effect and the client packet all use. Native bonuses may legitimately take the cast
 * above the scroll maximum; only the Ars-adjusted level is bounded.
 *
 * <p><b>Money.</b> The first execution of every occurrence is reserved here at Iron's price for its
 * native level, all or nothing. The cast event can still change that price; the payment at the
 * effect boundary then settles the difference (refunding a discount, charging an increase or
 * refusing the effect before it runs). A discount cannot rescue a cast the caster cannot afford at
 * the native price: Iron's posts its cast event at cast time and ANS does not post it twice to
 * probe. A native recast is free and reserves nothing.
 */
public final class GlyphAdmission {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GlyphAdmission.class);
    private GlyphAdmission() {}

    /** One imported glyph found in a recipe, at its recipe index. */
    public record Found(int index, ImportedIronEffect effect) {}

    public static List<Found> importedIn(Spell spell) {
        List<Found> found = new ArrayList<>();
        if (spell == null || spell.recipe == null) return found;
        for (int i = 0; i < spell.recipe.size(); i++) {
            if (spell.recipe.get(i) instanceof ImportedIronEffect effect) found.add(new Found(i, effect));
        }
        return found;
    }

    public static boolean containsImported(Spell spell) {
        if (spell == null || spell.recipe == null) return false;
        for (AbstractSpellPart part : spell.recipe) if (part instanceof ImportedIronEffect) return true;
        return false;
    }

    /** Base level plus Amplify minus Dampen, never below the first registered level, before any bound: what the spell asks for. */
    public static int requestedLevel(Spell spell, int index, ImportedGlyphDefinition definition, Player caster) {
        int amplify = 0, dampen = 0;
        for (AbstractAugment augment : spell.getAugments(index, caster)) {
            if (augment == AugmentAmplify.INSTANCE) amplify++;
            else if (augment == AugmentDampen.INSTANCE) dampen++;
        }
        return Math.max(definition.minLevel(), definition.level() + amplify - dampen);
    }

    /**
     * The level an occurrence casts at before Iron's own modifiers: {@link #requestedLevel}, which
     * admission has already checked against Iron's live maximum (an over-maximum request is
     * refused there, never clamped here).
     */
    public static int augmentedLevel(Spell spell, int index, ImportedGlyphDefinition definition, Player caster) {
        return requestedLevel(spell, index, definition, caster);
    }

    /**
     * Admit and fund {@code context}'s imported glyphs. Returns true when the cast holds none, or
     * holds them all funded and stamped with a session; false (with the caster told why) when it
     * must not proceed, in which case nothing is held.
     */
    public static boolean admit(Player player, SpellContext context) {
        List<Found> found = importedIn(context.getSpell());
        if (found.isEmpty()) return true;
        // ANS-002: one predicate for root admission and every later stage; Forge and Ars fake players alike are refused.
        ServerPlayer serverPlayer = GlyphCasters.admissible(player, context);
        if (serverPlayer == null) return false;
        int limit = Math.min(AnsConfig.MAX_IMPORTED_EFFECTS_PER_SPELL.get(), AnsConfig.MAX_IMPORTED_EFFECTS_CEILING);
        if (found.size() > limit) return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.too_many", found.size(), limit);
        IronGlyphBridge bridge = IronGlyphBridges.get();
        if (!com.otectus.arsnspells.glyph.behavior.GlyphBehavior.live().authoritative())
            return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.config_pending");
        for (Found f : found) {
            ImportedGlyphDefinition definition = f.effect().definition();
            GlyphAvailability.Status status = GlyphAvailability.status(definition);
            if (!status.available()) {
                serverPlayer.displayClientMessage(status.message(definition).copy().withStyle(ChatFormatting.RED), true);
                return false;
            }
            // Iron's live requirement: the catalog's flag only says what was true when it was generated.
            if (!bridge.meetsLearning(serverPlayer, definition.spell()))
                return refuse(serverPlayer, "message.ars_n_spells.glyph.requires_irons_learning", f.effect().displayName());
            int requested = requestedLevel(context.getSpell(), f.index(), definition, serverPlayer);
            int liveMax = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.effectiveMax(definition);
            if (requested > liveMax)
                return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.above_max", f.effect().displayName(), requested, liveMax);
            boolean recast = bridge.hasRecast(serverPlayer, definition.spell());
            if (!recast && !bridge.creativeNoCooldown(serverPlayer) && bridge.onCooldown(serverPlayer, definition.spell()))
                return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.cooldown", f.effect().displayName());
            // 3.4.3: Iron's has one cast slot; only a charged or channelled child of the spell keeps this cast out.
            if (GlyphSessions.childPending(serverPlayer.getUUID(), definition.spell()))
                return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.pending", f.effect().displayName());
        }
        // 3.4.3: before the session count (a session that holds nothing is not active) and before the
        // reservations below (a caster low on mana pays for this cast with what the earlier one held).
        java.util.Set<net.minecraft.resources.ResourceLocation> released = new java.util.HashSet<>();
        for (Found f : found)
            if (released.add(f.effect().definition().spell())) GlyphSessions.releaseHoldsFor(serverPlayer, f.effect().definition().spell());
        int sessions = Math.min(AnsConfig.MAX_ACTIVE_GLYPH_SESSIONS_PER_PLAYER.get(), AnsConfig.MAX_GLYPH_SESSIONS_CEILING);
        if (GlyphSessions.activeFor(serverPlayer.getUUID()) >= sessions)
            return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.sessions", sessions);

        CostRules rules = QuoteService.currentRules();
        long now = serverPlayer.level().getGameTime();
        // 3.4.3: as long as Ars lets this cast's projectile fly, within the published bounds.
        long lifetime = GlyphSessions.lifetimeTicks();
        GlyphCastSession session = new GlyphCastSession(serverPlayer, serverPlayer.level().dimension(), now,
            lifetime, rules, GlyphMappingSnapshot.current().generation(),
            new GlyphInvocationBudget(AnsConfig.MAX_FOREIGN_EMISSIONS_PER_CAST.get(), AnsConfig.MAX_FOREIGN_EMISSIONS_CEILING));
        List<CastAttempt> reserved = new ArrayList<>();
        var access = CastLedger.forPlayer(serverPlayer);
        for (Found f : found) {
            ImportedGlyphDefinition definition = f.effect().definition();
            int level = augmentedLevel(context.getSpell(), f.index(), definition, serverPlayer);
            // The one evaluation of Iron's level modifiers for this occurrence (see Occurrence#nativeLevel).
            int nativeLevel = bridge.levelFor(definition.spell(), level, serverPlayer);
            GlyphCastSession.Occurrence occurrence = new GlyphCastSession.Occurrence(f.index(), definition, level, nativeLevel);
            session.occurrences.put(f.index(), occurrence);
            boolean free = bridge.hasRecast(serverPlayer, definition.spell()) || bridge.creativeFree(serverPlayer);
            double cost = free ? 0 : bridge.manaCost(definition.spell(), nativeLevel);
            if (!Double.isFinite(cost)) {
                release(reserved, access);
                releaseLp(session, serverPlayer);
                return refuse(serverPlayer, "message.ars_n_spells.glyph.dormant.spell_missing", f.effect().displayName());
            }
            // 3.4.1 review PAYMENT-01: the resource native casting would take from this caster now.
            occurrence.route = free ? GlyphRoute.MANA : GlyphRoute.decide(serverPlayer);
            // Audit ANS-001: every later execution must still be authorized for this route.
            occurrence.admittedRoute = free ? null : occurrence.route;
            if (occurrence.route == GlyphRoute.AURA_NATIVE) {
                if (!GlyphRoute.auraProviderServices(serverPlayer)) {
                    // A ring only in #ars_n_spells:virtue_rings: Covenant checks its own item and would
                    // charge nothing, so the native delegation is refused rather than marked paid.
                    release(reserved, access);
                    releaseLp(session, serverPlayer);
                    return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.aura_unsupported", f.effect().displayName());
                }
                // Covenant takes the aura at Iron's pre-cast event; the execution then needs its receipt.
                occurrence.quote = EmissionPayment.quote(serverPlayer, rules, 0);
                continue;
            }
            if (occurrence.route == GlyphRoute.LP) {
                Component lpRefusal = EmissionPayment.admitLp(serverPlayer, occurrence,
                    bridge.lpCost(serverPlayer, definition.spell(), nativeLevel, cost), f.effect().displayName());
                if (lpRefusal != null) {
                    release(reserved, access);
                    releaseLp(session, serverPlayer);
                    return refuseWith(serverPlayer, lpRefusal);
                }
                if (occurrence.route == GlyphRoute.LP) {
                    occurrence.quote = EmissionPayment.quote(serverPlayer, rules, 0);
                    continue;
                }
                // The native fallback policy moved it to the mana route.
            }
            CostQuote quote = EmissionPayment.quote(serverPlayer, rules, cost);
            occurrence.quote = quote;
            if (quote.isFree()) continue;
            CastAttempt attempt = CastLedger.open(serverPlayer.getUUID(), "glyph:" + session.id + "/" + f.index(), 0,
                quote, now, lifetime + 20);
            CastLedger.reserve(attempt, access);
            if (!attempt.paymentAccepted()) {
                CastLedger.fail(attempt, access);
                release(reserved, access);
                releaseLp(session, serverPlayer);
                return refuseWith(serverPlayer, withManaOnlyNote(Component.translatable("message.ars_n_spells.glyph.refused.shortage",
                    f.effect().displayName())));
            }
            occurrence.reservation = attempt;
            reserved.add(attempt);
        }
        GlyphSessions.open(serverPlayer, session);
        GlyphSessions.stamp(context, session);
        return true;
    }

    private static void release(List<CastAttempt> reserved, com.otectus.arsnspells.contract.ResourceAccess access) {
        for (CastAttempt attempt : reserved) CastLedger.cancel(attempt, access);
    }

    /**
     * 3.4.1 review PAYMENT-01: Animus pays native Iron's casts with Life Essence inside Iron's own
     * mana block, which an imported glyph never runs through, so imported glyphs are paid only by
     * their route (mana here). When Animus is installed, a mana shortage says so.
     */
    public static Component withManaOnlyNote(Component shortage) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("animus")) return shortage;
        return shortage.copy().append(" ").append(Component.translatable("message.ars_n_spells.glyph.refused.mana_only_note", "Animus"));
    }

    /** A refused admission returns every LP leg it took. */
    private static void releaseLp(GlyphCastSession session, ServerPlayer player) {
        for (GlyphCastSession.Occurrence occurrence : session.occurrences.values())
            EmissionPayment.releaseLp(occurrence, player.getUUID(), player, player.getServer());
    }

    private static boolean refuseWith(ServerPlayer player, Component why) {
        GlyphSessions.record(player, new GlyphSessions.CastRecord(new net.minecraft.resources.ResourceLocation("ars_n_spells", "cast"),
            0, 0, -1, -1, "nothing", "refused " + GlyphSessions.detail(why), "admission", player.level().getGameTime()));
        player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
        LOGGER.debug("[Glyph] admission refused for {}: {}", com.otectus.arsnspells.util.LogPrivacy.token(player.getUUID()), why.getString());
        return false;
    }

    private static boolean refuse(ServerPlayer player, String key, Object... args) {
        GlyphSessions.record(player, new GlyphSessions.CastRecord(new net.minecraft.resources.ResourceLocation("ars_n_spells", "cast"),
            0, 0, -1, -1, "nothing", "refused " + key, "admission", player.level().getGameTime()));
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        LOGGER.debug("[Glyph] admission refused for {}: {}", com.otectus.arsnspells.util.LogPrivacy.token(player.getUUID()), key);
        return false;
    }
}
