package com.otectus.arsnspells.bridge;

import net.minecraft.world.entity.player.Player;

/**
 * Bridge to an underlying mana pool (Ars Nouveau native cap or Iron's MagicData).
 *
 * <p>Mana values are exchanged as {@code float} to match the Iron's API; this loses
 * precision against Ars's native {@code double} for pool values above ~16.7M. Modpack
 * authors should keep configured max-mana ceilings well below that range.
 *
 * <p>All methods are expected to be called from the server main thread. Implementations
 * are not required to be thread-safe; see {@code BridgeManager.consumeManaForMode} for
 * the canonical dual-cost coordination point.
 */
public interface IManaBridge {
    float getMana(Player player);
    void setMana(Player player, float amount);
    boolean consumeMana(Player player, float amount);

    /**
     * Compensating add — used to refund a previous {@link #consumeMana} without
     * clobbering concurrent regen/buffs. Default implementation is the naive
     * {@code setMana(getMana + amount)} which is racy; impls should override with
     * an atomic add operation when the backing API supports one.
     */
    default void addMana(Player player, float amount) {
        if (player == null || amount == 0.0f) return;
        setMana(player, getMana(player) + amount);
    }

    float getMaxMana(Player player);
    String getBridgeType();
}