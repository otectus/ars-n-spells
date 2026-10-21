package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.glyph.GlyphChildren;
import com.otectus.arsnspells.glyph.GlyphFamily;
import com.otectus.arsnspells.glyph.GlyphInvocation;
import com.otectus.arsnspells.spell.target.CastTargetKind;
import com.otectus.arsnspells.spell.target.ValidatedCastTarget;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ICastData;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

/**
 * Iron's-isolated execution of one imported glyph.
 *
 * <p><b>Instant spells</b> run synchronously, inside the Ars resolution, so the Ars recipe keeps
 * its order: an instant foreign effect completes before the next part. The sequence is Iron's
 * own {@code castSpell} driver, step for step: the spell's {@code checkPreCastConditions}, the
 * cancellable {@code SpellPreCastEvent}, the final {@code SpellOnCastEvent} (level and cost
 * listeners), payment at the effect boundary, the spell's own {@code onCast}, native recast or
 * cooldown bookkeeping keyed on the spell id, and the client cast packet. The spell's own code
 * does the work; ANS only supplies the Ars target through {@link IronTargetingScope} and, for
 * projectiles and summons, delivers what the spell spawned. The native cast slot is not touched,
 * so a parent cast in progress (the outer proxy of an exported spell) is never disturbed; the
 * slot's additional cast data is swapped out and restored around the call.
 *
 * <p><b>Long and continuous spells</b> run in Iron's native cast slot as a child of the Ars cast
 * ({@link GlyphChildren}): Iron's own windup, cast-time modifiers, interruption, pulse timing,
 * per-pulse payment and completion apply, and the rest of the Ars recipe resumes when the cast
 * completes. The slot must be free; when it holds the outer native cast this Ars cast runs
 * inside, the child starts on the next tick, after that cast ends; any other cast in progress
 * refuses the child without being cancelled.
 */
final class IronsGlyphInvoker {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(IronsGlyphInvoker.class);
    static final String CHILD_SLOT = "ars_n_spells_glyph";
    /** How long a deferred child may wait for the slot before it is refused. */
    static final int MAX_CHILD_WAIT_TICKS = 40;

    private IronsGlyphInvoker() {}

    static IronGlyphBridge.InvocationOutcome invoke(GlyphInvocation invocation) {
        ServerPlayer player = invocation.caster();
        AbstractSpell spell = IronsGlyphRuntime.spell(invocation.definition().spell());
        if (spell == null) return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.dormant.spell_missing");
        if (ServerConfigs.DISABLE_ADVENTURE_MODE_CASTING.get() && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
            return IronGlyphBridge.InvocationOutcome.rejected("ui.irons_spellbooks.cast_error_adventure");
        if (spell.requiresLearning() && !spell.isLearned(player))
            return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.requires_irons_learning");
        MagicData data = MagicData.getPlayerMagicData(player);
        if (!invocation.definition().instant()) return startChild(invocation, spell, data);
        return runInstant(invocation, spell, data);
    }

    private static IronGlyphBridge.InvocationOutcome runInstant(GlyphInvocation invocation, AbstractSpell spell, MagicData data) {
        ServerPlayer player = invocation.caster();
        Level level = player.level();
        int castLevel = spell.getLevelFor(invocation.augmentedLevel(), player);
        ICastData previous = data.getAdditionalCastData();
        IronTargetingScope.Scope scope = IronTargetingScope.push(invocation);
        boolean effectStarted = false;
        try {
            data.setAdditionalCastData(null);
            if (!spell.checkPreCastConditions(level, castLevel, player, data))
                return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.refused.no_valid_target");
            if (MinecraftForge.EVENT_BUS.post(new SpellPreCastEvent(player, spell.getSpellId(), castLevel, spell.getSchoolType(), CastSource.SPELLBOOK)))
                return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.refused.vetoed");
            boolean recast = data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
            SpellOnCastEvent event = new SpellOnCastEvent(player, spell.getSpellId(), castLevel, spell.getManaCost(castLevel),
                spell.getSchoolType(), CastSource.SPELLBOOK);
            MinecraftForge.EVENT_BUS.post(event);
            boolean free = recast || (player.isCreative() && !ServerConfigs.CREATIVE_MANA_COST.get());
            if (!invocation.payment().payAtEffect(event.getManaCost(), free))
                return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.refused.shortage");
            effectStarted = true;
            spell.onCast(level, event.getSpellLevel(), player, CastSource.SPELLBOOK, data);
            deliver(scope, invocation);
            boolean recastsLeft = data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
            if (recast && recastsLeft) {
                data.getPlayerRecasts().decrementRecastCount(spell.getSpellId());
            } else if (!recastsLeft && (!player.isCreative() || ServerConfigs.CREATIVE_COOLDOWN.get())) {
                MagicHelper.MAGIC_MANAGER.addCooldown(player, spell, CastSource.SPELLBOOK);
            }
            io.redspace.ironsspellbooks.setup.PacketDistributor.sendToPlayer(player,
                new io.redspace.ironsspellbooks.network.casting.OnClientCastPacket(spell.getSpellId(), castLevel, CastSource.SPELLBOOK,
                    data.getAdditionalCastData()));
            return new IronGlyphBridge.InvocationOutcome(IronGlyphBridge.InvocationOutcome.Status.EFFECT_APPLIED, "");
        } finally {
            IronTargetingScope.pop(scope);
            // The execution's own cast data (a target marker, a targeting area) is spent;
            // whatever the slot held before goes back untouched.
            if (data.getAdditionalCastData() != previous) data.resetAdditionalCastData();
            data.setAdditionalCastData(previous);
            if (!effectStarted) invocation.payment().releaseIfUnpaid();
        }
    }

    // ---- Charged and channelled children --------------------------------------------------

    private static IronGlyphBridge.InvocationOutcome startChild(GlyphInvocation invocation, AbstractSpell spell, MagicData data) {
        ServerPlayer player = invocation.caster();
        if (GlyphChildren.busy(player.getUUID()))
            return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.refused.child_busy");
        if (data.isCasting()) {
            AbstractSpell running = IronsGlyphRuntime.spell(new net.minecraft.resources.ResourceLocation(data.getCastingSpellId()));
            boolean parent = running instanceof com.otectus.arsnspells.spell.irons.ArsCrossProxySpell;
            if (!parent) return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.refused.slot_busy");
            GlyphChildren.Child child = GlyphChildren.register(invocation);
            child.pendingStart = c -> begin(c);
            return new IronGlyphBridge.InvocationOutcome(IronGlyphBridge.InvocationOutcome.Status.ACCEPTED_PENDING, "");
        }
        GlyphChildren.Child child = GlyphChildren.register(invocation);
        if (!begin(child)) {
            GlyphChildren.forget(player.getUUID());
            return IronGlyphBridge.InvocationOutcome.rejected("");
        }
        return new IronGlyphBridge.InvocationOutcome(IronGlyphBridge.InvocationOutcome.Status.ACCEPTED_PENDING, "");
    }

    /**
     * Start {@code child} in the native slot through Iron's own entry point. The reservation the
     * Ars cast took is handed back first: from here Iron's pipeline prices and pays each
     * invocation at its native mana write, as a cross-cast (see {@code IronsCastPayments}).
     */
    static boolean begin(GlyphChildren.Child child) {
        GlyphInvocation invocation = child.invocation;
        ServerPlayer player = invocation.caster();
        AbstractSpell spell = IronsGlyphRuntime.spell(child.spell);
        if (spell == null || player.isRemoved()) return false;
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data.isCasting()) return false;
        invocation.payment().releaseForNativePipeline();
        IronTargetingScope.Scope scope = IronTargetingScope.push(invocation);
        boolean started;
        try {
            ItemStack tool = invocation.casterTool() == null ? ItemStack.EMPTY : invocation.casterTool();
            started = spell.attemptInitiateCast(tool, spell.getLevelFor(invocation.augmentedLevel(), player), player.level(), player,
                CastSource.SPELLBOOK, true, CHILD_SLOT);
        } finally {
            IronTargetingScope.pop(scope);
        }
        if (!started && LOGGER.isDebugEnabled()) {
            var check = spell.canBeCastedBy(spell.getLevelFor(invocation.augmentedLevel(), player), CastSource.SPELLBOOK, data, player);
            LOGGER.debug("[Glyph] Iron's refused to start {} for {}: {} {}", child.spell,
                com.otectus.arsnspells.util.LogPrivacy.token(player.getUUID()), check.type,
                check.message == null ? "" : check.message.getString());
        }
        child.started = started;
        child.pendingStart = null;
        return started;
    }

    /** Called by the native ticker hook when a child's invocation starts and ends. */
    static IronTargetingScope.Scope enterChildInvocation(ServerPlayer player, AbstractSpell spell) {
        GlyphChildren.Child child = GlyphChildren.running(player.getUUID(), spell.getSpellResource());
        if (child == null || !child.started) return null;
        return IronTargetingScope.push(child.invocation);
    }

    static void exitChildInvocation(IronTargetingScope.Scope scope, boolean effectReached, boolean finalInvocation) {
        if (scope == null) return;
        try {
            if (effectReached) deliver(scope, scope.invocation());
        } finally {
            IronTargetingScope.pop(scope);
        }
        GlyphChildren.Child child = GlyphChildren.running(scope.caster.getUUID(), scope.invocation().definition().spell());
        if (child != null) {
            child.effectReached |= effectReached;
            if (effectReached && finalInvocation) child.completed = true;
        }
    }

    // ---- Delivery ------------------------------------------------------------------------

    /**
     * Deliver what a projectile or summon spell spawned to the Ars target. A projectile is moved
     * to just before the target and aimed at it, keeping its speed, owner and native impact; a
     * summon is moved to the nearest free spot at the target. Self forms keep the native launch.
     */
    static void deliver(IronTargetingScope.Scope scope, GlyphInvocation invocation) {
        if (scope.spawned.isEmpty() || invocation.selfForm()) return;
        Level level = invocation.caster().level();
        ValidatedCastTarget target = invocation.target();
        Vec3 point = targetPoint(level, invocation);
        if (point == null) return;
        GlyphFamily family = invocation.definition().family();
        for (Entity spawned : scope.spawned) {
            if (spawned.isRemoved()) continue;
            if (family == GlyphFamily.PROJECTILE && spawned instanceof Projectile projectile
                    && projectile.getOwner() == invocation.caster()) {
                reorigin(projectile, invocation, target, point);
            } else if (family == GlyphFamily.SUMMON && spawned instanceof Mob mob) {
                Vec3 spot = freeSpot(level, mob, point);
                if (spot != null) mob.moveTo(spot.x, spot.y, spot.z, mob.getYRot(), mob.getXRot());
            }
        }
    }

    private static Vec3 targetPoint(Level level, GlyphInvocation invocation) {
        ValidatedCastTarget target = invocation.target();
        if (target.kind() == CastTargetKind.ENTITY) {
            Entity entity = target.entity(level);
            return entity == null ? null : entity.getBoundingBox().getCenter();
        }
        return target.location();
    }

    private static void reorigin(Projectile projectile, GlyphInvocation invocation, ValidatedCastTarget target, Vec3 point) {
        Vec3 direction;
        Vec3 origin;
        if (target.kind() == CastTargetKind.BLOCK && target.face() != null) {
            Direction face = target.face();
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            origin = point.add(normal.scale(0.6 + projectile.getBbWidth()));
            direction = normal.scale(-1);
        } else {
            Vec3 eye = invocation.caster().getEyePosition();
            direction = point.subtract(eye);
            if (direction.lengthSqr() < 1.0e-4) direction = invocation.caster().getLookAngle();
            direction = direction.normalize();
            double clearance = 0.6 + projectile.getBbWidth();
            if (target.kind() == CastTargetKind.ENTITY) {
                Entity entity = target.entity(invocation.caster().level());
                if (entity != null) clearance += entity.getBbWidth() * 0.5;
            }
            origin = point.subtract(direction.scale(clearance));
        }
        double speed = Math.max(0.2, projectile.getDeltaMovement().length());
        projectile.setPos(origin.x, origin.y - projectile.getBbHeight() * 0.5, origin.z);
        projectile.xo = projectile.getX();
        projectile.yo = projectile.getY();
        projectile.zo = projectile.getZ();
        if (projectile instanceof AbstractMagicProjectile magic) magic.shoot(direction);
        else projectile.setDeltaMovement(direction.scale(speed));
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        projectile.setYRot((float) (Math.atan2(direction.x, direction.z) * (180F / Math.PI)));
        projectile.setXRot((float) (Math.atan2(direction.y, horizontal) * (180F / Math.PI)));
        projectile.yRotO = projectile.getYRot();
        projectile.xRotO = projectile.getXRot();
    }

    private static Vec3 freeSpot(Level level, Mob mob, Vec3 point) {
        BlockPos base = BlockPos.containing(point);
        for (int dy = 0; dy <= 2; dy++) {
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, dy, -1), base.offset(1, dy, 1))) {
                Vec3 spot = Vec3.atBottomCenterOf(pos);
                AABB box = mob.getType().getDimensions().makeBoundingBox(spot);
                if (level.noCollision(mob, box) && level.getWorldBorder().isWithinBounds(pos) && level.isLoaded(pos)) return spot;
            }
        }
        return null;
    }
}
