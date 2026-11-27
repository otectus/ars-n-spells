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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(IronGlyphChannel.class);
    private boolean pulsedOnce;
    private boolean ended;
    private boolean failed;

    IronGlyphChannel(AbstractSpell spell, GlyphRequest request, MagicData slot, AdapterFrame frame, int extraPulses,
                     IronGlyphBridge.PulseGate pulses) {
        this.spell = spell;
        this.request = request;
        this.slot = slot;
        this.frame = frame;
        this.pulses = pulses;
        int base = spell.getEffectiveCastTime(request.nativeLevel(), request.caster());
        int interval = MagicManager.CONTINUOUS_CAST_TICK_INTERVAL;
        int adjusted = base + extraPulses * interval;
        this.duration = Math.max(interval, (adjusted / interval) * interval);
    }

    /**
     * Initiate the slot, check the spell's own conditions, then run the prepaid first pulse.
     *
     * <p>3.4.1 review CAST-04: a failure before the first pulse's payment refuses the channel and
     * leaves the detached slot clean; a failure once the first pulse's effect began keeps that pulse
     * paid and marks the channel {@link #failed()}, which its continuation ends at once with the
     * cooldown the pulse earned.
     */
    boolean begin(IronGlyphBridge.PaymentGate firstPulse) {
        ServerPlayer player = request.caster();
        ServerLevel level = player.serverLevel();
        try {
            boolean ready = AdapterFrame.call(frame, () -> spell.checkPreCastConditions(level, request.nativeLevel(), player, slot));
            if (!ready) return false;
            slot.initiateCast(spell, request.nativeLevel(), duration, SOURCE, "");
            AdapterFrame.run(frame, () -> spell.onServerPreCast(level, request.nativeLevel(), player, slot));
            PulseTerms first = castEvent();
            if (!firstPulse.pay(first.cost(), first.level())) {
                resetSlot();
                return false;
            }
            runPulse(first.level());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[ImportedGlyphs] channel {} failed {} its first pulse: {}", request.key(), pulsedOnce ? "during" : "before", failure.toString());
            LOGGER.debug("[ImportedGlyphs] channel failure", failure);
            if (!pulsedOnce) {
                resetSlot();
                return false;
            }
            failed = true;
            return true;
        }
    }

    /**
     * One pulse's terms as Iron's cast event settled them: the cost paid and the level the pulse runs
     * at. Iron's continuous casting posts this event for every pulse and casts that pulse at the
     * event's level, so a listener may change either per pulse; both are used, never only the cost.
     */
    record PulseTerms(int cost, int level) {}

    /** The pulse terms of the last event, for diagnostics and tests. */
    private PulseTerms lastTerms;

    private PulseTerms castEvent() {
        SpellOnCastEvent event = new SpellOnCastEvent(request.caster(), spell.getSpellId(), request.nativeLevel(),
            spell.getManaCost(request.nativeLevel()), spell.getSchoolType(), SOURCE);
        NeoForge.EVENT_BUS.post(event);
        lastTerms = new PulseTerms(Math.max(0, event.getManaCost()), Math.max(1, event.getSpellLevel()));
        com.otectus.arsnspells.glyph.runtime.GlyphSessions.recordPulse(request, lastTerms.level(), lastTerms.cost());
        return lastTerms;
    }

    /** The effect boundary is marked before {@code onCast}: a pulse that throws stays paid and earns the cooldown. */
    private void runPulse(int level) {
        ServerPlayer player = request.caster();
        pulsedOnce = true;
        com.otectus.arsnspells.casting.PaymentTimeline.record(player.getUUID(), -1,
            com.otectus.arsnspells.casting.PaymentTimeline.Kind.NATIVE_EFFECT, spell.getSpellId(), "imported pulse level=" + level);
        AdapterFrame.run(frame, () -> spell.onCast(player.serverLevel(), level, player, SOURCE, slot));
    }

    @Override
    public void tick() {
        if (ended || failed) return;
        GlyphInvocationScope.enter(request.invocationId());
        try {
            slot.handleCastDuration();
            if (slot.isCasting()) {
                ServerPlayer player = request.caster();
                AdapterFrame.run(frame, () -> spell.onServerCastTick(player.serverLevel(), request.nativeLevel(), player, slot));
            }
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            LOGGER.warn("[ImportedGlyphs] channel {} failed while ticking: {}", request.key(), failure.toString());
            LOGGER.debug("[ImportedGlyphs] channel failure", failure);
        } finally {
            GlyphInvocationScope.exit(request.invocationId());
        }
    }

    @Override
    public boolean pulseDue() {
        return !ended && !failed && slot.getCastDurationRemaining() % MagicManager.CONTINUOUS_CAST_TICK_INTERVAL == 0;
    }

    @Override
    public boolean pulse() {
        if (ended || failed) return false;
        GlyphInvocationScope.enter(request.invocationId());
        boolean paid = false;
        try {
            PulseTerms terms = castEvent();
            if (!pulses.payPulse(terms.cost())) return false;
            paid = true;
            runPulse(terms.level());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            // Paid and begun: the pulse stays paid; the continuation ends the channel with its cooldown.
            failed = true;
            LOGGER.warn("[ImportedGlyphs] channel {} pulse failed {} payment: {}", request.key(), paid ? "after" : "before", failure.toString());
            LOGGER.debug("[ImportedGlyphs] channel failure", failure);
            return paid;
        } finally {
            GlyphInvocationScope.exit(request.invocationId());
        }
    }

    @Override
    public boolean finished() {
        return ended || slot.getCastDurationRemaining() <= 0;
    }

    @Override
    public boolean pulsedOnce() {
        return pulsedOnce;
    }

    @Override
    public boolean failed() {
        return failed;
    }

    /**
     * End the channel: clear the detached slot, then commit the native cooldown when a pulse crossed
     * its effect boundary and the caller says it is owed. Cleanup failures are contained so the
     * scheduler stays usable. Idempotent.
     */
    @Override
    public void end(boolean cancelled, boolean applyCooldown) {
        if (ended) return;
        ended = true;
        resetSlot();
        ServerPlayer player = request.caster();
        try {
            if (pulsedOnce && applyCooldown && request.applyCooldown()
                    && !MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(spell.getSpellId())
                    && (!player.isCreative() || ServerConfigs.CREATIVE_COOLDOWN.get())) {
                MagicHelper.MAGIC_MANAGER.addCooldown(player, spell, SOURCE);
            }
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[ImportedGlyphs] channel {} cooldown bookkeeping failed: {}", request.key(), failure.toString());
        }
    }

    private void resetSlot() {
        try {
            slot.resetCastingState();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("[ImportedGlyphs] detached channel slot cleanup failed", failure);
        }
    }

    @Override
    public int pulseCost() {
        return spell.getManaCost(request.nativeLevel());
    }
}
