package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Taking one bound Ars spell back off an Iron's spellbook at the Inscription Table (3.4.1 review
 * UNBIND-01).
 *
 * <p>Iron's own table offers the selected wheel spell as a scroll and, when the scroll is taken,
 * removes whatever sits at that wheel index. For an ANS entry that would hand back a bare proxy that
 * casts nothing and leave the Ars payload stranded on the book. Instead the table offers a real ANS
 * carrier, built from the sidecar entry the selected proxy points to, and taking it removes exactly
 * that entry and that proxy slot:
 *
 * <ul>
 *   <li>{@link #preview} is pure: it reads the book and builds the carrier without changing anything,
 *       or says why the entry cannot be extracted;</li>
 *   <li>{@link #commit} mutates a copy of the book, verifies that only that proxy and that entry are
 *       gone and every other native spell and Ars entry is untouched, and only then returns it.</li>
 * </ul>
 * The proxy is found by its pool id, never by assuming the wheel index equals the sidecar index.
 * Native spells, other Ars entries, names, enchantments and third-party data stay; only ANS-owned
 * empty tail capacity shrinks, under the baseline rule binding uses. A book in a newer sidecar format,
 * a missing or unreadable payload, or a duplicated proxy id is refused with a reason and left as is.
 */
public final class IronsTableExtraction {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsTableExtraction.class);

    /** What the table offers for the selected wheel slot of an ANS entry. */
    public record Preview(ItemStack book, int index, int poolId, ItemStack result, @Nullable String refusalKey) {
        public boolean ok() {
            return refusalKey == null && !result.isEmpty();
        }
    }

    private IronsTableExtraction() {}

    /**
     * What extracting wheel slot {@code index} of {@code book} offers: a carrier, or a refusal. Null
     * when that slot is not an ANS proxy, so Iron's own table handles it.
     */
    @Nullable
    public static Preview preview(ItemStack book, int index) {
        if (book == null || book.isEmpty() || index < 0) return null;
        ISpellContainer container;
        try {
            if (!ISpellContainer.isSpellContainer(book)) return null;
            container = ISpellContainer.get(book);
        } catch (RuntimeException unreadable) {
            return null;
        }
        if (container == null || index >= container.getMaxSpellCount()) return null;
        SpellData data = container.getSpellAtIndex(index);
        if (data == null || !(data.getSpell() instanceof ArsCrossProxySpell proxy)) return null;
        int poolId = proxy.getPoolId();
        ItemStack snapshot = book.copy();
        if (!data.canRemove()) return refused(snapshot, index, poolId, "message.ars_n_spells.extract.locked");
        CompoundTag tag = book.getTag();
        if (CrossCastNbt.schemaVersion(tag) > CrossCastNbt.SCHEMA_VERSION)
            return refused(snapshot, index, poolId, "message.ars_n_spells.extract.newer_format");
        List<CompoundTag> entries = entriesFor(tag, poolId);
        if (entries.isEmpty()) return refused(snapshot, index, poolId, "message.ars_n_spells.extract.orphan");
        if (entries.size() > 1) return refused(snapshot, index, poolId, "message.ars_n_spells.extract.duplicate");
        CompoundTag entry = entries.get(0);
        if (!IronsBookBindingUtil.isCastableArsPayload(entry.getCompound(CrossCastNbt.TAG_ARS_SPELL)))
            return refused(snapshot, index, poolId, "message.ars_n_spells.extract.unreadable");
        ItemStack carrier = ArsSpellExportUtil.carrierFromEntry(entry);
        if (carrier.isEmpty()) return refused(snapshot, index, poolId, "message.ars_n_spells.extract.unavailable");
        return new Preview(snapshot, index, poolId, carrier, null);
    }

    /** The extraction each open table offers now, by menu (both sides run the menu). */
    private static final java.util.Map<Object, Preview> STAGED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static void stage(Object menu, @Nullable Preview preview) {
        if (preview == null) STAGED.remove(menu);
        else STAGED.put(menu, preview);
    }

    @Nullable
    public static Preview staged(Object menu) {
        return STAGED.get(menu);
    }

    private static Preview refused(ItemStack snapshot, int index, int poolId, String key) {
        return new Preview(snapshot, index, poolId, ItemStack.EMPTY, key);
    }

    /** Whether {@code book} is still exactly the book {@code preview} was made from. */
    public static boolean fresh(Preview preview, ItemStack book) {
        return preview != null && ItemStack.matches(preview.book(), book);
    }

    /**
     * The book with exactly the previewed entry and its proxy slot removed, verified, or null when
     * the book changed since the preview or the removal could not be verified (nothing changed then).
     */
    @Nullable
    public static ItemStack commit(Preview preview, ItemStack book) {
        if (preview == null || !preview.ok() || !fresh(preview, book)) return null;
        ItemStack copy = book.copy();
        List<String> nativeBefore = nativeSpells(copy, preview.poolId());
        List<CompoundTag> othersBefore = otherEntries(copy.getTag(), preview.poolId());
        if (!IronsProxySlotWriter.removeProxySlot(copy, preview.poolId())) return reject("the proxy slot could not be removed");
        if (!CrossCastNbt.removeEntryByProxyPoolId(copy.getOrCreateTag(), preview.poolId())) return reject("the sidecar entry could not be removed");
        IronsProxySlotWriter.restoreBaseCapacity(copy);
        CompoundTag tag = copy.getTag();
        if (tag != null && CrossCastNbt.countArsEntries(tag) == 0) {
            tag.remove(CrossCastNbt.TAG_NATIVE_BASE_CAPACITY);
            if (tag.isEmpty()) copy.setTag(null);
        }
        if (!entriesFor(copy.getTag(), preview.poolId()).isEmpty()) return reject("the entry is still present");
        if (!nativeSpells(copy, preview.poolId()).equals(nativeBefore)) return reject("another wheel spell changed");
        if (!otherEntries(copy.getTag(), preview.poolId()).equals(othersBefore)) return reject("another Ars entry changed");
        return copy;
    }

    private static ItemStack reject(String why) {
        LOGGER.warn("[ANS] Inscription Table extraction refused: {}; the book is unchanged", why);
        return null;
    }

    private static List<CompoundTag> entriesFor(@Nullable CompoundTag tag, int poolId) {
        List<CompoundTag> out = new ArrayList<>();
        if (tag == null || !tag.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) return out;
        ListTag list = tag.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains(CrossCastNbt.TAG_PROXY_POOL_ID, Tag.TAG_INT) && entry.getInt(CrossCastNbt.TAG_PROXY_POOL_ID) == poolId) out.add(entry);
        }
        return out;
    }

    private static List<CompoundTag> otherEntries(@Nullable CompoundTag tag, int poolId) {
        List<CompoundTag> out = new ArrayList<>();
        if (tag == null || !tag.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) return out;
        ListTag list = tag.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!(entry.contains(CrossCastNbt.TAG_PROXY_POOL_ID, Tag.TAG_INT) && entry.getInt(CrossCastNbt.TAG_PROXY_POOL_ID) == poolId))
                out.add(entry.copy());
        }
        return out;
    }

    /** Every wheel slot's spell id and level, in order, except the proxy for {@code poolId}. */
    private static List<String> nativeSpells(ItemStack book, int poolId) {
        List<String> out = new ArrayList<>();
        if (!ISpellContainer.isSpellContainer(book)) return out;
        ISpellContainer container = ISpellContainer.get(book);
        for (int i = 0; i < container.getMaxSpellCount(); i++) {
            SpellData data = container.getSpellAtIndex(i);
            if (data == null || data == SpellData.EMPTY) continue;
            if (data.getSpell() instanceof ArsCrossProxySpell proxy && proxy.getPoolId() == poolId) continue;
            out.add(data.getSpell().getSpellId() + "@" + data.getLevel() + (data.isLocked() ? "#locked" : ""));
        }
        return out;
    }
}
