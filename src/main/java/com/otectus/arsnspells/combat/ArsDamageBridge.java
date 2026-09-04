package com.otectus.arsnspells.combat;

import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.SpellScalingUtil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Iron's Spellbooks spell power -> Ars Nouveau spell damage.
 *
 * <p>Replaces the old {@code ArsSpellScalingHandler}, which computed the right multiplier on
 * {@code SpellCastEvent}, staged it against the caster's UUID for 60 ticks, and then tried to
 * find the resulting hit on a generic {@code LivingDamageEvent.Pre} by sniffing
 * {@code DamageSource.getMsgId()} for {@code "magic"} or {@code "ars_nouveau"}. Ars 5.x posts
 * its spell damage under the msgIds {@code player}, {@code fire} and {@code freeze}, so that
 * test rejected every Ars spell hit and the scaling never applied at all.
 *
 * <p>The fix is to stop guessing: Ars posts its own typed {@link SpellDamageEvent.Pre} on the
 * exact path we want, carrying the caster, the target, the {@code SpellContext} and a mutable
 * damage field. No staging map, no tick window, no UUID bookkeeping, and no way to hit a
 * melee swing that happened to land inside the window.
 *
 * <p>Iron's-only: {@link SpellScalingUtil} resolves Iron's {@code AttributeRegistry} entries,
 * so this class must only ever be registered behind the Iron's-loaded gate in
 * {@code ArsNSpells}, and must not be referenced from a static field anywhere else.
 */
public class ArsDamageBridge {

    /**
     * Scale the damage of an Ars spell by its caster's Iron's spell power.
     *
     * <p>LOW priority mirrors the handler this replaces: run after ordinary listeners have
     * had their say about the base number, so the multiplier applies to the value that would
     * otherwise have been dealt.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onArsSpellDamage(SpellDamageEvent.Pre event) {
        if (!AnsConfig.flag(AnsConfig.ENABLE_CROSS_MOD_COMBAT_STATS, true)) {
            return;
        }
        if (!AnsConfig.flag(AnsConfig.ENABLE_IRONS_POWER_FOR_ARS_DAMAGE, true)) {
            return;
        }
        // Ars exposes this event's payload as public fields, not getters.
        if (!(event.caster instanceof ServerPlayer player)) {
            return;
        }
        if (event.damage <= 0) {
            return;
        }
        if (event.context == null) {
            return;
        }
        // getSpell(), not getRemainingSpell(): the school is resolved from the whole recipe,
        // which is what the cast-time code this replaces analyzed. Using the remainder would
        // silently re-resolve the school partway through a multi-glyph spell - a rebalance,
        // not a port.
        Spell spell = event.context.getSpell();
        if (spell == null || spell.isEmpty()) {
            return;
        }

        // No cross-cast/proxy guard here, deliberately. An Ars spell fired from an Iron's
        // spellbook goes through CrossCastingHandler.castArsSpell, which builds a real
        // SpellContext and runs SpellResolver.onCast - by the time this event is posted it is
        // an ordinary Ars resolve, and it is required to scale exactly like a native cast.
        SpellScalingUtil.SpellPowerBreakdown breakdown =
            SpellScalingUtil.getMultiplierForCaster(player, spell);

        // Only the multiplier. Ars folds PerkAttributes.SPELL_DAMAGE_BONUS into event.damage
        // before posting this event, so adding it again here would double-count the perk.
        float raw = event.damage;
        event.damage *= breakdown.multiplier();

        // Diagnostics only, and a no-op unless debug mode is on: CombatDebugState tests the
        // flag as its first statement, so an ordinary hit pays one boolean and allocates
        // nothing. The breakdown is handed over as-is rather than recomputed.
        CombatDebugState.recordArs(player, spell, breakdown, raw, event.damage);
    }
}
