package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.compat.ScrollLPTracker;
import com.otectus.arsnspells.spell.CrossCastContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * One place that evicts every piece of per-player state this mod keeps outside the world save.
 *
 * <p>Four static maps hold entries keyed by player UUID:
 * {@link ScrollLPTracker} (staged scroll costs), {@link ResonanceManager} (the damage
 * multiplier cache), {@link CrossCastContext} (in-flight cross-cast attempts) and
 * {@link ArsSpellScalingHandler} (the post-cast damage window). Each has an opportunistic TTL
 * that expires the <em>value</em> on the next touch by that same player, but nothing removed
 * the <em>key</em> — so on a long-lived server every player who ever logged in stayed in all
 * four maps until restart, and a player who logged out mid-cast left a live entry behind.
 * {@code ResonanceManager.cleanupOfflinePlayers} and {@code ScrollLPTracker.clear} both
 * existed and both had zero callers.
 *
 * <p>Registered unconditionally: three of the four maps are written on paths that do not
 * require Iron's Spellbooks, and eviction of an empty map costs nothing.
 *
 * <p>The periodic sweep restores {@code ANS-MED-028}: it fires once per 1200 server ticks
 * regardless of player count, rather than once per player per tick. It exists in addition to
 * the logout hook because a player can leave without a clean logout event (crash, timeout on
 * a proxy) and because dimension changes recreate the {@code ServerPlayer}.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class StateEvictionHandler {

    /** 1200 ticks = 60s at 20 TPS. */
    private static final int SWEEP_INTERVAL_TICKS = 1200;

    private static int sweepCounter = 0;

    private StateEvictionHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        ScrollLPTracker.clear(player.getUUID());
        ResonanceManager.clear(player);
        CrossCastContext.clear(player);
        ArsSpellScalingHandler.clear(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++sweepCounter < SWEEP_INTERVAL_TICKS) {
            return;
        }
        sweepCounter = 0;
        MinecraftServer server = event.getServer();
        if (server != null) {
            ResonanceManager.cleanupOfflinePlayers(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Integrated server: the JVM survives world exit, so these statics carry into the next
        // world unless they are drained here.
        sweepCounter = 0;
        ScrollLPTracker.clearAll();
        ResonanceManager.clearAll();
        CrossCastContext.clearAll();
        ArsSpellScalingHandler.clearAll();
    }
}
