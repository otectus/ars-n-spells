package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.world.entity.player.Player;

/**
 * STUB — the 1.20.1 implementation read Iron's per-school spell-power
 * attributes (FIRE_SPELL_POWER, ICE_SPELL_POWER, etc.) via Forge
 * {@code RegistryObject<Attribute>} entries and combined them with affinity
 * + resonance multipliers. NeoForge 1.21.1 replaced {@code RegistryObject}
 * with {@code Holder<Attribute>}; AN 5.x changed spell introspection.
 *
 * Until Phase 11: returns 1.0f (no scaling). The stub keeps the call
 * surface so {@link com.otectus.arsnspells.spell.CrossCastingHandler} and
 * any other callers compile; mechanical effect is that Ars spells cast
 * with Iron's spell-power attributes equipped see no boost from those
 * attributes, but base damage still applies.
 */
public class SpellScalingUtil {
    public static float getMultiplierForCaster(Player player, Spell spell) {
        try {
            return Math.min(1.0f, AnsConfig.SPELL_POWER_CAP.get().floatValue());
        } catch (Exception e) {
            return 1.0f;
        }
    }
}
