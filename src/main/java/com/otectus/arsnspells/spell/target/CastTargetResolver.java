package com.otectus.arsnspells.spell.target;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.util.MathUtil;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSensitive;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the target of an Ars cast on the server, using Ars Nouveau's own spellbook rules.
 *
 * <p>Ars 5.13.1 casts a spellbook with {@code SpellUtil.rayTrace(caster, 0.5 + block reach, 1, sensitive)}:
 * a block pick up to the caster's block-interaction range plus half a block (through fluids only
 * when the spell's form carries Sensitive), replaced by any pickable entity
 * {@code MathUtil.getLookedAtEntity(caster, 25)} finds. That method compares a squared distance
 * against 25, so its real reach is five blocks. This class reproduces both picks with the same
 * Ars methods and the same constants, so a scroll, a generic carrier and a spellbook wheel slot
 * reach exactly what an Ars spellbook would.
 *
 * <p>One deliberate tightening: an entity counts only when nothing solid is nearer along the ray.
 * Ars's own entity trace ignores blocks, so a spellbook can touch a mob through a wall within five
 * blocks; a carrier cast never does. Touch gets no projectile range and no extended reach.
 *
 * <p>Client hints aim the pick and may name what the client clicked; see {@link CastTargetHint}.
 */
public final class CastTargetResolver {

    /** Ars's {@code getLookedAtEntity} range argument; squared-distance comparison makes it 5 blocks. */
    public static final int ARS_ENTITY_TRACE_RANGE = 25;
    /** Largest re-aim, in degrees, allowed when reconstructing a claimed target from the client's aim. */
    public static final double MAX_RECONSTRUCTION_DEGREES = 15.0;
    /** Tolerance, in blocks, on a claimed hit point's distance; vanilla allows one block for block use. */
    public static final double CLAIM_DISTANCE_TOLERANCE = 1.0;

    private CastTargetResolver() {
    }

    /** Whether the spell's first form carries Sensitive, read exactly as Ars's spellbook reads it. */
    public static boolean isSensitive(Spell spell, LivingEntity caster) {
        return spell.getBuffsAtIndex(0, caster, AugmentSensitive.INSTANCE) > 0;
    }

    /** Block reach Ars uses for a spellbook cast: half a block more than the caster's block interaction range. */
    public static double blockReach(ServerPlayer player) {
        return 0.5 + player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
    }

    /**
     * Resolve the target of {@code spell} cast by {@code player}.
     *
     * @param hint the client's input-time aim and claim, or null for a cast with none (a native
     *             wheel, hotkey or staff cast)
     */
    public static ValidatedCastTarget resolve(ServerPlayer player, Spell spell, @Nullable CastTargetHint hint) {
        ServerLevel level = player.serverLevel();
        ResourceKey<Level> dimension = level.dimension();
        if (hint != null && hint.dimension() != null && !hint.dimension().equals(dimension.location())) {
            return ValidatedCastTarget.refused(dimension, "message.ars_n_spells.cast.target.wrong_dimension");
        }
        boolean sensitive = isSensitive(spell, player);
        boolean aimed = hint != null && hint.hasAim();
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = aimed ? Vec3.directionFromRotation(hint.pitch(), hint.wrappedYaw()) : player.getViewVector(1.0f);
        ValidatedCastTarget.Resolution baseResolution = aimed
            ? ValidatedCastTarget.Resolution.AIMED_PICK : ValidatedCastTarget.Resolution.SERVER_PICK;

        Pick pick = pick(player, level, eye, look, sensitive);
        if (hint == null || hint.kind() == CastTargetHint.Kind.NONE) {
            return pick.toTarget(dimension, sensitive, baseResolution);
        }
        if (pick.matches(hint)) {
            return pick.toTarget(dimension, sensitive, ValidatedCastTarget.Resolution.HINT_CONFIRMED);
        }
        // The claim and the pick disagree. Aim once at the claimed point, within a bounded
        // deviation from the input-time aim; only a pick that reaches the claimed target itself
        // is accepted. Anything else is a stale or forged claim and the original pick stands.
        Vec3 toClaim = hint.location().subtract(eye);
        double claimDistance = toClaim.length();
        double maxDistance = (hint.kind() == CastTargetHint.Kind.BLOCK ? blockReach(player) : Math.sqrt(ARS_ENTITY_TRACE_RANGE))
            + CLAIM_DISTANCE_TOLERANCE;
        if (claimDistance > 1.0e-6 && claimDistance <= maxDistance
                && angleDegrees(look, toClaim) <= MAX_RECONSTRUCTION_DEGREES) {
            Pick reaim = pick(player, level, eye, toClaim.normalize(), sensitive);
            if (reaim.matches(hint)) {
                return reaim.toTarget(dimension, sensitive, ValidatedCastTarget.Resolution.HINT_RECONSTRUCTED);
            }
        }
        return pick.toTarget(dimension, sensitive, ValidatedCastTarget.Resolution.HINT_STALE);
    }

    /** The Ars pick along {@code look}: a block clip, then the nearest unoccluded pickable entity. */
    static Pick pick(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 look, boolean sensitive) {
        Vec3 blockEnd = eye.add(look.scale(blockReach(player)));
        BlockHitResult block = level.clip(new ClipContext(eye, blockEnd, ClipContext.Block.OUTLINE,
            sensitive ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, player));
        Vec3 entityEnd = eye.add(look.scale(ARS_ENTITY_TRACE_RANGE));
        AABB box = player.getBoundingBox().expandTowards(look.scale(ARS_ENTITY_TRACE_RANGE)).inflate(1.0);
        EntityHitResult entity = MathUtil.traceEntities(player, eye, entityEnd, box,
            e -> !e.isSpectator() && e.isPickable(), ARS_ENTITY_TRACE_RANGE);
        if (entity != null && block.getType() == HitResult.Type.BLOCK
                && eye.distanceToSqr(entity.getLocation()) > eye.distanceToSqr(block.getLocation())) {
            entity = null; // Something solid is nearer: the entity is behind it.
        }
        if (entity != null && !isTargetable(level, entity.getEntity())) {
            entity = null;
        }
        if (block.getType() == HitResult.Type.BLOCK && !isLoadedInBorder(level, block)) {
            block = BlockHitResult.miss(block.getLocation(), block.getDirection(), block.getBlockPos());
        }
        return new Pick(block, entity);
    }

    private static boolean isTargetable(ServerLevel level, Entity entity) {
        return entity.isAlive() && !entity.isRemoved() && entity.level() == level
            && level.getWorldBorder().isWithinBounds(entity.blockPosition())
            && level.isLoaded(entity.blockPosition());
    }

    private static boolean isLoadedInBorder(ServerLevel level, BlockHitResult block) {
        return level.isLoaded(block.getBlockPos()) && level.getWorldBorder().isWithinBounds(block.getBlockPos());
    }

    private static double angleDegrees(Vec3 a, Vec3 b) {
        double denominator = a.length() * b.length();
        if (denominator < 1.0e-9) return 180.0;
        double cos = Math.max(-1.0, Math.min(1.0, a.dot(b) / denominator));
        return Math.toDegrees(Math.acos(cos));
    }

    /** One pick's two candidates, before the native precedence decides between them. */
    record Pick(BlockHitResult block, @Nullable EntityHitResult entity) {

        boolean matches(CastTargetHint hint) {
            return switch (hint.kind()) {
                case ENTITY -> entity != null && entity.getEntity().getId() == hint.entityId();
                case BLOCK -> entity == null && block.getType() == HitResult.Type.BLOCK
                    && block.getBlockPos().equals(hint.blockPos());
                case NONE -> false;
            };
        }

        /**
         * Ars's precedence: a looked-at entity wins; only a living one is cast at. A block hit, or
         * a Sensitive pick into air or fluid, is a block cast. Everything else is the form's
         * generic entry point.
         */
        ValidatedCastTarget toTarget(ResourceKey<Level> dimension, boolean sensitive, ValidatedCastTarget.Resolution resolution) {
            if (entity != null) {
                if (entity.getEntity() instanceof LivingEntity living) {
                    return new ValidatedCastTarget(ValidatedCastTarget.Kind.ENTITY, null, living, entity.getLocation(),
                        dimension, sensitive, resolution, null);
                }
                // Ars resolves a non-living looked-at entity with the form's generic entry point.
                return new ValidatedCastTarget(ValidatedCastTarget.Kind.AIR, null, null, entity.getLocation(),
                    dimension, sensitive, resolution, null);
            }
            if (block.getType() == HitResult.Type.BLOCK || sensitive) {
                return new ValidatedCastTarget(ValidatedCastTarget.Kind.BLOCK, block, null, block.getLocation(),
                    dimension, sensitive, resolution, null);
            }
            return new ValidatedCastTarget(ValidatedCastTarget.Kind.AIR, null, null, block.getLocation(),
                dimension, sensitive, resolution, null);
        }
    }

    /** The dimension id a client puts in its hint. */
    public static ResourceLocation dimensionId(Level level) {
        return level.dimension().location();
    }
}
