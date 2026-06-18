package com.otectus.arsnspells.bridge;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.world.entity.player.Player;

/**
 * Reads and writes Ars Nouveau's mana pool via Ars 5.x's
 * {@link CapabilityRegistry#getMana(net.minecraft.world.entity.LivingEntity)}
 * accessor. The underlying {@link ManaCap} still uses NeoForge entity
 * capabilities on Ars 5.x (not attachments), so consumption / regen flow
 * through the same {@code getCurrentMana} / {@code setMana} / {@code addMana}
 * / {@code removeMana} / {@code getMaxMana} surface that
 * {@code MixinManaCapability} also intercepts.
 *
 * Recursion is handled by MixinManaCapability's {@code arsnspells$inBridgeCall}
 * thread-local guard: when this bridge is the active bridge (ARS_PRIMARY)
 * the mixin's HEAD intercepts no-op so native ManaCap behavior wins.
 */
public class ArsNativeBridge implements IManaBridge {

    @Override
    public float getMana(Player player) {
        if (player == null) return 0.0f;
        ManaCap cap = CapabilityRegistry.getMana(player);
        return cap != null ? (float) cap.getCurrentMana() : 0.0f;
    }

    @Override
    public void setMana(Player player, float amount) {
        if (player == null) return;
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap == null) return;
        cap.setMana(amount);
    }

    @Override
    public boolean consumeMana(Player player, float amount) {
        if (player == null || amount <= 0.0f) return amount <= 0.0f;
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap == null) return false;
        double current = cap.getCurrentMana();
        if (current < amount) {
            return false;
        }
        cap.removeMana(amount);
        return true;
    }

    @Override
    public float getMaxMana(Player player) {
        if (player == null) {
            return safeDefaultMax();
        }
        ManaCap cap = CapabilityRegistry.getMana(player);
        return cap != null ? cap.getMaxMana() : safeDefaultMax();
    }

    @Override
    public String getBridgeType() {
        return "ARS_NATIVE";
    }

    private static float safeDefaultMax() {
        try {
            return AnsConfig.DEFAULT_MAX_MANA.get().floatValue();
        } catch (Exception e) {
            return 100.0f;
        }
    }
}
