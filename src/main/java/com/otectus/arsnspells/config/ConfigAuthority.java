package com.otectus.arsnspells.config;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may change this world's Ars 'n' Spells settings (3.4.1 review CONFIG-01).
 *
 * <ul>
 *   <li>the server console and operators of permission level 2, as before;</li>
 *   <li>the owner of a single-player world, cheats on or off: the world is theirs, and a cheat-free
 *       survival world used to leave its owner with no way to change the mana mode at all;</li>
 *   <li>nobody else. A guest on an opened-to-LAN world is not the owner just because the server is
 *       integrated, and a dedicated server has no owner, only operators.</li>
 * </ul>
 * The settings are world-global, so this is decided on the server for every route: the command, the
 * in-game settings screen (through its apply request) and nothing client-side.
 */
public final class ConfigAuthority {
    public static final int OPERATOR_LEVEL = 2;

    private ConfigAuthority() {}

    /** For a command source: the console, an operator, or the owning player of a single-player world. */
    public static boolean mayChange(CommandSourceStack source) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
        return decide(source.hasPermission(OPERATOR_LEVEL), player != null && isDedicated(player), player != null && isOwnerProfile(player));
    }

    /** For a player's request from the settings screen. */
    public static boolean mayChange(ServerPlayer player) {
        return player != null && decide(player.hasPermissions(OPERATOR_LEVEL), isDedicated(player), isOwnerProfile(player));
    }

    /** The owner of an integrated (single-player) world; never anyone on a dedicated server. */
    public static boolean isOwner(ServerPlayer player) {
        return decide(false, isDedicated(player), isOwnerProfile(player));
    }

    /**
     * The rule itself, pure: an operator (level 2) always; otherwise only the profile that owns an
     * integrated server. A dedicated server's owner check is never trusted.
     */
    static boolean decide(boolean operator, boolean dedicatedServer, boolean singleplayerOwnerProfile) {
        return operator || (!dedicatedServer && singleplayerOwnerProfile);
    }

    private static boolean isDedicated(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null || server.isDedicatedServer();
    }

    private static boolean isOwnerProfile(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.isSingleplayerOwner(player.getGameProfile());
    }
}
