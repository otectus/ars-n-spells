package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BridgeManager - central hub for mana system integration. Resolves the
 * active bridge based on configuration and the presence of Iron's
 * Spellbooks at common-setup time.
 */
public class BridgeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeManager.class);

    /**
     * The one routing snapshot (audit V04).
     *
     * <p>This single volatile replaces the former {@code activeBridge} / {@code secondaryBridge}
     * / {@code currentMode} trio. Three separately-volatile fields could be read at three
     * different instants during one cast, so a {@code /ans mode set} or a config reload landing
     * between two reads produced a cast that validated against one pool and charged another.
     * A {@link BridgeRouting} is built in exactly one place and published by a single reference
     * assignment, so every reader sees a self-consistent answer or the previous one - never a
     * mixture.
     */
    private static volatile BridgeRouting routing;

    /** Monotonic generation, stamped onto each snapshot and onto the cost rules of a cast. */
    private static final java.util.concurrent.atomic.AtomicInteger GENERATION =
        new java.util.concurrent.atomic.AtomicInteger();

    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                publish(AnsConfig.getManaMode());
                logInitialization();
            } catch (Throwable t) {
                // The SERVER config is not loaded yet at common setup. refreshMode()
                // runs again from ModConfigEvent.Loading once the config is available,
                // and getCurrentMode() falls back to a live config read in the meantime.
                LOGGER.debug("Bridge init deferred until config load: {}", t.toString());
            }
        });
    }

    /**
     * Re-read the configured mana mode and rebuild the routing snapshot.
     *
     * <p>Runs on config load/reload (the SERVER config loads after common setup, so
     * this is the real init point on a server) and from {@code /ans mode set}. Bridges
     * are stateless, so rebuilding is safe.
     */
    public static synchronized void refreshMode() {
        publish(AnsConfig.getManaMode());
        logInitialization();
    }

    /**
     * Test-only seam: swap the routing snapshot without constructing real bridges (bridge
     * construction touches mod APIs absent from the unit-test classpath).
     */
    static void testSetRouting(BridgeRouting replacement) {
        routing = replacement;
    }

    /**
     * Test-only seam: set the cached mode without constructing real bridges. Retained at its
     * old name and shape so existing callers keep working; it now publishes a whole snapshot,
     * because a mode with no snapshot behind it is exactly the split state V04 removed.
     *
     * <p>Iron's is reported present so the mode survives the absent-Iron's fallback and the
     * seam still means "set this mode". Both named adapters are the fallback bridge: the seam
     * exists for mode readers, and constructing an {@link IronsBridge} would drag Iron's onto
     * the unit-test classpath.
     */
    static void testSetMode(ManaUnificationMode mode) {
        routing = BridgeRouting.build(mode, true, FALLBACK_BRIDGE, FALLBACK_BRIDGE,
            GENERATION.incrementAndGet());
    }

    /** The live routing snapshot, or {@code null} before the first publish. */
    public static BridgeRouting routing() {
        return routing;
    }

    /**
     * The generation of the live snapshot, stamped onto the cost rules of every cast so a quote
     * held across a mode switch can be recognised as stale rather than charged.
     */
    public static int routingGeneration() {
        BridgeRouting current = routing;
        return current != null ? current.snapshot().generation() : 0;
    }

    /**
     * Build and publish the routing snapshot for {@code requested}.
     *
     * <p>The Iron's adapter is constructed whenever Iron's is loaded, whatever the mode. The
     * previous switch left {@code secondaryBridge} holding a stale Iron's bridge when SEPARATE
     * fell back without Iron's, and nulled the Iron's adapter outright in DISABLED - so a
     * native Iron's spend in DISABLED fell through to the Ars pool. Both are gone with the
     * per-mode branches that produced them.
     */
    private static void publish(ManaUnificationMode requested) {
        boolean ironsLoaded = ModList.get().isLoaded("irons_spellbooks");
        ManaUnificationMode mode = requested != null ? requested : ManaUnificationMode.DISABLED;
        BridgeRouting next = BridgeRouting.build(mode, ironsLoaded,
            new ArsNativeBridge(), ironsLoaded ? new IronsBridge() : null,
            GENERATION.incrementAndGet());
        if (next.effectiveMode() != next.requestedMode()) {
            LOGGER.warn("{} mode selected but Iron's Spellbooks not found. Falling back to {}.",
                next.requestedMode().getConfigName(), next.effectiveMode().getConfigName());
        }
        routing = next;
    }

    private static void logInitialization() {
        BridgeRouting current = routing;
        if (current == null) {
            return;
        }
        LOGGER.info("========================================");
        LOGGER.info("Ars 'n' Spells - Mana Bridge Initialization");
        LOGGER.info("========================================");
        LOGGER.info("Iron's Spellbooks Detected: {}", current.ironsPresent());
        LOGGER.info("Mana Unification Mode: {}", current.effectiveMode().getConfigName());
        LOGGER.info("Mode Description: {}", current.effectiveMode().getDescription());
        LOGGER.info("Authoritative Pool: {}", current.snapshot().authoritativeUnit());
        LOGGER.info("Native Ars Adapter: {}", current.nativeArs().getBridgeType());
        if (current.nativeIrons() != null) {
            LOGGER.info("Native Iron's Adapter: {}", current.nativeIrons().getBridgeType());
        }
        LOGGER.info("Mana Unification Enabled: {}", AnsConfig.ENABLE_MANA_UNIFICATION.get());
        // This used to claim a restart was required. It is not: refreshMode() runs from the
        // mod's ModConfigEvent.Loading/Reloading listeners, from `/ans mode set`, and from the
        // config screen, and this banner is printed by refreshMode itself - so anyone reading
        // the line had already just changed the mode without restarting.
        LOGGER.info("NOTE: mana_unification_mode applies immediately - no restart needed.");
        if (current.effectiveMode() == ManaUnificationMode.SEPARATE) {
            double arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get();
            double issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
            double total = arsPercent + issPercent;
            if (Math.abs(total - 1.0) > 0.01) {
                LOGGER.warn("Dual-cost percentages sum to {} (Ars: {}, ISS: {}) - expected 1.0", total, arsPercent, issPercent);
            }
        }
        LOGGER.info("========================================");
    }

    /** Cached fallback so the pre-init null path does not allocate per call. */
    private static final IManaBridge FALLBACK_BRIDGE = new ArsNativeBridge();

    /** The adapter for the pool the current mode treats as authoritative. */
    public static IManaBridge getBridge() {
        BridgeRouting current = routing;
        return current != null ? current.authoritative() : FALLBACK_BRIDGE;
    }

    /**
     * The named native-Ars adapter. Prefer this over {@link #getBridge()} wherever the caller
     * means "Ars", not "whichever pool is authoritative" - inferring a spend's system from the
     * bridge's slot is the V04 defect.
     */
    public static IManaBridge getNativeArsBridge() {
        BridgeRouting current = routing;
        return current != null ? current.nativeArs() : FALLBACK_BRIDGE;
    }

    /** The named native-Iron's adapter, or {@code null} when Iron's is not loaded. */
    public static IManaBridge getNativeIronsBridge() {
        BridgeRouting current = routing;
        return current != null ? current.nativeIrons() : null;
    }

    /**
     * The non-authoritative adapter.
     *
     * <p>Kept for callers that genuinely mean "the other pool", but it is now derived from the
     * snapshot rather than stored, so it can no longer name Iron's after Iron's has been found
     * absent, nor go missing in DISABLED while Iron's is loaded.
     */
    public static IManaBridge getSecondaryBridge() {
        BridgeRouting current = routing;
        if (current == null) {
            return null;
        }
        IManaBridge authoritative = current.authoritative();
        return authoritative == current.nativeArs() ? current.nativeIrons() : current.nativeArs();
    }

    public static ManaUnificationMode getCurrentMode() {
        BridgeRouting current = routing;
        if (current != null) {
            return current.effectiveMode();
        }
        // Pre-init (early client render frames during world load, or before the SERVER
        // config has loaded) — read live so callers never see a null mode.
        try {
            return AnsConfig.getManaMode();
        } catch (Throwable t) {
            return ManaUnificationMode.DISABLED;
        }
    }

    public static boolean isIronsSpellbooksLoaded() {
        BridgeRouting current = routing;
        return current != null && current.ironsPresent();
    }

    public static boolean isUnificationEnabled() {
        // Guarded like its sibling getCurrentMode() above. This is the mod's most-called
        // config read - every ManaCap read/write, every Ars cast, every Iron's mana access and
        // two player-tick handlers reach it - and nearly all of those callers read the mode
        // straight afterwards. Leaving the defensive one behind the undefended one meant the
        // SERVER config not being loaded (world transitions, early client render frames) threw
        // from here before the guard downstream could help.
        boolean enabled;
        try {
            enabled = AnsConfig.ENABLE_MANA_UNIFICATION.get();
        } catch (Throwable configNotReady) {
            return false;
        }
        if (!enabled) {
            return false;
        }
        BridgeRouting current = routing;
        if (current != null) {
            return current.snapshot().isUnified();
        }
        ManaUnificationMode mode = getCurrentMode();
        return mode != null && mode.isUnificationEnabled();
    }

    public static boolean usesSharedPool() {
        ManaUnificationMode mode = getCurrentMode();
        return isUnificationEnabled() && mode != null && mode.usesSharedPool();
    }

    public static boolean usesDualCost() {
        ManaUnificationMode mode = getCurrentMode();
        return isUnificationEnabled() && mode != null && mode.requiresDualCost();
    }

    /**
     * The balance of the pool that services spells originating in {@code origin}.
     *
     * <p>Takes the origin unit explicitly. The old {@code boolean fromArs} said which system
     * asked, and the answer was then looked up by bridge <em>slot</em> rather than by name.
     */
    public static float getManaForMode(net.minecraft.world.entity.player.Player player,
                                       ResourceUnit origin) {
        BridgeRouting current = routing;
        if (current == null) {
            return FALLBACK_BRIDGE.getMana(player);
        }
        if (!isUnificationEnabled() || current.snapshot().isDualCost()) {
            // No unification, or separate pools: each system reads its own pool. With Iron's
            // loaded that pool exists in DISABLED too, which the nulled secondary used to hide.
            IManaBridge own = current.adapterFor(origin);
            return own != null ? own.getMana(player) : current.nativeArs().getMana(player);
        }
        return current.authoritative().getMana(player);
    }

    /** Source-compatible overload for callers that still speak in "is this Ars?" terms. */
    public static float getManaForMode(net.minecraft.world.entity.player.Player player,
                                       boolean fromArs) {
        return getManaForMode(player, fromArs ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA);
    }

    /**
     * Spend {@code amount} for an operation that originated in {@code origin}.
     *
     * <p>{@code origin} is passed in, never inferred. The previous signature took
     * {@code boolean fromArs} and then resolved the pool through the active/secondary slot, so
     * a native Iron's spend could be billed against the Ars pool simply because the mode had
     * decided which bridge occupied which slot - which is exactly what DISABLED did, having
     * nulled the Iron's adapter outright.
     */
    public static boolean consumeManaForMode(net.minecraft.world.entity.player.Player player,
                                             float amount, ResourceUnit origin) {
        BridgeRouting current = routing;
        if (current == null) {
            return FALLBACK_BRIDGE.consumeMana(player, amount);
        }
        if (!isUnificationEnabled()) {
            IManaBridge own = current.adapterFor(origin);
            return own != null ? own.consumeMana(player, amount)
                : current.nativeArs().consumeMana(player, amount);
        }
        if (!current.snapshot().isDualCost()) {
            return current.authoritative().consumeMana(player, amount);
        }

        // SEPARATE. ANS-MED-005: the split is normalised so the two halves always sum to the
        // base cost, whatever the two independently-validated config keys add up to.
        double[] split = AnsConfig.dualCostSplit();
        float arsCost = (float) (amount * split[0]);
        float issCost = (float) (amount * split[1]);
        IManaBridge arsBridge = current.nativeArs();
        IManaBridge issBridge = current.nativeIrons();

        if (issBridge == null) {
            return arsBridge.consumeMana(player, arsCost);
        }

        boolean arsHas = arsBridge.getMana(player) >= arsCost;
        boolean issHas = issBridge.getMana(player) >= issCost;
        if (!arsHas || !issHas) {
            return false;
        }

        boolean arsSuccess = arsBridge.consumeMana(player, arsCost);
        if (!arsSuccess) {
            return false;
        }

        boolean issSuccess = issBridge.consumeMana(player, issCost);
        if (!issSuccess) {
            // ANS-CRIT-003: compensating refund, never snapshot-and-restore. The
            // previous setMana(manaBefore) clobbered any regen, buff or ritual mana
            // that landed between the snapshot and the rollback. addMana delegates to
            // the backing API's atomic add, so concurrent deltas survive.
            arsBridge.addMana(player, arsCost);
            return false;
        }

        return true;
    }

    /** Source-compatible overload for callers that still speak in "is this Ars?" terms. */
    public static boolean consumeManaForMode(net.minecraft.world.entity.player.Player player,
                                             float amount, boolean fromArs) {
        return consumeManaForMode(player, amount,
            fromArs ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA);
    }
}
