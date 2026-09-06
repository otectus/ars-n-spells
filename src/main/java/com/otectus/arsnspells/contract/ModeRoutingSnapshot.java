package com.otectus.arsnspells.contract;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One immutable answer to "what is the mana mode, is Iron's here, and which pool is
 * authoritative?" (audit V04).
 *
 * <p>Closes the finding that those three facts lived in three separately-volatile fields on the
 * bridge manager and were re-read at different instants during a single cast. A mode switch or
 * a mod-presence re-check landing between two reads produced a cast that validated against one
 * pool and charged another, and the "secondary" bridge could still name Iron's mana after
 * Iron's had been found absent. One object, read once, replaces all three.
 *
 * <p>The adapters are named, not positional. The old code inferred a cast's origin from whether
 * the bridge it came through was the "active" or the "secondary" one, so a native Ars spend
 * could be billed as an Iron's spend simply because the mode had swapped which bridge sat in
 * the active slot. {@link #routeNativeArsSpend()} and {@link #routeNativeIronsSpend()} name
 * their system.
 *
 * <p>The compact constructor enforces the absent-Iron's invariant: with {@code ironsPresent}
 * false there is no effective unified mode and no Iron's adapter, so no route can name a pool
 * that does not exist. That is what stops SEPARATE-without-Iron's from exposing a stale
 * secondary.
 *
 * @param requestedMode       the mode as configured, lower-cased
 * @param effectiveMode       the mode actually in force, lower-cased; forced to
 *                            {@code "disabled"} when Iron's is absent
 * @param ironsPresent        whether Iron's Spellbooks is loaded
 * @param authoritativeUnit   the pool that unified modes bill; forced to {@link
 *                            ResourceUnit#ARS_MANA} when Iron's is absent
 * @param nativeArsAdapterId  the named adapter that services native Ars operations
 * @param nativeIronsAdapterId the named adapter that services native Iron's operations, empty
 *                            when Iron's is absent
 * @param generation          monotonic snapshot generation, matching {@link CostRules#generation()}
 */
public record ModeRoutingSnapshot(
    String requestedMode,
    String effectiveMode,
    boolean ironsPresent,
    ResourceUnit authoritativeUnit,
    String nativeArsAdapterId,
    Optional<String> nativeIronsAdapterId,
    int generation
) {
    /** The effective mode string that means "no integration at all". */
    public static final String DISABLED = "disabled";

    /** The effective mode string that means "both pools pay a share". */
    public static final String SEPARATE = "separate";

    public ModeRoutingSnapshot {
        Objects.requireNonNull(requestedMode, "requestedMode");
        Objects.requireNonNull(effectiveMode, "effectiveMode");
        Objects.requireNonNull(authoritativeUnit, "authoritativeUnit");
        Objects.requireNonNull(nativeArsAdapterId, "nativeArsAdapterId");
        Objects.requireNonNull(nativeIronsAdapterId, "nativeIronsAdapterId");
        requestedMode = requestedMode.trim().toLowerCase(Locale.ROOT);
        effectiveMode = effectiveMode.trim().toLowerCase(Locale.ROOT);
        if (nativeArsAdapterId.isEmpty()) {
            throw new IllegalArgumentException("nativeArsAdapterId must be named");
        }
        if (!ironsPresent) {
            effectiveMode = DISABLED;
            authoritativeUnit = ResourceUnit.ARS_MANA;
            nativeIronsAdapterId = Optional.empty();
        }
    }

    /** Whether the effective mode integrates the two systems at all. */
    public boolean isUnified() {
        return !DISABLED.equals(effectiveMode);
    }

    /** Whether the effective mode splits a cross-system cast across both pools. */
    public boolean isDualCost() {
        return SEPARATE.equals(effectiveMode);
    }

    /**
     * Where an Ars Nouveau spell that Ars itself is casting gets billed.
     *
     * <p>Never inferred from an active-versus-secondary bridge position: this method is named
     * for the Ars system and answers only for it.
     */
    public BillingRoute routeNativeArsSpend() {
        if (!isUnified() || isDualCost()) {
            return BillingRoute.nativeOwned(ResourceUnit.ARS_MANA);
        }
        if (authoritativeUnit == ResourceUnit.ARS_MANA) {
            return BillingRoute.nativeOwned(ResourceUnit.ARS_MANA);
        }
        return new BillingRoute(authoritativeUnit, false, BillingRoute.Direction.ARS_TO_IRONS);
    }

    /**
     * Where an Iron's Spellbooks spell that Iron's itself is casting gets billed.
     *
     * <p>With Iron's loaded and the mode disabled this still answers {@link
     * ResourceUnit#IRONS_MANA}: "no integration" means Iron's pays for its own spells out of its
     * own pool, not that Iron's stops being billed.
     */
    public BillingRoute routeNativeIronsSpend() {
        if (!ironsPresent) {
            throw new IllegalStateException(
                "No native Iron's route exists: Iron's Spellbooks is not present");
        }
        if (!isUnified() || isDualCost()) {
            return BillingRoute.nativeOwned(ResourceUnit.IRONS_MANA);
        }
        if (authoritativeUnit == ResourceUnit.IRONS_MANA) {
            return BillingRoute.nativeOwned(ResourceUnit.IRONS_MANA);
        }
        return new BillingRoute(authoritativeUnit, false, BillingRoute.Direction.IRONS_TO_ARS);
    }

    /**
     * Where a cross-cast gets billed: a spell of one system cast from the other system's
     * carrier. ANS always owns that charge, so the native system never also debits.
     */
    public BillingRoute routeCrossCast() {
        if (!isUnified()) {
            return BillingRoute.nativeOwned(authoritativeUnit);
        }
        if (isDualCost()) {
            return new BillingRoute(authoritativeUnit, false, BillingRoute.Direction.NONE);
        }
        BillingRoute.Direction direction = authoritativeUnit == ResourceUnit.IRONS_MANA
            ? BillingRoute.Direction.ARS_TO_IRONS
            : BillingRoute.Direction.IRONS_TO_ARS;
        return new BillingRoute(authoritativeUnit, false, direction);
    }
}
