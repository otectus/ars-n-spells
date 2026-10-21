package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.IronsCompat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Puts a GameTest server into the state a real server is in once a player has joined.
 *
 * <p>Iron's builds its per-spell config (maximum levels, schools, cooldowns, rarities) on the
 * first datapack sync, which a real server performs when a player logs in. A GameTest server has
 * no players, so without this every Iron's spell would report the global fallbacks (maximum level
 * 1, school Evocation) to every test. GameTest runs only: the run configuration sets
 * {@code ans.gametest.castProbe}; nothing here runs in a normal game.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class GameTestRuntimeSetup {

    private GameTestRuntimeSetup() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("ans.gametest.castProbe") || !IronsCompat.isLoaded()) {
            return;
        }
        NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(event.getServer().getPlayerList(), null));
        ArsNSpells.LOGGER.info("[GameTest] Iron's spell config built for the GameTest server (datapack sync with no player)");
    }
}
