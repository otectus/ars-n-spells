package com.otectus.arsnspells.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real {@link ServerPlayer} fixtures for GameTests (audit ANS-002).
 *
 * <p>Imported glyphs are reserved for real players, and both of their boundaries refuse Forge's
 * {@code FakePlayer}, so a positive test cast with one would turn green by being refused. These are
 * ordinary {@code ServerPlayer}s with a test connection: an in-memory {@link EmbeddedChannel}
 * buffers whatever the server sends, nothing is logged in and the player is not added to the
 * level or the player list. This is test scope only; production code has no admission bypass.
 *
 * <p>Like {@code FakePlayerFactory}, one instance is kept per (level, profile), so callers reset
 * whatever state they depend on.
 */
public final class TestPlayers {
    private static final Map<UUID, ServerPlayer> PLAYERS = new ConcurrentHashMap<>();

    private TestPlayers() {}

    public static ServerPlayer get(ServerLevel level, GameProfile profile) {
        ServerPlayer cached = PLAYERS.get(profile.getId());
        if (cached != null && cached.level() == level && !cached.isRemoved()) {
            if (!cached.isAlive()) {
                // A previous test killed it; a fresh body keeps the next test independent.
                PLAYERS.remove(profile.getId());
            } else {
                return cached;
            }
        }
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player);
        PLAYERS.put(profile.getId(), player);
        return player;
    }
}
