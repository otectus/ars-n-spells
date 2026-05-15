package com.otectus.arsnspells.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FriendlyByteBuf wire round-trip for {@link CrossCastRequestPacket}. Mirrors
 * the bootstrap-free style of {@code CrossCastNbtRoundTripTest}: a packet
 * write/read cycle is testable without booting Minecraft because the buffer
 * itself is just netty.
 */
class CrossCastRequestPacketTest {

    @Test
    void mainHandCastAction_roundTripsBitIdentically() {
        UUID id = UUID.randomUUID();
        CrossCastRequestPacket original =
            new CrossCastRequestPacket(InteractionHand.MAIN_HAND,
                CrossCastRequestPacket.Action.CAST, 2, id);
        CrossCastRequestPacket round = writeAndRead(original);

        assertEquals(InteractionHand.MAIN_HAND, round.hand());
        assertEquals(CrossCastRequestPacket.Action.CAST, round.action());
        assertEquals(2, round.clientSelectedIndex());
        assertEquals(id, round.clientAttemptId());
    }

    @Test
    void offHandCycleAction_roundTrips() {
        UUID id = UUID.randomUUID();
        CrossCastRequestPacket original =
            new CrossCastRequestPacket(InteractionHand.OFF_HAND,
                CrossCastRequestPacket.Action.CYCLE, 7, id);
        CrossCastRequestPacket round = writeAndRead(original);

        assertEquals(InteractionHand.OFF_HAND, round.hand());
        assertEquals(CrossCastRequestPacket.Action.CYCLE, round.action());
        assertEquals(7, round.clientSelectedIndex());
        assertEquals(id, round.clientAttemptId());
    }

    @Test
    void zeroIndex_roundTrips() {
        UUID id = UUID.randomUUID();
        CrossCastRequestPacket original =
            new CrossCastRequestPacket(InteractionHand.MAIN_HAND,
                CrossCastRequestPacket.Action.CAST, 0, id);
        CrossCastRequestPacket round = writeAndRead(original);
        assertEquals(0, round.clientSelectedIndex());
    }

    @Test
    void largeIndex_varIntEncodesAndDecodes() {
        UUID id = UUID.randomUUID();
        CrossCastRequestPacket original =
            new CrossCastRequestPacket(InteractionHand.MAIN_HAND,
                CrossCastRequestPacket.Action.CAST, 100_000, id);
        CrossCastRequestPacket round = writeAndRead(original);
        assertEquals(100_000, round.clientSelectedIndex());
        assertEquals(id, round.clientAttemptId());
    }

    @Test
    void nullAttemptId_isCoercedToNilUuidByConstructor() {
        CrossCastRequestPacket original =
            new CrossCastRequestPacket(InteractionHand.MAIN_HAND,
                CrossCastRequestPacket.Action.CAST, 0, null);
        CrossCastRequestPacket round = writeAndRead(original);
        // Nil UUID is round-tripped, not null.
        assertEquals(new UUID(0L, 0L), round.clientAttemptId());
    }

    private static CrossCastRequestPacket writeAndRead(CrossCastRequestPacket pkt) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        pkt.toBytes(buf);
        return new CrossCastRequestPacket(buf);
    }
}
