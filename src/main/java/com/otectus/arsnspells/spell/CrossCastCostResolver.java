package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.world.entity.player.Player;

/**
 * Single source of truth for cross-cast cost calculation.
 *
 * <p>Captures the algorithm previously scattered across
 * {@code CrossCastingHandler.onArsSpellCost},
 * {@code CrossCastingHandler.castIronsSpell} (SEPARATE branch),
 * {@code CrossCastIronsHandler.onIronsSpellCast},
 * {@code MixinSpellResolverMana.arsnspells$expendMana}, and the ring handlers.
 *
 * <p>For 2.0.0 this is the *authoritative calculator* — call sites still own
 * the mutation choreography (event.setManaCost, mixin cancels, pending-cost
 * stamping), but they consult this class for the numeric decision. A
 * follow-up release may complete the migration so call sites delegate
 * mutation as well.
 */
public final class CrossCastCostResolver {

    public enum CostStage {
        /** Pre-calc inside {@code onArsSpellCost} - SpellCostCalcEvent has fired. */
        ARS_PRECALC,
        /** Pre-calc inside {@code castIronsSpell} - before {@code attemptInitiateCast}. */
        IRON_PRECALC,
        /** Post-event adjustment inside {@code onIronsSpellCast} - SpellOnCastEvent. */
        IRON_POSTEVENT
    }

    public enum ResourceMode {
        ARS_MANA,
        IRONS_MANA,
        CURSED_LP,
        VIRTUE_AURA,
        NONE
    }

    public record CostBreakdown(
        int primaryCost,
        float secondaryCost,
        ResourceMode primary,
        ResourceMode secondary,
        ManaUnificationMode mode,
        boolean unified,
        boolean ringActive,
        float multiplier
    ) {
        public boolean hasSecondary() {
            return secondaryCost > 0.0f && secondary != ResourceMode.NONE;
        }
    }

    private CrossCastCostResolver() {
    }

    /**
     * Resolve a cross-cast cost breakdown at the given pipeline stage. The
     * resolver consults the active bridge mode, the {@code cross_cast_cost_multiplier},
     * and (for trace clarity) any active ring state — but it does not mutate
     * the player or the event.
     *
     * @param player        the casting player (non-null)
     * @param entry         the active cross-cast context (may be null for
     *                      non-cross casts; the resolver still gives a valid
     *                      breakdown that callers can apply)
     * @param baseCost      the base upstream cost (what Ars/Iron computed
     *                      natively, before the bridge adjusts it)
     * @param stage         which pipeline stage is calling
     */
    public static CostBreakdown resolve(Player player, CrossCastContext.Entry entry,
        int baseCost, CostStage stage) {

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        boolean unified = BridgeManager.isUnificationEnabled();
        float multiplier = (float) Math.max(0.0, AnsConfig.CROSS_CAST_COST_MULTIPLIER.get());
        boolean cursedActive = SanctifiedLegacyCompat.isAvailable()
            && SanctifiedLegacyCompat.isWearingCursedRing(player);
        boolean virtueActive = SanctifiedLegacyCompat.isAvailable()
            && SanctifiedLegacyCompat.isWearingVirtueRing(player);
        boolean ringActive = cursedActive || virtueActive;

        // Ring takes priority: cost is rerouted to LP/Aura by the ring
        // handlers. The cross-cast multiplier still applies (to the derived
        // ring cost rather than to mana). The ring handlers compute the
        // LP/Aura value themselves; we surface the *base* and *multiplier*
        // here for consistent trace correlation.
        if (ringActive) {
            ResourceMode primary = cursedActive ? ResourceMode.CURSED_LP : ResourceMode.VIRTUE_AURA;
            int multipliedBase = entry != null ? Math.max(0, Math.round(baseCost * multiplier)) : baseCost;
            return new CostBreakdown(multipliedBase, 0.0f, primary, ResourceMode.NONE,
                mode, unified, true, multiplier);
        }

        // Cross-cast paths: apply multiplier, then mode-specific split or
        // conversion. If entry is null (non-cross cast), we pass the base
        // cost through unchanged so callers can still call us for trace
        // consistency.
        boolean isCrossCast = entry != null;

        if (!isCrossCast) {
            return passthrough(baseCost, mode, unified, multiplier);
        }

        int multiplied = Math.max(0, Math.round(baseCost * multiplier));

        switch (stage) {
            case ARS_PRECALC:
                return resolveArsPrecalc(multiplied, mode, unified, multiplier);
            case IRON_PRECALC:
                return resolveIronPrecalc(multiplied, mode, unified, multiplier);
            case IRON_POSTEVENT:
                return resolveIronPostevent(multiplied, mode, unified, multiplier);
            default:
                return passthrough(multiplied, mode, unified, multiplier);
        }
    }

    private static CostBreakdown passthrough(int cost, ManaUnificationMode mode,
        boolean unified, float multiplier) {
        return new CostBreakdown(cost, 0.0f, ResourceMode.ARS_MANA, ResourceMode.NONE,
            mode, unified, false, multiplier);
    }

    private static CostBreakdown resolveArsPrecalc(int multiplied, ManaUnificationMode mode,
        boolean unified, float multiplier) {
        if (unified && mode == ManaUnificationMode.SEPARATE) {
            float arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get().floatValue();
            float issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get().floatValue();
            int arsCost = Math.max(0, Math.round(multiplied * arsPercent));
            float issCost = (float) (multiplied * issPercent * AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());
            return new CostBreakdown(arsCost, issCost, ResourceMode.ARS_MANA, ResourceMode.IRONS_MANA,
                mode, unified, false, multiplier);
        }
        return new CostBreakdown(multiplied, 0.0f, ResourceMode.ARS_MANA, ResourceMode.NONE,
            mode, unified, false, multiplier);
    }

    private static CostBreakdown resolveIronPrecalc(int multiplied, ManaUnificationMode mode,
        boolean unified, float multiplier) {
        if (unified && mode == ManaUnificationMode.SEPARATE) {
            float arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get().floatValue();
            float issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get().floatValue();
            float arsCost = (float) (multiplied * arsPercent * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get());
            int issCost = Math.max(0, Math.round(multiplied * issPercent));
            return new CostBreakdown(issCost, arsCost, ResourceMode.IRONS_MANA, ResourceMode.ARS_MANA,
                mode, unified, false, multiplier);
        }
        return new CostBreakdown(multiplied, 0.0f, ResourceMode.IRONS_MANA, ResourceMode.NONE,
            mode, unified, false, multiplier);
    }

    private static CostBreakdown resolveIronPostevent(int multiplied, ManaUnificationMode mode,
        boolean unified, float multiplier) {
        if (unified && mode == ManaUnificationMode.ARS_PRIMARY) {
            int converted = Math.max(0, (int) Math.round(
                multiplied * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get()));
            return new CostBreakdown(converted, 0.0f, ResourceMode.ARS_MANA, ResourceMode.NONE,
                mode, unified, false, multiplier);
        }
        return new CostBreakdown(multiplied, 0.0f, ResourceMode.IRONS_MANA, ResourceMode.NONE,
            mode, unified, false, multiplier);
    }
}
