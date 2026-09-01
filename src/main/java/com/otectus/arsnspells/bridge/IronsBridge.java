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
    /**
     * 3.2.0: re-checks the shared-pool ceiling immediately before deducting, then audits
     * its own arithmetic.
     *
     * <p>Iron's clamps every mana write down to the {@code max_mana} attribute, so a
     * ceiling that has drifted below the current pool does not cap a deduction - it
     * destroys the surplus. The ceiling is a transient modifier and is otherwise only
     * refreshed on equipment change, login and respawn, so this is the last line of
     * defence on the hot path.
     *
     * <p>After writing, the result is compared against the arithmetic one; a mismatch
     * means some other ceiling source is still wrong, and it is logged once rather than
     * being silently eaten - the previous code had no way to tell a correct deduction
     * from a wipe.
     */
    public boolean consumeMana(Player player, float amount) {
        if (player == null || player.level().isClientSide()) return false;
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return false;
            float before = data.getMana();
            if (before < amount) {
                return false;
            }
            com.otectus.arsnspells.equipment.EquipmentIntegration.ensureSharedPoolCeiling(player);
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

    /**
     * Atomic add. Overriding this matters: {@link IManaBridge}'s default is
     * {@code setMana(getMana() + amount)}, and every refund path in the mod - the SEPARATE
     * rollback and the cross-cast pre-pay compensation - runs through here. A get-then-set
     * loses any regen that lands between the two, which is exactly the race ANS-CRIT-003 was
     * filed against.
     */
    @Override
    public void addMana(Player player, float amount) {
        if (player == null || player.level().isClientSide() || amount == 0.0f) return;
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return;
            data.addMana(amount);
        } catch (Throwable e) {
            logCriticalError("addMana", e);
        }
    }

    /** Float slop below which a shortfall is rounding, not a clamp. */
    private static final float CLAMP_TOLERANCE = 1.0e-3f;

    private static boolean clampWarningLogged = false;

    private void warnOnce(Player player, float before, float amount, float expected, float after) {
        if (clampWarningLogged) {
            return;
        }
        clampWarningLogged = true;
        LOGGER.warn("Ars 'n' Spells: casting for {} cost {} mana but the pool fell from {} to {} "
                + "(expected {}). Iron's clamps every mana write down to the max_mana attribute, "
                + "currently {}, so the surplus above it was destroyed rather than spent. "
                + "Please report this with your mana_unification_mode and gear.",
            player.getName().getString(), amount, before, after, expected,
            getMaxMana(player));
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
