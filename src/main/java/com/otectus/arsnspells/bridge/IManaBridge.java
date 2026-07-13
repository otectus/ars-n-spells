package com.otectus.arsnspells.bridge;

import net.minecraft.world.entity.player.Player;

public interface IManaBridge {
    float getMana(Player player);
    void setMana(Player player, float amount);
    boolean consumeMana(Player player, float amount);

    /**
     * Credit mana back to this pool (e.g. the ANS-HIGH-030 refund of a pre-paid
     * SEPARATE-mode share after a failed cast). Unclamped by design: refunding
     * what was just consumed cannot exceed the pool, and bridge setters clamp
     * defensively anyway.
     */
    default void addMana(Player player, float amount) {
        if (player == null || amount == 0.0f) return;
        setMana(player, getMana(player) + amount);
    }

    float getMaxMana(Player player);
    String getBridgeType();
}