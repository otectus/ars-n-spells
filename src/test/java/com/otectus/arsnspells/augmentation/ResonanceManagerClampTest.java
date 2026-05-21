package com.otectus.arsnspells.augmentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-006 / ANS-HIGH-007 — verifies that {@link ResonanceManager} clamps
 * the client-side resonance value to a finite range and that the static field
 * is marked {@code volatile} so the render thread sees writes from the network
 * thread.
 */
class ResonanceManagerClampTest {

    @Test
    void clientResonanceField_isVolatile() throws NoSuchFieldException {
        Field f = ResonanceManager.class.getDeclaredField("clientResonance");
        assertTrue(java.lang.reflect.Modifier.isVolatile(f.getModifiers()),
            "clientResonance must be volatile so the network->render-thread "
                + "publication is visible (ANS-HIGH-007 / E-MED-06)");
    }

    @Test
    void setClientResonance_rejectsNaN() {
        ResonanceManager.setClientResonance(1.5f); // baseline
        ResonanceManager.setClientResonance(Float.NaN);
        // The setter should have rejected NaN and left the baseline in place.
        // We can't call getResonance() without a Player; check the field directly.
        try {
            Field f = ResonanceManager.class.getDeclaredField("clientResonance");
            f.setAccessible(true);
            double value = f.getDouble(null);
            assertTrue(Double.isFinite(value),
                "clientResonance must remain finite after a NaN setClientResonance call (ANS-HIGH-006)");
            assertEquals(1.5, value, 1.0e-6,
                "clientResonance must keep the prior valid value when NaN is rejected");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void setClientResonance_rejectsInfinity() throws Exception {
        ResonanceManager.setClientResonance(2.0f);
        ResonanceManager.setClientResonance(Float.POSITIVE_INFINITY);
        Field f = ResonanceManager.class.getDeclaredField("clientResonance");
        f.setAccessible(true);
        double value = f.getDouble(null);
        assertEquals(2.0, value, 1.0e-6,
            "clientResonance must keep the prior valid value when Infinity is rejected");
    }

    @Test
    void setClientResonance_clampsAbsurdValueTo100() throws Exception {
        ResonanceManager.setClientResonance(1e9f);
        Field f = ResonanceManager.class.getDeclaredField("clientResonance");
        f.setAccessible(true);
        double value = f.getDouble(null);
        assertTrue(value <= 100.0,
            "clientResonance must be clamped to the MAX_DAMAGE_MULTIPLIER ceiling (100)");
        assertTrue(value > 0.0);
    }

    @Test
    void setClientResonance_clampsNegativeToZero() throws Exception {
        ResonanceManager.setClientResonance(-5.0f);
        Field f = ResonanceManager.class.getDeclaredField("clientResonance");
        f.setAccessible(true);
        double value = f.getDouble(null);
        assertEquals(0.0, value, 1.0e-6,
            "negative resonance values must be clamped to 0 (negative damage multipliers "
                + "would heal enemies)");
    }
}
