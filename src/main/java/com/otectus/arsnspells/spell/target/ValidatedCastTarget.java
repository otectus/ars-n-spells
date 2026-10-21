package com.otectus.arsnspells.spell.target;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The target a cast resolves against, as the server established it.
 *
 * <p>Immutable and short-lived: it is built, dispatched and dropped within one server tick, so
 * the entity reference it holds never outlives the cast that picked it and is never kept in a
 * static map. Later stages that need the target again (a delayed effect, a projectile in flight)
 * carry Ars's own hit results, not this record.
 *
 * @param kind        what the cast will resolve against
 * @param block       the block hit for {@link Kind#BLOCK}; also present for a Sensitive pick
 *                    that ended in air or a fluid, which Ars itself casts as a block hit
 * @param entity      the living target for {@link Kind#ENTITY}
 * @param location    the hit position
 * @param dimension   the level the pick was made in
 * @param sensitive   whether the spell's first form carries Sensitive
 * @param resolution  how the server arrived at this target
 * @param refusalKey  the translation key explaining a {@link Kind#REFUSED} target
 */
public record ValidatedCastTarget(Kind kind, @Nullable BlockHitResult block, @Nullable LivingEntity entity,
                                  Vec3 location, ResourceKey<Level> dimension, boolean sensitive,
                                  Resolution resolution, @Nullable String refusalKey) {

    public enum Kind {
        /** Nothing in reach: the form's generic entry point applies. */
        AIR,
        /** A block face in reach, or a Sensitive pick into air or fluid. */
        BLOCK,
        /** A living entity in reach and in line of sight. */
        ENTITY,
        /** The request itself was unacceptable (wrong dimension, malformed aim). */
        REFUSED
    }

    public enum Resolution {
        /** No usable hint; the server picked from the player's own rotation. */
        SERVER_PICK,
        /** The server picked along the client's input-time aim. */
        AIMED_PICK,
        /** The client's claimed block or entity matched the server's pick. */
        HINT_CONFIRMED,
        /** The claim was reached by a bounded re-aim at the claimed point. */
        HINT_RECONSTRUCTED,
        /** The claim could not be confirmed; the server's own pick was used instead. */
        HINT_STALE
    }

    public static ValidatedCastTarget refused(ResourceKey<Level> dimension, String reasonKey) {
        return new ValidatedCastTarget(Kind.REFUSED, null, null, Vec3.ZERO, dimension, false, Resolution.SERVER_PICK, reasonKey);
    }

    public boolean isRefused() {
        return kind == Kind.REFUSED;
    }

    /** The native hit result this target corresponds to, or null for air and refusals. */
    @Nullable
    public HitResult hitResult() {
        return switch (kind) {
            case BLOCK -> block;
            case ENTITY -> entity == null ? null : new net.minecraft.world.phys.EntityHitResult(entity, location);
            default -> null;
        };
    }

    @Nullable
    public Entity targetEntity() {
        return entity;
    }

    /** One bounded line for traces and diagnostics. */
    public String describe() {
        return switch (kind) {
            case BLOCK -> "BLOCK@" + (block == null ? "?" : block.getBlockPos().toShortString() + "/" + block.getDirection()
                + (block.getType() == HitResult.Type.MISS ? "(sensitive-air)" : "")) + " via " + resolution;
            case ENTITY -> "ENTITY#" + (entity == null ? "?" : entity.getId() + ":" + entity.getType().getDescriptionId()) + " via " + resolution;
            case AIR -> "AIR via " + resolution;
            case REFUSED -> "REFUSED(" + refusalKey + ")";
        };
    }
}
