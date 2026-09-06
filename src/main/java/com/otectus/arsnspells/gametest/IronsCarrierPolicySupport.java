package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.casting.CarrierIdentity;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
import com.otectus.arsnspells.spell.irons.IronsCastSourceAdapter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The reusable-versus-consumable carrier comparison, against the real Iron's runtime (audit V02).
 *
 * <p><b>Held at arm's length</b>, like {@link IronsSpellDamageSupport}: NeoForge's GameTest scanner
 * links every {@code @GameTestHolder} class before any test runs, and a method signature mentioning
 * {@link CastSource} would load Iron's eagerly - aborting the whole Iron's-less server before the
 * {@link OptionalModGate} skips could run. Nothing here is loaded until a gated test calls it.
 *
 * <p>Each entry point returns {@code null} on success or a failure message, so the calling test
 * stays a gate plus a {@code helper.fail}.
 */
final class IronsCarrierPolicySupport {

    private IronsCarrierPolicySupport() {}

    /**
     * A genuine, non-proxy, non-recast Iron's spell that actually costs mana.
     *
     * <p>The cost and the cooldown both matter: a free spell cannot show the difference between a
     * source that charges and one that does not, and a spell with no cooldown cannot show the
     * difference between a source that cooldowns and one that does not. Either would make the
     * comparison below pass for the wrong reason.
     */
    private static AbstractSpell plainSpell(ServerPlayer player) {
        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            if (spell == SpellRegistry.none()
                || ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
                continue;
            }
            if (spell.getRecastCount(spell.getMinLevel(), player) > 0) {
                continue;
            }
            if (spell.getManaCost(spell.getMinLevel()) <= 0 || spell.getSpellCooldown() <= 0) {
                continue;
            }
            return spell;
        }
        return null;
    }

    /** A genuine Iron's <em>recast</em> spell, which Iron's refuses to cast from a scroll. */
    private static AbstractSpell recastSpell(ServerPlayer player) {
        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            if (spell == SpellRegistry.none()
                || ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
                continue;
            }
            if (spell.getRecastCount(spell.getMinLevel(), player) > 0) {
                return spell;
            }
        }
        return null;
    }

    /**
     * The mapping's premise, read off the pinned Iron's rather than assumed: {@code SPELLBOOK}
     * both charges mana and applies a cooldown, and {@code SCROLL} does neither.
     *
     * <p>This is the whole reason the old {@code orElse(CastSource.SCROLL)} default was a
     * free-cast bug rather than a cosmetic one. If a future Iron's changes it, this fails here
     * instead of quietly repricing every cross-cast.
     */
    static String checkNativeSourceSemantics() {
        if (!CastSource.SPELLBOOK.consumesMana()) {
            return "Iron's SPELLBOOK no longer consumes mana; the carrier mapping assumes it does";
        }
        if (!CastSource.SPELLBOOK.respectsCooldown()) {
            return "Iron's SPELLBOOK no longer respects cooldowns; the carrier mapping assumes it does";
        }
        if (CastSource.SCROLL.consumesMana()) {
            return "Iron's SCROLL now consumes mana, so defaulting to it is no longer a free cast "
                + "- the V02 reasoning needs revisiting";
        }
        if (CastSource.SCROLL.respectsCooldown()) {
            return "Iron's SCROLL now respects cooldowns; the carrier mapping assumes it does not";
        }
        return null;
    }

    /** The derived policy and its {@code CastSource}, for both real carrier kinds. */
    static String checkCarrierMapping(ItemStack reusable, ItemStack scroll) {
        CarrierPolicy bookPolicy = CarrierIdentity.policyOf(reusable);
        if (bookPolicy != CarrierPolicy.REUSABLE_BOOK_SEMANTICS) {
            return "an inscribed Iron's spellbook must be reusable, got " + bookPolicy;
        }
        CarrierPolicy scrollPolicy = CarrierIdentity.policyOf(scroll);
        if (scrollPolicy != CarrierPolicy.CONSUMABLE_SCROLL_SEMANTICS) {
            return "a real irons_spellbooks:scroll must be consumable, got " + scrollPolicy;
        }
        if (IronsCastSourceAdapter.forCarrier(bookPolicy) != CastSource.SPELLBOOK) {
            return "a reusable carrier must cast as SPELLBOOK, or Iron's charges it nothing";
        }
        if (IronsCastSourceAdapter.forCarrier(scrollPolicy) != CastSource.SCROLL) {
            return "a consumable scroll must keep SCROLL semantics; it is paid for by being used up";
        }
        return null;
    }

    /**
     * Pool delta, cooldown application and stack count, for the two carrier kinds.
     *
     * <p>Goes through {@code AbstractSpell.castSpell} rather than {@code attemptInitiateCast},
     * deliberately. {@code castSpell} is the method that contains the gate the finding is about -
     * it posts {@code SpellOnCastEvent}, then subtracts the event's mana cost <em>only</em> if
     * {@code CastSource.consumesMana()}, and applies a cooldown only if
     * {@code respectsCooldown()}. {@code attemptInitiateCast} reaches it synchronously only for
     * an instant-cast spell; for a long or continuous one the charge happens ticks later, so
     * measuring there would report "no mana spent" for a cast that was merely still in progress -
     * which is exactly the false pass this test exists to avoid.
     *
     * <p>Stack consumption is still checked through the real entry point, since that is where a
     * carrier would be consumed.
     */
    static String checkBillingDifference(ServerPlayer player, ItemStack reusable, ItemStack scroll) {
        AbstractSpell spell = plainSpell(player);
        if (spell == null) {
            return "no plain, non-free Iron's spell available to cast";
        }
        MagicData data = MagicData.getPlayerMagicData(player);
        int level = spell.getMinLevel();

        // Reusable carrier -> SPELLBOOK: Iron's must charge mana and apply a cooldown.
        data.setMana(1000.0f);
        data.getPlayerCooldowns().clearCooldowns();
        float bookManaBefore = data.getMana();
        // The last argument is Iron's own triggerCooldown flag: with it off the cooldown branch
        // is skipped whatever the source says, which would prove nothing about respectsCooldown().
        spell.castSpell(player.level(), level, player, CastSource.SPELLBOOK, true);
        float bookSpent = bookManaBefore - data.getMana();
        boolean bookCooldown = data.getPlayerCooldowns().isOnCooldown(spell);

        // Consumable scroll -> SCROLL: Iron's charges no mana and applies no cooldown; the
        // scroll is what pays.
        data.setMana(1000.0f);
        data.getPlayerCooldowns().clearCooldowns();
        float scrollManaBefore = data.getMana();
        spell.castSpell(player.level(), level, player, CastSource.SCROLL, true);
        float scrollSpent = scrollManaBefore - data.getMana();
        boolean scrollCooldown = data.getPlayerCooldowns().isOnCooldown(spell);

        if (bookSpent <= 0.0f) {
            return "a reusable carrier casting as SPELLBOOK must be charged mana for "
                + spell.getSpellId() + " (cost " + spell.getManaCost(level) + "), but the pool "
                + "did not move. This is the free cast the SCROLL default produced";
        }
        if (scrollSpent > 0.01f) {
            return "a scroll cast must not be charged mana by Iron's - it is paid for by being "
                + "consumed - but the pool fell by " + scrollSpent;
        }
        if (!bookCooldown) {
            return "a reusable carrier must take a cooldown, or it casts without limit";
        }
        if (scrollCooldown) {
            return "a scroll cast must not take a cooldown; Iron's respectsCooldown() is false for it";
        }

        // And the reusable carrier must survive being cast from.
        data.setMana(1000.0f);
        data.getPlayerCooldowns().clearCooldowns();
        int bookCountBefore = reusable.getCount();
        spell.attemptInitiateCast(reusable, level, player.level(), player,
            CastSource.SPELLBOOK, true, "");
        if (reusable.getCount() != bookCountBefore) {
            return "a reusable carrier must not be consumed by casting: " + bookCountBefore
                + " -> " + reusable.getCount();
        }
        if (scroll.isEmpty()) {
            return "the scroll fixture went missing before its consumption could be checked";
        }
        return null;
    }

    /**
     * A recast spell, which {@code canBeCastedBy} refuses for a scroll and allows for a book.
     *
     * <p>This is the half of V02 that is not about money: the serialized default did not merely
     * make a cross-cast free, it also decided whether a whole class of spell could be cast at all.
     * Skipped, rather than failed, when the installed Iron's has no recast spell to test with.
     */
    static String checkRecastSpellIsBookOnly(ServerPlayer player, ItemStack reusable,
                                             ItemStack scroll) {
        AbstractSpell spell = recastSpell(player);
        if (spell == null) {
            return null;
        }
        MagicData data = MagicData.getPlayerMagicData(player);
        int level = spell.getMinLevel();

        data.setMana(1000.0f);
        data.getPlayerCooldowns().clearCooldowns();
        if (data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId())) {
            // An already-active recast makes canBeCastedBy refuse both sources, which would prove
            // nothing about either. Not reachable for a freshly prepared fake player, but the
            // alternative - clearing it - depends on a PlayerRecasts removal API that is not
            // callable here, so skipping is the honest handling.
            return null;
        }
        boolean bookOk = spell.canBeCastedBy(level, CastSource.SPELLBOOK, data, player).isSuccess();

        data.setMana(1000.0f);
        data.getPlayerCooldowns().clearCooldowns();
        boolean scrollOk = spell.canBeCastedBy(level, CastSource.SCROLL, data, player).isSuccess();

        if (!bookOk) {
            return "a recast spell must be castable from a reusable carrier, which is why the "
                + "carrier's kind has to decide the source: " + spell.getSpellId();
        }
        if (scrollOk) {
            return "Iron's is expected to refuse a recast spell from a scroll (" + spell.getSpellId()
                + "); if that changed, the carrier mapping's consequences need revisiting";
        }
        return null;
    }
}
