package com.otectus.arsnspells.glyph;

import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lets an imported glyph's execution see what its Iron's spell spawned, so projectiles and
 * summons can be delivered to the Ars target. A no-op unless such an execution is running on this
 * thread; observes only, never cancels.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class GlyphSpawnObserver {
    private GlyphSpawnObserver() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        com.otectus.arsnspells.compat.irons.glyph.IronTargetingScope.observeSpawn(event.getEntity());
    }
}
