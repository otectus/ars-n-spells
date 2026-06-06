package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.affinity.AffinityBonuses;
import com.otectus.arsnspells.affinity.SchoolKeys;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Iron's Spellbooks-aware spell scaling for Ars Nouveau spells. The {@code ELEMENT_MAP}
 * resolves Iron's {@code AttributeRegistry} entries, so this class must never be loaded
 * on a server that does not have Iron's installed. All callers are expected to gate
 * on {@link com.otectus.arsnspells.compat.IronsCompat#isLoaded()} first; the map itself
 * is lazily built so that even a stray accidental import will not trip a static initializer
 * crash before the gate runs.
 *
 * <p>NeoForge 1.21.1 note: Iron's {@code AttributeRegistry} entries are
 * {@code DeferredHolder<Attribute, Attribute>} (a {@link Holder}) rather than Forge
 * {@code RegistryObject<Attribute>}; {@link Player#getAttributeValue(Holder)} takes the
 * holder directly, so the former {@code .get()} unwrap is gone.
 */
public class SpellScalingUtil {
    private static volatile Map<String, Holder<Attribute>> ELEMENT_MAP;

    private static Map<String, Holder<Attribute>> elementMap() {
        Map<String, Holder<Attribute>> m = ELEMENT_MAP;
        if (m == null) {
            synchronized (SpellScalingUtil.class) {
                m = ELEMENT_MAP;
                if (m == null) {
                    m = new HashMap<>();
                    m.put("fire", AttributeRegistry.FIRE_SPELL_POWER);
                    m.put("ice", AttributeRegistry.ICE_SPELL_POWER);
                    m.put("lightning", AttributeRegistry.LIGHTNING_SPELL_POWER);
                    m.put("holy", AttributeRegistry.HOLY_SPELL_POWER);
                    m.put("ender", AttributeRegistry.ENDER_SPELL_POWER);
                    m.put("blood", AttributeRegistry.BLOOD_SPELL_POWER);
                    m.put("evocation", AttributeRegistry.EVOCATION_SPELL_POWER);
                    m.put("nature", AttributeRegistry.NATURE_SPELL_POWER);
                    m.put("eldritch", AttributeRegistry.ELDRITCH_SPELL_POWER);
                    ELEMENT_MAP = m;
                }
            }
        }
        return m;
    }

    public static float getMultiplierForCaster(Player player, Spell spell) {
        float multiplier = (float) player.getAttributeValue(AttributeRegistry.SPELL_POWER);

        SpellAnalysis.Result analysis = SpellAnalysis.analyze(spell);
        AbstractSpellPart effect = analysis.firstEffect();
        String school = analysis.dominantSchool();

        // Additive scaling: base power + (elemental bonus - 1.0) prevents exponential stacking
        if (effect != null && effect.getRegistryName() != null) {
            String path = effect.getRegistryName().getPath().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Holder<Attribute>> entry : elementMap().entrySet()) {
                if (path.contains(entry.getKey())) {
                    float elementalPower = (float) player.getAttributeValue(entry.getValue());
                    multiplier = multiplier + (elementalPower - 1.0f);
                    break;
                }
            }
        }

        // Apply affinity bonus: 0.5% per affinity level for the matching school.
        // The key is the same one AffinityHandler stored under, so the lookup hits
        // the correct track (including the aqua/geo/wind ars_n_spells:* tracks).
        if (AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            String affinityKey = SchoolKeys.fromArsSchool(school);
            if (affinityKey != null) {
                multiplier *= AffinityBonuses.getAttributeMultiplier(player, affinityKey);
            }
        }

        // Apply resonance multiplier from cross-mod mana synergy
        if (AnsConfig.ENABLE_RESONANCE_SYSTEM.get()) {
            multiplier *= (float) ResonanceManager.getResonance(player);
        }

        return Math.min(multiplier, AnsConfig.SPELL_POWER_CAP.get().floatValue());
    }
}
