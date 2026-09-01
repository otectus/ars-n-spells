package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Repairs ANS-created items that were written by an older build, on the occasions something
 * already has the stack in hand.
 *
 * <p><b>Lazy by design.</b> Nothing here scans inventories, and nothing runs on a tick. A
 * legacy item can sit in a chest indefinitely; it is repaired the moment it reaches a code path
 * that was going to read it anyway (binding, casting, the inscription guard). Sweeping every
 * player's inventory to find items that may not exist is exactly the kind of per-tick global
 * work this design avoids.
 *
 * <p>Two repairs, matching the two ways a legacy item can violate the current invariant:
 * <ul>
 *   <li><b>Missing native container on a carrier scroll</b> — the Inscription Table crash.
 *       Repairable in place, and worth repairing rather than rejecting, because the scroll's
 *       Ars payload is still perfectly good.</li>
 *   <li><b>Orphan native proxy slots on a book</b> — a wheel slot whose sidecar entry is gone,
 *       left behind by the pre-fix uninscribe path. These are selectable and do nothing, so
 *       removing them is what makes the wheel honest.</li>
 * </ul>
 *
 * <p>The inverse case — a sidecar entry with no proxy slot — is deliberately <em>not</em>
 * repaired here. Re-adding a native slot allocates capacity on the player's book, which is a
 * gameplay-visible change that should happen through an explicit bind, not silently while
 * something else was reading the item.
 *
 * <p><b>No schema stamp.</b> The 1.20.1 version wrote an {@code arsnspells:schema_version} int
 * so a later pass could skip an already-checked stack. The payload is a codec-backed data
 * component here, and both checks below are cheap predicates over data already in memory, so
 * the stamp would cost a component write to save nothing.
 *
 * <p><b>Iron's-isolated.</b> Callers gate on {@code IronsCompat.isLoaded()}.
 */
public final class CarrierReconciler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CarrierReconciler.class);

    private CarrierReconciler() {}

    /** What a reconcile pass did, so callers can log or test it without re-deriving. */
    public enum Outcome {
        /** Already current; nothing was written. */
        UNCHANGED,
        /** A missing native spell container was created on a carrier scroll. */
        CONTAINER_REPAIRED,
        /** One or more orphan proxy slots were removed from a book. */
        ORPHANS_REMOVED,
        /** Both repairs applied. */
        REPAIRED_BOTH
    }

    /**
     * Bring {@code stack} up to the current invariant if it is an ANS-created item that needs
     * it. Safe and cheap to call on anything: non-ANS stacks return {@link Outcome#UNCHANGED}
     * without allocation.
     */
    public static Outcome reconcile(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !IronsCompat.isLoaded()) {
            return Outcome.UNCHANGED;
        }
        if (!CrossModSpellComponents.has(stack)) {
            return Outcome.UNCHANGED;
        }

        boolean containerRepaired = repairCarrierContainer(stack);
        boolean orphansRemoved = removeOrphanProxySlots(stack);

        if (containerRepaired || orphansRemoved
            || CrossModSpellComponents.schemaVersion(stack) < CrossModSpellComponents.SCHEMA_VERSION) {
            // Either we just repaired it, or nothing was wrong with it and it had never been
            // checked. Either way, record that this build has now seen it.
            CrossModSpellComponents.stampSchemaVersion(stack);
        }

        if (containerRepaired && orphansRemoved) {
            return Outcome.REPAIRED_BOTH;
        }
        if (containerRepaired) {
            return Outcome.CONTAINER_REPAIRED;
        }
        return orphansRemoved ? Outcome.ORPHANS_REMOVED : Outcome.UNCHANGED;
    }

    /**
     * Give a legacy carrier scroll the native container it was created without.
     *
     * <p>Gated on {@code hasReadableContainer}, not on mere key presence: a container that is
     * present but does not decode throws out of {@code ISpellContainer.get} on every read
     * Iron's makes — including the scroll tooltip — and a presence check would have declared
     * such a carrier healthy and left it broken forever.
     */
    private static boolean repairCarrierContainer(ItemStack stack) {
        if (!IronsBookBindingUtil.isIronsScroll(stack)
            || IronsScrollFactory.hasReadableContainer(stack)) {
            return false;
        }
        boolean ok = IronsScrollFactory.initializeCarrierContainer(stack);
        if (ok) {
            LOGGER.debug("Repaired a legacy ANS carrier scroll that had no native spell container");
        } else {
            LOGGER.warn("Could not repair a legacy ANS carrier scroll's native spell container; "
                + "it will still be refused by the Inscription Table guard");
        }
        return ok;
    }

    /**
     * Drop native proxy slots whose sidecar entry no longer exists.
     *
     * <p>Only pool ids ANS owns are considered, and only those absent from the sidecar, so a
     * genuine Iron's spell can never be removed.
     */
    private static boolean removeOrphanProxySlots(ItemStack stack) {
        if (!IronsBookBindingUtil.isIronsSpellBook(stack)) {
            return false;
        }
        Set<Integer> live = CrossModSpellComponents.usedProxyPoolIds(
            CrossModSpellComponents.get(stack));
        boolean removedAny = false;
        for (int poolId = 1; poolId <= CrossModSpellComponents.PROXY_POOL_SIZE; poolId++) {
            if (live.contains(poolId)) {
                continue;
            }
            if (IronsProxySlotWriter.removeProxySlot(stack, poolId)) {
                removedAny = true;
                LOGGER.debug("Removed orphan ars_cross_{} proxy slot with no sidecar entry", poolId);
            }
        }
        return removedAny;
    }
}
