package com.otectus.arsnspells.util;

/** Discovery cadence changes latency only; income is specified in mana per server second. */
public final class SourceSynergyPolicy {
    /** Preserve pre-3.3 income when discovery cadence no longer controls payment frequency. */
    public static double migratePerScanMultiplier(double multiplier, int intervalTicks) {
        if (!Double.isFinite(multiplier) || multiplier < 0 || intervalTicks < 1) {
            throw new IllegalArgumentException("Invalid Source income settings");
        }
        return multiplier * 20.0 / intervalTicks;
    }
    private SourceSynergyPolicy() {}

    public static double income(double manaPerSecond, int elapsedTicks) {
        if (!Double.isFinite(manaPerSecond) || manaPerSecond <= 0) return 0;
        // No burst of offline/catch-up income after a stalled or absent player tick.
        return manaPerSecond * Math.max(0, Math.min(20, elapsedTicks)) / 20.0;
    }

    public static boolean expired(long scannedAt, long now, int interval) {
        return now < scannedAt || now - scannedAt >= Math.max(1, interval);
    }
}
