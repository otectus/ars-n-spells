package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.world.entity.player.Player;

/**
 * STUB — the 1.20.1 implementation read AN's mana via {@code IManaCap}, a
 * Forge capability that no longer exists in NeoForge 1.21.1 (AN 5.x moved
 * to data attachments). Wiring this back up requires identifying AN 5.x's
 * mana attachment / public API and reimplementing the four bridge methods.
 *
 * Until Phase 11: returns {@link AnsConfig#DEFAULT_MAX_MANA} as max,
 * 0 as current, and refuses consumption. Practical effect: ARS_PRIMARY,
 * HYBRID, and SEPARATE mana modes effectively no-op the Ars side. Use
 * ISS_PRIMARY (Iron's-only) until the bridge is restored.
 */
public class ArsNativeBridge implements IManaBridge {
    @Override
    public float getMana(Player player) {
        return 0.0f;
    }

    @Override
    public void setMana(Player player, float amount) {
        // TODO(Phase 11): write to AN 5.x mana attachment
    }

    @Override
    public boolean consumeMana(Player player, float amount) {
        // TODO(Phase 11): consume from AN 5.x mana attachment
        return false;
    }

    @Override
    public float getMaxMana(Player player) {
        try {
            return AnsConfig.DEFAULT_MAX_MANA.get().floatValue();
        } catch (Exception e) {
            return 100.0f;
        }
    }

    @Override
    public String getBridgeType() {
        return "ARS_NATIVE_STUB";
    }
}
