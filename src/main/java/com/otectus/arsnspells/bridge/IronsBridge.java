package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.equipment.AttributeContribution;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import com.otectus.arsnspells.config.AnsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class IronsBridge implements IManaBridge {
    // Transaction methods propagate recoverable failures to the observer; no fabricated zero reads.
    @Override public double transactionMana(Player player) { return MagicData.getPlayerMagicData(player).getMana(); }
    @Override public double transactionMax(Player player) { return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get()); }
    @Override public boolean transactionDebit(Player player, double amount) {
        var data = MagicData.getPlayerMagicData(player);
        if (data.getMana() < amount) return false;
        subtractExactly(player, (float) amount);
        return true;
    }

    /**
     * The call-scoped modifier that keeps Iron's ceiling clamp off a debit.
     *
     * <p>Iron's {@code MagicData.setMana} ends by clamping the pool to the {@code max_mana}
     * attribute, and {@code addMana} is {@code setMana(mana + delta)}. A pool above that ceiling
     * at the moment of a debit therefore loses the whole surplus, not the price: 240 mana under a
     * ceiling of 100 pays 90 and lands on 100. The ceiling can sit under the pool for a few ticks
     * whenever a max-mana modifier goes away (an equipment swap, a mirrored bonus being
     * re-applied, another mod's modifier) until Iron's next regeneration tick clamps the pool;
     * 3.3.4 and the first 3.3.5 builds refused every payment in that state, which the 3.3.5
     * report showed as every Iron's cast failing with {@code CEILING_INCONSISTENT} at 240 mana.
     * While this modifier is on the attribute the ceiling is at least the balance being debited,
     * so the write moves exactly the price and the ceiling is applied, as natively, by the
     * regeneration tick rather than by the payment. It lives for one write only, is removed
     * however that write exits, and is never persisted, so the modifier registry and cleanup
     * never see it.
     */
    private static final UUID DEBIT_GUARD_ID = UUID.fromString("b1d6e9a2-5f3c-4c7e-8a41-2e0c9d7f6b35");
    private static final String DEBIT_GUARD_NAME = "Ars 'n' Spells debit guard";

    /** Whether the debit guard is on {@code player}'s ceiling right now; diagnostics and GameTests. */
    public static boolean debitGuardActive(Player player) {
        AttributeInstance ceiling = player.getAttribute(AttributeRegistry.MAX_MANA.get());
        return ceiling != null && ceiling.getModifier(DEBIT_GUARD_ID) != null;
    }

    /**
     * Subtract exactly {@code amount} from the Iron's pool, however the ceiling sits.
     *
     * <p>No Iron's type in the signature: unit tests reflect over this class on a classpath
     * without Iron's, and an unresolvable parameter type fails every method lookup on it.
     *
     * <p>An ADDITION modifier is scaled by the attribute's MULTIPLY modifiers, so the guard is
     * sized in pre-multiplier units from the ceiling's own amplification. A ceiling the
     * attribute's range cannot reach is left to clamp as it natively would; the callers' checks
     * report that case.
     */
    private static void subtractExactly(Player player, float amount) {
        MagicData data = MagicData.getPlayerMagicData(player);
        float before = data.getMana();
        AttributeInstance ceiling = player.getAttribute(AttributeRegistry.MAX_MANA.get());
        if (ceiling == null || !(before > ceiling.getValue()) || ceiling.getModifier(DEBIT_GUARD_ID) != null) {
            data.addMana(-amount);
            return;
        }
        double guard = SharedPoolCeiling.modifierAmount(ceiling.getValue(), Math.nextUp(before),
            AttributeContribution.additiveAmplification(ceiling.getModifiers()));
        ceiling.addTransientModifier(new AttributeModifier(DEBIT_GUARD_ID, DEBIT_GUARD_NAME, guard,
            AttributeModifier.Operation.ADDITION));
        try {
            data.addMana(-amount);
        } finally {
            ceiling.removeModifier(DEBIT_GUARD_ID);
        }
    }
    @Override public void transactionCredit(Player player, double amount) {
        MagicData.getPlayerMagicData(player).addMana((float) amount);
    }

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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
     * <p>Three defences. Before writing, {@code ensureSharedPoolCeiling} re-applies the ceiling
     * if it has drifted below Ars's real max. The write itself runs under the debit guard
     * ({@link #subtractExactly}), so a ceiling that is still below the pool cannot turn the
     * price into a wipe. After writing, the result is compared against the arithmetic one; a
     * mismatch means the guard could not reach the balance (the attribute's range caps it), and
     * it is logged once rather than being silently eaten — the previous code had no way to tell
     * a correct deduction from a wipe.
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
            subtractExactly(player, amount);
            float expected = before - amount;
            float after = data.getMana();
            if (after < expected - CLAMP_TOLERANCE) {
                warnOnce(player, before, amount, expected, after);
            }
            return true;
        } catch (RuntimeException e) {
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
            // we lose concurrent regen between the read and the write. A negative add is a
            // debit (an Ars-side drain routed here) and gets the same ceiling guard as a payment.
            if (amount < 0.0f) { subtractExactly(player, -amount); return; }
            data.addMana(amount);
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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