package com.otectus.arsnspells.util;

import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Structured cross-cast pipeline trace logger.
 *
 * <p>Every cast attempt is assigned a UUID at packet receipt; that ID is then
 * threaded through {@link com.otectus.arsnspells.spell.CrossCastContext} and
 * logged at every pipeline stage. Operators correlating multiplayer failures
 * grep for one {@code attempt=<uuid>} and see a complete trace.
 *
 * <p>Output format:
 * <pre>
 * [CrossCastTrace] attempt=&lt;uuid&gt; player=&lt;name&gt; side=&lt;C|S&gt; stage=&lt;symbolic&gt; k1=v1 k2=v2 ...
 * </pre>
 *
 * <p>All calls are no-ops unless {@link AnsConfig#DEBUG_MODE} is enabled.
 */
public final class CrossCastTrace {
    private static final Logger LOG = LoggerFactory.getLogger("ArsNSpells/CrossCastTrace");
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public enum Side { C, S }

    public enum Stage {
        INPUT_DETECTED,
        REQUEST_SENT,
        REQUEST_RECEIVED,
        DESCRIPTOR_VALIDATED,
        DESCRIPTOR_REJECTED,
        RESOURCE_CHECK,
        RESOURCE_SPEND,
        ARS_COST_APPLIED,
        IRON_COST_APPLIED,
        UPSTREAM_CAST_ENTER,
        UPSTREAM_CAST_EXIT,
        EFFECT_APPLIED,
        CYCLE_APPLIED
    }

    private CrossCastTrace() {
    }

    public static boolean enabled() {
        return AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get();
    }

    public static void log(UUID attemptId, Player player, Side side, Stage stage, Object... kv) {
        if (!enabled()) {
            return;
        }
        String name = player != null ? player.getGameProfile().getName() : "?";
        String attempt = attemptId != null ? attemptId.toString() : NIL_UUID.toString();
        LOG.info("[CrossCastTrace] attempt={} player={} side={} stage={} {}",
            attempt, name, side, stage.name().toLowerCase(), formatKv(kv));
    }

    public static void log(UUID attemptId, String playerName, Side side, Stage stage, Object... kv) {
        if (!enabled()) {
            return;
        }
        String attempt = attemptId != null ? attemptId.toString() : NIL_UUID.toString();
        LOG.info("[CrossCastTrace] attempt={} player={} side={} stage={} {}",
            attempt, playerName != null ? playerName : "?", side, stage.name().toLowerCase(), formatKv(kv));
    }

    private static String formatKv(Object[] kv) {
        if (kv == null || kv.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) sb.append(' ');
            sb.append(kv[i]).append('=').append(kv[i + 1]);
        }
        // ANS-LOW-028: explicitly note the orphaned final argument so debug consumers
        // notice the odd-length kv array instead of silently truncating.
        if ((kv.length & 1) == 1) {
            sb.append(" !ORPHAN=").append(kv[kv.length - 1]);
        }
        return sb.toString();
    }
}
