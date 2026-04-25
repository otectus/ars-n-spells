package com.otectus.arsnspells.rituals;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * Shared player-facing feedback helpers for the inscription rituals. Centralizes
 * the "find the nearest player to the brazier and send a chat message" pattern
 * so both rituals route through one code path and the styling stays in lockstep.
 */
public final class RitualFeedback {
    private static final int FEEDBACK_RADIUS = 8;

    private RitualFeedback() {}

    public static void error(Level level, BlockPos pos, String key, Object... args) {
        send(level, pos, Component.translatable(key, args), ChatFormatting.RED);
    }

    public static void success(Level level, BlockPos pos, String key, Object... args) {
        send(level, pos, Component.translatable(key, args), ChatFormatting.GREEN);
    }

    private static void send(Level level, BlockPos pos, Component message, ChatFormatting color) {
        Player player = findNearestPlayer(level, pos);
        if (player != null) {
            player.displayClientMessage(message.copy().withStyle(color), false);
        }
    }

    @Nullable
    private static Player findNearestPlayer(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(FEEDBACK_RADIUS);
        return level.getEntitiesOfClass(Player.class, area).stream()
            .min(Comparator.comparingDouble(p -> p.distanceToSqr(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
            .orElse(null);
    }
}
