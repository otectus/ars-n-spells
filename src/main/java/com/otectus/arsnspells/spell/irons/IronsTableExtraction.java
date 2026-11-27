package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ModDataComponents;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Taking one bound Ars spell back off an Iron's spellbook at the Inscription Table (3.4.1 review
 * UNBIND-01). The NeoForge counterpart of the Forge build's class of the same name, on data
 * components.
 *
 * <p>Iron's own table offers the selected wheel spell as a scroll and, when the scroll is taken,
 * removes whatever sits at that wheel index. For an ANS entry that would hand back a bare proxy that
 * casts nothing and leave the Ars payload stranded on the book. Instead the table offers a real ANS
 * carrier built from the entry the selected proxy points to, and taking it removes exactly that entry
 * and that proxy slot:
 *
 * <ul>
 *   <li>{@link #preview} is pure: it reads the book and builds the carrier, or says why not;</li>
 *   <li>{@link #commit} mutates a copy of the book, verifies that only that proxy and that entry are
 *       gone and every other native spell and Ars entry is untouched, and only then returns it.</li>
 * </ul>
 * The proxy is found by its pool id, never by assuming the wheel index equals the entry index. A book
 * in a newer format, a missing or unreadable payload, a duplicated proxy id or a locked slot is refused
 * with a reason and left as is.
 */
public final class IronsTableExtraction {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsTableExtraction.class);

    /** What the table offers for the selected wheel slot of an ANS entry. */
    public record Preview(ItemStack book, int index, int poolId, ItemStack result, @Nullable String refusalKey) {
        public boolean ok() {
            return refusalKey == null && !result.isEmpty();
        }
    }

    /** The extraction each open table offers now, by menu (both sides run the menu). */
    private static final java.util.Map<Object, Preview> STAGED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private IronsTableExtraction() {}

    public static void stage(Object menu, @Nullable Preview preview) {
        if (preview == null) STAGED.remove(menu);
        else STAGED.put(menu, preview);
    }

    @Nullable
    public static Preview staged(Object menu) {
        return STAGED.get(menu);
    }

    /** What extracting wheel slot {@code index} of {@code book} offers; null when that slot is not an ANS proxy. */
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
        if (CrossModSpellComponents.schemaVersion(book) > CrossModSpellComponents.SCHEMA_VERSION)
            return refused(snapshot, index, poolId, "message.ars_n_spells.extract.newer_format");
        List<CrossModSpell> entries = entriesFor(book, poolId);
        if (entries.isEmpty()) return refused(snapshot, index, poolId, "message.ars_n_spells.extract.orphan");
        if (entries.size() > 1) return refused(snapshot, index, poolId, "message.ars_n_spells.extract.duplicate");
        CrossModSpell entry = entries.get(0);
        if (entry.arsSpellTag().isEmpty() || !IronsBookBindingUtil.isCastableArsPayload(entry.arsSpellTag().get()))
            return refused(snapshot, index, poolId, "message.ars_n_spells.extract.unreadable");
        ItemStack carrier = ArsSpellExportUtil.carrierFromEntry(entry);
        if (carrier.isEmpty()) return refused(snapshot, index, poolId, "message.ars_n_spells.extract.unavailable");
        return new Preview(snapshot, index, poolId, carrier, null);
    }

    private static Preview refused(ItemStack snapshot, int index, int poolId, String key) {
        return new Preview(snapshot, index, poolId, ItemStack.EMPTY, key);
    }

    /** Whether {@code book} is still exactly the book {@code preview} was made from. */
    public static boolean fresh(Preview preview, ItemStack book) {
        return preview != null && ItemStack.matches(preview.book(), book);
    }

    /** The book with exactly the previewed entry and its proxy slot removed, verified; null when refused (nothing changed). */
    @Nullable
    public static ItemStack commit(Preview preview, ItemStack book) {
        if (preview == null || !preview.ok() || !fresh(preview, book)) return null;
        ItemStack copy = book.copy();
        List<String> nativeBefore = nativeSpells(copy, preview.poolId());
        List<CrossModSpell> othersBefore = otherEntries(copy, preview.poolId());
        if (!IronsProxySlotWriter.removeProxySlot(copy, preview.poolId())) return reject("the proxy slot could not be removed");
        if (othersBefore.isEmpty()) {
            // The last entry: remove the payload and the ANS markers, as erasing does (the slot is already gone).
            CrossModSpellComponents.clearPayloadOnly(copy);
            IronsProxySlotWriter.restoreBaseCapacity(copy);
            copy.remove(ModDataComponents.SCHEMA_VERSION.get());
            copy.remove(ModDataComponents.NATIVE_BASE_CAPACITY.get());
        } else {
            copy.set(ModDataComponents.CROSS_SPELLS.get(), CrossModSpellComponents.withoutProxyPoolId(CrossModSpellComponents.get(copy), preview.poolId()));
            IronsProxySlotWriter.restoreBaseCapacity(copy);
        }
        if (!entriesFor(copy, preview.poolId()).isEmpty()) return reject("the entry is still present");
        if (!nativeSpells(copy, preview.poolId()).equals(nativeBefore)) return reject("another wheel spell changed");
        if (!otherEntries(copy, preview.poolId()).equals(othersBefore)) return reject("another Ars entry changed");
        return copy;
    }

    private static ItemStack reject(String why) {
        LOGGER.warn("[ANS] Inscription Table extraction refused: {}; the book is unchanged", why);
        return null;
    }

    private static List<CrossModSpell> entriesFor(ItemStack book, int poolId) {
        List<CrossModSpell> out = new ArrayList<>();
        for (CrossModSpell entry : CrossModSpellComponents.get(book).spells()) if (entry.proxyPoolId() == poolId) out.add(entry);
        return out;
    }

    private static List<CrossModSpell> otherEntries(ItemStack book, int poolId) {
        List<CrossModSpell> out = new ArrayList<>();
        for (CrossModSpell entry : CrossModSpellComponents.get(book).spells()) if (entry.proxyPoolId() != poolId) out.add(entry);
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
