package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.glyph.IronGlyphBridge;
import com.otectus.arsnspells.glyph.runtime.AdapterFrame;
import com.otectus.arsnspells.glyph.runtime.GlyphChannel;
import com.otectus.arsnspells.glyph.runtime.GlyphInvocationScope;
import com.otectus.arsnspells.glyph.runtime.GlyphRequest;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A continuous Iron's spell run by an imported glyph on a detached casting slot.
 *
 * <p>Mirrors Iron's own continuous lifecycle tick for tick: the slot is initiated for the effective
 * cast time (plus or minus Extend Time / Duration Down, two pulses each), the spell's
 * {@code onServerCastTick} runs every tick, and {@code onCast} pulses whenever the remaining time is
 * a multiple of {@value MagicManager#CONTINUOUS_CAST_TICK_INTERVAL} ticks, including the final pulse at
 * zero, each paid before it runs. Spells that keep an entity on the slot (cones, telekinesis targets,
 * starfall centres) see a real casting state on the detached slot; the player's own slot and any
 * native cast in it are never touched. Ending resets the detached slot (which discards a cone) and
 * commits the native cooldown once a pulse has run.
 */
final class IronGlyphChannel implements GlyphChannel {

    private static final CastSource SOURCE = CastSource.SPELLBOOK;

    private final AbstractSpell spell;
    private final GlyphRequest request;
    private final MagicData slot;
    private final AdapterFrame frame;
    private final IronGlyphBridge.PulseGate pulses;
    private final int duration;
    private boolean pulsedOnce;
    private boolean ended;

    IronGlyphChannel(AbstractSpell spell, GlyphRequest request, MagicData slot, AdapterFrame frame, int extraPulses,
                     IronGlyphBridge.PulseGate pulses) {
        this.spell = spell;
        this.request = request;
        this.slot = slot;
        this.frame = frame;
        this.pulses = pulses;
        int base = spell.getEffectiveCastTime(request.effectiveLevel(), request.caster());
        int interval = MagicManager.CONTINUOUS_CAST_TICK_INTERVAL;
        int adjusted = base + extraPulses * interval;
        this.duration = Math.max(interval, (adjusted / interval) * interval);
    }

    /** Initiate the slot, check the spell's own conditions, then run the prepaid first pulse. */
    boolean begin(IronGlyphBridge.PaymentGate firstPulse) {
        ServerPlayer player = request.caster();
        ServerLevel level = player.serverLevel();
        boolean ready = AdapterFrame.call(frame, () -> spell.checkPreCastConditions(level, request.effectiveLevel(), player, slot));
        if (!ready) return false;
        slot.initiateCast(spell, request.effectiveLevel(), duration, SOURCE, "");
        AdapterFrame.run(frame, () -> spell.onServerPreCast(level, request.effectiveLevel(), player, slot));
        int cost = onCastEventCost();
        if (!firstPulse.pay(cost, request.effectiveLevel())) {
            slot.resetCastingState();
            return false;
        }
        runPulse();
        return true;
    }

    private int onCastEventCost() {
        SpellOnCastEvent event = new SpellOnCastEvent(request.caster(), spell.getSpellId(), request.effectiveLevel(),
            spell.getManaCost(request.effectiveLevel()), spell.getSchoolType(), SOURCE);
        NeoForge.EVENT_BUS.post(event);
        return Math.max(0, event.getManaCost());
    }

    private void runPulse() {
        ServerPlayer player = request.caster();
        AdapterFrame.run(frame, () -> spell.onCast(player.serverLevel(), request.effectiveLevel(), player, SOURCE, slot));
        pulsedOnce = true;
    }

    @Override
    public void tick() {
        if (ended) return;
        GlyphInvocationScope.enter(request.invocationId());
        try {
            slot.handleCastDuration();
            if (slot.isCasting()) {
                ServerPlayer player = request.caster();
                AdapterFrame.run(frame, () -> spell.onServerCastTick(player.serverLevel(), request.effectiveLevel(), player, slot));
            }
        } finally {
            GlyphInvocationScope.exit(request.invocationId());
        }
    }

    @Override
    public boolean pulseDue() {
        return !ended && slot.getCastDurationRemaining() % MagicManager.CONTINUOUS_CAST_TICK_INTERVAL == 0;
    }

    @Override
    public boolean pulse() {
        if (ended) return false;
        GlyphInvocationScope.enter(request.invocationId());
        try {
            int cost = onCastEventCost();
            if (!pulses.payPulse(cost)) return false;
            runPulse();
            return true;
        } finally {
            GlyphInvocationScope.exit(request.invocationId());
        }
    }

    @Override
    public boolean finished() {
        return ended || slot.getCastDurationRemaining() <= 0;
    }

    @Override
    public void end(boolean cancelled, boolean applyCooldown) {
        if (ended) return;
        ended = true;
        slot.resetCastingState();
        ServerPlayer player = request.caster();
        if (pulsedOnce && applyCooldown && request.applyCooldown()
                && !MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(spell.getSpellId())
                && (!player.isCreative() || ServerConfigs.CREATIVE_COOLDOWN.get())) {
            MagicHelper.MAGIC_MANAGER.addCooldown(player, spell, SOURCE);
        }
    }

    @Override
    public int pulseCost() {
        return spell.getManaCost(request.effectiveLevel());
    }
}
