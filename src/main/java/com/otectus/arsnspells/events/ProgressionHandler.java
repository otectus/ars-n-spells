package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Cross-mod progression: casting Ars spells builds persistent per-school experience
 * that provides transient attribute bonuses to Iron's spell power.
 */
public class ProgressionHandler {
    private static final UUID ELEMENT_XP_ID = UUID.fromString("b0ba11ad-dead-beef-cafe-f00d20245678");

    @SubscribeEvent
    public void onArsSpellCast(SpellCastEvent event) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            String school = SpellAnalysis.analyze(event.spell).dominantSchool();
            if (!"generic".equals(school)) {
                player.getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(data -> {
                    data.incrementCastCount(school);
                    applyTransientBonus(player, school, data.getBonusForSchool(school));
                });
            }
        }
    }

    /**
     * Reapply transient bonuses on login from persisted data.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(data -> {
                data.getAllCastCounts().forEach((school, count) -> {
                    double bonus = data.getBonusForSchool(school);
                    if (bonus > 0) {
                        applyTransientBonus(player, school, bonus);
                    }
                });
            });
        }
    }

    private static void applyTransientBonus(ServerPlayer player, String school, double bonus) {
        String attrName = school + "_spell_power";
        var attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", attrName));
        if (attribute == null) return;

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        instance.removeModifier(ELEMENT_XP_ID);
        if (bonus > 0) {
            instance.addTransientModifier(new AttributeModifier(
                ELEMENT_XP_ID, "Ars Element Progression", bonus, AttributeModifier.Operation.ADDITION));
        }
    }
}
