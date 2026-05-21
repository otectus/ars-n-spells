package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.util.CrossCastTrace;
import com.otectus.arsnspells.util.ManaUtil;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrossCastIronsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossCastIronsHandler.class);

    @SubscribeEvent
    public void onIronsSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        boolean unified = BridgeManager.isUnificationEnabled();

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry != null && entry.type == CrossSpellType.IRONS_SPELLBOOKS) {
            // Different spell than the one we tagged: stale or interleaved cast.
            // Drop the entry and let the cast proceed without our adjustments.
            if (entry.spellId != null && !entry.spellId.equals(event.getSpellId())) {
                CrossCastContext.clear(player);
                return;
            }

            int baseEventCost = event.getManaCost();

            if (unified && mode == ManaUnificationMode.SEPARATE) {
                // Cost was precomputed (multiplier already applied) in
                // CrossCastingHandler.castIronsSpell. Use it as-is so the
                // multiplier is applied exactly once.
                int issCost = Math.max(0, Math.round(entry.issCost));
                event.setManaCost(issCost);
                if (!player.isCreative() && entry.arsCost > 0.0f) {
                    BridgeManager.getBridge().consumeMana(player, entry.arsCost);
                }
            } else {
                // Non-SEPARATE (or unified=false): Iron's computed the cost
                // itself. Apply the cross-cast multiplier here, exactly once.
                // ARS_PRIMARY (only when unified) routes the multiplied Iron's
                // cost into the Ars pool via the configured conversion rate.
                float multiplier = (float) Math.max(0.0, AnsConfig.CROSS_CAST_COST_MULTIPLIER.get());
                int multiplied = Math.max(0, Math.round(baseEventCost * multiplier));
                if (unified && mode == ManaUnificationMode.ARS_PRIMARY) {
                    multiplied = Math.max(0, (int) Math.round(
                        multiplied * effectiveIronToArsRate(player)));
                }
                event.setManaCost(multiplied);
            }

            CrossCastTrace.log(entry.attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.IRON_COST_APPLIED,
                "spell", event.getSpellId(), "mode", mode, "unified", unified,
                "base", baseEventCost, "final", event.getManaCost());
            if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
                LOGGER.info(
                    "[CrossCasting] [DEBUG] Iron's cross-cast spell={} mode={} unified={} baseEventCost={} finalCost={}",
                    event.getSpellId(), mode, unified, baseEventCost, event.getManaCost());
            }

            // Clear after applying so a duplicate event fire (or stale entry
            // surviving beyond this cast) cannot apply the multiplier twice.
            CrossCastContext.clear(player);
            return;
        }

        // Normal Iron's cast (not via cross-cast pipeline). ARS_PRIMARY
        // currency conversion applies only when mana unification is enabled;
        // the cross-cast multiplier does not apply here.
        if (unified && mode == ManaUnificationMode.ARS_PRIMARY) {
            int adjusted = (int) Math.round(event.getManaCost() * effectiveIronToArsRate(player));
            event.setManaCost(Math.max(0, adjusted));
        }
    }

    /**
     * ANS-HIGH-012: pool-aware conversion rate. The raw config value is a flat scalar
     * (default 1.0), which at default pool sizes (Ars max ~100, Iron's max ~1000) makes
     * a 50-mana Iron's spell cost 50 Ars mana — 50% of the Ars pool vs. 5% of Iron's.
     * Multiplying by arsMax/ironsMax restores cost proportionality across pools.
     * Mirrors the design in {@link ManaRegenBridge#convertIronsToArs}.
     */
    private static double effectiveIronToArsRate(Player player) {
        double base = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        if (player == null) return base;
        double arsMax = ManaUtil.getNativeMana(player)
            .map(c -> (double) c.getMaxMana())
            .orElseGet(() -> AnsConfig.DEFAULT_MAX_MANA.get());
        double ironsMax = ManaRegenBridge.getCurrentIronsMaxMana(player);
        if (ironsMax <= 0.0 || arsMax <= 0.0) return base;
        return base * (arsMax / ironsMax);
    }
}
