package com.otectus.arsnspells.glyph.runtime;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Collects entities a native Iron's call adds while an {@link AdapterFrame} with capture is active. */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class GlyphSpawnCapture {

    private GlyphSpawnCapture() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        AdapterFrame frame = AdapterFrame.current();
        if (frame != null && frame.capture && event.getEntity() != frame.caster) {
            frame.spawned.add(event.getEntity());
        }
    }
}
