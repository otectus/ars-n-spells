package com.otectus.arsnspells.spell.irons;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import net.minecraft.world.item.ItemStack;

/**
 * Writes/removes Ars cross-cast proxy slots in an Iron's spellbook's <em>native</em>
 * spell container (the {@code irons_spellbooks} spell-container data component).
 * Each proxy slot makes one Ars entry appear in Iron's native spell wheel; the Ars
 * payload itself lives in the book's {@code ars_n_spells:cross_spells} sidecar
 * component.
 *
 * <p>To avoid evicting the player's real Iron's spells, a proxy slot is added into
 * <em>grown</em> capacity (max spell count + 1) rather than into a tier slot the
 * player may want for an Iron's spell.
 *
 * <p><b>Iron's-isolated.</b> All Iron's API imports are confined here; callers gate
 * with {@code IronsCompat.isLoaded()} before touching this class, keeping
 * {@link com.otectus.arsnspells.spell.IronsBookBindingUtil} free of Iron's classes.
 */
public final class IronsProxySlotWriter {
    private IronsProxySlotWriter() {}

    /**
     * Ensure {@code book}'s native container holds the proxy spell for {@code poolId}
     * (added into newly-grown capacity if absent). Idempotent: a no-op when the proxy
     * is already present. Returns true when the book now contains the proxy slot.
     */
    public static boolean addProxySlot(ItemStack book, int poolId, int level) {
        AbstractSpell proxy = ArsCrossProxyRegistry.get(poolId);
        if (proxy == null || book == null || book.isEmpty()) {
            return false;
        }
        ISpellContainer container = ISpellContainer.getOrCreate(book);
        if (container.getIndexForSpell(proxy) >= 0) {
            return true; // already present
        }
        ISpellContainerMutable mutable = container.mutableCopy();
        int newIndex = mutable.getMaxSpellCount();
        mutable.setMaxSpellCount(newIndex + 1);
        boolean added = mutable.addSpellAtIndex(proxy, Math.max(1, level), newIndex, false);
        if (added) {
            ISpellContainer.set(book, mutable.toImmutable());
        }
        return added;
    }

    /**
     * Remove the proxy spell for {@code poolId} from {@code book}'s native container,
     * leaving an empty slot (max count is not shrunk to avoid re-indexing the
     * player's real spells). Returns true when a slot was removed.
     */
    public static boolean removeProxySlot(ItemStack book, int poolId) {
        AbstractSpell proxy = ArsCrossProxyRegistry.get(poolId);
        if (proxy == null || book == null || book.isEmpty() || !ISpellContainer.isSpellContainer(book)) {
            return false;
        }
        // ISpellContainer.get returns null when the component is absent; the
        // isSpellContainer guard above keeps this null-safe.
        ISpellContainer container = ISpellContainer.get(book);
        if (container == null) {
            return false;
        }
        int index = container.getIndexForSpell(proxy);
        if (index < 0) {
            return false;
        }
        ISpellContainerMutable mutable = container.mutableCopy();
        boolean removed = mutable.removeSpellAtIndex(index);
        if (removed) {
            ISpellContainer.set(book, mutable.toImmutable());
        }
        return removed;
    }

    /**
     * Remove every proxy slot in {@code poolIds} from {@code book} in a single native write
     * (audit V20).
     *
     * <p>Where {@link #removeProxySlot} publishes a container per pool id, this builds one
     * mutable copy, removes all of them, and calls {@code ISpellContainer.set} once. A failure
     * part-way through therefore leaves the book exactly as it was, rather than half
     * reconciled - which matters because the ANS sidecar holding the pool ids is cleared
     * immediately afterwards, so a partial pass is unrecoverable.
     *
     * <p>Max spell count is not shrunk, for the same deliberate reason as
     * {@link #removeProxySlot}: shrinking re-indexes the player's own spells.
     *
     * @return how many proxy slots were removed; {@code 0} means the book was not written to
     */
    public static int removeProxySlots(ItemStack book, Iterable<Integer> poolIds) {
        if (book == null || book.isEmpty() || poolIds == null
            || !ISpellContainer.isSpellContainer(book)) {
            return 0;
        }
        try {
            ISpellContainer container = ISpellContainer.get(book);
            if (container == null) {
                return 0;
            }
            ISpellContainerMutable mutable = container.mutableCopy();
            int removed = 0;
            for (Integer poolId : poolIds) {
                if (poolId == null) {
                    continue;
                }
                AbstractSpell proxy = ArsCrossProxyRegistry.get(poolId);
                if (proxy == null) {
                    continue;
                }
                int index = mutable.getIndexForSpell(proxy);
                if (index >= 0 && mutable.removeSpellAtIndex(index)) {
                    removed++;
                }
            }
            if (removed > 0) {
                ISpellContainer.set(book, mutable.toImmutable());
            }
            return removed;
        } catch (Exception e) {
            // Narrow to Exception so LinkageError still propagates. Nothing was published:
            // every mutation above happened on the local mutable copy.
            return 0;
        }
    }
}
