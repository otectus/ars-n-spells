package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Single point of truth for converting mana regen values between Ars Nouveau
 * and Iron's Spellbooks unit systems.
 *
 * <p>Ars Nouveau represents regen as an absolute rate ({@code mana/sec}).
 * Iron's Spellbooks represents regen as a multiplier on the max pool: each tick,
 * Iron's adds approximately {@code max_mana * MANA_REGEN_attr * IRONS_REGEN_PER_SECOND_FACTOR}
 * mana per second. Treating one as the other (without going through this bridge)
 * is a unit-mismatch bug.
 *
 * <p>All cross-system code paths that translate a regen number from one system
 * to the other MUST go through this class. Direct multiplication by
 * {@code CONVERSION_RATE_*} alone is not sufficient — those rates handle pool
 * scaling, not unit conversion.
 */
public final class ManaRegenBridge {

    /**
     * Iron's Spellbooks effective regen formula (per second):
     * {@code regen_per_sec ≈ MAX_MANA * MANA_REGEN_attr * 0.01}.
     * Verified against irons-spells-n-spellbooks 1.20.1 and the user-observed
     * baseline of {@code MANA_REGEN=1, MAX_MANA=1000 -> 10 mana/sec}.
     */
    public static final double IRONS_REGEN_PER_SECOND_FACTOR = 0.01;

    private ManaRegenBridge() {}

    /**
     * Conversion strategies for cross-system regen translation.
     */
    public enum ConversionMode {
        /** Match the absolute mana/sec effect using the target system's current max pool. */
        EQUAL_EFFECT,
        /** Use a fixed reference pool size for conversion (predictable, pool-independent). */
        REFERENCE_POOL,
        /** Disable cross-system regen translation entirely. */
        DISABLED;

        public static ConversionMode fromString(String s) {
            if (s == null) return EQUAL_EFFECT;
            try {
                return ConversionMode.valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return EQUAL_EFFECT;
            }
        }
    }

    /**
     * Convert an absolute Ars regen value ({@code mana/sec}) into the equivalent
     * Iron's MANA_REGEN attribute delta for a given current max mana pool.
     *
     * @param arsAbsPerSec absolute regen rate in mana/sec
     * @param ironsMaxMana current Iron's MAX_MANA attribute value at conversion time
     * @return delta to add to the Iron's MANA_REGEN attribute, or 0 if max &le; 0
     */
    public static double arsToIronsRegen(double arsAbsPerSec, double ironsMaxMana) {
        if (arsAbsPerSec == 0.0 || ironsMaxMana <= 0.0) {
            return 0.0;
        }
        return arsAbsPerSec / (ironsMaxMana * IRONS_REGEN_PER_SECOND_FACTOR);
    }

    /**
     * Convert an Iron's MANA_REGEN attribute value into its equivalent absolute
     * regen rate ({@code mana/sec}) at a given current max mana pool.
     */
    public static double ironsToArsRegen(double ironsRegenAttr, double ironsMaxMana) {
        if (ironsRegenAttr == 0.0 || ironsMaxMana <= 0.0) {
            return 0.0;
        }
        return ironsRegenAttr * ironsMaxMana * IRONS_REGEN_PER_SECOND_FACTOR;
    }

    /**
     * Convert from Ars to Iron's regen units, honouring the configured
     * conversion mode and global cross-system multiplier.
     *
     * @param arsAbsPerSec absolute regen rate in mana/sec
     * @param player       the affected player (used to read live max mana for EQUAL_EFFECT)
     * @return delta to add to the Iron's MANA_REGEN attribute
     */
    public static double convertArsToIrons(double arsAbsPerSec, Player player) {
        if (arsAbsPerSec == 0.0) return 0.0;
        ConversionMode mode = getConversionMode();
        if (mode == ConversionMode.DISABLED) return 0.0;

        double maxMana = (mode == ConversionMode.REFERENCE_POOL)
            ? AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL.get()
            : getCurrentIronsMaxMana(player);

        double base = arsToIronsRegen(arsAbsPerSec, maxMana);
        return base * AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER.get();
    }

    /**
     * Convert from Iron's to Ars regen units, honouring the configured
     * conversion mode and global cross-system multiplier.
     *
     * @param ironsRegenAttr value of the Iron's MANA_REGEN attribute
     * @param player         the affected player (used to read live max mana for EQUAL_EFFECT)
     * @return absolute Ars regen rate ({@code mana/sec}) to add to the Ars regen event
     */
    public static double convertIronsToArs(double ironsRegenAttr, Player player) {
        if (ironsRegenAttr == 0.0) return 0.0;
        ConversionMode mode = getConversionMode();
        if (mode == ConversionMode.DISABLED) return 0.0;

        double maxMana = (mode == ConversionMode.REFERENCE_POOL)
            ? AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL.get()
            : getCurrentIronsMaxMana(player);

        double base = ironsToArsRegen(ironsRegenAttr, maxMana);
        return base * AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER.get();
    }

    /**
     * Read the player's current Iron's MAX_MANA attribute value (post-modifier).
     * Returns 0 if Iron's is not loaded or the read fails.
     */
    public static double getCurrentIronsMaxMana(Player player) {
        if (player == null || !ModList.get().isLoaded("irons_spellbooks")) {
            return 0.0;
        }
        try {
            return player.getAttributeValue(AttributeRegistry.MAX_MANA);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static ConversionMode getConversionMode() {
        return ConversionMode.fromString(AnsConfig.CROSS_SYSTEM_REGEN_CONVERSION.get());
    }
}
