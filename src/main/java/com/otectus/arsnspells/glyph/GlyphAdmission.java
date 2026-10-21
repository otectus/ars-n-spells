package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.PlayerCaster;
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
 * requires it, off its native cooldown (or holding a native recast) and not already pending in
 * another cast; and fewer than {@code max_active_glyph_sessions_per_player} sessions in flight.
 * Each refusal tells the caster why and costs nothing.
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

    /** The level an occurrence casts at before Iron's equipment bonuses: base, plus Amplify, minus Dampen, within the spell's range. */
    public static int augmentedLevel(Spell spell, int index, ImportedGlyphDefinition definition, Player caster) {
        int amplify = 0, dampen = 0;
        for (AbstractAugment augment : spell.getAugments(index, caster)) {
            if (augment == AugmentAmplify.INSTANCE) amplify++;
            else if (augment == AugmentDampen.INSTANCE) dampen++;
        }
        return Math.max(1, Math.min(definition.maxLevel(), definition.level() + amplify - dampen));
    }

    /**
     * Admit and fund {@code context}'s imported glyphs. Returns true when the cast holds none, or
     * holds them all funded and stamped with a session; false (with the caster told why) when it
     * must not proceed, in which case nothing is held.
     */
    public static boolean admit(Player player, SpellContext context) {
        List<Found> found = importedIn(context.getSpell());
        if (found.isEmpty()) return true;
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof com.hollingsworth.arsnouveau.api.ANFakePlayer
                || !(context.getCaster() instanceof PlayerCaster)) {
            return false;
        }
        int limit = Math.min(AnsConfig.MAX_IMPORTED_EFFECTS_PER_SPELL.get(), AnsConfig.MAX_IMPORTED_EFFECTS_CEILING);
        if (found.size() > limit) return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.too_many", found.size(), limit);
        IronGlyphBridge bridge = IronGlyphBridges.get();
        for (Found f : found) {
            ImportedGlyphDefinition definition = f.effect().definition();
            GlyphAvailability.Status status = GlyphAvailability.status(definition);
            if (!status.available()) {
                serverPlayer.displayClientMessage(status.message(definition).copy().withStyle(ChatFormatting.RED), true);
                return false;
            }
            if (definition.requiresLearning() && !bridge.meetsLearning(serverPlayer, definition.spell()))
                return refuse(serverPlayer, "message.ars_n_spells.glyph.requires_irons_learning", f.effect().displayName());
            boolean recast = bridge.hasRecast(serverPlayer, definition.spell());
            if (!recast && !bridge.creativeNoCooldown(serverPlayer) && bridge.onCooldown(serverPlayer, definition.spell()))
                return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.cooldown", f.effect().displayName());
            if (GlyphSessions.spellPending(serverPlayer.getUUID(), definition.spell()))
                return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.pending", f.effect().displayName());
        }
        int sessions = Math.min(AnsConfig.MAX_ACTIVE_GLYPH_SESSIONS_PER_PLAYER.get(), AnsConfig.MAX_GLYPH_SESSIONS_CEILING);
        if (GlyphSessions.activeFor(serverPlayer.getUUID()) >= sessions)
            return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.sessions", sessions);

        CostRules rules = QuoteService.currentRules();
        long now = serverPlayer.level().getGameTime();
        GlyphCastSession session = new GlyphCastSession(serverPlayer, serverPlayer.level().dimension(), now,
            GlyphSessions.LIFETIME_TICKS, rules, GlyphMappingSnapshot.current().generation(),
            new GlyphInvocationBudget(AnsConfig.MAX_FOREIGN_EMISSIONS_PER_CAST.get(), AnsConfig.MAX_FOREIGN_EMISSIONS_CEILING));
        List<CastAttempt> reserved = new ArrayList<>();
        var access = CastLedger.forPlayer(serverPlayer);
        for (Found f : found) {
            ImportedGlyphDefinition definition = f.effect().definition();
            int level = augmentedLevel(context.getSpell(), f.index(), definition, serverPlayer);
            GlyphCastSession.Occurrence occurrence = new GlyphCastSession.Occurrence(f.index(), definition, level);
            session.occurrences.put(f.index(), occurrence);
            boolean free = bridge.hasRecast(serverPlayer, definition.spell()) || bridge.creativeFree(serverPlayer);
            double cost = free ? 0 : bridge.manaCost(definition.spell(), bridge.levelFor(definition.spell(), level, serverPlayer));
            if (!Double.isFinite(cost)) {
                release(reserved, access);
                return refuse(serverPlayer, "message.ars_n_spells.glyph.dormant.spell_missing", f.effect().displayName());
            }
            CostQuote quote = EmissionPayment.quote(serverPlayer, rules, cost);
            occurrence.quote = quote;
            if (quote.isFree()) continue;
            CastAttempt attempt = CastLedger.open(serverPlayer.getUUID(), "glyph:" + session.id + "/" + f.index(), 0,
                quote, now, GlyphSessions.LIFETIME_TICKS + 20);
            CastLedger.reserve(attempt, access);
            if (!attempt.paymentAccepted()) {
                CastLedger.fail(attempt, access);
                release(reserved, access);
                return refuse(serverPlayer, "message.ars_n_spells.glyph.refused.shortage", f.effect().displayName());
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

    private static boolean refuse(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        LOGGER.debug("[Glyph] admission refused for {}: {}", com.otectus.arsnspells.util.LogPrivacy.token(player.getUUID()), key);
        return false;
    }
}
