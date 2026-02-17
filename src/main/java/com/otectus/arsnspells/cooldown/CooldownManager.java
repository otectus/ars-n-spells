package com.otectus.arsnspells.cooldown;

import net.minecraft.world.entity.player.Player;

public class CooldownManager {
    private static final CooldownManager INSTANCE = new CooldownManager();

    private CooldownManager() {}
    public static CooldownManager getInstance() { return INSTANCE; }

    public void applyCooldown(Player player, CooldownCategory category) {
        UnifiedCooldownManager.applyCooldown(player, category, false);
    }

    public boolean isOnCooldown(Player player, CooldownCategory category) {
        return UnifiedCooldownManager.isOnCooldown(player, category);
    }

    public CooldownTracker getTracker(Player player) {
        return UnifiedCooldownManager.getClientTracker();
    }
}
