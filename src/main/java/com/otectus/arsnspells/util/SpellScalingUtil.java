package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.affinity.AffinityBonuses;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SpellScalingUtil {
    private static final Map<String, RegistryObject<Attribute>> ELEMENT_MAP = new HashMap<>();
    static {
        ELEMENT_MAP.put("fire", AttributeRegistry.FIRE_SPELL_POWER);
        ELEMENT_MAP.put("ice", AttributeRegistry.ICE_SPELL_POWER);
        ELEMENT_MAP.put("lightning", AttributeRegistry.LIGHTNING_SPELL_POWER);
        ELEMENT_MAP.put("holy", AttributeRegistry.HOLY_SPELL_POWER);
        ELEMENT_MAP.put("ender", AttributeRegistry.ENDER_SPELL_POWER);
        ELEMENT_MAP.put("blood", AttributeRegistry.BLOOD_SPELL_POWER);
        ELEMENT_MAP.put("evocation", AttributeRegistry.EVOCATION_SPELL_POWER);
        ELEMENT_MAP.put("nature", AttributeRegistry.NATURE_SPELL_POWER);
        ELEMENT_MAP.put("eldritch", AttributeRegistry.ELDRITCH_SPELL_POWER);
    }

    public static float getMultiplierForCaster(Player player, Spell spell) {
        float multiplier = (float) player.getAttributeValue(AttributeRegistry.SPELL_POWER.get());

        SpellAnalysis.Result analysis = SpellAnalysis.analyze(spell);
        AbstractSpellPart effect = analysis.firstEffect();
        String school = analysis.dominantSchool();

        // Additive scaling: base power + (elemental bonus - 1.0) prevents exponential stacking
        if (effect != null && effect.getRegistryName() != null) {
            String path = effect.getRegistryName().getPath().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, RegistryObject<Attribute>> entry : ELEMENT_MAP.entrySet()) {
                if (path.contains(entry.getKey())) {
                    float elementalPower = (float) player.getAttributeValue(entry.getValue().get());
                    multiplier = multiplier + (elementalPower - 1.0f);
                    break;
                }
            }
        }

        // Apply affinity bonus: 0.5% per affinity level for matching school
        if (AnsConfig.ENABLE_AFFINITY_SYSTEM.get() && !"generic".equals(school)) {
            try {
                AffinityType affinityType = AffinityType.valueOf(school.toUpperCase());
                float affinityMultiplier = AffinityBonuses.getAttributeMultiplier(player, affinityType);
                multiplier *= affinityMultiplier;
            } catch (IllegalArgumentException ignored) {
                // School doesn't map to an AffinityType — skip
            }
        }

        // Apply resonance multiplier from cross-mod mana synergy
        if (AnsConfig.ENABLE_RESONANCE_SYSTEM.get()) {
            multiplier *= (float) ResonanceManager.getResonance(player);
        }

        return Math.min(multiplier, AnsConfig.SPELL_POWER_CAP.get().floatValue());
    }
}
