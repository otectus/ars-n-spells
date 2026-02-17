package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.progression.XpConverter;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.AffinitySyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class AffinityHandler {
    @SubscribeEvent
    public void onSpellCast(SpellCastEvent event) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
            && event.spell != null
            && event.spell.recipe != null
            && !event.spell.recipe.isEmpty()) {
            String schoolId = XpConverter.mapGlyphToSchool(event.spell.recipe.get(0));
            if (!schoolId.equals("NONE")) {
                player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
                    try {
                        String typePart = schoolId.contains(":") ? schoolId.split(":")[1] : schoolId;
                        AffinityType type = AffinityType.valueOf(typePart.toUpperCase());
                        data.addLevel(type, 1);
                        
                        // High-Fidelity Sync: Push mirroring to client player for immediate UI updates
                        PacketHandler.sendToClient(new AffinitySyncPacket(type, data.getLevel(type)), player);
                    } catch (Exception ignored) {}
                });
            }
        }
    }
}
