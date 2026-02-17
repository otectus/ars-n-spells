package com.otectus.arsnspells.bridge;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IronsBridge implements IManaBridge {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean errorLogged = false;

    @Override
    public float getMana(Player player) {
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) {
                return 0.0f;
            }
            return data.getMana();
        } catch (Throwable e) {
            logCriticalError("getMana", e);
            return 0.0f;
        }
    }

    @Override
    public void setMana(Player player, float amount) {
        if (player.level().isClientSide()) return;
        try {
            MagicData.getPlayerMagicData(player).setMana(amount);
        } catch (Throwable e) {
            logCriticalError("setMana", e);
        }
    }

    @Override
    public boolean consumeMana(Player player, float amount) {
        if (player.level().isClientSide()) return false;
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) {
                return false;
            }
            // Remove unsafe synchronization - MagicData handles thread safety internally
            if (data.getMana() >= amount) {
                data.addMana(-amount);
                return true;
            }
        } catch (Throwable e) {
            logCriticalError("consumeMana", e);
        }
        return false;
    }

    @Override
    public float getMaxMana(Player player) {
        try {
            if (player == null) {
                return 100.0f;
            }
            return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        } catch (Throwable e) {
            logCriticalError("getMaxMana", e);
            return 100.0f;
        }
    }

    private void logCriticalError(String op, Throwable e) {
        if (!errorLogged) {
            LOGGER.error("Ars 'n' Spells: Iron's Spells API failure during {}. integration may be unstable.", op);
            errorLogged = true;
        }
    }

    @Override
    public String getBridgeType() { return "IRONS_SPELLS"; }
}