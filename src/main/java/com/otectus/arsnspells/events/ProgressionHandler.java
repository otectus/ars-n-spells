package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.progression.XpConverter;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public class ProgressionHandler {
    private static final UUID ELEMENT_XP_ID = UUID.fromString("b0ba11ad-dead-beef-cafe-f00d20245678");

    @SubscribeEvent
    public void onArsSpellCast(SpellCastEvent event) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
            && event.spell != null
            && event.spell.recipe != null
            && !event.spell.recipe.isEmpty()) {
            String schoolId = XpConverter.mapGlyphToSchool(event.spell.recipe.get(0));
            if (!schoolId.equals("NONE")) {
                // Logic: Converts Iron's School ID to the corresponding Spell Power Attribute
                String[] parts = schoolId.split(":");
                if (parts.length < 2) {
                    return;
                }
                String attrName = parts[1] + "_spell_power";
                var attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", attrName));
                
                if (attribute != null) {
                    AttributeInstance instance = player.getAttribute(attribute);
                    if (instance != null) {
                        double current = instance.getModifier(ELEMENT_XP_ID) != null ? instance.getModifier(ELEMENT_XP_ID).getAmount() : 0.0;
                        instance.removeModifier(ELEMENT_XP_ID);
                        // High-Fidelity Logic: Growth of 0.1% power per cast, up to 25% total element bonus
                        double next = Math.min(0.25, current + 0.001);
                        instance.addPermanentModifier(new AttributeModifier(ELEMENT_XP_ID, "Ars Element Sync", next, AttributeModifier.Operation.ADDITION));
                    }
                }
            }
        }
    }
}
