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
        if (player == null || player.level().isClientSide()) return;
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return;
            data.setMana(amount);
        } catch (Throwable e) {
            logCriticalError("setMana", e);
        }
    }

    /**
     * Deduct {@code amount} from Iron's pool, and verify that is all that was deducted.
     *
     * <p>Iron's {@code MagicData.setMana} clamps <em>every</em> write down to the player's
     * {@code MAX_MANA} attribute, and {@code addMana} is just {@code setMana(mana + v)}. So
     * if the ceiling ever sits below the current pool, this call does not subtract the cost —
     * it collapses the pool to the ceiling, however large the surplus and however small the
     * spell. That is the "one spell drained all my mana" report.
     *
     * <p>Two defences. Before writing, {@code ensureSharedPoolCeiling} re-applies the ceiling
     * if it has drifted below Ars's real max, which is what makes the collapse impossible.
     * After writing, the result is compared against the arithmetic one; a mismatch means some
     * other ceiling source is still wrong, and it is logged once rather than being silently
     * eaten — the previous code had no way to tell a correct deduction from a wipe.
     */
    @Override
    public boolean consumeMana(Player player, float amount) {
        if (player == null || player.level().isClientSide()) return false;
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) {
                return false;
            }
            float before = data.getMana();
            if (before < amount) {
                return false;
            }
            com.otectus.arsnspells.equipment.EquipmentIntegration.ensureSharedPoolCeiling(player);
            // Remove unsafe synchronization - MagicData handles thread safety internally
            data.addMana(-amount);
            float expected = before - amount;
            float after = data.getMana();
            if (after < expected - CLAMP_TOLERANCE) {
                warnOnce(player, before, amount, expected, after);
            }
            return true;
        } catch (Throwable e) {
            logCriticalError("consumeMana", e);
        }
        return false;
    }

    /** Float slop below which a shortfall is rounding, not a clamp. */
    private static final float CLAMP_TOLERANCE = 1.0e-3f;

    private void warnOnce(Player player, float before, float amount, float expected, float after) {
        if (!loggedOps.add("consumeMana:clamped")) {
            return;
        }
        LOGGER.warn("Ars 'n' Spells: casting for {} cost {} mana but the pool fell from {} to {} "
                + "(expected {}). Iron's clamps every mana write down to the max_mana attribute, "
                + "currently {}, so the surplus above it was destroyed rather than spent. "
                + "Please report this with your mana_unification_mode and gear.",
            player.getName().getString(), amount, before, after, expected,
            getMaxMana(player));
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