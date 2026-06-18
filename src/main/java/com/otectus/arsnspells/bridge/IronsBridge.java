package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iron's Spellbooks mana bridge. The {@link MagicData} API is stable in
 * Iron's 3.x for 1.21.1 (still {@code getMana()/setMana(float)/addMana(float)}).
 * The one drift point is {@link AttributeRegistry#MAX_MANA} returning a
 * {@code Holder<Attribute>} now — {@link Player#getAttributeValue(net.minecraft.core.Holder)}
 * accepts that directly, so the call shape is preserved.
 */
public class IronsBridge implements IManaBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsBridge.class);
    private static boolean errorLogged = false;

    @Override
    public float getMana(Player player) {
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return 0.0f;
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
            if (data == null) return false;
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
            if (player == null) return AnsConfig.DEFAULT_MAX_MANA.get().floatValue();
            // 1.21.1 NeoForge: getAttributeValue accepts Holder<Attribute>; AttributeRegistry.MAX_MANA is one.
            return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
        } catch (Throwable e) {
            logCriticalError("getMaxMana", e);
            return AnsConfig.DEFAULT_MAX_MANA.get().floatValue();
        }
    }

    private void logCriticalError(String op, Throwable e) {
        if (!errorLogged) {
            LOGGER.error("Iron's Spells API failure during {} - integration unstable.", op, e);
            errorLogged = true;
        }
    }

    @Override
    public String getBridgeType() {
        return "IRONS_SPELLS";
    }
}
