package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.ModeRoutingSnapshot;
import com.otectus.arsnspells.contract.ResourceUnit;

import java.util.Optional;

/**
 * The loader half of one routing decision: a {@link ModeRoutingSnapshot} plus the two named
 * bridge instances it refers to (audit V04).
 *
 * <p>{@link BridgeManager} used to hold {@code activeBridge}, {@code secondaryBridge} and
 * {@code currentMode} as three separately-volatile fields, re-read at different instants during
 * one cast. This is the single object that replaces them: built in one place, published by a
 * single reference assignment, and read once per operation.
 *
 * <p>The adapters are <b>named, not positional</b>. {@code secondaryBridge} used to mean "Ars"
 * in ISS_PRIMARY and "Iron's" in ARS_PRIMARY, so a caller that inferred a spend's origin from
 * which slot the bridge sat in billed the wrong pool the moment the mode changed.
 * {@link #nativeArs()} and {@link #nativeIrons()} name their system and never move.
 *
 * <p>{@link #nativeIrons()} is populated whenever Iron's Spellbooks is loaded, <em>in every
 * mode including {@code disabled}</em>. "No integration" means Iron's pays for its own spells
 * out of its own pool, not that the Iron's adapter stops existing; nulling it in DISABLED is
 * what made a native Iron's spend fall through to the Ars pool.
 *
 * @param snapshot      the loader-neutral routing answer
 * @param requestedMode the mode as configured
 * @param effectiveMode the mode actually in force after the Iron's-presence fallback
 * @param nativeArs     the adapter servicing native Ars operations, never {@code null}
 * @param nativeIrons   the adapter servicing native Iron's operations, {@code null} when
 *                      Iron's is absent
 */
public record BridgeRouting(
    ModeRoutingSnapshot snapshot,
    ManaUnificationMode requestedMode,
    ManaUnificationMode effectiveMode,
    IManaBridge nativeArs,
    IManaBridge nativeIrons
) {
    /** Stable adapter identities carried on the snapshot; they match the contract fixtures. */
    public static final String ARS_ADAPTER_ID = "ans:ars_native";
    public static final String IRONS_ADAPTER_ID = "ans:irons_native";

    /**
     * Build the one snapshot for {@code requested}.
     *
     * <p>The Iron's-absent fallback is applied here and nowhere else. Note that the fallback
     * target differs by mode exactly as it did before: ISS_PRIMARY, HYBRID and SEPARATE all
     * need Iron's, so without it they degrade to ARS_PRIMARY, whose effective routing has no
     * Iron's leg at all. {@link ModeRoutingSnapshot}'s own compact constructor then forces the
     * effective mode to {@code disabled} and drops the Iron's adapter id, so no route can name
     * a pool that does not exist.
     */
    public static BridgeRouting build(ManaUnificationMode requested, boolean ironsLoaded,
                                      IManaBridge arsBridge, IManaBridge ironsBridge,
                                      int generation) {
        ManaUnificationMode effective = requested;
        if (!ironsLoaded && requested != ManaUnificationMode.DISABLED
            && requested != ManaUnificationMode.ARS_PRIMARY) {
            effective = ManaUnificationMode.ARS_PRIMARY;
        }
        ModeRoutingSnapshot snapshot = new ModeRoutingSnapshot(
            requested.getConfigName(),
            effective.getConfigName(),
            ironsLoaded,
            authoritativeUnitOf(effective),
            ARS_ADAPTER_ID,
            ironsLoaded ? Optional.of(IRONS_ADAPTER_ID) : Optional.empty(),
            generation);
        // nativeIrons regardless of mode: see the class javadoc.
        return new BridgeRouting(snapshot, requested, effective, arsBridge,
            ironsLoaded ? ironsBridge : null);
    }

    /**
     * Which pool a unified mode treats as authoritative. ISS_PRIMARY and HYBRID both bill
     * Iron's; ARS_PRIMARY, SEPARATE and DISABLED bill Ars.
     */
    private static ResourceUnit authoritativeUnitOf(ManaUnificationMode mode) {
        return (mode == ManaUnificationMode.ISS_PRIMARY || mode == ManaUnificationMode.HYBRID)
            ? ResourceUnit.IRONS_MANA
            : ResourceUnit.ARS_MANA;
    }

    /** Whether Iron's Spellbooks is loaded, i.e. whether {@link #nativeIrons()} is non-null. */
    public boolean ironsPresent() {
        return snapshot.ironsPresent();
    }

    /**
     * The adapter for the pool this mode treats as authoritative.
     *
     * <p>This is the old {@code activeBridge}, derived rather than stored, so it can never
     * disagree with the mode it was selected for.
     */
    public IManaBridge authoritative() {
        if (snapshot.authoritativeUnit() == ResourceUnit.IRONS_MANA && nativeIrons != null) {
            return nativeIrons;
        }
        return nativeArs;
    }

    /** The adapter servicing {@code unit}, or {@code null} when that pool does not exist. */
    public IManaBridge adapterFor(ResourceUnit unit) {
        return unit == ResourceUnit.IRONS_MANA ? nativeIrons : nativeArs;
    }
}
