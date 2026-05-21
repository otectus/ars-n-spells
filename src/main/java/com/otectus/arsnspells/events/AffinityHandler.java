package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.util.SpellAnalysis;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.AffinitySyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AffinityHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AffinityHandler.class);

    @SubscribeEvent
    public void onSpellCast(SpellCastEvent event) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            String school = SpellAnalysis.analyze(event.spell).dominantSchool();
            if (!"generic".equals(school)) {
                player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
                    AffinityType type;
                    try {
                        // ANS-HIGH-023: narrow to IllegalArgumentException only. The previous
                        // broad catch(Exception) silently swallowed packet-send failures too,
                        // leaving the server-side affinity incremented while the client HUD
                        // stayed stale (with no log line to diagnose the desync).
                        type = AffinityType.valueOf(school.toUpperCase());
                    } catch (IllegalArgumentException unmappedSchool) {
                        LOGGER.debug("Unmapped affinity school: {}", school);
                        return;
                    }
                    data.addLevel(type, 1);
                    // High-Fidelity Sync: Push mirroring to client player for immediate UI updates.
                    // If the packet send throws, it will propagate to Forge's network layer
                    // where it is logged — that is the right place for the diagnostic.
                    PacketHandler.sendToClient(new AffinitySyncPacket(type, data.getLevel(type)), player);
                });
            }
        }
    }
}
