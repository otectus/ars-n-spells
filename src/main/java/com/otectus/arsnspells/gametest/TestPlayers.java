package com.otectus.arsnspells.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real {@link ServerPlayer} fixtures for GameTests (audit ANS-002).
 *
 * <p>Imported glyphs are reserved for real players, and both of their boundaries refuse NeoForge's
 * {@code FakePlayer}, so a positive test cast with one would turn green by being refused. These are
 * ordinary {@code ServerPlayer}s whose connection is NeoForge's packet-discarding fake handler: nothing
 * is logged in and the player is not added to the level or the player list. Test scope only;
 * production code has no admission bypass. The pattern is the one {@code ArsAffinityInteropGameTests}
 * already used for its probe player.
 *
 * <p>Like {@code FakePlayerFactory}, one instance is kept per (level, profile), so callers reset
 * whatever state they depend on.
 */
public final class TestPlayers {
    private static final Map<UUID, ServerPlayer> PLAYERS = new ConcurrentHashMap<>();

    private TestPlayers() {}

    public static ServerPlayer get(ServerLevel level, GameProfile profile) {
        ServerPlayer cached = PLAYERS.get(profile.getId());
        if (cached != null && cached.level() == level && !cached.isRemoved() && cached.isAlive()) return cached;
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        try {
            var handler = Class.forName("net.neoforged.neoforge.common.util.FakePlayer$FakePlayerNetHandler")
                .getDeclaredConstructor(net.minecraft.server.MinecraftServer.class, ServerPlayer.class);
            handler.setAccessible(true);
            player.connection = (ServerGamePacketListenerImpl) handler.newInstance(level.getServer(), player);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("NeoForge FakePlayer network handler changed", error);
        }
        PLAYERS.put(profile.getId(), player);
        return player;
    }
}
