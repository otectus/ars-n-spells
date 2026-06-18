package com.otectus.arsnspells.casting;

import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
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
     * Validate mana availability against the mode-correct pool.
     *
     * @param player  the player
     * @param cost    the mana cost
     * @param fromArs true for an Ars spell, false for an Iron's spell
     * @return true if the player can afford the (possibly converted) cost
     */
    private static boolean validateManaResource(Player player, int cost, boolean fromArs) {
        float availableMana;
        float effectiveCost = cost;

        if (!BridgeManager.isUnificationEnabled()) {
            // No unification: each system uses its own native pool.
            if (fromArs) {
                availableMana = BridgeManager.getBridge().getMana(player);
            } else {
                availableMana = BridgeManager.isIronsSpellbooksLoaded()
                    ? BridgeManager.getManaForMode(player, false) : 0;
            }
        } else {
            // Unified: apply the cross-system conversion rate, read the mode pool.
            double conversionRate = fromArs
                ? AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get()
                : AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
            effectiveCost = (float) (cost * conversionRate);
            availableMana = BridgeManager.getManaForMode(player, fromArs);
        }

        boolean canAfford = availableMana >= effectiveCost;

        if (!canAfford) {
            logDebug("Mana validation failed for {}: cost={}, available={}, fromArs={}",
                player.getName().getString(), effectiveCost, availableMana, fromArs);
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
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CastingAuthority] [DEBUG] " + message, args);
        }
    }
}
