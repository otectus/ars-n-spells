package com.otectus.arsnspells.bridge;

import com.hollingsworth.arsnouveau.common.capability.ManaData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Network projection of the shared pool; the persisted Ars attachment stays native. */
public final class ArsManaDisplay {
    private ArsManaDisplay() {}
    public static CompoundTag snapshot(Player player, ManaData nativeData) {
        CompoundTag tag = nativeData.serializeNBT(player.registryAccess());
        if (!BridgeManager.ironsOwnsSharedPool()) return tag;
        var bridge = BridgeManager.getBridge();
        int max = BridgeManager.getCurrentMode().isHybrid() ? nativeData.getMaxMana() : (int) bridge.getMaxMana(player);
        tag.putInt("max", max);
        tag.putDouble("current", Math.min(bridge.getMana(player), max));
        return tag;
    }
}
