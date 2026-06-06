package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.affinity.SchoolKeys;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.network.AffinitySyncPayload;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Ars-side affinity accrual. {@link SpellAnalysis#deriveSchool} reduces the
 * spell's first effect glyph to a heuristic school word, which
 * {@link SchoolKeys#fromArsSchool} maps to the canonical school id so an Ars
 * fireball accrues the same {@code irons_spellbooks:fire} track an Iron's
 * fireball does.
 *
 * <p><b>2.5.0:</b> previously the word was run through {@code AffinityType.valueOf},
 * which threw on {@code aqua}/{@code geo}/{@code wind} (words the heuristic emits
 * but the enum lacked) and silently dropped those casts. Those now map to
 * {@code ars_n_spells:*} keys and are tracked.
 *
 * <p>Iron's-independent: registered unconditionally.
 */
public class AffinityHandler {
    @SubscribeEvent
    public void onSpellCast(SpellCastEvent event) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            String key = SchoolKeys.fromArsSchool(SpellAnalysis.analyze(event.spell).dominantSchool());
            if (key != null) {
                AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
                data.addLevel(key, 1);
                PacketHandler.sendToClient(new AffinitySyncPayload(key, data.getLevel(key)), player);
            }
        }
    }
}
