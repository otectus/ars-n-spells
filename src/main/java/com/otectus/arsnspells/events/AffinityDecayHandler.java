package com.otectus.arsnspells.events;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.network.AffinitySyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Periodically decays player affinity levels when no spells of that school
 * are being cast. Implementation of {@code enable_affinity_decay} that, prior
 * to 1.9.0, was promised by config but never actually implemented.
 *
 * <p>Decay math: at each interval (default 60s = 1200 ticks), every non-zero
 * affinity accrues {@code level * AFFINITY_DECAY_RATE * (interval / 24000)}
 * fractional decay, and loses a point only once a whole point has accumulated
 * (the sub-1.0 remainder persists in {@link AffinityData} NBT). With defaults
 * (rate=0.01, interval=1200) a level-100 school accrues 0.05/interval — one
 * point per ~20 real minutes — and the rate slows proportionally as the level
 * drops, so low levels linger for a long tail rather than draining flat.
 *
 * <p>Pre-3.0.2 bug: the old integer math floored the per-interval amount and
 * then clamped it to a minimum of 1, silently turning the documented
 * proportional curve into a flat 1 point/interval (~20x faster; a maxed
 * school emptied in ~5 in-game days).
 *
 * <p>Iron's-independent: registered unconditionally.
 */
public class AffinityDecayHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get() || !AnsConfig.ENABLE_AFFINITY_DECAY.get()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        int interval = AnsConfig.AFFINITY_DECAY_INTERVAL_TICKS.get();
        if (interval <= 0) {
            return;
        }
        if (player.tickCount % interval != 0) {
            return;
        }

        double rate = AnsConfig.AFFINITY_DECAY_RATE.get();
        if (rate <= 0.0) {
            return;
        }
        // Per-day rate prorated to per-interval (24000 ticks per Minecraft day).
        double perInterval = rate * (interval / 24000.0);
        if (perInterval <= 0.0) {
            return;
        }

        player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
            for (AffinityType type : AffinityType.values()) {
                int level = data.getLevel(type);
                if (level <= 0) {
                    continue;
                }
                int decay = data.accrueDecay(type, level * perInterval);
                if (decay <= 0) {
                    continue;
                }
                int newLevel = Math.max(0, level - decay);
                data.setLevel(type, newLevel);
                if (newLevel <= 0) {
                    data.clearDecayRemainder(type);
                }
                PacketHandler.sendToClient(new AffinitySyncPacket(type, newLevel), player);
            }
        });
    }
}
