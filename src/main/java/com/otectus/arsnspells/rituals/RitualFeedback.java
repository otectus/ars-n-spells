package com.otectus.arsnspells.rituals;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.UUID;

/**
 * Shared player-facing feedback helpers for the inscription rituals. Centralizes the "work out who
 * ran this ritual and send them a chat message" pattern so all three rituals route through one code
 * path and the styling stays in lockstep.
 *
 * <p>Recipients are resolved initiator-first. An ANS ritual burns for a few seconds before
 * {@code onEnd()} runs, so the original proximity-only lookup found nobody whenever the player
 * walked away while it burned — and since {@code send} silently no-ops with no recipient, every
 * error message was dropped. A player who lit a ritual, stepped away and came back to an unchanged
 * scroll had no way to learn why. {@link AnsRitual} captures the initiator's UUID for exactly this;
 * proximity remains as the fallback for redstone-triggered rituals, which have no initiator.
 */
public final class RitualFeedback {
    /**
     * Fallback search radius when there is no reachable initiator. Matches the radius the binding
     * ritual uses to credit its advancement — those disagreed before (8 vs 16), so a player twelve
     * blocks out silently earned the advancement while being told nothing.
     */
    private static final int FEEDBACK_RADIUS = 16;

    private RitualFeedback() {}

    public static void error(Level level, BlockPos pos, @Nullable UUID initiator,
                             String key, Object... args) {
        send(level, pos, initiator, Component.translatable(key, args), ChatFormatting.RED);
    }

    public static void success(Level level, BlockPos pos, @Nullable UUID initiator,
                               String key, Object... args) {
        send(level, pos, initiator, Component.translatable(key, args), ChatFormatting.GREEN);
    }

    private static void send(Level level, BlockPos pos, @Nullable UUID initiator,
                             Component message, ChatFormatting color) {
        Player target = resolveRecipient(level, pos, initiator);
        if (target != null) {
            target.displayClientMessage(message.copy().withStyle(color), false);
        }
    }

    /**
     * The player a ritual at {@code pos} should report to: the one who started it if they are still
     * online, otherwise the nearest player within {@link #FEEDBACK_RADIUS}. Null when neither
     * exists, in which case the caller has nobody to tell.
     */
    @Nullable
    public static Player resolveRecipient(@Nullable Level level, @Nullable BlockPos pos,
                                          @Nullable UUID initiator) {
        if (level == null || pos == null || level.isClientSide()) {
            return null;
        }
        // Resolved through the player list on every send rather than held as a reference: a stored
        // ServerPlayer survives its own disconnect and messaging it dereferences a null connection.
        if (initiator != null && level instanceof ServerLevel server) {
            Player byId = server.getServer().getPlayerList().getPlayer(initiator);
            if (byId != null) {
                return byId;
            }
        }
        return findNearestPlayer(level, pos);
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
