package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.affinity.AffinityBonuses;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * Iron's Spellbooks-aware spell scaling for Ars Nouveau spells. The {@code ELEMENT_MAP}
 * resolves Iron's {@code AttributeRegistry} entries, so this class must never be loaded
 * on a server that does not have Iron's installed. All callers are expected to gate
 * on {@link com.otectus.arsnspells.compat.IronsCompat#isLoaded()} first; the map itself
 * is lazily built so that even a stray accidental import will not trip a static initializer
 * crash before the gate runs.
 */
public class SpellScalingUtil {
    private static volatile Map<SpellSchoolId, RegistryObject<Attribute>> ELEMENT_MAP;

    /**
     * Canonical school → Iron's elemental spell-power attribute.
     *
     * <p>Keyed by {@link SpellSchoolId} rather than by string, so it is a compile-time-checked
     * total mapping: every non-generic school has an attribute, and no school can be introduced
     * that silently has none. The old string map was missing entries for the {@code aqua},
     * {@code geo} and {@code wind} values the classifier could produce, so those spells got no
     * elemental bonus at all and nothing reported it.
     */
    private static Map<SpellSchoolId, RegistryObject<Attribute>> elementMap() {
        Map<SpellSchoolId, RegistryObject<Attribute>> m = ELEMENT_MAP;
        if (m == null) {
            synchronized (SpellScalingUtil.class) {
                m = ELEMENT_MAP;
                if (m == null) {
                    m = new EnumMap<>(SpellSchoolId.class);
                    m.put(SpellSchoolId.FIRE, AttributeRegistry.FIRE_SPELL_POWER);
                    m.put(SpellSchoolId.ICE, AttributeRegistry.ICE_SPELL_POWER);
                    m.put(SpellSchoolId.LIGHTNING, AttributeRegistry.LIGHTNING_SPELL_POWER);
                    m.put(SpellSchoolId.HOLY, AttributeRegistry.HOLY_SPELL_POWER);
                    m.put(SpellSchoolId.ENDER, AttributeRegistry.ENDER_SPELL_POWER);
                    m.put(SpellSchoolId.BLOOD, AttributeRegistry.BLOOD_SPELL_POWER);
                    m.put(SpellSchoolId.EVOCATION, AttributeRegistry.EVOCATION_SPELL_POWER);
                    m.put(SpellSchoolId.NATURE, AttributeRegistry.NATURE_SPELL_POWER);
                    m.put(SpellSchoolId.ELDRITCH, AttributeRegistry.ELDRITCH_SPELL_POWER);
                    ELEMENT_MAP = m;
                }
            }
        }
        return m;
    }

    public static float getMultiplierForCaster(Player player, Spell spell) {
        float multiplier = (float) player.getAttributeValue(AttributeRegistry.SPELL_POWER.get());

        SpellAnalysis.Result analysis = SpellAnalysis.analyze(spell);
        SpellSchoolId school = analysis.school();

        // Additive scaling: base power + (elemental bonus - 1.0) prevents exponential stacking.
        //
        // This now uses the SAME school the rest of the mod uses. It previously called
        // SpellAnalysis, discarded the answer, and re-derived the element with a different
        // substring test over a HashMap — so Firework counted as generic for affinity but
        // matched "fire" for scaling, and a path containing two element names resolved by hash
        // iteration order.
        RegistryObject<Attribute> elemental = elementMap().get(school);
        if (elemental != null) {
            float elementalPower = (float) player.getAttributeValue(elemental.get());
            multiplier = multiplier + (elementalPower - 1.0f);
        }

        // Apply affinity bonus: 0.5% per affinity level for matching school
        if (AnsConfig.ENABLE_AFFINITY_SYSTEM.get() && !school.isGeneric()) {
            try {
                AffinityType affinityType = AffinityType.valueOf(school.name());
                float affinityMultiplier = AffinityBonuses.getAttributeMultiplier(player, affinityType);
                multiplier *= affinityMultiplier;
            } catch (IllegalArgumentException ignored) {
                // Unreachable for the canonical vocabulary (every non-generic school has an
                // AffinityType constant); kept so adding a school cannot crash a cast.
            }
        }

        // Apply resonance multiplier from cross-mod mana synergy
        if (AnsConfig.ENABLE_RESONANCE_SYSTEM.get()) {
            multiplier *= (float) ResonanceManager.getResonance(player);
        }

        return Math.min(multiplier, AnsConfig.SPELL_POWER_CAP.get().floatValue());
    }
}
