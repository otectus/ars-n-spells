package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.ModeRoutingSnapshot;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * BridgeManager - Central hub for mana system integration
 *
 * <p>Manages the active mana bridge based on configuration and available mods.
 * Supports 5 different mana unification modes.
 *
 * <p><b>V04.</b> The mode, the Iron's-presence flag and the two adapters used to be three
 * separately-volatile fields. A refresh landing between two reads produced a cast that
 * validated against one pool and charged another, and DISABLED nulled the Iron's adapter
 * outright, so turning unification off did not restore native Iron's routing - it routed
 * Iron's spells at the Ars pool. All of it now lives in one immutable {@code Routing},
 * built in {@code buildRouting} and published by a single reference assignment. Readers
 * take one reference and answer every question from it.
 *
 * <p>Origin is told, never inferred. {@link #consumeManaForMode} and {@link #getManaForMode}
 * take the {@link ResourceUnit} the operation originates in, so no caller can decide "this
 * must be Iron's, it came through the secondary bridge" - a claim the old code made and the
 * mode switch quietly invalidated.
 */
public class BridgeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeManager.class);

    /** Adapter names carried by {@link ModeRoutingSnapshot}; matched by the shared fixtures. */
    public static final String ARS_ADAPTER_ID = "ans:ars_native";
    public static final String IRONS_ADAPTER_ID = "ans:irons_native";

    /** ANS-OPT-016: cached fallback so the null path does not allocate per call. */
    private static final IManaBridge FALLBACK_BRIDGE = new ArsNativeBridge();

    /**
     * One routing decision, published atomically.
     *
     * <p>{@code nativeIrons} is populated whenever Iron's is loaded, <em>whatever the mode</em>.
     * That is the DISABLED half of V04: "no integration" means Iron's pays for its own spells
     * out of its own pool, not that the Iron's adapter disappears. The absent-Iron's half is
     * enforced by {@link ModeRoutingSnapshot} itself, which forces the effective mode to
     * disabled and drops the Iron's adapter id, so SEPARATE-without-Iron's cannot leave a
     * stale secondary behind.
     */
    private static final class Routing {
        final ModeRoutingSnapshot snapshot;
        final ManaUnificationMode mode;
        final IManaBridge nativeArs;
        final IManaBridge nativeIrons;

        Routing(ModeRoutingSnapshot snapshot, ManaUnificationMode mode,
                IManaBridge nativeArs, IManaBridge nativeIrons) {
            this.snapshot = snapshot;
            this.mode = mode;
            this.nativeArs = nativeArs;
            this.nativeIrons = nativeIrons;
        }

        /**
         * The adapter that services operations denominated in {@code unit}.
         *
         * <p>An Iron's-denominated operation with Iron's absent cannot happen - there are no
         * Iron's spells to cast - but the Ars adapter is returned rather than {@code null} so
         * a defensive caller degrades to the only pool that exists instead of throwing.
         */
        IManaBridge adapterFor(ResourceUnit unit) {
            if (unit == ResourceUnit.IRONS_MANA && nativeIrons != null) {
                return nativeIrons;
            }
            return nativeArs;
        }
    }

    /**
     * The one volatile. Every read of mode, presence or adapter goes through a single
     * reference load, so a refresh can never be observed half-applied.
     */
    private static volatile Routing routing = null;

    private static boolean isIronsLoaded = false;

    /** Monotonic, stamped onto each snapshot so a stale quote can be detected (see CostRules). */
    private static int generation = 0;

    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Check if Iron's Spellbooks is loaded
            isIronsLoaded = ModList.get().isLoaded("irons_spellbooks");

            // ANS-HIGH-029: the config is SERVER-type and is NOT loaded yet at
            // common setup - in production a read returns defaults; in dev Forge
            // throws IllegalStateException. Treat init as provisional: fall back
            // to DISABLED (adapters always present, never null) and rely on the
            // ModConfigEvent Loading/Reloading listeners in ArsNSpells to call
            // refreshMode() once real values exist. logInitialization() also
            // reads config values, so it is skipped until then.
            boolean configReadable = true;
            ManaUnificationMode requested;
            try {
                requested = AnsConfig.getManaMode();
            } catch (Exception e) {
                configReadable = false;
                LOGGER.info("Config not loaded at common setup (expected for SERVER configs); "
                    + "starting in DISABLED mode until config load refreshes it");
                requested = ManaUnificationMode.DISABLED;
            }

            publish(buildRouting(requested, isIronsLoaded));

            // Log initialization (only when real config values were available;
            // refreshMode() logs the definitive state at config load).
            if (configReadable) {
                logInitialization();
            }
        });
    }

    /**
     * Re-read the configured mana mode and rebuild the routing snapshot at runtime.
     *
     * <p>ANS 2.0.1: lets {@code /ans mode set} and the in-game config screen apply a
     * mode change live instead of requiring a restart. Adapters are stateless, so
     * rebuilding them is safe. {@code synchronized} serialises concurrent refreshes;
     * the single volatile publish gives readers a consistent snapshot rather than a
     * mixture of old and new fields. Callers must persist the config value
     * ({@code AnsConfig.MANA_UNIFICATION_MODE.set} + {@code safeSave}) first, and invoke
     * this on the server thread (command handlers already are; the config screen marshals
     * via the integrated server executor).
     */
    public static synchronized void refreshMode() {
        publish(buildRouting(AnsConfig.getManaMode(), isIronsLoaded));
        logInitialization();
    }

    /**
     * Test-only seam: rebuild the routing for {@code mode} without touching mod APIs.
     *
     * <p>{@link #refreshMode()} reads the config and {@link ModList}, neither of which
     * exists on the unit-test classpath. This keeps the current adapters and swaps only
     * the mode, which is enough to drive every mode-read branch.
     */
    static void testSetMode(ManaUnificationMode mode) {
        Routing current = routing;
        publish(buildRouting(mode, isIronsLoaded,
            current != null ? current.nativeArs : FALLBACK_BRIDGE,
            current != null ? current.nativeIrons : null));
    }

    /**
     * Test-only seam: publish a routing with explicit adapters and Iron's-presence.
     *
     * <p>Exists so the V04 regression test can assert <em>which</em> pool a spend lands in
     * without a running Iron's Spellbooks. Constructing a real {@link IronsBridge} in a unit
     * test would route every call into a {@code NoClassDefFoundError} its catch-all swallows,
     * which is indistinguishable from "routed to the wrong pool".
     */
    static void testSetRouting(ManaUnificationMode mode, boolean ironsPresent,
                               IManaBridge nativeArs, IManaBridge nativeIrons) {
        isIronsLoaded = ironsPresent;
        publish(buildRouting(mode, ironsPresent, nativeArs, ironsPresent ? nativeIrons : null));
    }

    private static synchronized void publish(Routing next) {
        routing = next;
    }

    /** Build a routing with freshly constructed native adapters. */
    private static Routing buildRouting(ManaUnificationMode requested, boolean ironsPresent) {
        return buildRouting(requested, ironsPresent,
            new ArsNativeBridge(),
            // Populated whenever Iron's is loaded, regardless of mode - see Routing.
            ironsPresent ? new IronsBridge() : null);
    }

    /**
     * The single place a routing is derived. Everything downstream reads the result; nothing
     * downstream re-derives "which pool is authoritative?" from the mode enum.
     */
    private static synchronized Routing buildRouting(ManaUnificationMode requested,
                                                     boolean ironsPresent,
                                                     IManaBridge nativeArs,
                                                     IManaBridge nativeIrons) {
        ManaUnificationMode mode = requested != null ? requested : ManaUnificationMode.DISABLED;
        if (!ironsPresent && mode.isUnificationEnabled()) {
            // Every unified mode needs a second pool. Without one there is nothing to unify,
            // so the effective mode is disabled - which is also the invariant
            // ModeRoutingSnapshot enforces, and the reason it cannot expose a stale secondary.
            LOGGER.warn("{} mode selected but Iron's Spellbooks is not present. "
                + "Running with mana unification disabled.", mode.getConfigName());
        }

        ResourceUnit authoritative = (mode == ManaUnificationMode.ISS_PRIMARY
            || mode == ManaUnificationMode.HYBRID)
            ? ResourceUnit.IRONS_MANA
            : ResourceUnit.ARS_MANA;

        ModeRoutingSnapshot snapshot = new ModeRoutingSnapshot(
            mode.getConfigName(),
            mode.getConfigName(),
            ironsPresent,
            authoritative,
            ARS_ADAPTER_ID,
            ironsPresent ? Optional.of(IRONS_ADAPTER_ID) : Optional.empty(),
            ++generation);

        // The snapshot is authoritative about the effective mode, so read it back rather
        // than keeping a second opinion in an enum field alongside it.
        ManaUnificationMode effective = ManaUnificationMode.fromString(snapshot.effectiveMode());
        return new Routing(snapshot, effective,
            nativeArs != null ? nativeArs : FALLBACK_BRIDGE,
            ironsPresent ? nativeIrons : null);
    }

    /** The live routing, never {@code null}: an un-inited manager routes everything at Ars. */
    private static Routing routing() {
        Routing current = routing;
        if (current != null) {
            return current;
        }
        // ANS-MED-018: before init runs (e.g. early client render frames during world load)
        // there is still a well-formed answer - no Iron's, no unification, Ars native.
        return buildRouting(ManaUnificationMode.DISABLED, false, FALLBACK_BRIDGE, null);
    }

    /** The immutable routing decision every mode question is answered from. */
    public static ModeRoutingSnapshot getRoutingSnapshot() {
        return routing().snapshot;
    }

    /**
     * Log initialization details
     */
    private static void logInitialization() {
        Routing current = routing();
        LOGGER.info("========================================");
        LOGGER.info("Ars 'n' Spells - Mana Bridge Initialization");
        LOGGER.info("========================================");
        LOGGER.info("Iron's Spellbooks Detected: {}", current.snapshot.ironsPresent());
        LOGGER.info("Mana Unification Mode: {}", current.snapshot.requestedMode());
        LOGGER.info("Effective Mode: {}", current.snapshot.effectiveMode());
        LOGGER.info("Mode Description: {}", current.mode.getDescription());
        LOGGER.info("Authoritative Pool: {}", current.snapshot.authoritativeUnit());
        LOGGER.info("Ars Adapter: {}", current.nativeArs.getBridgeType());
        if (current.nativeIrons != null) {
            LOGGER.info("Iron's Adapter: {}", current.nativeIrons.getBridgeType());
        }
        LOGGER.info("Mana Unification Enabled: {}", current.snapshot.isUnified());
        LOGGER.info("NOTE: mana_unification_mode can be changed live via '/ans mode set' or the in-game config screen (applied by refreshMode()).");
        if (current.snapshot.isDualCost()) {
            double arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get();
            double issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
            double total = arsPercent + issPercent;
            if (Math.abs(total - 1.0) > 0.01) {
                LOGGER.warn("Dual-cost percentages sum to {} (Ars: {}, ISS: {}) - expected 1.0", total, arsPercent, issPercent);
            }
        }
        LOGGER.info("========================================");
    }

    /**
     * Get the authoritative mana bridge - the one a unified mode bills.
     *
     * <p>Derived from the snapshot's authoritative unit, not from an "active slot": which
     * adapter sits where is no longer a thing a caller can observe.
     */
    public static IManaBridge getBridge() {
        Routing current = routing();
        return current.adapterFor(current.snapshot.authoritativeUnit());
    }

    /**
     * The Iron's adapter, or {@code null} when Iron's Spellbooks is absent.
     *
     * <p>Named for its system rather than its position. This is non-null in DISABLED mode
     * with Iron's installed, which is the whole point of V04: an Iron's spell still gets
     * billed to the Iron's pool when integration is off.
     */
    public static IManaBridge getNativeIronsBridge() {
        return routing().nativeIrons;
    }

    /** The Ars adapter. Always present. */
    public static IManaBridge getNativeArsBridge() {
        return routing().nativeArs;
    }

    /**
     * Get the current mana unification mode - a thin read of the routing snapshot.
     */
    public static ManaUnificationMode getCurrentMode() {
        return routing().mode;
    }

    /**
     * Check if Iron's Spellbooks is loaded
     */
    public static boolean isIronsSpellbooksLoaded() {
        return routing().snapshot.ironsPresent();
    }

    /**
     * Check if mana unification is enabled - a thin read of the routing snapshot.
     *
     * <p><b>Precedence source of truth</b> (audit F5): the master toggle
     * {@code enable_mana_unification} wins over {@code mana_unification_mode}. That rule
     * lives in {@link AnsConfig#getManaMode()}, which returns DISABLED when the toggle is
     * off, so it is already folded into the snapshot by the time anyone reads it. This
     * method no longer re-reads the live config - doing so was one of the ways the mode
     * could change between a cast's validation and its charge.
     */
    public static boolean isUnificationEnabled() {
        return routing().snapshot.isUnified();
    }

    /**
     * Check if we're using a shared mana pool
     */
    public static boolean usesSharedPool() {
        Routing current = routing();
        return current.snapshot.isUnified() && current.mode.usesSharedPool();
    }

    /**
     * Whether Iron's owns the shared pool, so Ars's native regeneration tick is redundant.
     *
     * <p>True in ISS_PRIMARY and HYBRID and nowhere else. Deliberately not
     * {@link #usesSharedPool()}: ARS_PRIMARY also shares a pool, but that pool <em>is</em>
     * the Ars one, so its regen tick is the only thing filling it and suppressing it would
     * stop regeneration outright. Read by {@code MixinManaRegenTick}.
     */
    public static boolean ironsOwnsSharedPool() {
        ModeRoutingSnapshot snapshot = routing().snapshot;
        return snapshot.isUnified() && snapshot.authoritativeUnit() == ResourceUnit.IRONS_MANA;
    }

    /**
     * Check if we're using dual-cost mechanics
     */
    public static boolean usesDualCost() {
        return routing().snapshot.isDualCost();
    }

    /**
     * The pool that actually pays for an amount denominated in {@code origin}.
     *
     * <p>Answered by {@link ModeRoutingSnapshot}'s named routes, never by adapter position:
     * a shared mode bills its authoritative pool, and every other mode bills the origin
     * system's own pool - including DISABLED, where Iron's pays for Iron's.
     */
    private static ResourceUnit payingUnit(ModeRoutingSnapshot snapshot, ResourceUnit origin) {
        if (origin == ResourceUnit.IRONS_MANA) {
            return snapshot.ironsPresent()
                ? snapshot.routeNativeIronsSpend().payingUnit()
                : ResourceUnit.ARS_MANA;
        }
        if (origin == ResourceUnit.ARS_MANA) {
            return snapshot.routeNativeArsSpend().payingUnit();
        }
        // LP and aura are not part of the Ars / Iron's exchange; they are paid natively by
        // their own handlers and never reach a mana adapter.
        return origin;
    }

    /**
     * Read the pool an operation originating in {@code origin} is measured against.
     *
     * @param origin the unit the operation is denominated in, told by the caller
     */
    public static float getManaForMode(net.minecraft.world.entity.player.Player player,
                                       ResourceUnit origin) {
        Routing current = routing();
        return current.adapterFor(payingUnit(current.snapshot, origin)).getMana(player);
    }

    /**
     * Spend one leg of {@code amount}, denominated in {@code origin}.
     *
     * @param origin the unit the operation is denominated in. Told, not inferred: this is
     *               the argument that stops an Ars spend being billed as an Iron's spend
     *               because the mode swapped which adapter used to sit in the active slot.
     */
    public static boolean consumeManaForMode(net.minecraft.world.entity.player.Player player,
                                             float amount, ResourceUnit origin) {
        Routing current = routing();
        return current.adapterFor(payingUnit(current.snapshot, origin)).consumeMana(player, amount);
    }

    /** The ceiling of the pool that pays for an amount denominated in {@code origin}. */
    public static float getMaxManaForMode(net.minecraft.world.entity.player.Player player,
                                          ResourceUnit origin) {
        Routing current = routing();
        return current.adapterFor(payingUnit(current.snapshot, origin)).getMaxMana(player);
    }

    /**
     * Return {@code amount} to the pool that would have paid for {@code origin}.
     *
     * <p>The refund side of {@link #consumeManaForMode}: it must land in the same pool the
     * spend came out of, or a released reservation credits a pool that was never debited.
     */
    public static void addManaForMode(net.minecraft.world.entity.player.Player player,
                                      float amount, ResourceUnit origin) {
        Routing current = routing();
        current.adapterFor(payingUnit(current.snapshot, origin)).addMana(player, amount);
    }

    /** Whether every leg of {@code quote} is affordable from the pool that would pay it. */
    public static boolean canAffordQuote(net.minecraft.world.entity.player.Player player,
                                         com.otectus.arsnspells.contract.CostQuote quote) {
        Routing current = routing();
        for (com.otectus.arsnspells.contract.ResourceAmount leg : quote.legs()) {
            if (leg.isZero()) {
                continue;
            }
            IManaBridge bridge = current.adapterFor(payingUnit(current.snapshot, leg.unit()));
            if (bridge.getMana(player) < (float) leg.amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pay every leg of {@code quote}, all or nothing.
     *
     * <p>This replaces the old dual-cost branch that re-derived the split from the live
     * config while charging. The split now arrives already decided, normalized once by
     * {@link com.otectus.arsnspells.contract.CostRules}, so the amount charged is the amount
     * the check validated.
     *
     * <p>ANS-CRIT-003: a leg that fails after an earlier leg was taken is rolled back with
     * {@code addMana}, the backing API's atomic add, rather than a snapshot-and-restore that
     * would clobber any concurrent regen landing in between.
     */
    public static boolean consumeQuote(net.minecraft.world.entity.player.Player player,
                                       com.otectus.arsnspells.contract.CostQuote quote) {
        Routing current = routing();
        if (!canAffordQuote(player, quote)) {
            return false;
        }
        java.util.List<com.otectus.arsnspells.contract.ResourceAmount> paid = new java.util.ArrayList<>();
        for (com.otectus.arsnspells.contract.ResourceAmount leg : quote.legs()) {
            if (leg.isZero()) {
                continue;
            }
            IManaBridge bridge = current.adapterFor(payingUnit(current.snapshot, leg.unit()));
            if (!bridge.consumeMana(player, (float) leg.amount())) {
                for (com.otectus.arsnspells.contract.ResourceAmount done : paid) {
                    current.adapterFor(payingUnit(current.snapshot, done.unit()))
                        .addMana(player, (float) done.amount());
                }
                return false;
            }
            paid.add(leg);
        }
        return true;
    }
}
