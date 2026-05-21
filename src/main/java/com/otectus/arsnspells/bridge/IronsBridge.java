package com.otectus.arsnspells.bridge;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.player.Player;
import com.otectus.arsnspells.config.AnsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IronsBridge implements IManaBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsBridge.class);
    // ANS-MED-007: per-op fail-once set instead of a single global boolean. The old
    // design latched true on the FIRST error of any kind and silenced ALL subsequent
    // errors — masking genuine regressions that happen after an unrelated startup hiccup.
    private static final java.util.Set<String> loggedOps =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

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
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return;
            data.setMana(amount);
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
    public void addMana(Player player, float amount) {
        if (player == null || player.level().isClientSide() || amount == 0.0f) return;
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return;
            // MagicData.addMana is the atomic add; do NOT route through get+set or
            // we lose concurrent regen between the read and the write.
            data.addMana(amount);
        } catch (Throwable e) {
            logCriticalError("addMana", e);
        }
    }

    @Override
    public float getMaxMana(Player player) {
        try {
            if (player == null) {
                return AnsConfig.DEFAULT_MAX_MANA.get().floatValue();
            }
            return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        } catch (Throwable e) {
            logCriticalError("getMaxMana", e);
            return AnsConfig.DEFAULT_MAX_MANA.get().floatValue();
        }
    }

    private void logCriticalError(String op, Throwable e) {
        // ANS-MED-007: log once per op-name, so getMana/setMana/addMana/consumeMana
        // failures each get exactly one ERROR line in the log instead of the first
        // one silencing all the others.
        if (loggedOps.add(op)) {
            LOGGER.error("Ars 'n' Spells: Iron's Spells API failure during {} - integration may be unstable.", op, e);
        }
    }

    @Override
    public String getBridgeType() { return "IRONS_SPELLS"; }
}