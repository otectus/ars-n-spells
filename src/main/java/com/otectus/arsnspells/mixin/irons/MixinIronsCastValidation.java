package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Single point of mana-value redirection inside Iron's
 * {@code AbstractSpell.canBeCastedBy}.
 *
 * <p>Background — what this fixes:
 * {@code canBeCastedBy} reads {@code MagicData.getMana()} and returns
 * {@code CastResult.FAILURE} with "cast_error_mana" if mana &lt; cost — BEFORE
 * {@code SpellPreCastEvent} fires. That means our event-based handlers
 * ({@code IronsLPHandler}, {@code IronsAuraHandler}) never get a chance to redirect
 * the cost to LP / aura, AND any cross-mod mana unification in ARS_PRIMARY mode is
 * compared against the wrong scale.
 *
 * <p>Two pre-existing redirects (1.x's {@code MixinIronsSpellDamage} cross-conversion
 * and round-2's bypass) used to fight each other on this same call site — Mixin
 * silently keeps only one when two {@code @Redirect}s collide. This class is the
 * single owner of that redirect; both behaviours are folded into one method.
 *
 * <p>All other {@code MagicData.getMana()} call sites (HUD, regen, cost consumption
 * in {@code castSpell}, etc.) are untouched — they continue to see the real value.
 * Only the read inside {@code canBeCastedBy} is intercepted, because that's the
 * one that gates the cast.
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

        // ----- 1. Ring bypass (highest priority) -----
        // Virtue Ring bypass: Covenant of the Seven owns Iron's aura now — bypass Iron's
        // own mana check so Covenant's own SpellPreCastEvent listener can take over.
        boolean wearsCursed = false;
        boolean wearsVirtue = false;
        try {
            if (SanctifiedLegacyCompat.isAvailable()) {
                wearsCursed = AnsConfig.ENABLE_LP_SYSTEM.get()
                    && SanctifiedLegacyCompat.isWearingCursedRing(player);
                wearsVirtue = SanctifiedLegacyCompat.isWearingVirtueRing(player);
            }
        } catch (IllegalStateException configNotReady) {
            // Config not loaded yet (very early game tick) — fall through to no-ring behaviour.
        }
        if (wearsCursed || wearsVirtue) {
            throttledLog(player, "[CastValidation] Bypassing Iron's mana check for {} (cursed={}, virtue={}, realMana={})",
                player.getName().getString(), wearsCursed, wearsVirtue, realMana);
            return Float.MAX_VALUE;
        }

        // ----- 2. ARS_PRIMARY cross-conversion (was MixinIronsSpellDamage.arsnspells$scaleManaForConversion) -----
        try {
            if (BridgeManager.isUnificationEnabled()) {
                ManaUnificationMode mode = BridgeManager.getCurrentMode();
                if (mode == ManaUnificationMode.ARS_PRIMARY) {
                    double rate = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
                    if (rate > 0.0) {
                        return (float) (realMana / rate);
                    }
                }
            }
        } catch (IllegalStateException configNotReady) {
            // Same — fall through.
        }

        // ----- 3. Normal flow: real mana value -----
        return realMana;
    }

    private static void throttledLog(ServerPlayer player, String message, Object... args) {
        long now = System.currentTimeMillis();
        UUID id = player.getUUID();
        Long last = lastLogMs.get(id);
        if (last != null && now - last < LOG_THROTTLE_MS) {
            return;
        }
        // ANS-MED-003: opportunistic eviction every 64th call so the throttle map
        // does not grow unbounded across player churn. We only ever look at the
        // most recent timestamp per player, so anything older than 60s is dead state.
        if ((lastLogMs.size() & 63) == 0) {
            long cutoff = now - 60_000L;
            lastLogMs.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        lastLogMs.put(id, now);
        LOGGER.info(message, args);
    }
}
