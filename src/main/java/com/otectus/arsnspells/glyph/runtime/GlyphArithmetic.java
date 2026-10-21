package com.otectus.arsnspells.glyph.runtime;

import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.ResourceAmount;

import java.util.List;

/** The pure arithmetic of imported-glyph casting, kept apart so it is unit-tested without a server. */
public final class GlyphArithmetic {
    /** Amplify raises the effective level by at most this many levels. */
    public static final int MAX_AMPLIFY_LEVELS = 2;

    private GlyphArithmetic() {
    }

    /** Base level, raised by Amplify (at most two) and lowered by Dampen, kept within the catalogued levels. */
    public static int effectiveLevel(int base, int min, int max, int amplify, int dampen) {
        int raised = base + Math.min(MAX_AMPLIFY_LEVELS, Math.max(0, amplify)) - Math.max(0, dampen);
        return Math.max(min, Math.min(max, raised));
    }

    /** A native cast time shortened by each Accelerate (x0.75) or lengthened by each Decelerate (x1.25); at least one tick. */
    public static int chargeTicks(int nativeTicks, int speed) {
        double factor = speed >= 0 ? Math.pow(0.75, speed) : Math.pow(1.25, -speed);
        return Math.max(1, (int) Math.round(Math.max(1, nativeTicks) * factor));
    }

    /** Projectile launch speed factor: +25% per Accelerate, x0.8 per Decelerate. */
    public static double launchSpeedFactor(int speed) {
        return speed >= 0 ? 1.0 + 0.25 * speed : Math.pow(0.8, -speed);
    }

    /** Extra channel pulses: two per Extend Time, minus two per Duration Down. */
    public static int extraPulses(int extendTime, int durationDown) {
        return 2 * (Math.max(0, extendTime) - Math.max(0, durationDown));
    }

    /** Whether two quotes charge the same amounts to the same pools, in order. */
    public static boolean sameLegs(CostQuote a, CostQuote b) {
        List<ResourceAmount> x = a.legs();
        List<ResourceAmount> y = b.legs();
        if (x.size() != y.size()) return false;
        for (int i = 0; i < x.size(); i++) {
            if (x.get(i).unit() != y.get(i).unit() || Math.abs(x.get(i).amount() - y.get(i).amount()) > 1.0e-6) return false;
        }
        return true;
    }
}
