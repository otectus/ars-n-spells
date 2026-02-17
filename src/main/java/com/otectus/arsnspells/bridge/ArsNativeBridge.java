package com.otectus.arsnspells.bridge;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.otectus.arsnspells.util.ManaUtil;
import net.minecraft.world.entity.player.Player;

public class ArsNativeBridge implements IManaBridge {
    @Override
    public float getMana(Player player) {
        // Unifying to float as per IManaBridge signature
        return ManaUtil.getNativeMana(player).map(cap -> (float)cap.getCurrentMana()).orElse(0.0f);
    }

    @Override
    public void setMana(Player player, float amount) {
        if (player.level().isClientSide()) return;
        ManaUtil.getNativeMana(player).ifPresent(cap -> cap.setMana((double)amount));
    }

    @Override
    public boolean consumeMana(Player player, float amount) {
        if (player.level().isClientSide()) return false;
        return ManaUtil.getNativeMana(player).map(cap -> {
            if (cap.getCurrentMana() >= (double)amount) {
                cap.removeMana((double)amount);
                return true;
            }
            return false;
        }).orElse(false);
    }

    @Override
    public float getMaxMana(Player player) {
        return ManaUtil.getNativeMana(player).map(cap -> (float)cap.getMaxMana()).orElse(100.0f);
    }

    @Override
    public String getBridgeType() { return "ARS_NATIVE"; }
}