package com.otectus.arsnspells.network;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The cross-cast request is the mod's one gameplay-critical client-to-server payload: it says
 * "cast the inscribed spell in this hand". Its fields are pinned here.
 *
 * <p>The {@code clientAttemptId} is minted client-side at the moment of input and threaded
 * through every {@code CrossCastTrace} stage, so one multiplayer failure can be grepped end to
 * end. A null id would break that correlation and NPE the trace logger, so the compact
 * constructor coerces it to the nil UUID rather than trusting the wire.
 */
class CrossCastRequestPayloadTest {

    private static final UUID ATTEMPT = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void fieldsAreCarriedThrough() {
        CrossCastRequestPayload p = new CrossCastRequestPayload(
            InteractionHand.MAIN_HAND, CrossCastRequestPayload.Action.CAST, 2, ATTEMPT);
        assertEquals(InteractionHand.MAIN_HAND, p.hand());
        assertEquals(CrossCastRequestPayload.Action.CAST, p.action());
        assertEquals(2, p.clientSelectedIndex());
        assertEquals(ATTEMPT, p.clientAttemptId());
    }

    @Test
    void offHandCycleIsRepresentable() {
        CrossCastRequestPayload p = new CrossCastRequestPayload(
            InteractionHand.OFF_HAND, CrossCastRequestPayload.Action.CYCLE, 7, ATTEMPT);
        assertEquals(InteractionHand.OFF_HAND, p.hand());
        assertEquals(CrossCastRequestPayload.Action.CYCLE, p.action());
        assertEquals(7, p.clientSelectedIndex());
    }

    @Test
    void nullAttemptId_isCoercedToTheNilUuid() {
        CrossCastRequestPayload p = new CrossCastRequestPayload(
            InteractionHand.MAIN_HAND, CrossCastRequestPayload.Action.CAST, 0, null);
        assertNotNull(p.clientAttemptId(), "a null attempt id would NPE the trace logger");
        assertEquals(new UUID(0L, 0L), p.clientAttemptId(),
            "the nil UUID is the documented stand-in for 'no attempt id'");
    }

    @Test
    void bothActionsExist() {
        // CAST and CYCLE are the whole action vocabulary; the stream codec writes this enum by
        // ordinal, so adding or reordering a constant is a wire-format change.
        assertEquals(2, CrossCastRequestPayload.Action.values().length,
            "the Action enum is written by ordinal - changing its shape changes the wire "
                + "format and needs a protocol bump");
        assertEquals(CrossCastRequestPayload.Action.CAST,
            CrossCastRequestPayload.Action.values()[0]);
        assertEquals(CrossCastRequestPayload.Action.CYCLE,
            CrossCastRequestPayload.Action.values()[1]);
    }
}
