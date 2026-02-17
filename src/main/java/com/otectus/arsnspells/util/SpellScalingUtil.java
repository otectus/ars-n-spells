package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

import java.util.Locale;
import java.util.HashMap;
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
    }

    public static float getMultiplierForCaster(Player player, Spell spell) {
        float multiplier = (float) player.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
        // Refinement: Scan for elemental primary glyph
        if (spell != null && spell.recipe != null && !spell.recipe.isEmpty()) {
            if (spell.recipe.get(0) == null || spell.recipe.get(0).getRegistryName() == null) {
                return multiplier;
            }
            String path = spell.recipe.get(0).getRegistryName().getPath().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, RegistryObject<Attribute>> entry : ELEMENT_MAP.entrySet()) {
                if (path.contains(entry.getKey())) {
                    multiplier *= (float) player.getAttributeValue(entry.getValue().get());
                    break;
                }
            }
        }
        return multiplier;
    }
}
