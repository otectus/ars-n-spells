package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes the GameTest server's Iron's state match a real server's.
 *
 * <p>Iron's builds its per-spell configuration (school, maximum level, rarity, cooldown, mana
 * multiplier) only on {@code OnDatapackSyncEvent}, which a real server fires when the first
 * player joins and on every {@code /reload}. A GameTest server has no joining player, so before
 * 3.4.0 every Iron's spell ran on the global fallback values: Evocation school, maximum level 1,
 * Common rarity. The imported-glyph tests depend on real per-spell values (level clamping, tier,
 * school credit, cost), so the GameTest server fires the same sync once at startup. The event
 * carries no player, so Iron's builds the configuration and has nobody to send it to.
 *
 * <p>Only {@code runGameTestServer} sets {@code ans.gametest.castProbe}; a normal server or
 * client never runs this.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class GameTestEnvironment {
    private GameTestEnvironment() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("ans.gametest.castProbe") || !IronsCompat.isLoaded()) return;
        MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(event.getServer().getPlayerList(), null));
    }
}
