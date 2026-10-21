package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.glyph.GlyphFamily;
import com.otectus.arsnspells.glyph.GlyphInvocation;
import com.otectus.arsnspells.spell.target.CastTargetKind;
import com.otectus.arsnspells.spell.target.ValidatedCastTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The narrow, per-execution context an imported glyph gives Iron's targeting helpers.
 *
 * <p>While a scope is active on the server thread, and only for its caster, the mixins on Iron's
 * aim helpers ({@code RaycastBuilder}, {@code Utils.raycastForEntity}/{@code raycastForBlock}/
 * {@code getTargetBlock}) answer an aim taken from the caster's eye with the Ars target instead
 * of tracing the caster's look. Nothing about the player changes (position, rotation and casting
 * state are untouched), a raycast that does not start at the caster's eye (a chain bounce, a
 * line-of-sight check, a spawn search) runs natively, and native casts never see a scope. The
 * spell's own filters, ranges of effect and placement rules then run on the Ars target exactly
 * as they would on an aimed one.
 *
 * <p>The scope also records every entity the spell adds while it runs, so the projectile and
 * summon families can deliver them to the target. A thread-local is only ever the store for one
 * synchronous call; charged casts re-enter a scope per native invocation from their child record.
 */
public final class IronTargetingScope {
    /** How far an aim's start may be from the caster's eye and still count as the caster's aim. */
    private static final double EYE_TOLERANCE_SQR = 0.6 * 0.6;
    private static final ThreadLocal<Deque<Scope>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

    private IronTargetingScope() {}

    public static final class Scope {
        final ServerPlayer caster;
        final GlyphInvocation invocation;
        /** Iron's aim helpers answer with the Ars target. False for Self forms of the caster-aimed families. */
        final boolean redirectAim;
        final boolean capture;
        /**
         * True for a charged or channelled child in Iron's native cast slot, which Iron's own
         * pipeline prices and paces; false for an instant execution ANS drives itself.
         */
        final boolean nativePipeline;
        final List<Entity> spawned = new ArrayList<>();
        int redirects;

        Scope(ServerPlayer caster, GlyphInvocation invocation, boolean redirectAim, boolean capture, boolean nativePipeline) {
            this.caster = caster;
            this.invocation = invocation;
            this.redirectAim = redirectAim;
            this.capture = capture;
            this.nativePipeline = nativePipeline;
        }

        public List<Entity> spawned() { return spawned; }
        public int redirects() { return redirects; }
        public GlyphInvocation invocation() { return invocation; }
    }

    /**
     * Enter a scope for {@code invocation}. The aim is redirected for the aimed family in every
     * form (Self resolves to the caster itself) and for movement in every form but Self, whose
     * destination is the caster's own aim. Projectiles and summons keep their native aim; their
     * spawned entities are delivered afterwards.
     */
    public static Scope push(GlyphInvocation invocation) {
        GlyphFamily family = invocation.definition().family();
        boolean redirect = family == GlyphFamily.AIMED || (family == GlyphFamily.MOVEMENT && !invocation.selfForm());
        boolean capture = (family == GlyphFamily.PROJECTILE || family == GlyphFamily.SUMMON) && !invocation.selfForm();
        Scope scope = new Scope(invocation.caster(), invocation, redirect, capture, !invocation.definition().instant());
        ACTIVE.get().push(scope);
        return scope;
    }

    public static void pop(Scope scope) {
        Deque<Scope> stack = ACTIVE.get();
        if (stack.peek() == scope) stack.pop();
        else stack.remove(scope);
        if (stack.isEmpty()) ACTIVE.remove();
    }

    @Nullable
    public static Scope current() {
        Deque<Scope> stack = ACTIVE.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * Whether an Iron's event for {@code player} comes from an instant imported-glyph execution
     * that ANS prices and paces itself, so ANS's native-cast listeners (pricing, the cross-mod
     * category cooldown) must leave it alone.
     */
    public static boolean instantDelegated(net.minecraft.world.entity.player.Player player) {
        Scope scope = current();
        return scope != null && scope.caster == player && !scope.nativePipeline;
    }

    /**
     * Whether an Iron's event for {@code player} and {@code spellId} belongs to any imported-glyph
     * execution, instant or charged. The root Ars cast already credited ANS training once for it.
     */
    public static boolean importedExecution(net.minecraft.world.entity.player.Player player, String spellId) {
        Scope scope = current();
        if (scope != null && scope.caster == player) return true;
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(spellId);
        return player != null && id != null && com.otectus.arsnspells.glyph.GlyphChildren.running(player.getUUID(), id) != null;
    }

    /**
     * The Ars target as an aim result, or null to let the helper trace natively.
     *
     * @param origin          the entity the helper aims for (null when the helper takes none)
     * @param start           where the helper's trace starts
     * @param checkForBlocks  whether the helper would stop at blocks
     */
    @Nullable
    public static HitResult redirect(Level level, @Nullable Entity origin, Vec3 start, boolean checkForBlocks) {
        Scope scope = current();
        if (scope == null || !scope.redirectAim || level != scope.caster.level()) return null;
        if (origin != null && origin != scope.caster) return null;
        if (start == null || start.distanceToSqr(scope.caster.getEyePosition()) > EYE_TOLERANCE_SQR) return null;
        HitResult hit = aim(level, scope.invocation.target(), scope.invocation.hit(), checkForBlocks);
        if (hit != null) scope.redirects++;
        return hit;
    }

    /** The block-only variant for helpers that never return entities. */
    @Nullable
    public static BlockHitResult redirectBlock(Level level, @Nullable Entity origin, Vec3 start) {
        HitResult hit = redirect(level, origin, start, true);
        if (hit == null) return null;
        if (hit instanceof BlockHitResult block) return block;
        Vec3 location = hit.getLocation();
        return BlockHitResult.miss(location, Direction.UP, BlockPos.containing(location));
    }

    @Nullable
    static HitResult aim(Level level, ValidatedCastTarget target, HitResult arsHit, boolean checkForBlocks) {
        if (target.kind() == CastTargetKind.ENTITY) {
            Entity entity = target.entity(level);
            if (entity == null && arsHit instanceof EntityHitResult entityHit && !entityHit.getEntity().isRemoved())
                entity = entityHit.getEntity();
            if (entity == null) return null;
            return new EntityHitResult(entity, entity.getBoundingBox().getCenter());
        }
        if (target.kind() == CastTargetKind.BLOCK && checkForBlocks)
            return new BlockHitResult(target.location(), target.face(), target.blockPos(), false);
        Vec3 location = target.location();
        return BlockHitResult.miss(location, target.face() == null ? Direction.UP : target.face(),
            target.blockPos() == null ? BlockPos.containing(location) : target.blockPos());
    }

    /** Called for every entity joining a level; records it when a capturing scope for its world is active. */
    public static void observeSpawn(Entity entity) {
        Scope scope = current();
        if (scope == null || !scope.capture || entity == scope.caster || entity.level() != scope.caster.level()) return;
        if (scope.spawned.size() < 64) scope.spawned.add(entity);
    }
}
