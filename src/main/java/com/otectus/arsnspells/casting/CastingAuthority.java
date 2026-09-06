package com.otectus.arsnspells.casting;

import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central authority for spell-casting resource validation.
 *
 * <p>Validates that a player has enough mana <em>in the unified/bridged pool</em>
 * before a spell executes. This matters because in the mana-unification primary
 * modes the underlying mods' native "enough mana?" checks read their own pool,
 * which is not the source of truth — {@code validateManaResource} routes the
 * check through {@link BridgeManager} so it reflects the mode-correct pool.
 *
 * <p><b>Scope note:</b> the 1.20.1 version also handled Cursed-Ring (LP) and
 * Virtue-Ring (Aura) alternate-resource costs via Sanctified Legacy / Covenant.
 * Those ring systems are not ported to 1.21.1 yet, so this is mana-only. The
 * spell-cast denial that the 1.20.1 {@code MixinSpellResolverPreCast} provided is
 * already achieved natively here: {@code MixinManaCapability} redirects Ars's
 * {@code ManaCap} reads through {@link BridgeManager}, so Ars's own
 * {@code canCast()} sees the bridged value and denies/allows correctly.
 */
public class CastingAuthority {
    private static final Logger LOGGER = LoggerFactory.getLogger(CastingAuthority.class);

    private CastingAuthority() {}

    // ---- Source-compatible convenience overloads (no resolver/cost context) ----
    // Retained so any caller that only has a Player still links; without cost
    // information there is nothing to validate, so these allow the cast.
    public static boolean canCast(Player player) { return true; }
    public static boolean canCastIronsSpell(Player player) { return true; }

    /**
     * Validate whether a player can cast an Ars Nouveau spell.
     * If this returns false the spell must not execute.
     *
     * @param player   the casting player
     * @param resolver the resolver carrying the spell cost
     * @return true if the player has sufficient mana in the mode-correct pool
     */
    public static boolean canCastArsSpell(Player player, SpellResolver resolver) {
        if (player == null || resolver == null) {
            logDebug("canCastArsSpell: player or resolver is null");
            return false;
        }

        if (player.isCreative()) {
            logDebug("canCastArsSpell: Creative mode - allowing cast");
            return true;
        }

        int manaCost = resolver.getResolveCost();
        logDebug("canCastArsSpell: cost={} for {}", manaCost, player.getName().getString());

        if (manaCost <= 0) {
            logDebug("canCastArsSpell: zero-cost spell - allowing cast");
            return true;
        }

        logDebug("canCastArsSpell: standard mana validation");
        return validateManaResource(player, manaCost, true);
    }

    /**
     * Validate whether a player can cast an Iron's Spellbooks spell.
     *
     * @param player   the casting player
     * @param manaCost the spell's mana cost
     * @return true if the player has sufficient mana in the mode-correct pool
     */
    public static boolean canCastIronsSpell(Player player, int manaCost) {
        if (player == null) {
            return false;
        }
        if (player.isCreative()) {
            return true;
        }
        if (manaCost <= 0) {
            return true;
        }
        return validateManaResource(player, manaCost, false);
    }

    /**
     * The amount an Ars spell of {@code baseCost} actually costs under the current config.
     *
     * <p>Single source of truth for the Ars-side price, shared by pre-cast validation and by the
     * expend-mana mixin. They used to compute it separately - {@code (float)(cost*rate)} in
     * validation versus {@code (int) Math.round(cost*rate)} when charging - so the amount charged
     * could differ from the amount checked by up to half a point, and at the config's 0.01 rate
     * floor the rounding made every spell under 50 mana free. Both now read one
     * {@link com.otectus.arsnspells.contract.CostQuote} from one {@link CostRules} snapshot.
     */
    public static float effectiveArsCost(int baseCost) {
        return effectiveArsCost(baseCost, AnsQuotes.rules());
    }

    /** As {@link #effectiveArsCost(int)}, against a snapshot the caller took once for the attempt. */
    public static float effectiveArsCost(int baseCost, CostRules rules) {
        if (baseCost <= 0) {
            return 0.0f;
        }
        return AnsQuotes.legAsFloat(
            AnsQuotes.quote(baseCost, ResourceUnit.ARS_MANA, CarrierPolicy.NATIVE_ONLY, rules),
            ResourceUnit.ARS_MANA);
    }

    /**
     * The mode-adjusted cost of an Iron's spell, in the units of whichever pool the active mode
     * spends from. Same quote the charging path reads, so the amount consumed always equals the
     * amount validated - the V05 defect was that in SEPARATE the check converted and the charge
     * did not, so at a rate of 10 a spell was validated at ten times its billed price.
     */
    public static float effectiveIronsCost(int baseCost) {
        return effectiveIronsCost(baseCost, AnsQuotes.rules());
    }

    /** As {@link #effectiveIronsCost(int)}, against a snapshot the caller took once. */
    public static float effectiveIronsCost(int baseCost, CostRules rules) {
        if (baseCost <= 0) {
            return 0.0f;
        }
        return AnsQuotes.legAsFloat(
            AnsQuotes.quote(baseCost, ResourceUnit.IRONS_MANA, CarrierPolicy.NATIVE_ONLY, rules),
            ResourceUnit.IRONS_MANA);
    }

    /**
     * ANS-MED-043: consume the mana previously validated by
     * {@link #canCastIronsSpell(Player, int)}. Iron's scrolls never deduct mana
     * natively, so {@code scroll_cost_mode=full} used to validate the cost and then
     * charge nothing — documented as costing mana while actually being free.
     */
    public static boolean consumeIronsSpellMana(Player player, int manaCost) {
        if (player == null) {
            return false;
        }
        if (player.isCreative() || manaCost <= 0) {
            return true;
        }
        // One snapshot for this charge, taken with the player so the Iron's-to-Ars rate is the
        // same pool-aware value canCastIronsSpell validated against.
        return BridgeManager.consumeManaForMode(
            player, effectiveIronsCost(manaCost, AnsQuotes.rules(player)), ResourceUnit.IRONS_MANA);
    }

    /**
     * Validate mana availability against the mode-correct pool.
     *
     * @param player  the player
     * @param cost    the mana cost
     * @param fromArs true for an Ars spell, false for an Iron's spell
     * @return true if the player can afford the (possibly converted) cost
     */
    private static boolean validateManaResource(Player player, int cost, boolean fromArs) {
        ResourceUnit origin = fromArs ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
        // One snapshot for this attempt: the check below and the charge that follows must be
        // priced by the same config values, not by two reads a tick apart.
        CostRules rules = AnsQuotes.rules(player);
        float effectiveCost = fromArs
            ? effectiveArsCost(cost, rules)
            : effectiveIronsCost(cost, rules);
        float availableMana = BridgeManager.getManaForMode(player, origin);

        boolean canAfford = availableMana >= effectiveCost;

        if (!canAfford) {
            logDebug("Mana validation failed for {}: cost={}, available={}, origin={}",
                player.getName().getString(), effectiveCost, availableMana, origin);
            sendDenialMessage(player,
                "§cNot Enough Mana: Need " + (int) effectiveCost + ", have " + (int) availableMana);
        }

        return canAfford;
    }

    /** Send an action-bar denial message to the player (server side only). */
    public static void sendDenialMessage(Player player, String reason) {
        if (player != null && !player.level().isClientSide()) {
            player.displayClientMessage(Component.literal(reason), true);
        }
    }

    private static void logDebug(String message, Object... args) {
        if (AnsConfig.debugEnabled()) {
            LOGGER.info("[CastingAuthority] [DEBUG] " + message, args);
        }
    }
}
