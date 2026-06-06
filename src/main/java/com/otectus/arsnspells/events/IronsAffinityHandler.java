package com.otectus.arsnspells.events;

import com.otectus.arsnspells.affinity.SchoolKeys;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.network.AffinitySyncPayload;
import com.otectus.arsnspells.network.PacketHandler;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Iron's-side mirror of {@link AffinityHandler}. Casting an Iron's spell builds
 * the matching school's affinity level (capped at 100), then syncs the new level
 * to the casting player's client for HUD parity.
 *
 * <p><b>2.5.0:</b> the Iron's school id (e.g. {@code irons_spellbooks:fire},
 * {@code cataclysm_spellbooks:abyssal}) is stored verbatim as the affinity key
 * via {@link SchoolKeys#fromResourceLocation}. Previously the path was uppercased
 * and run through {@code AffinityType.valueOf}, silently dropping every addon
 * school not in the 16-value enum — so the ~19 addon schools in the target pack
 * earned no affinity. That drop is gone; every registered school now tracks.
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
        String key = SchoolKeys.fromResourceLocation(schoolId);
        if (key == null || key.isEmpty()) {
            return;
        }
        AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
        data.addLevel(key, 1);
        PacketHandler.sendToClient(new AffinitySyncPayload(key, data.getLevel(key)), player);
    }
}
