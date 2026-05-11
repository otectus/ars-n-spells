package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.util.SpellAnalysis;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.AffinitySyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;

public class AffinityHandler {
    @SubscribeEvent
    public void onSpellCast(SpellCastEvent event) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            String school = SpellAnalysis.analyze(event.spell).dominantSchool();
            if (!"generic".equals(school)) {
                AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
                try {
                    AffinityType type = AffinityType.valueOf(school.toUpperCase());
                    data.addLevel(type, 1);
                    PacketHandler.sendToClient(new AffinitySyncPayload(type, data.getLevel(type)), player);
                } catch (Exception ignored) {}
            }
        }
    }
}
