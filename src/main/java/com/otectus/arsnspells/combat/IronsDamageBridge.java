package com.otectus.arsnspells.combat;

import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Ars Nouveau's Spell Damage Bonus perk -> Iron's Spellbooks spell damage.
 *
 * <p>The other half of the cross-mod combat bridge, and the half that never existed: nothing
 * in the tree read {@link PerkAttributes#SPELL_DAMAGE_BONUS} outside Ars' own damage path, so
 * a player who had invested in the perk got nothing from it while casting from a spellbook.
 *
 * <p>Ars treats that attribute as a flat addition rather than a multiplier, so it is added
 * the same way here. {@code spell_power_cap} is a cap on a <em>multiplier</em> and is
 * deliberately not applied - clamping a flat bonus with it would make the number mean
 * something different on each side of the bridge - and no new flat cap is introduced.
 *
 * <p>Iron's posts this event only from its spell <em>damage</em> path, so healing spells and
 * other non-damaging effects are untouched by construction; there is no need to sign-check the
 * amount.
 *
 * <p>Iron's-only: must only ever be registered behind the Iron's-loaded gate in
 * {@code ArsNSpells}.
 */
public class IronsDamageBridge {

    /** Add the caster's Ars Spell Damage Bonus to an Iron's spell hit, exactly once. */
    @SubscribeEvent
    public void onIronsSpellDamage(SpellDamageEvent event) {
        if (!AnsConfig.flag(AnsConfig.ENABLE_CROSS_MOD_COMBAT_STATS, true)) {
            return;
        }
        if (!AnsConfig.flag(AnsConfig.ENABLE_ARS_DAMAGE_FOR_IRONS_DAMAGE, true)) {
            return;
        }
        SpellDamageSource source = event.getSpellDamageSource();
        if (source == null || !(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Defensive only: an ars_cross_* proxy never builds an Iron's damage source - it hands
        // the cast to Ars, which posts Ars' own SpellDamageEvent and is handled by
        // ArsDamageBridge. So this cannot currently fire. The guard exists so that if a proxy
        // ever does reach Iron's damage path, the bonus is not applied twice to one hit.
        AbstractSpell spell = source.spell();
        if (spell != null && CrossCastNbt.isArsCrossProxyId(spell.getSpellId())) {
            return;
        }

        double bonus = player.getAttributeValue(PerkAttributes.SPELL_DAMAGE_BONUS.get());
        if (bonus == 0) {
            // A player with no perk investment used to leave no trace at all, so
            // /ans debug combat reported "none recorded" - indistinguishable from "the bridge
            // never fired", which is the one ambiguity these diagnostics exist to remove.
            // Record the zero case too (native == final, bonus 0) and return without touching
            // the event. The flag is still tested before anything allocates, so a bonus-less
            // hit with debug off costs exactly what it did before.
            if (AnsConfig.debugEnabled()) {
                float amount = event.getAmount();
                recordDebug(player, spell, amount, 0.0f, amount);
            }
            return;
        }

        // No resonance factor here: the Iron's side already has it, applied inside
        // getAmount() by the existing getSpellPower() mixin.
        float nativeAmount = event.getAmount();
        event.setAmount(nativeAmount + (float) bonus);

        // Diagnostics only, and gated on the flag by every caller - see recordDebug.
        if (AnsConfig.debugEnabled()) {
            recordDebug(player, spell, nativeAmount, (float) bonus, event.getAmount());
        }
    }

    /**
     * Flatten the Iron's spell into strings and hand the hit to {@link CombatDebugState}.
     *
     * <p>CombatDebugState must stay free of Iron's types (StateEvictionHandler reaches it on an
     * Iron's-less server), so the spell id and school are resolved here, on the gated side of
     * the fence - which means {@link AnsConfig#debugEnabled()} has to be tested by the caller,
     * before that flattening allocates anything. One boolean when debug is off, exactly as on
     * the Ars side.
     */
    private static void recordDebug(ServerPlayer player, AbstractSpell spell, float nativeAmount,
                                    float bonus, float finalAmount) {
        String spellId = spell == null ? null : spell.getSpellId();
        String school = spell == null || spell.getSchoolType() == null
            ? null : spell.getSchoolType().getId().toString();
        CombatDebugState.recordIrons(player, spellId, school, nativeAmount, bonus, finalAmount);
    }
}
