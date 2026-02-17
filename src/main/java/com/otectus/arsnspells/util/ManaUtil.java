package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

public class ManaUtil {
    private static final Capability<IManaCap> MANA_CAP_INTERNAL = CapabilityManager.get(new CapabilityToken<>() {});

    public static LazyOptional<IManaCap> getNativeMana(Player player) {
        return player.getCapability(MANA_CAP_INTERNAL);
    }
}