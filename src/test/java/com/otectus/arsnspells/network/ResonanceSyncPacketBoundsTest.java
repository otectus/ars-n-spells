package com.otectus.arsnspells.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-006 — verifies that {@link ResonanceSyncPacket} rejects non-finite
 * floats (NaN, +Inf, -Inf) and clamps negative or out-of-range values at decode.
 *
 * <p>Without this, a NaN multiplier from a hostile or version-skewed server
 * would propagate through {@code ResonanceManager.getResonance} into Iron's
 * spell damage math, producing NaN damage values that corrupt entity health.
 */
class ResonanceSyncPacketBoundsTest {

    private static float readResonance(ResonanceSyncPacket pkt) throws NoSuchFieldException, IllegalAccessException {
        Field f = ResonanceSyncPacket.class.getDeclaredField("resonance");
        f.setAccessible(true);
        return f.getFloat(pkt);
    }

    private static ResonanceSyncPacket decode(float value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeFloat(value);
        return new ResonanceSyncPacket(buf);
    }

    @Test
    void finiteValue_roundTripsUnchanged() throws Exception {
        ResonanceSyncPacket pkt = decode(2.5f);
        assertEquals(2.5f, readResonance(pkt));
    }

    @Test
    void nan_isReplacedWithDefaultOne() throws Exception {
        ResonanceSyncPacket pkt = decode(Float.NaN);
        assertEquals(1.0f, readResonance(pkt),
            "NaN must be replaced with 1.0 so it cannot propagate into spell damage math");
    }

    @Test
    void positiveInfinity_isReplacedWithDefaultOne() throws Exception {
        ResonanceSyncPacket pkt = decode(Float.POSITIVE_INFINITY);
        assertEquals(1.0f, readResonance(pkt));
    }

    @Test
    void negativeInfinity_isReplacedWithDefaultOne() throws Exception {
        ResonanceSyncPacket pkt = decode(Float.NEGATIVE_INFINITY);
        assertEquals(1.0f, readResonance(pkt));
    }

    @Test
    void negative_isReplacedWithDefaultOne() throws Exception {
        ResonanceSyncPacket pkt = decode(-5.0f);
        assertEquals(1.0f, readResonance(pkt));
    }

    @Test
    void absurdlyLarge_isClampedToMax100() throws Exception {
        ResonanceSyncPacket pkt = decode(1e9f);
        assertTrue(readResonance(pkt) <= 100.0f);
        assertTrue(readResonance(pkt) > 0.0f);
    }
}
