package com.otectus.arsnspells.spell.target;

import com.otectus.arsnspells.network.CrossCastRequestPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's aim and claimed target travel in every cast request. They are hints the server
 * re-checks, so the wire format must be bounded and must turn anything malformed into "no claim"
 * rather than into a target.
 */
class CastTargetHintTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

    private static CastTargetHint roundTrip(CastTargetHint hint) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        hint.write(buf);
        return CastTargetHint.read(buf);
    }

    @Test
    void blockClaimRoundTrips() {
        CastTargetHint hint = CastTargetHint.block(12.5f, -30.0f, OVERWORLD, new BlockPos(1, 64, -3), Direction.WEST,
            new Vec3(1.0, 64.5, -2.5));
        assertEquals(hint, roundTrip(hint));
    }

    @Test
    void entityClaimRoundTrips() {
        CastTargetHint hint = CastTargetHint.entity(-170.0f, 10.0f, OVERWORLD, 42, new Vec3(0.5, 70.25, 0.5));
        assertEquals(hint, roundTrip(hint));
    }

    @Test
    void aimOnlyAndAbsentRoundTrip() {
        CastTargetHint aim = CastTargetHint.aimOnly(90.0f, 45.0f, OVERWORLD);
        assertEquals(aim, roundTrip(aim));
        CastTargetHint absent = roundTrip(CastTargetHint.ABSENT);
        assertEquals(CastTargetHint.Kind.NONE, absent.kind());
        assertFalse(absent.hasAim(), "a request with no aim must not aim the server pick");
    }

    @Test
    void malformedClaimsBecomeNoClaim() {
        assertEquals(CastTargetHint.Kind.NONE,
            new CastTargetHint(CastTargetHint.Kind.BLOCK, 0, 0, OVERWORLD, null, Direction.UP, Vec3.ZERO, -1).kind());
        assertEquals(CastTargetHint.Kind.NONE,
            new CastTargetHint(CastTargetHint.Kind.ENTITY, 0, 0, OVERWORLD, null, null, Vec3.ZERO, -5).kind());
        assertEquals(CastTargetHint.Kind.NONE,
            CastTargetHint.entity(0, 0, OVERWORLD, 3, new Vec3(Double.NaN, 0, 0)).kind());
        assertEquals(CastTargetHint.Kind.NONE,
            CastTargetHint.block(0, 0, OVERWORLD, BlockPos.ZERO, Direction.UP, new Vec3(4.0e7, 0, 0)).kind());
        CastTargetHint stripped = new CastTargetHint(CastTargetHint.Kind.NONE, 0, 0, OVERWORLD, BlockPos.ZERO,
            Direction.UP, Vec3.ZERO, 7);
        assertNull(stripped.blockPos(), "a NONE hint must not carry claim fields");
        assertEquals(-1, stripped.entityId());
    }

    @Test
    void impossibleAimIsNotUsed() {
        assertFalse(CastTargetHint.aimOnly(Float.NaN, 0, OVERWORLD).hasAim());
        assertFalse(CastTargetHint.aimOnly(0, 91.0f, OVERWORLD).hasAim());
        assertFalse(CastTargetHint.aimOnly(Float.POSITIVE_INFINITY, 0, OVERWORLD).hasAim());
        assertTrue(CastTargetHint.aimOnly(725.0f, -90.0f, OVERWORLD).hasAim());
        assertEquals(5.0f, CastTargetHint.aimOnly(725.0f, 0, OVERWORLD).wrappedYaw(), 1.0e-4f);
    }

    @Test
    void unknownKindOrdinalIsNoClaim() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(9);
        buf.writeFloat(0);
        buf.writeFloat(0);
        buf.writeBoolean(false);
        assertEquals(CastTargetHint.Kind.NONE, CastTargetHint.read(buf).kind());
    }

    /** The whole request stays small however its hint is filled. */
    @Test
    void requestWithTheLargestHintIsBounded() {
        CastTargetHint hint = CastTargetHint.block(1, 2, ResourceLocation.fromNamespaceAndPath("some_mod", "a".repeat(200)),
            new BlockPos(30_000_000, 300, -30_000_000), Direction.NORTH, new Vec3(1, 2, 3));
        CrossCastRequestPayload payload = new CrossCastRequestPayload(InteractionHand.MAIN_HAND,
            CrossCastRequestPayload.Action.CAST, 3, UUID.randomUUID(), "f".repeat(64), hint);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        CrossCastRequestPayload.STREAM_CODEC.encode(buf, payload);
        assertTrue(buf.readableBytes() < 512, "request size " + buf.readableBytes());
        CrossCastRequestPayload decoded = CrossCastRequestPayload.STREAM_CODEC.decode(buf);
        assertEquals(payload, decoded);
    }

    @Test
    void legacyConstructorsCarryNoClaim() {
        CrossCastRequestPayload p = new CrossCastRequestPayload(InteractionHand.MAIN_HAND,
            CrossCastRequestPayload.Action.CAST, 0, UUID.randomUUID(), "abc");
        assertEquals(CastTargetHint.Kind.NONE, p.target().kind());
        assertFalse(p.target().hasAim());
    }
}
