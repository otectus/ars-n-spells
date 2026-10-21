package com.otectus.arsnspells.spell.target;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * The client's aim when it sent a cast request: what its own crosshair pick reported.
 *
 * <p>A hint is never authority. The server makes its own pick with Ars's casting reach rules
 * and only compares; a hint that disagrees is recorded as stale and the fresh server pick is
 * used, so a forged or outdated hint can neither aim a cast at an arbitrary coordinate nor at
 * an entity the server cannot see from the caster. The wire form is fixed-size apart from
 * the variable-length integers, so a hint cannot inflate a request.
 *
 * @param kind     what the client's pick hit
 * @param blockPos the block the client aimed at, for {@link CastTargetKind#BLOCK}
 * @param face     the face of that block
 * @param entityId the network id of the entity the client aimed at, for {@link CastTargetKind#ENTITY}
 */
public record TargetHint(CastTargetKind kind, @Nullable BlockPos blockPos, @Nullable Direction face, int entityId) {
    public static final TargetHint NONE = new TargetHint(CastTargetKind.AIR, null, null, -1);

    public TargetHint {
        if (kind == null) kind = CastTargetKind.AIR;
        if (kind == CastTargetKind.BLOCK && (blockPos == null || face == null))
            throw new IllegalArgumentException("A block hint needs a position and a face");
        if (kind != CastTargetKind.BLOCK) { blockPos = null; face = null; }
        if (kind != CastTargetKind.ENTITY) entityId = -1;
        else if (entityId < 0) throw new IllegalArgumentException("An entity hint needs an entity id");
    }

    public static TargetHint block(BlockPos pos, Direction face) {
        return new TargetHint(CastTargetKind.BLOCK, pos.immutable(), face, -1);
    }

    public static TargetHint entity(int entityId) {
        return new TargetHint(CastTargetKind.ENTITY, null, null, entityId);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(kind.ordinal());
        switch (kind) {
            case BLOCK -> { buffer.writeLong(blockPos.asLong()); buffer.writeByte(face.get3DDataValue()); }
            case ENTITY -> buffer.writeVarInt(entityId);
            default -> { }
        }
    }

    public static TargetHint read(FriendlyByteBuf buffer) {
        CastTargetKind kind = CastTargetKind.fromWire(buffer.readByte());
        return switch (kind) {
            case BLOCK -> {
                BlockPos pos = BlockPos.of(buffer.readLong());
                int face = buffer.readByte();
                if (face < 0 || face >= Direction.values().length) throw new IllegalArgumentException("Unknown face " + face);
                yield block(pos, Direction.from3DDataValue(face));
            }
            case ENTITY -> entity(buffer.readVarInt());
            default -> NONE;
        };
    }

    /**
     * Whether this hint describes the same target as the server's pick. The server's pick is
     * what is cast at either way; a mismatch only marks the client's aim as stale in traces.
     */
    public boolean agreesWith(ValidatedCastTarget target) {
        if (target == null) return kind == CastTargetKind.AIR;
        if (kind != target.kind()) return false;
        return switch (kind) {
            case BLOCK -> blockPos.equals(target.blockPos()) && face == target.face();
            case ENTITY -> entityId == target.entityId();
            default -> true;
        };
    }
}
