package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.glyph.IronGlyphBridge;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.SpellInventoryEntry;
import com.otectus.arsnspells.glyph.runtime.AdapterFrame;
import com.otectus.arsnspells.glyph.runtime.GlyphChannel;
import com.otectus.arsnspells.glyph.runtime.GlyphInvocationScope;
import com.otectus.arsnspells.glyph.runtime.GlyphRequest;
import com.otectus.arsnspells.mixin.irons.MagicDataAccessor;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ICastData;
import io.redspace.ironsspellbooks.player.SpinAttackType;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import io.redspace.ironsspellbooks.datagen.DamageTypeTagGenerator;
import io.redspace.ironsspellbooks.network.casting.OnClientCastPacket;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Iron's-backed {@link IronGlyphBridge}. Constructed only when Iron's is loaded.
 *
 * <h2>How an imported effect runs a native spell</h2>
 * Every execution follows Iron's own {@code castSpell} order: {@code SpellPreCastEvent}, the
 * spell's own {@code checkPreCastConditions}, {@code SpellOnCastEvent} (whose final cost and level
 * are what is paid and cast), payment, {@code onCast}, then native recast and cooldown
 * bookkeeping and the client cast packet. Two things differ, both scoped to this one call:
 * <ul>
 *   <li>the spell gets a <em>detached casting slot</em>: a fresh {@link MagicData} that shares the
 *       player's real recast store and cooldowns but not their casting state or cast data, so a
 *       native cast the player has in progress (including the Iron's wheel proxy an exported Ars
 *       spell is running inside) is never replaced or cancelled;</li>
 *   <li>an {@link AdapterFrame} points the caster's Iron's raycasts and target-block picks at the
 *       Ars hit, and collects what the call spawns so a projectile or summon can be placed there.</li>
 * </ul>
 * Families (the catalog's adapter) decide the frame and the placement; see
 * {@code docs/3.4.0/iron-glyph-coverage.md}.
 */
public final class IronGlyphBridgeImpl implements IronGlyphBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(IronGlyphBridgeImpl.class);
    private static final CastSource SOURCE = CastSource.SPELLBOOK;
    private static final Map<Class<?>, Method> DISTANCE = new ConcurrentHashMap<>();
    /** Used only if a teleport spell's own range cannot be read; Iron's base teleports stay well inside it. */
    static final float FALLBACK_TELEPORT_RANGE = 24.0f;

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<SpellInventoryEntry> inventory() {
        return IronsSpellInventory.snapshot();
    }

    @Override
    public GlyphBehaviorSnapshot.LiveSpell liveSpell(ResourceLocation spellId) {
        AbstractSpell spell = SpellRegistry.REGISTRY.get(spellId);
        if (spell == null || spell == SpellRegistry.none()) {
            return new GlyphBehaviorSnapshot.LiveSpell(false, false, 0, List.of());
        }
        int max = Math.max(1, spell.getMaxLevel());
        List<String> rarities = new ArrayList<>(max);
        for (int level = 1; level <= max; level++) rarities.add(spell.getRarity(level).name());
        return new GlyphBehaviorSnapshot.LiveSpell(true, spell.isEnabled(), max, rarities);
    }

    @Override
    public boolean hasLearned(ServerPlayer player, ResourceLocation spellId) {
        AbstractSpell spell = SpellRegistry.REGISTRY.get(spellId);
        return spell != null && (!spell.requiresLearning() || spell.isLearned(player));
    }

    // ---- Glyph Press ----

    private static final ResourceLocation SCROLL_ITEM = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "scroll");

    @Override
    public boolean isScrollItem(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof io.redspace.ironsspellbooks.item.Scroll
            && SCROLL_ITEM.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Strict reading for conversion: the native scroll item only (addon scroll items need their own
     * reader), never an ANS carrier or Spell Loom export, exactly one entry in a one-slot container,
     * a registered spell other than {@code none}, and a level the spell actually has. Nothing is
     * clamped or guessed; anything else is a named refusal.
     */
    @Override
    public com.otectus.arsnspells.glyph.recipe.ScrollReading readScroll(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return com.otectus.arsnspells.glyph.recipe.ScrollReading.EMPTY;
        if (com.otectus.arsnspells.spell.CrossModSpellComponents.has(stack)) {
            return com.otectus.arsnspells.glyph.recipe.ScrollReading.EXPORTED;
        }
        if (!isScrollItem(stack)) return com.otectus.arsnspells.glyph.recipe.ScrollReading.NOT_A_SCROLL;
        try {
            io.redspace.ironsspellbooks.api.spells.ISpellContainer container = io.redspace.ironsspellbooks.api.spells.ISpellContainer.get(stack);
            if (container == null || container.isEmpty()) return com.otectus.arsnspells.glyph.recipe.ScrollReading.BLANK;
            List<io.redspace.ironsspellbooks.api.spells.SpellSlot> active = container.getActiveSpells();
            if (active.size() > 1) return com.otectus.arsnspells.glyph.recipe.ScrollReading.MULTIPLE;
            if (container.getMaxSpellCount() != 1 || active.isEmpty()) {
                return active.isEmpty() ? com.otectus.arsnspells.glyph.recipe.ScrollReading.BLANK
                    : com.otectus.arsnspells.glyph.recipe.ScrollReading.MALFORMED;
            }
            io.redspace.ironsspellbooks.api.spells.SpellData data = active.get(0).spellData();
            AbstractSpell spell = data == null ? null : data.getSpell();
            if (spell == null || spell == SpellRegistry.none()) return com.otectus.arsnspells.glyph.recipe.ScrollReading.BLANK;
            ResourceLocation id = spell.getSpellResource();
            if (com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry.poolIdOf(id) > 0) {
                return com.otectus.arsnspells.glyph.recipe.ScrollReading.EXPORTED;
            }
            int level = data.getLevel();
            if (level < spell.getMinLevel() || level > spell.getMaxLevel()) {
                return com.otectus.arsnspells.glyph.recipe.ScrollReading.invalidLevel(id, level);
            }
            return com.otectus.arsnspells.glyph.recipe.ScrollReading.spell(id, level);
        } catch (RuntimeException unreadable) {
            return com.otectus.arsnspells.glyph.recipe.ScrollReading.MALFORMED;
        }
    }

    @Override
    public net.minecraft.world.item.ItemStack scrollStack(ResourceLocation spellId, int level) {
        AbstractSpell spell = spell(spellId);
        if (spell == null) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(ItemRegistry.SCROLL.get());
        io.redspace.ironsspellbooks.api.spells.ISpellContainer.createScrollContainer(spell, level, stack);
        return stack;
    }

    @Override
    public int schoolColor(ResourceLocation spellId) {
        AbstractSpell spell = spell(spellId);
        if (spell == null) return 0x9B6BD6;
        org.joml.Vector3f color = spell.getSchoolType().getTargetingColor();
        int r = Math.round(Math.max(0, Math.min(1, color.x())) * 255);
        int g = Math.round(Math.max(0, Math.min(1, color.y())) * 255);
        int b = Math.round(Math.max(0, Math.min(1, color.z())) * 255);
        return (r << 16) | (g << 8) | b;
    }

    @Nullable
    static AbstractSpell spell(ResourceLocation id) {
        AbstractSpell spell = SpellRegistry.REGISTRY.get(id);
        return spell == null || spell == SpellRegistry.none() ? null : spell;
    }

    // ---- Admission ----

    @Override
    public Component nativeRefusal(ServerPlayer player, ResourceLocation spellId, boolean checkCooldown) {
        AbstractSpell spell = spell(spellId);
        if (spell == null) return Component.translatable("ars_n_spells.glyph.dormant.spell_not_registered");
        if (!spell.isEnabled()) return Component.translatable("ars_n_spells.glyph.dormant.spell_disabled_by_irons");
        if (ServerConfigs.DISABLE_ADVENTURE_MODE_CASTING.get() && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
            return Component.translatable("ui.irons_spellbooks.cast_error_adventure");
        }
        if (spell.requiresLearning() && !spell.isLearned(player)) {
            return Component.translatable("ui.irons_spellbooks.cast_error_unlearned");
        }
        if (checkCooldown && MagicData.getPlayerMagicData(player).getPlayerCooldowns().isOnCooldown(spell)
                && !(player.isCreative() && !ServerConfigs.CREATIVE_COOLDOWN.get())) {
            return Component.translatable("ui.irons_spellbooks.cast_error_cooldown", spell.getDisplayName(player));
        }
        return null;
    }

    @Override
    public boolean hasPendingRecast(ServerPlayer player, ResourceLocation spellId) {
        return MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(spellId.toString());
    }

    @Override
    public int manaCost(ResourceLocation spellId, int level) {
        AbstractSpell spell = spell(spellId);
        return spell == null ? 0 : spell.getManaCost(level);
    }

    @Override
    public boolean chargesMana(ServerPlayer player) {
        return !player.isCreative() || ServerConfigs.CREATIVE_MANA_COST.get();
    }

    @Override
    public int effectiveCastTime(ResourceLocation spellId, int level, ServerPlayer player) {
        AbstractSpell spell = spell(spellId);
        return spell == null ? 0 : spell.getEffectiveCastTime(level, player);
    }

    @Override
    public boolean interruptsCharge(ResourceLocation spellId, ServerPlayer player, DamageSource source) {
        AbstractSpell spell = spell(spellId);
        return spell != null && spell.canBeInterrupted(player) && !source.is(DamageTypeTagGenerator.LONG_CAST_IGNORE)
            && !ItemRegistry.CONCENTRATION_AMULET.get().isEquippedBy(player);
    }

    @Override
    public void chargeStarted(GlyphRequest request) {
        AbstractSpell spell = spell(request.key().ironSpellId());
        if (spell != null) spell.playSound(spell.getCastStartSound(), request.caster());
    }

    // ---- Execution ----

    /** A detached casting slot sharing the player's real recasts and cooldowns. */
    static MagicData childSlot(ServerPlayer player) {
        MagicData real = MagicData.getPlayerMagicData(player);
        MagicData child = new MagicData();
        child.getSyncedData();
        ((MagicDataAccessor) child).arsnspells$setPlayerRecasts(real.getPlayerRecasts());
        ((MagicDataAccessor) child).arsnspells$setPlayerCooldowns(real.getPlayerCooldowns());
        return child;
    }

    @Override
    public ExecutionResult execute(GlyphRequest request, PaymentGate gate) {
        AbstractSpell spell = spell(request.key().ironSpellId());
        ServerPlayer player = request.caster();
        if (spell == null) return ExecutionResult.refused(Component.translatable("ars_n_spells.glyph.dormant.spell_not_registered"));
        ServerLevel level = player.serverLevel();
        GlyphInvocationScope.enter(request.invocationId());
        try {
            SpellPreCastEvent pre = new SpellPreCastEvent(player, spell.getSpellId(), request.effectiveLevel(), spell.getSchoolType(), SOURCE);
            if (NeoForge.EVENT_BUS.post(pre).isCanceled()) {
                return ExecutionResult.refused(Component.translatable("ars_n_spells.glyph.refused.vetoed", spell.getDisplayName(player)));
            }
            MagicData real = MagicData.getPlayerMagicData(player);
            MagicData child = childSlot(player);
            Family family = Family.of(request.entry());
            if (family == Family.TELEPORT && !request.selfHit()) {
                Vec3 destination = teleportDestination(spell, request, level);
                if (destination == null) {
                    return ExecutionResult.refused(Component.translatable("ars_n_spells.glyph.refused.teleport_unsafe", spell.getDisplayName(player)));
                }
                child.setAdditionalCastData(new TeleportSpell.TeleportData(destination));
            }
            AdapterFrame frame = frame(family, request, player);
            boolean ready = AdapterFrame.call(frame, () -> spell.checkPreCastConditions(level, request.effectiveLevel(), player, child));
            if (!ready) {
                return ExecutionResult.refused(Component.translatable("ars_n_spells.glyph.refused.native_condition", spell.getDisplayName(player)));
            }
            boolean hadRecast = real.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
            SpellOnCastEvent onCast = new SpellOnCastEvent(player, spell.getSpellId(), request.effectiveLevel(),
                spell.getManaCost(request.effectiveLevel()), spell.getSchoolType(), SOURCE);
            NeoForge.EVENT_BUS.post(onCast);
            int finalLevel = Math.max(1, onCast.getSpellLevel());
            if (!hadRecast && !gate.pay(Math.max(0, onCast.getManaCost()), finalLevel)) {
                child.resetAdditionalCastData();
                return new ExecutionResult(false, Component.translatable("ars_n_spells.glyph.refused.unpaid", spell.getDisplayName(player)), false);
            }
            AdapterFrame.run(frame, () -> spell.onCast(level, finalLevel, player, SOURCE, child));
            place(family, request, frame.spawned, player);
            propagateVisualState(child, real);
            boolean recastsLeft = real.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
            if (hadRecast && recastsLeft) {
                real.getPlayerRecasts().decrementRecastCount(spell.getSpellId());
            } else if (!recastsLeft && request.applyCooldown() && (!player.isCreative() || ServerConfigs.CREATIVE_COOLDOWN.get())) {
                MagicHelper.MAGIC_MANAGER.addCooldown(player, spell, SOURCE);
            }
            ICastData castData = child.getAdditionalCastData();
            PacketDistributor.sendToPlayer(player, new OnClientCastPacket(spell.getSpellId(), finalLevel, SOURCE, castData),
                new CustomPacketPayload[0]);
            child.resetCastingState();
            return new ExecutionResult(true, null, !hadRecast);
        } catch (RuntimeException failure) {
            LOGGER.warn("[ImportedGlyphs] {} failed while running: {}", request.key(), failure.toString());
            LOGGER.debug("[ImportedGlyphs] failure", failure);
            return ExecutionResult.refused(Component.translatable("ars_n_spells.glyph.refused.failed", spell.getDisplayName(player)));
        } finally {
            GlyphInvocationScope.exit(request.invocationId());
        }
    }

    /** Burning Dash and Volt Strike set a visual spin state on the casting slot; the client reads the real one. */
    private static void propagateVisualState(MagicData child, MagicData real) {
        SpinAttackType spin = child.getSyncedData().getSpinAttackType();
        if (spin != null && spin != SpinAttackType.RIPTIDE && spin != real.getSyncedData().getSpinAttackType()) {
            real.getSyncedData().setSpinAttackType(spin);
        }
    }

    // ---- Families ----

    enum Family {
        SELF, PROJECTILE, ENTITY, POSITION, BLOCK, TELEPORT, SUMMON, CHANNEL;

        static Family of(CatalogEntry entry) {
            return switch (entry.adapter().getPath()) {
                case "projectile" -> PROJECTILE;
                case "entity" -> ENTITY;
                case "position" -> POSITION;
                case "block" -> BLOCK;
                case "teleport" -> TELEPORT;
                case "summon" -> SUMMON;
                case "channel" -> CHANNEL;
                default -> SELF;
            };
        }
    }

    /**
     * The frame a family runs native code in. A Self hit always runs the spell exactly as a
     * native cast would (no redirection), so Self means what it means in Iron's.
     */
    static AdapterFrame frame(Family family, GlyphRequest request, ServerPlayer player) {
        boolean capture = family == Family.PROJECTILE || family == Family.SUMMON;
        if (request.selfHit()) {
            if (request.entry().target() == CatalogEntry.TargetKind.ENTITY_OR_SELF) {
                // Iron's falls back to its caster when nothing is targeted; point the pick at nothing.
                return new AdapterFrame(player, BlockHitResult.miss(player.getEyePosition(), Direction.UP, player.blockPosition()), null, capture);
            }
            return new AdapterFrame(player, null, null, capture);
        }
        HitResult hit = request.hit();
        BlockHitResult block = hit instanceof BlockHitResult b ? b : null;
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            block = new BlockHitResult(target.position(), Direction.UP, target.blockPosition(), false);
        }
        return switch (family) {
            case SELF -> new AdapterFrame(player, null, null, false);
            case PROJECTILE, SUMMON -> new AdapterFrame(player, null, null, true);
            case ENTITY, POSITION, BLOCK, TELEPORT, CHANNEL -> new AdapterFrame(player, hit, block, capture);
        };
    }

    /** After {@code onCast}: put a projectile or summon the call spawned at the Ars hit. */
    static void place(Family family, GlyphRequest request, List<Entity> spawned, ServerPlayer player) {
        if (spawned.isEmpty()) return;
        if (family == Family.PROJECTILE) {
            double speedFactor = com.otectus.arsnspells.glyph.runtime.GlyphArithmetic.launchSpeedFactor(request.speedSteps());
            for (Entity entity : spawned) {
                if (!(entity instanceof Projectile projectile) || projectile.getOwner() != player) continue;
                Vec3 velocity = projectile.getDeltaMovement();
                double speed = Math.max(0.05, velocity.length()) * speedFactor;
                if (request.selfHit()) {
                    projectile.setDeltaMovement(velocity.lengthSqr() < 1.0e-8 ? velocity : velocity.normalize().scale(speed));
                    continue;
                }
                directImpact(projectile, request.hit(), player, speed);
            }
        } else if (family == Family.SUMMON && !request.selfHit()) {
            Vec3 anchor = anchor(request.hit());
            for (Entity entity : spawned) {
                if (entity instanceof Mob mob && isOwnedSummon(mob, player)) {
                    Vec3 safe = safeStandingSpot(mob, anchor);
                    if (safe != null) mob.moveTo(safe.x, safe.y, safe.z, mob.getYRot(), mob.getXRot());
                }
            }
        }
    }

    /**
     * Supported direct-impact delivery: the native projectile, with its native owner, damage and
     * on-hit behaviour, starts just short of the Ars hit and travels into it, so it strikes that
     * target on its next tick and nothing else first. The caster is never in its path.
     */
    static void directImpact(Projectile projectile, HitResult hit, ServerPlayer player, double speed) {
        Vec3 target;
        double standOff;
        if (hit instanceof EntityHitResult entityHit) {
            Entity victim = entityHit.getEntity();
            target = victim.getBoundingBox().getCenter();
            standOff = victim.getBbWidth() * 0.5 + projectile.getBbWidth() * 0.5 + 0.6;
        } else {
            target = hit.getLocation();
            standOff = projectile.getBbWidth() * 0.5 + 0.6;
        }
        Vec3 from = player.getEyePosition();
        Vec3 direction = target.subtract(from);
        if (direction.lengthSqr() < 1.0e-6) direction = player.getViewVector(1.0f);
        direction = direction.normalize();
        Vec3 start = target.subtract(direction.scale(standOff));
        projectile.moveTo(start.x, start.y - projectile.getBbHeight() * 0.5, start.z, projectile.getYRot(), projectile.getXRot());
        projectile.setDeltaMovement(direction.scale(speed));
        projectile.hasImpulse = true;
    }

    static Vec3 anchor(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit) return entityHit.getEntity().position();
        if (hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
            return Vec3.atBottomCenterOf(block.getBlockPos().relative(block.getDirection()));
        }
        return hit.getLocation();
    }

    /**
     * A summon the spell's own {@code onCast} just created inside this frame. Capture is scoped to
     * that one call for its caster, so this is the caster's summon by construction; Iron's records
     * the owner itself ({@code SummonManager.initSummon}).
     */
    private static boolean isOwnedSummon(Mob mob, ServerPlayer player) {
        return mob instanceof io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
    }

    /** A free standing spot within two blocks of {@code anchor}, on solid ground, or null. */
    @Nullable
    static Vec3 safeStandingSpot(Entity entity, Vec3 anchor) {
        ServerLevel level = (ServerLevel) entity.level();
        BlockPos base = BlockPos.containing(anchor);
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos feet = base.offset(dx, dy, dz);
                    if (!level.isLoaded(feet) || !level.getWorldBorder().isWithinBounds(feet)) continue;
                    if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) continue;
                    Vec3 pos = Vec3.atBottomCenterOf(feet);
                    AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(pos);
                    if (level.noCollision(entity, box)) return pos;
                }
            }
        }
        return null;
    }

    /**
     * Where a teleport glyph moves its caster: Iron's own landing solver at the Ars hit (beside a
     * hit entity, never onto it), only within the spell's native range for the effective level, in
     * a loaded chunk inside the world border. The target never moves.
     */
    @Nullable
    static Vec3 teleportDestination(AbstractSpell spell, GlyphRequest request, ServerLevel level) {
        ServerPlayer player = request.caster();
        HitResult hit = request.hit();
        Vec3 dest;
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            Vec3 toTarget = target.position().subtract(player.position());
            Vec3 back = toTarget.lengthSqr() < 1.0e-6 ? Vec3.ZERO : toTarget.normalize()
                .scale(target.getBbWidth() * 0.5 + player.getBbWidth() * 0.5 + 0.5);
            Vec3 spot = target.position().subtract(back.x, 0, back.z);
            dest = TeleportSpell.solveTeleportDestination(level, player, BlockPos.containing(spot).below(), spot);
        } else if (hit instanceof BlockHitResult block) {
            dest = TeleportSpell.solveTeleportDestination(level, player, block.getBlockPos(), block.getLocation());
        } else {
            return null;
        }
        BlockPos at = BlockPos.containing(dest);
        if (!level.isLoaded(at) || !level.getWorldBorder().isWithinBounds(at)) return null;
        float range = nativeRange(spell, request.effectiveLevel(), player);
        if (dest.distanceTo(player.position()) > range + 1.0) return null;
        AABB box = player.getDimensions(player.getPose()).makeBoundingBox(dest);
        return level.noCollision(player, box) ? dest : null;
    }

    /** The spell's own teleport range at {@code level} (each teleport spell keeps it private). */
    static float nativeRange(AbstractSpell spell, int level, LivingEntity caster) {
        try {
            Method method = DISTANCE.computeIfAbsent(spell.getClass(), type -> {
                try {
                    Method m = type.getDeclaredMethod("getDistance", int.class, LivingEntity.class);
                    m.setAccessible(true);
                    return m;
                } catch (ReflectiveOperationException missing) {
                    throw new IllegalStateException(missing);
                }
            });
            return ((Number) method.invoke(spell, level, caster)).floatValue();
        } catch (RuntimeException | ReflectiveOperationException unreadable) {
            if (com.otectus.arsnspells.util.LogThrottle.allow(new java.util.UUID(0, 1), 60_000)) {
                LOGGER.warn("[ImportedGlyphs] could not read {}'s teleport range; using {} blocks", spell.getSpellId(), FALLBACK_TELEPORT_RANGE);
            }
            return FALLBACK_TELEPORT_RANGE;
        }
    }

    // ---- Channels ----

    @Override
    public GlyphChannel startChannel(GlyphRequest request, int extraPulses, PaymentGate gate, PulseGate pulses) {
        AbstractSpell spell = spell(request.key().ironSpellId());
        if (spell == null) return null;
        ServerPlayer player = request.caster();
        GlyphInvocationScope.enter(request.invocationId());
        try {
            SpellPreCastEvent pre = new SpellPreCastEvent(player, spell.getSpellId(), request.effectiveLevel(), spell.getSchoolType(), SOURCE);
            if (NeoForge.EVENT_BUS.post(pre).isCanceled()) {
                player.displayClientMessage(Component.translatable("ars_n_spells.glyph.refused.vetoed", spell.getDisplayName(player))
                    .withStyle(ChatFormatting.RED), true);
                return null;
            }
            IronGlyphChannel channel = new IronGlyphChannel(spell, request, childSlot(player),
                frame(Family.CHANNEL, request, player), extraPulses, pulses);
            return channel.begin(gate) ? channel : null;
        } finally {
            GlyphInvocationScope.exit(request.invocationId());
        }
    }
}
