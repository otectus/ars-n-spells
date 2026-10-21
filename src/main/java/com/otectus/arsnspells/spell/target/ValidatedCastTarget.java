package com.otectus.arsnspells.spell.target;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * An immutable description of what a cast resolved against, made by the server.
 *
 * <p>It holds no world or entity reference, only a bounded identity (dimension, position,
 * entity id and UUID), so it can be logged, compared with a client hint, or held by a delayed
 * effect without keeping a level or a disconnected entity alive. {@link #resolve} turns it back
 * into a live hit, and refuses when the world no longer matches.
 */
public record ValidatedCastTarget(CastTargetKind kind, ResourceKey<Level> dimension, Vec3 location,
                                  @Nullable BlockPos blockPos, @Nullable Direction face, boolean inside,
                                  int entityId, @Nullable UUID entityUuid) {

    public static ValidatedCastTarget of(Level level, HitResult hit) {
        ResourceKey<Level> dimension = level.dimension();
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            return new ValidatedCastTarget(CastTargetKind.ENTITY, dimension, hit.getLocation(), null, null, false,
                entity.getId(), entity.getUUID());
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return new ValidatedCastTarget(CastTargetKind.BLOCK, dimension, hit.getLocation(),
                blockHit.getBlockPos().immutable(), blockHit.getDirection(), blockHit.isInside(), -1, null);
        }
        BlockPos pos = hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos().immutable() : BlockPos.containing(hit.getLocation());
        Direction face = hit instanceof BlockHitResult blockHit ? blockHit.getDirection() : Direction.UP;
        return new ValidatedCastTarget(CastTargetKind.AIR, dimension, hit.getLocation(), pos, face, false, -1, null);
    }

    /** The live entity this target names, or null when it is gone, moved worlds or was replaced. */
    @Nullable
    public Entity entity(Level level) {
        if (kind != CastTargetKind.ENTITY || !level.dimension().equals(dimension)) return null;
        Entity entity = level.getEntity(entityId);
        if (entity == null || entity.isRemoved() || !entity.getUUID().equals(entityUuid)) return null;
        return entity;
    }

    /** A live hit for this target in {@code level}, or null when the world no longer matches it. */
    @Nullable
    public HitResult resolve(Level level) {
        if (!level.dimension().equals(dimension)) return null;
        return switch (kind) {
            case ENTITY -> {
                Entity entity = entity(level);
                yield entity == null ? null : new EntityHitResult(entity, location);
            }
            case BLOCK -> level.isLoaded(blockPos) ? new BlockHitResult(location, face, blockPos, inside) : null;
            case AIR -> BlockHitResult.miss(location, face == null ? Direction.UP : face, blockPos == null ? BlockPos.containing(location) : blockPos);
        };
    }

    @Override
    public String toString() {
        return switch (kind) {
            case ENTITY -> "entity#" + entityId;
            case BLOCK -> "block" + blockPos.toShortString() + "/" + face;
            case AIR -> "air";
        };
    }
}
