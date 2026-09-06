package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.SpellScalingUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Iron's Spellbooks spell power to Ars Nouveau spell damage.
 *
 * <p><b>Audit V08.</b> This used to compute the multiplier at {@code SpellCastEvent}, stage it
 * against the caster's UUID for a 60-tick window, and then try to recognise the resulting hit
 * on a generic {@code LivingHurtEvent} by substring-matching {@code DamageSource.getMsgId()}
 * for {@code "magic"} or {@code "ars_nouveau"}. That is player-wide, time-windowed attribution
 * and it was wrong in both directions: a delayed projectile fired by player A that landed after
 * player B opened their own window was scaled by whichever entry the map happened to hold, and
 * any incidental magic damage the caster dealt inside the window was scaled as if it were their
 * spell.
 *
 * <p>The fix is to stop guessing. Ars posts its own typed {@link SpellDamageEvent.Pre} on
 * exactly the path we want, carrying the caster, the target, the {@code SpellContext} and a
 * mutable damage field, so the spell that is being scaled is the spell that is being resolved.
 * No staging map, no tick window, and no message-string test. This is the same shape the
 * NeoForge 1.21.1 repo settled on; the imports and the event's payload differ because that repo
 * targets Ars 5.x.
 *
 * <p>Only the identification changed. The school-power x resonance composition is
 * {@link SpellScalingUtil#getMultiplierForCaster} exactly as before, and the
 * {@code spell_power_cap} ceiling is applied exactly as before; composition ordering is 3.4.0
 * work and is deliberately untouched here.
 *
 * <p>Event surface confirmed against the pinned Ars Nouveau 4.12.7 jar
 * ({@code ars-nouveau-401955-6688854.jar}):
 * {@code com.hollingsworth.arsnouveau.api.event.SpellDamageEvent$Pre} extends
 * {@code SpellDamageEvent} extends the Forge {@code Event}, with public fields
 * {@code damageSource}, {@code context}, {@code caster}, {@code target} and {@code damage}. It
 * is a FORGE-bus event and {@code isCancelable()} returns true, but this handler never cancels:
 * scaling a hit is not a reason to delete it, and cancelling would silently swallow the spell.
 *
 * <p>Iron's-only: {@link SpellScalingUtil} resolves Iron's {@code AttributeRegistry} entries, so
 * this class must only be registered behind the Iron's-loaded gate in
 * {@link com.otectus.arsnspells.ArsNSpells}.
 */
public class ArsSpellScalingHandler {

    /**
     * Scale one Ars spell hit by its own caster's Iron's spell power.
     *
     * <p>LOW priority preserves the ordering of the handler this replaces: run after ordinary
     * listeners have settled the base number, so the multiplier lands on the value that would
     * otherwise have been dealt.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onArsSpellDamage(SpellDamageEvent.Pre event) {
        // Ars exposes this event's payload as public fields, not getters.
        if (!(event.caster instanceof ServerPlayer player)) {
            return;
        }
        if (event.damage <= 0.0f) {
            return;
        }
        if (event.context == null) {
            return;
        }
        // getSpell(), not getRemainingSpell(): the school is resolved from the whole recipe,
        // which is what the cast-time code this replaces analyzed. Reading the remainder would
        // re-resolve the school partway through a multi-glyph spell — a rebalance, not a port.
        Spell spell = event.context.getSpell();
        if (spell == null || spell.isEmpty()) {
            return;
        }

        try {
            float multiplier = SpellScalingUtil.getMultiplierForCaster(player, spell);
            // Only touch the number when scaling actually changes the outcome.
            if (multiplier <= 1.001f && multiplier >= 0.999f) {
                return;
            }
            // The cap is applied against the pre-scaled amount, exactly as before.
            event.damage = (float) Math.min(
                event.damage * multiplier,
                event.damage * AnsConfig.SPELL_POWER_CAP.get());
        } catch (Throwable t) {
            // SpellScalingUtil reads Iron's attributes; a failure there must leave the hit
            // unscaled rather than break the spell.
        }
    }
}
