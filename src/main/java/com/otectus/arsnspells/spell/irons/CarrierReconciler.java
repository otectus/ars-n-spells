package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 * that was going to read it anyway (binding, casting, uninscribing). Sweeping every player's
 * inventory to find items that may not exist is exactly the kind of per-tick global work the
 * rest of this update is removing.
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
        if (stack == null || stack.isEmpty() || !stack.hasTag() || !IronsCompat.isLoaded()) {
            return Outcome.UNCHANGED;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || CrossCastNbt.schemaVersion(tag) > CrossCastNbt.SCHEMA_VERSION) {
            // A future schema's contents are not guessed at: left exactly as it is.
            return Outcome.UNCHANGED;
        }
        SidecarState sidecar = sidecarState(tag);
        if (sidecar == SidecarState.UNDECODABLE) {
            // Present but not a list of entries: which proxies are live cannot be known, so nothing is removed.
            return Outcome.UNCHANGED;
        }
        if (sidecar == SidecarState.ABSENT_OR_EMPTY) {
            // Audit ANS-010: a book whose whole sidecar is gone (or empty) can still carry ANS proxy
            // slots, the most complete orphan case. The scroll repair needs a sidecar; this one does not.
            return removeOrphanProxySlots(stack, true) ? Outcome.ORPHANS_REMOVED : Outcome.UNCHANGED;
        }

        boolean containerRepaired = repairCarrierContainer(stack);
        boolean orphansRemoved = removeOrphanProxySlots(stack, false);

        if (containerRepaired || orphansRemoved) {
            CrossCastNbt.stampSchemaVersion(stack.getOrCreateTag());
        } else if (CrossCastNbt.schemaVersion(tag) < CrossCastNbt.SCHEMA_VERSION) {
            // Nothing was wrong with it; record that it has been checked so later passes can
            // skip straight past it.
            CrossCastNbt.stampSchemaVersion(stack.getOrCreateTag());
        }

        if (containerRepaired && orphansRemoved) {
            return Outcome.REPAIRED_BOTH;
        }
        if (containerRepaired) {
            return Outcome.CONTAINER_REPAIRED;
        }
        return orphansRemoved ? Outcome.ORPHANS_REMOVED : Outcome.UNCHANGED;
    }

    private enum SidecarState { ABSENT_OR_EMPTY, ENTRIES, UNDECODABLE }

    /**
     * The supported sidecar is a list of compound entries. Absent, or an empty list, means no ANS
     * entry survives. Present in any other shape is a format this build cannot read.
     */
    private static SidecarState sidecarState(CompoundTag tag) {
        if (!tag.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS)) return SidecarState.ABSENT_OR_EMPTY;
        if (!(tag.get(CrossCastNbt.TAG_CROSS_MOD_SPELLS) instanceof ListTag list)) return SidecarState.UNDECODABLE;
        if (list.isEmpty()) return SidecarState.ABSENT_OR_EMPTY;
        return list.getElementType() == Tag.TAG_COMPOUND ? SidecarState.ENTRIES : SidecarState.UNDECODABLE;
    }

    /**
     * Give a legacy carrier scroll the native container it was created without.
     *
     * <p>Gated on {@code hasReadableContainer}, not on mere key presence: a container that is
     * present but does not decode throws out of {@code ISpellContainer.get} on every read Iron's
     * makes — including the scroll tooltip — and a presence check would have declared such a
     * carrier healthy and left it broken forever.
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
     * genuine Iron's spell can never be removed. Audit ANS-010: the owned ids are read from the
     * book's native container itself. When no ANS entry is left at all, the native capacity ANS
     * added is handed back by the same rule uninscription uses ({@code restoreBaseCapacity}:
     * trailing empty slots down to the recorded baseline, never below it, never a native spell).
     */
    private static boolean removeOrphanProxySlots(ItemStack stack, boolean noEntriesLeft) {
        if (!IronsBookBindingUtil.isIronsSpellBook(stack)) {
            return false;
        }
        Set<Integer> live = noEntriesLeft ? Set.of() : CrossCastNbt.usedProxyPoolIds(stack.getTag());
        boolean removedAny = false;
        for (int poolId : IronsProxySlotWriter.nativeProxyPoolIds(stack)) {
            if (live.contains(poolId)) {
                continue;
            }
            if (IronsProxySlotWriter.removeProxySlot(stack, poolId)) {
                removedAny = true;
                LOGGER.debug("Removed orphan ars_cross_{} proxy slot with no sidecar entry", poolId);
            }
        }
        if (removedAny && noEntriesLeft) {
            IronsProxySlotWriter.restoreBaseCapacity(stack);
            if (stack.hasTag() && stack.getTag() != null) stack.getTag().remove(CrossCastNbt.TAG_NATIVE_BASE_CAPACITY);
        }
        return removedAny;
    }
}
