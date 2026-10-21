package com.otectus.arsnspells.spell.target;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the client saw when it asked for a cast: its look angles, and the block or entity under
 * its crosshair, if any.
 *
 * <p>A hint is never authority. The server recomputes the target from its own copy of the world
 * ({@link CastTargetResolver}); the angles only aim that pick, which grants nothing a movement
 * packet could not, and a claimed block or entity is accepted only when the server's own pick,
 * or a bounded re-aim at the claimed point, reaches it. A stale or forged claim falls back to the
 * server pick and can never become a hit the server did not see.
 *
 * <p>Wire shape: kind, angles, optional dimension, then the kind's own fields. Every field is
 * fixed-size or bounded, so a request stays small however it is filled.
 */
public record CastTargetHint(Kind kind, float yaw, float pitch, @Nullable ResourceLocation dimension,
                             @Nullable BlockPos blockPos, @Nullable Direction face, @Nullable Vec3 location,
                             int entityId) {

    /** What the client's crosshair rested on at input time. */
    public enum Kind { NONE, BLOCK, ENTITY }

    /** A request with no aim at all; the server uses the player's current rotation. */
    public static final CastTargetHint ABSENT = new CastTargetHint(Kind.NONE, Float.NaN, Float.NaN, null, null, null, null, -1);

    /** Coordinates further than this from the origin are malformed, not a target. */
    private static final double MAX_COORDINATE = 3.0E7;

    public CastTargetHint {
        kind = kind == null ? Kind.NONE : kind;
        if (kind == Kind.BLOCK && (blockPos == null || face == null || !finite(location))) {
            kind = Kind.NONE;
        }
        if (kind == Kind.ENTITY && (entityId < 0 || !finite(location))) {
            kind = Kind.NONE;
        }
        if (kind == Kind.NONE) {
            blockPos = null;
            face = null;
            location = null;
            entityId = -1;
        }
    }

    public static CastTargetHint aimOnly(float yaw, float pitch, @Nullable ResourceLocation dimension) {
        return new CastTargetHint(Kind.NONE, yaw, pitch, dimension, null, null, null, -1);
    }

    public static CastTargetHint block(float yaw, float pitch, ResourceLocation dimension, BlockPos pos, Direction face, Vec3 location) {
        return new CastTargetHint(Kind.BLOCK, yaw, pitch, dimension, pos, face, location, -1);
    }

    public static CastTargetHint entity(float yaw, float pitch, ResourceLocation dimension, int entityId, Vec3 location) {
        return new CastTargetHint(Kind.ENTITY, yaw, pitch, dimension, null, null, location, entityId);
    }

    /** Whether the angles can aim a pick: finite, with a pitch a player can actually have. */
    public boolean hasAim() {
        return Float.isFinite(yaw) && Float.isFinite(pitch) && pitch >= -90.0f && pitch <= 90.0f;
    }

    public float wrappedYaw() {
        return Mth.wrapDegrees(yaw);
    }

    private static boolean finite(@Nullable Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
            && Math.abs(v.x) < MAX_COORDINATE && Math.abs(v.y) < MAX_COORDINATE && Math.abs(v.z) < MAX_COORDINATE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(kind.ordinal());
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeBoolean(dimension != null);
        if (dimension != null) buf.writeResourceLocation(dimension);
        switch (kind) {
            case BLOCK -> {
                buf.writeBlockPos(blockPos);
                buf.writeByte(face.get3DDataValue());
                writeVec(buf, location);
            }
            case ENTITY -> {
                buf.writeVarInt(entityId);
                writeVec(buf, location);
            }
            default -> { }
        }
    }

    public static CastTargetHint read(FriendlyByteBuf buf) {
        int ordinal = buf.readByte();
        Kind kind = ordinal >= 0 && ordinal < Kind.values().length ? Kind.values()[ordinal] : Kind.NONE;
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        ResourceLocation dimension = buf.readBoolean() ? buf.readResourceLocation() : null;
        return switch (kind) {
            case BLOCK -> {
                BlockPos pos = buf.readBlockPos();
                Direction face = Direction.from3DDataValue(buf.readByte());
                yield new CastTargetHint(Kind.BLOCK, yaw, pitch, dimension, pos, face, readVec(buf), -1);
            }
            case ENTITY -> {
                int id = buf.readVarInt();
                yield new CastTargetHint(Kind.ENTITY, yaw, pitch, dimension, null, null, readVec(buf), id);
            }
            default -> new CastTargetHint(Kind.NONE, yaw, pitch, dimension, null, null, null, -1);
        };
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
