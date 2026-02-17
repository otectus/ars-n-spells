package com.otectus.arsnspells.cooldown;

import java.util.HashMap;
import java.util.Map;

public class CooldownTracker {
    private final Map<CooldownCategory, Long> cooldownEndTicks = new HashMap<>();

    public void setLastCastTime(CooldownCategory category, long cooldownEndTick) {
        cooldownEndTicks.put(category, cooldownEndTick);
    }

    public long getLastCastTime(CooldownCategory category) {
        return cooldownEndTicks.getOrDefault(category, 0L);
    }

    public boolean isOnCooldown(CooldownCategory category, long currentTime) {
        long cooldownEnd = getLastCastTime(category);
        return currentTime < cooldownEnd;
    }
}
