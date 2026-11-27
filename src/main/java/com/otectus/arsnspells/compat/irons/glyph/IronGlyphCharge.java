package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.glyph.IronGlyphBridge;
import com.otectus.arsnspells.glyph.runtime.AdapterFrame;
import com.otectus.arsnspells.glyph.runtime.GlyphCharge;
import com.otectus.arsnspells.glyph.runtime.GlyphInvocationScope;
import com.otectus.arsnspells.glyph.runtime.GlyphRequest;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ICastData;
import io.redspace.ironsspellbooks.network.casting.OnClientCastPacket;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A charged Iron's spell run by an imported glyph on a detached casting slot, in Iron's own order.
 * See {@link GlyphCharge}. The detached slot shares the player's recasts and cooldowns but never their
 * casting state, so a native cast the player has in progress is untouched.
 *
 * <p>The charge ends by resetting its slot, which releases any cast data its pre-cast hook created
 * (Fortify's target-area marker, for one). It does not post {@code onServerCastComplete}: Iron's base
 * implementation tells the player's client and trackers that a cast finished, which would end a native
 * cast the player has running in their real slot. No pinned 3.16.3 charged spell has completion logic
 * of its own (Heat Surge's override only calls the base), so nothing is lost; an addon charged spell
 * that needs it is a documented limitation.
 */
final class IronGlyphCharge implements GlyphCharge {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronGlyphCharge.class);
    private static final CastSource SOURCE = CastSource.SPELLBOOK;

    private final AbstractSpell spell;
    private final GlyphRequest request;
    private final MagicData slot;
    private final AdapterFrame frame;
    private final IronGlyphBridgeImpl.Family family;
    private boolean ended;
    private boolean failed;

    private IronGlyphCharge(AbstractSpell spell, GlyphRequest request, MagicData slot, AdapterFrame frame, IronGlyphBridgeImpl.Family family) {
        this.spell = spell;
        this.request = request;
        this.slot = slot;
        this.frame = frame;
        this.family = family;
    }

    /** Initiate: the pre-cast event and conditions, then the slot, {@code onServerPreCast} and the start sound. */
    @Nullable
    static IronGlyphCharge start(AbstractSpell spell, GlyphRequest request, int ticks, Component[] refusalOut) {
        ServerPlayer player = request.caster();
        ServerLevel level = player.serverLevel();
        MagicData slot = null;
        GlyphInvocationScope.enter(request.invocationId());
        try {
            SpellPreCastEvent pre = new SpellPreCastEvent(player, spell.getSpellId(), request.nativeLevel(), spell.getSchoolType(), SOURCE);
            if (NeoForge.EVENT_BUS.post(pre).isCanceled()) {
                refusalOut[0] = Component.translatable("ars_n_spells.glyph.refused.vetoed", spell.getDisplayName(player));
                return null;
            }
            slot = IronGlyphBridgeImpl.childSlot(player);
            IronGlyphBridgeImpl.Family family = IronGlyphBridgeImpl.Family.of(request.entry());
            if (family == IronGlyphBridgeImpl.Family.TELEPORT && !request.selfHit()) {
                Vec3 destination = IronGlyphBridgeImpl.teleportDestination(spell, request, level);
                if (destination == null) {
                    refusalOut[0] = Component.translatable("ars_n_spells.glyph.refused.teleport_unsafe", spell.getDisplayName(player));
                    resetQuietly(slot);
                    return null;
                }
                slot.setAdditionalCastData(new TeleportSpell.TeleportData(destination));
            }
            AdapterFrame frame = IronGlyphBridgeImpl.frame(family, request, player);
            MagicData target = slot;
            boolean ready = AdapterFrame.call(frame, () -> spell.checkPreCastConditions(level, request.nativeLevel(), player, target));
            if (!ready) {
                refusalOut[0] = Component.translatable("ars_n_spells.glyph.refused.native_condition", spell.getDisplayName(player));
                resetQuietly(slot);
                return null;
            }
            slot.initiateCast(spell, request.nativeLevel(), Math.max(1, ticks), SOURCE, "");
            // Iron's base onServerPreCast plays the cast-start sound; a spell's own override adds its setup.
            AdapterFrame.run(frame, () -> spell.onServerPreCast(level, request.nativeLevel(), player, target));
            return new IronGlyphCharge(spell, request, slot, frame, family);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[ImportedGlyphs] {} could not start its charge: {}", request.key(), failure.toString());
            LOGGER.debug("[ImportedGlyphs] charge failure", failure);
            refusalOut[0] = Component.translatable("ars_n_spells.glyph.refused.failed", spell.getDisplayName(player));
            resetQuietly(slot);
            return null;
        } finally {
            GlyphInvocationScope.exit(request.invocationId());
        }
    }

    @Override
    public void tick() {
        if (ended || failed) return;
        ServerPlayer player = request.caster();
        GlyphInvocationScope.enter(request.invocationId());
        try {
            slot.handleCastDuration();
            AdapterFrame.run(frame, () -> spell.onServerCastTick(player.serverLevel(), request.nativeLevel(), player, slot));
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            LOGGER.warn("[ImportedGlyphs] {} failed while charging: {}", request.key(), failure.toString());
            LOGGER.debug("[ImportedGlyphs] charge failure", failure);
        } finally {
            GlyphInvocationScope.exit(request.invocationId());
        }
    }

    @Override
    public IronGlyphBridge.ExecutionResult complete(IronGlyphBridge.PaymentGate gate) {
        if (ended) return IronGlyphBridge.ExecutionResult.refused(Component.translatable("ars_n_spells.glyph.refused.native"));
        ended = true;
        ServerPlayer player = request.caster();
        ServerLevel level = player.serverLevel();
        MagicData real = MagicData.getPlayerMagicData(player);
        boolean effectStarted = false;
        boolean paid = false;
        boolean hadRecast = false;
        int finalLevel = -1;
        GlyphInvocationScope.enter(request.invocationId());
        try {
            hadRecast = real.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
            SpellOnCastEvent onCast = new SpellOnCastEvent(player, spell.getSpellId(), request.nativeLevel(),
                spell.getManaCost(request.nativeLevel()), spell.getSchoolType(), SOURCE);
            NeoForge.EVENT_BUS.post(onCast);
            finalLevel = Math.max(1, onCast.getSpellLevel());
            if (!hadRecast && !gate.pay(Math.max(0, onCast.getManaCost()), finalLevel)) {
                return new IronGlyphBridge.ExecutionResult(IronGlyphBridge.Stage.REFUSED_AT_PAYMENT,
                    Component.translatable("ars_n_spells.glyph.refused.unpaid", spell.getDisplayName(player)), false, finalLevel);
            }
            paid = !hadRecast;
            effectStarted = true;
            com.otectus.arsnspells.casting.PaymentTimeline.record(player.getUUID(), -1,
                com.otectus.arsnspells.casting.PaymentTimeline.Kind.NATIVE_EFFECT, spell.getSpellId(), "imported charge level=" + finalLevel);
            int castLevel = finalLevel;
            AdapterFrame.run(frame, () -> spell.onCast(level, castLevel, player, SOURCE, slot));
            IronGlyphBridgeImpl.place(family, request, frame.spawned, player);
            ICastData castData = slot.getAdditionalCastData();
            PacketDistributor.sendToPlayer(player, new OnClientCastPacket(spell.getSpellId(), finalLevel, SOURCE, castData),
                new CustomPacketPayload[0]);
            return new IronGlyphBridge.ExecutionResult(IronGlyphBridge.Stage.COMPLETED, null, paid, finalLevel);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[ImportedGlyphs] {} release failed {} its effect began: {}", request.key(), effectStarted ? "after" : "before", failure.toString());
            LOGGER.debug("[ImportedGlyphs] charge failure", failure);
            Component why = Component.translatable("ars_n_spells.glyph.refused.failed", spell.getDisplayName(player));
            return effectStarted ? new IronGlyphBridge.ExecutionResult(IronGlyphBridge.Stage.FAILED_AFTER_EFFECT, why, paid, finalLevel)
                : IronGlyphBridge.ExecutionResult.refused(why);
        } finally {
            try {
                if (effectStarted) IronGlyphBridgeImpl.commitRecastOrCooldown(spell, player, real, hadRecast, request.applyCooldown());
            } catch (RuntimeException | LinkageError bookkeeping) {
                LOGGER.warn("[ImportedGlyphs] {} cooldown bookkeeping failed: {}", request.key(), bookkeeping.toString());
            }
            resetQuietly(slot);
            GlyphInvocationScope.exit(request.invocationId());
        }
    }

    @Override
    public void cancel() {
        if (ended) return;
        ended = true;
        resetQuietly(slot);
    }

    @Override
    public boolean failed() {
        return failed;
    }

    private static void resetQuietly(@Nullable MagicData slot) {
        if (slot == null) return;
        try {
            slot.resetCastingState();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("[ImportedGlyphs] detached charge slot cleanup failed", failure);
        }
    }
}
