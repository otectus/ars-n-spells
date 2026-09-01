package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single point of mana-value redirection inside Iron's
 * {@code AbstractSpell.canBeCastedBy}.
 *
 * <p>Background — what this fixes:
 * {@code canBeCastedBy} reads {@code MagicData.getMana()} and returns
 * {@code CastResult.FAILURE} with "cast_error_mana" if mana &lt; cost — BEFORE
 * {@code SpellPreCastEvent} fires. That means our event-based handlers never get a
 * chance to redirect the cost, AND any cross-mod mana unification in ARS_PRIMARY
 * mode is compared against the wrong scale.
 *
 * <p><b>Why this class exists separately from {@link MixinIronsSpellDamage}.</b>
 * Two {@code @Redirect}s on the same call site do not stack — Mixin silently keeps
 * only one. Historically the ARS_PRIMARY conversion and the ring bypass each owned
 * a redirect here and fought each other. This class is the declared single owner of
 * that call site; nothing else may redirect {@code MagicData.getMana()} inside
 * {@code canBeCastedBy}.
 *
 * <p><b>Covenant of the Seven on 1.21.1.</b> The 1.20.1 build folded a third
 * behaviour in here: a Cursed/Virtue ring wearer got {@code Float.MAX_VALUE} so
 * Iron's own mana gate stepped aside and Covenant's LP/aura listener could take the
 * cost instead. Covenant has no 1.21.1 release, so that branch has no consumer and
 * is not carried here — see {@code src/covenant-disabled/README.md}. It belongs at
 * the top of this method when the subsystem is re-enabled, because the bypass must
 * win over the conversion below.
 *
 * <p>All other {@code MagicData.getMana()} call sites (HUD, regen, cost consumption
 * in {@code castSpell}, etc.) are untouched — they continue to see the real value.
 * Only the read inside {@code canBeCastedBy} is intercepted, because that is the one
 * that gates the cast.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsCastValidation {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinIronsCastValidation.class);

    /** Throttle map to avoid info-level log spam on attribute reads. */
    private static final ConcurrentHashMap<UUID, Long> lastLogMs = new ConcurrentHashMap<>();
    private static final long LOG_THROTTLE_MS = 1000;

    @Redirect(
        method = "canBeCastedBy",
        at = @At(
            value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F"
        ),
        require = 0
    )
    private float arsnspells$redirectManaForRingOrConversion(MagicData magicData) {
        float realMana;
        try {
            realMana = magicData.getMana();
        } catch (Throwable t) {
            LOGGER.warn("[CastValidation] getMana() threw; failing open with 0", t);
            return 0f;
        }

        ServerPlayer player;
        try {
            player = ((MagicDataAccessor) (Object) magicData).arsnspells$getServerPlayer();
        } catch (Throwable t) {
            LOGGER.warn("[CastValidation] MagicDataAccessor cast failed; cross-cut features may be broken", t);
            return realMana;
        }
        if (player == null) {
            return realMana;
        }

        // ARS_PRIMARY cross-conversion: Iron's is about to compare this value against a
        // cost denominated in Iron's mana, but in ARS_PRIMARY the pool being spent is
        // Ars's. Divide by the conversion rate so the comparison happens on one scale.
        try {
            if (BridgeManager.isUnificationEnabled()) {
                ManaUnificationMode mode = BridgeManager.getCurrentMode();
                if (mode == ManaUnificationMode.ARS_PRIMARY) {
                    double rate = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
                    if (rate > 0.0) {
                        throttledLog(player, "[CastValidation] ARS_PRIMARY scaling mana for {} (real={}, rate={})",
                            player.getName().getString(), realMana, rate);
                        return (float) (realMana / rate);
                    }
                }
            }
        } catch (IllegalStateException configNotReady) {
            // Config not loaded yet (very early game tick) — fall through to the real value.
        }

        // Normal flow: real mana value.
        return realMana;
    }

    private static void throttledLog(ServerPlayer player, String message, Object... args) {
        if (AnsConfig.DEBUG_MODE == null || !AnsConfig.DEBUG_MODE.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID id = player.getUUID();
        Long last = lastLogMs.get(id);
        if (last != null && now - last < LOG_THROTTLE_MS) {
            return;
        }
        // ANS-MED-003: opportunistic eviction every 64th call so the throttle map does
        // not grow unbounded across player churn. We only ever look at the most recent
        // timestamp per player, so anything older than 60s is dead state.
        if ((lastLogMs.size() & 63) == 0) {
            long cutoff = now - 60_000L;
            lastLogMs.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        lastLogMs.put(id, now);
        LOGGER.info(message, args);
    }
}
