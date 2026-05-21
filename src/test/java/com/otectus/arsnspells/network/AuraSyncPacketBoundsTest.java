package com.otectus.arsnspells.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-005 — verifies that {@link AuraSyncPacket} clamps payload at decode
 * so hostile / version-skewed servers cannot push absurd values into the HUD.
 *
 * <p>The invariants the production HUD path relies on:
 * <ul>
 *   <li>{@code maxAura} is in {@code [1, 100_000]}</li>
 *   <li>{@code aura} is in {@code [0, maxAura]}</li>
 *   <li>The packet wire format never throws on decode (any int sequence is accepted but clamped)</li>
 * </ul>
 */
class AuraSyncPacketBoundsTest {

    private static int readAura(AuraSyncPacket pkt) throws NoSuchFieldException, IllegalAccessException {
        Field f = AuraSyncPacket.class.getDeclaredField("aura");
        f.setAccessible(true);
        return f.getInt(pkt);
    }

    private static int readMaxAura(AuraSyncPacket pkt) throws NoSuchFieldException, IllegalAccessException {
        Field f = AuraSyncPacket.class.getDeclaredField("maxAura");
        f.setAccessible(true);
        return f.getInt(pkt);
    }

    private static AuraSyncPacket decode(int aura, int maxAura) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(aura);
        buf.writeInt(maxAura);
        return new AuraSyncPacket(buf);
    }

    @Test
    void normalValues_roundTripUnchanged() throws Exception {
        AuraSyncPacket pkt = decode(50, 100);
        assertEquals(50, readAura(pkt));
        assertEquals(100, readMaxAura(pkt));
    }

    @Test
    void maxAuraIntegerMaxValue_isClampedTo100k() throws Exception {
        AuraSyncPacket pkt = decode(50, Integer.MAX_VALUE);
        assertEquals(100_000, readMaxAura(pkt),
            "maxAura must be clamped to 100_000 (matches AnsConfig.AURA_MAX_DEFAULT ceiling)");
    }

    @Test
    void auraGreaterThanMaxAura_isClampedToMaxAura() throws Exception {
        AuraSyncPacket pkt = decode(Integer.MAX_VALUE, 50);
        assertEquals(50, readMaxAura(pkt));
        assertEquals(50, readAura(pkt),
            "aura must be clamped to maxAura so the render fraction is always in [0,1]");
    }

    @Test
    void maxAuraZero_isRaisedToOne() throws Exception {
        AuraSyncPacket pkt = decode(0, 0);
        assertEquals(1, readMaxAura(pkt),
            "maxAura must be at least 1 to avoid divide-by-zero in HUD render math");
    }

    @Test
    void maxAuraNegative_isRaisedToOne() throws Exception {
        AuraSyncPacket pkt = decode(0, -42);
        assertEquals(1, readMaxAura(pkt));
    }

    @Test
    void auraNegative_isRaisedToZero() throws Exception {
        AuraSyncPacket pkt = decode(-999, 100);
        assertEquals(0, readAura(pkt));
        assertTrue(readAura(pkt) <= readMaxAura(pkt));
    }

    @Test
    void hostilePayload_auraMaxIntMaxMaxIntMin_isFullyClamped() throws Exception {
        AuraSyncPacket pkt = decode(Integer.MAX_VALUE, Integer.MIN_VALUE);
        assertEquals(1, readMaxAura(pkt));
        assertEquals(1, readAura(pkt));
    }
}
