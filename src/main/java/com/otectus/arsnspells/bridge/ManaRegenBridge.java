package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.player.Player;

/**
 * Single point of truth for converting mana regen values between Ars Nouveau
 * and Iron's Spellbooks unit systems.
 *
 * <p><b>The measured model (audit ANS-011).</b> Neither system's number is "mana per second":
 * <ul>
 *   <li>Ars Nouveau's regen ({@code ManaUtil.getManaRegen}, the {@code ManaRegenCalcEvent} value and
 *       {@code PerkAttributes.MANA_REGEN_BONUS}) is a <em>nominal</em> rate.
 *       {@code ManaCapEvents.playerOnTick} runs on every server tick whose game time is a multiple of
 *       {@code updateInterval} (default 5), {@link #ARS_CALLS_PER_ELIGIBLE_TICK} times, and adds
 *       {@code regen / max(1, (int) MEAN_TPS / updateInterval)} each time. One nominal point is
 *       therefore {@link #arsRealPerNominal(int, double)} real mana per second; the integer division
 *       makes intervals that do not divide 20 deliver more (interval 3: 1.11x, interval 7: 1.43x).</li>
 *   <li>Iron's MANA_REGEN attribute is a fraction of the pool: {@code MagicManager.regenPlayerMana} adds
 *       {@code MAX_MANA * MANA_REGEN * 0.01 * manaRegenMultiplier} per call, every
 *       {@code MANA_REGEN_TICKS} (10) ticks. One attribute point is
 *       {@link #ironsRealPerSecondFactor(int, double)} times the pool per second: 0.02 by default.</li>
 * </ul>
 * The earlier helper called {@code max * attr * 0.01} "per second" and Ars's value "mana/sec". On
 * Forge 1.20.1 both native loops double their nominal values at the defaults, so the two factors of
 * two cancelled and the default configuration converted correctly; they stop cancelling once Iron's
 * {@code manaRegenMultiplier} or Ars's {@code updateInterval} differ from the defaults. On NeoForge
 * 1.21.1, Ars 5 does not double, so the earlier helper gave bridged Iron's regeneration twice the
 * Ars equivalent even at the defaults. {@code RegenCadenceGameTests} measured both on each loader.
 * The conversion below matches real mana per second on both sides; on Forge at the default settings
 * it returns exactly what it did before.
 *
 * <p>All cross-system code paths that translate a regen number from one system
 * to the other MUST go through this class. Direct multiplication by
 * {@code CONVERSION_RATE_*} alone is not sufficient — those rates handle pool
 * scaling, not unit conversion.
 */
public final class ManaRegenBridge {

    /** Server ticks per second the native schedulers are written against. */
    public static final double NOMINAL_TPS = 20.0;
    /**
     * How often Ars's regen handler runs per eligible tick. Forge 1.20.1, Ars Nouveau 4.12.7: twice,
     * since it has no phase filter on {@code PlayerTickEvent} and runs for START and END. (NeoForge
     * 1.21.1, Ars Nouveau 5.13.1: once, on {@code PlayerTickEvent.Pre}.)
     */
    public static final int ARS_CALLS_PER_ELIGIBLE_TICK = 2;
    /** Ars Nouveau 4.12.7 defaults: {@code updateInterval} and {@code ManaCapEvents.MEAN_TPS}. */
    public static final int ARS_DEFAULT_REGEN_INTERVAL = 5;
    public static final double ARS_DEFAULT_MEAN_TPS = 20.0;
    /** Iron's per-call fraction of the pool per attribute point, and its default cadence. */
    public static final double IRONS_REGEN_PER_CALL_FACTOR = 0.01;
    public static final int IRONS_DEFAULT_REGEN_TICKS = 10;

    private ManaRegenBridge() {}

    /** Real Ars mana per second delivered by one nominal Ars regen point. */
    public static double arsRealPerNominal(int regenInterval, double meanTps) {
        int interval = Math.max(1, regenInterval);
        double eligibleTicksPerSecond = NOMINAL_TPS / interval;
        int divisor = Math.max(1, (int) meanTps / interval);
        return ARS_CALLS_PER_ELIGIBLE_TICK * eligibleTicksPerSecond / divisor;
    }

    /** Real Iron's mana per second, per point of max mana, delivered by one MANA_REGEN attribute point. */
    public static double ironsRealPerSecondFactor(int regenTicks, double nativeMultiplier) {
        if (!Double.isFinite(nativeMultiplier) || nativeMultiplier <= 0.0) return 0.0;
        return IRONS_REGEN_PER_CALL_FACTOR * (NOMINAL_TPS / Math.max(1, regenTicks)) * nativeMultiplier;
    }

    /** The default-settings factors, for the pure two-argument helpers. */
    private static final double DEFAULT_ARS_REAL = arsRealPerNominal(ARS_DEFAULT_REGEN_INTERVAL, ARS_DEFAULT_MEAN_TPS);
    private static final double DEFAULT_IRONS_REAL = ironsRealPerSecondFactor(IRONS_DEFAULT_REGEN_TICKS, 1.0);

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
     * The Iron's MANA_REGEN attribute delta that delivers the same real mana per second as
     * {@code arsNominal} Ars regen points, for a pool of {@code ironsMaxMana}, at the default
     * native settings. See {@link #arsToIronsRegen(double, double, double, double)}.
     */
    public static double arsToIronsRegen(double arsNominal, double ironsMaxMana) {
        return arsToIronsRegen(arsNominal, ironsMaxMana, DEFAULT_ARS_REAL, DEFAULT_IRONS_REAL);
    }

    /**
     * The nominal Ars regen points that deliver the same real mana per second as
     * {@code ironsRegenAttr} Iron's attribute points on a pool of {@code ironsMaxMana}, at the
     * default native settings. See {@link #ironsToArsRegen(double, double, double, double)}.
     */
    public static double ironsToArsRegen(double ironsRegenAttr, double ironsMaxMana) {
        return ironsToArsRegen(ironsRegenAttr, ironsMaxMana, DEFAULT_ARS_REAL, DEFAULT_IRONS_REAL);
    }

    /**
     * Ars nominal regen to an Iron's MANA_REGEN delta under explicit native factors
     * ({@link #arsRealPerNominal}, {@link #ironsRealPerSecondFactor}). Zero, never a non-finite
     * value, when either side cannot deliver regeneration (an empty pool, a disabled Iron's
     * multiplier).
     */
    public static double arsToIronsRegen(double arsNominal, double ironsMaxMana, double arsRealPerNominal, double ironsRealFactor) {
        if (arsNominal == 0.0 || !(ironsMaxMana > 0.0) || !(ironsRealFactor > 0.0) || !Double.isFinite(arsRealPerNominal)) {
            return 0.0;
        }
        double result = arsNominal * arsRealPerNominal / (ironsMaxMana * ironsRealFactor);
        return Double.isFinite(result) ? result : 0.0;
    }

    /** Iron's MANA_REGEN points to Ars nominal regen under explicit native factors; zero when nothing can be delivered. */
    public static double ironsToArsRegen(double ironsRegenAttr, double ironsMaxMana, double arsRealPerNominal, double ironsRealFactor) {
        if (ironsRegenAttr == 0.0 || !(ironsMaxMana > 0.0) || !(arsRealPerNominal > 0.0) || !Double.isFinite(ironsRealFactor)) {
            return 0.0;
        }
        double result = ironsRegenAttr * ironsMaxMana * ironsRealFactor / arsRealPerNominal;
        return Double.isFinite(result) ? result : 0.0;
    }

    /** The live Ars factor: its own {@code updateInterval} and {@code MEAN_TPS}, the defaults when unreadable. */
    public static double liveArsRealPerNominal() {
        int interval = ARS_DEFAULT_REGEN_INTERVAL;
        double meanTps = ARS_DEFAULT_MEAN_TPS;
        try {
            interval = com.hollingsworth.arsnouveau.setup.config.ServerConfig.REGEN_INTERVAL.get();
            meanTps = com.hollingsworth.arsnouveau.common.event.ManaCapEvents.MEAN_TPS;
        } catch (RuntimeException | LinkageError notReadable) {
            // Config not loaded yet: the defaults, which is what Ars itself runs until it is.
        }
        return arsRealPerNominal(interval, meanTps);
    }

    /** The live Iron's factor, through the Iron's-isolated reader; the default when Iron's is absent. */
    public static double liveIronsRealFactor() {
        if (!IronsCompat.isLoaded()) return DEFAULT_IRONS_REAL;
        try {
            return ironsRealPerSecondFactor(com.otectus.arsnspells.compat.irons_spells.IronsRegenSettings.regenTicks(),
                com.otectus.arsnspells.compat.irons_spells.IronsRegenSettings.multiplier());
        } catch (RuntimeException | LinkageError notReadable) {
            return DEFAULT_IRONS_REAL;
        }
    }

    /**
     * Convert from Ars to Iron's regen units, honouring the configured
     * conversion mode, the global cross-system multiplier and both mods' live regen schedules.
     *
     * @param arsNominal Ars nominal regen points (already scaled by the pool conversion rate)
     * @param player     the affected player (used to read live max mana for EQUAL_EFFECT)
     * @return delta to add to the Iron's MANA_REGEN attribute
     */
    public static double convertArsToIrons(double arsNominal, Player player) {
        if (arsNominal == 0.0) return 0.0;
        ConversionMode mode = getConversionMode();
        if (mode == ConversionMode.DISABLED) return 0.0;

        double maxMana = (mode == ConversionMode.REFERENCE_POOL)
            ? AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL.get()
            : getCurrentIronsMaxMana(player);

        double base = arsToIronsRegen(arsNominal, maxMana, liveArsRealPerNominal(), liveIronsRealFactor());
        return base * AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER.get();
    }

    /**
     * Convert from Iron's to Ars regen units, honouring the configured
     * conversion mode, the global cross-system multiplier and both mods' live regen schedules.
     *
     * @param ironsRegenAttr value of the Iron's MANA_REGEN attribute
     * @param player         the affected player (used to read live max mana for EQUAL_EFFECT)
     * @return nominal Ars regen points to add to the Ars regen event
     */
    public static double convertIronsToArs(double ironsRegenAttr, Player player) {
        if (ironsRegenAttr == 0.0) return 0.0;
        ConversionMode mode = getConversionMode();
        if (mode == ConversionMode.DISABLED) return 0.0;

        double maxMana = (mode == ConversionMode.REFERENCE_POOL)
            ? AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL.get()
            : getCurrentIronsMaxMana(player);

        double base = ironsToArsRegen(ironsRegenAttr, maxMana, liveArsRealPerNominal(), liveIronsRealFactor());
        return base * AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER.get();
    }

    /**
     * Read the player's current Iron's MAX_MANA attribute value (post-modifier).
     * Returns 0 if Iron's is not loaded or the read fails.
     */
    public static double getCurrentIronsMaxMana(Player player) {
        if (player == null || !IronsCompat.isLoaded()) {
            return 0.0;
        }
        try {
            return player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static ConversionMode getConversionMode() {
        return ConversionMode.fromString(AnsConfig.CROSS_SYSTEM_REGEN_CONVERSION.get());
    }
}
