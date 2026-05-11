package com.otectus.arsnspells.events;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.network.AffinitySyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * Iron's-side mirror of {@link AffinityHandler}. Casting an Iron's spell
 * builds the matching {@link AffinityType} level (capped at 100), then syncs
 * the new level to the casting player's client for HUD parity.
 *
 * <p>Iron's school IDs (e.g. {@code irons_spellbooks:fire}) are mapped to
 * {@link AffinityType} by uppercasing the path. Iron's schools that don't
 * have a matching enum value are silently skipped — the new HOLY/ENDER/BLOOD/
 * EVOCATION/ELDRITCH values added in 1.9.0 cover Iron's stock schools.
 *
 * <p>Iron's-only: must only be registered when Iron's is loaded.
 */
public class IronsAffinityHandler {

    @SubscribeEvent
    public void onIronsSpellCast(SpellOnCastEvent event) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            return;
        }
        if (event.getEntity() == null || event.getSchoolType() == null) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation schoolId = event.getSchoolType().getId();
        if (schoolId == null) {
            return;
        }
        String schoolName = schoolId.getPath().toUpperCase(Locale.ROOT);
        if (schoolName.isEmpty()) {
            return;
        }
        AffinityType type;
        try {
            type = AffinityType.valueOf(schoolName);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
            data.addLevel(type, 1);
            PacketHandler.sendToClient(new AffinitySyncPacket(type, data.getLevel(type)), player);
        });
    }
}
