package com.otectus.arsnspells.bridge;

import net.minecraft.world.entity.player.Player;

public interface IManaBridge {
    float getMana(Player player);
    void setMana(Player player, float amount);
    boolean consumeMana(Player player, float amount);
    float getMaxMana(Player player);
    String getBridgeType();
}