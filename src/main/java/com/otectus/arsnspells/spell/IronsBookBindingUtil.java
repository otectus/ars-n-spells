package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Binds an exported Ars spell carried by a real Iron's scroll onto a real Iron's
 * spellbook. This is the second leg of the 3.0.0 Ars &rarr; scroll &rarr;
 * spellbook workflow.
 *
 * <p>The bound entry is appended to the spellbook's {@code arsnspells:cross_spells}
 * sidecar list, which coexists with Iron's own {@code ISB_Spells} container on
 * the same item (the cross-cast NBT helpers only touch ANS-owned root keys).
 * The spell still casts through {@link CrossCastingHandler}, not Iron's native
 * slot model.
 *
 * <p>No top-level Iron's imports: scrolls and spellbooks are recognized by
 * registry id, and all mutation goes through {@link CrossCastNbt}.
 */
public final class IronsBookBindingUtil {
    /** Placeholder id every exported Ars spell shares; dedup must key off the payload, not this. */
    public static final ResourceLocation ARS_PLACEHOLDER_ID =
        new ResourceLocation("ars_nouveau", "spell");

    private static final ResourceLocation IRONS_SCROLL_ID =
        new ResourceLocation(IronsCompat.MODID, "scroll");

    private IronsBookBindingUtil() {}

    /** True when {@code stack} is the real Iron's scroll item. */
    public static boolean isIronsScroll(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return IRONS_SCROLL_ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /**
     * True when {@code stack} is an Iron's spellbook. Matches any item in the
     * {@code irons_spellbooks} namespace whose path denotes a spell book, so
     * tiered book variants are recognized without loading Iron's classes.
     */
    public static boolean isIronsSpellBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !IronsCompat.MODID.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.contains("spell_book") || path.contains("spellbook");
    }

    /**
     * Extracts the serialized Ars spell from a carrier scroll, requiring exactly
     * one cross-cast entry and that it is an Ars entry with a non-empty
     * {@code ars_spell} payload. Returns empty otherwise (ambiguous or non-Ars
     * carriers are rejected rather than guessed at).
     */
    public static Optional<CompoundTag> extractSingleArsEntry(ItemStack carrierScroll) {
        if (carrierScroll == null || !carrierScroll.hasTag()) {
            return Optional.empty();
        }
        return extractSingleArsEntryFromTag(carrierScroll.getTag());
    }

    /**
     * CompoundTag-level companion to {@link #extractSingleArsEntry(ItemStack)} so
     * the carrier-validation contract is unit-testable without bootstrapping the
     * item registry.
     */
    public static Optional<CompoundTag> extractSingleArsEntryFromTag(CompoundTag tag) {
        if (tag == null || !tag.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) {
            return Optional.empty();
        }
        ListTag list = tag.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        if (list.size() != 1) {
            return Optional.empty();
        }
        CompoundTag entry = list.getCompound(0);
        if (CrossCastValidator.resolveType(entry) != CrossSpellType.ARS_NOUVEAU) {
            return Optional.empty();
        }
        if (!entry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag arsTag = entry.getCompound(CrossCastNbt.TAG_ARS_SPELL);
        return arsTag.isEmpty() ? Optional.empty() : Optional.of(arsTag.copy());
    }

    /**
     * Like {@link #extractSingleArsEntry(ItemStack)} but returns the <em>whole</em>
     * cross-spell entry compound (including any Spell Loom display metadata —
     * custom name, nature, icon), not just the {@code ars_spell} sub-tag. Used by
     * the binding step so a scroll's chosen name/nature/icon ride onto the book.
     */
    public static Optional<CompoundTag> extractSingleEntry(ItemStack carrierScroll) {
        if (carrierScroll == null || !carrierScroll.hasTag()) {
            return Optional.empty();
        }
        return extractSingleEntryFromTag(carrierScroll.getTag());
    }

    /** CompoundTag-level companion to {@link #extractSingleEntry(ItemStack)}. */
    public static Optional<CompoundTag> extractSingleEntryFromTag(CompoundTag tag) {
        if (tag == null || !tag.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) {
            return Optional.empty();
        }
        ListTag list = tag.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        if (list.size() != 1) {
            return Optional.empty();
        }
        CompoundTag entry = list.getCompound(0);
        if (CrossCastValidator.resolveType(entry) != CrossSpellType.ARS_NOUVEAU) {
            return Optional.empty();
        }
        if (!entry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)
            || entry.getCompound(CrossCastNbt.TAG_ARS_SPELL).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entry.copy());
    }

    /**
     * True when {@code book} already carries an Ars entry whose {@code ars_spell}
     * payload equals {@code arsTag}. Dedup keys off the serialized payload, not
     * the shared placeholder id.
     */
    public static boolean containsEquivalentArsSpell(ItemStack book, CompoundTag arsTag) {
        if (book == null || !book.hasTag() || arsTag == null) {
            return false;
        }
        return tagContainsEquivalentArsSpell(book.getTag(), arsTag);
    }

    /** CompoundTag-level companion to {@link #containsEquivalentArsSpell(ItemStack, CompoundTag)}. */
    public static boolean tagContainsEquivalentArsSpell(CompoundTag bookTag, CompoundTag arsTag) {
        if (bookTag == null || arsTag == null) {
            return false;
        }
        if (!bookTag.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = bookTag.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)
                && arsTag.equals(entry.getCompound(CrossCastNbt.TAG_ARS_SPELL))) {
                return true;
            }
        }
        return false;
    }

    /** Outcome of a bind attempt, so callers can surface a precise message. */
    public enum AppendResult {
        ADDED, DUPLICATE, BOOK_FULL, FAILED;

        public boolean wasAdded() {
            return this == ADDED;
        }
    }

    /**
     * Backward-compatible append: binds with default (no) metadata and no cap.
     * Returns true only when a new entry was added.
     */
    public static boolean appendArsSpellToBook(ItemStack book, CompoundTag arsTag) {
        return appendArsSpellToBook(book, arsTag, null, null, null, -1).wasAdded();
    }

    /**
     * Appends {@code arsTag} as a new Ars entry on {@code book}, allocates a
     * native-wheel proxy pool id, writes the optional display metadata
     * (name/nature/icon), and — when Iron's is loaded — mirrors the entry into the
     * book's native spell container so it appears in Iron's spell wheel.
     *
     * <p>{@code maxCap < 0} means "no cap" (still bounded by the proxy pool size).
     * Returns {@link AppendResult#DUPLICATE} for an already-present payload and
     * {@link AppendResult#BOOK_FULL} when every proxy slot is taken; neither
     * mutates the book.
     */
    public static AppendResult appendArsSpellToBook(ItemStack book, CompoundTag arsTag,
                                                    String customName, String nature,
                                                    String iconSymbol, int maxCap) {
        if (book == null || book.isEmpty() || arsTag == null || arsTag.isEmpty()) {
            return AppendResult.FAILED;
        }
        if (containsEquivalentArsSpell(book, arsTag)) {
            return AppendResult.DUPLICATE;
        }
        CompoundTag bookTag = book.getOrCreateTag();
        int ceiling = effectiveProxyCeiling(maxCap);
        int poolId = CrossCastNbt.allocateProxyPoolId(bookTag, ceiling);
        if (poolId == CrossCastNbt.NO_PROXY_POOL_ID) {
            return AppendResult.BOOK_FULL;
        }
        CrossCastNbt.addArsEntryWithMetaToTag(bookTag, ARS_PLACEHOLDER_ID, 1, arsTag.copy(),
            poolId, customName, nature, iconSymbol);
        // Mirror into Iron's native container so the entry shows in the wheel.
        // Gated + referenced by FQN so IronsProxySlotWriter (which imports Iron's
        // API) only classloads when Iron's is present.
        if (IronsCompat.isLoaded()) {
            com.otectus.arsnspells.spell.irons.IronsProxySlotWriter.addProxySlot(book, poolId, 1);
        }
        return AppendResult.ADDED;
    }

    /**
     * The effective per-book Ars ceiling: a negative {@code maxCap} means
     * "no cap" (still bounded by {@link CrossCastNbt#PROXY_POOL_SIZE}, the number
     * of distinct native-wheel slots that can exist).
     */
    public static int effectiveProxyCeiling(int maxCap) {
        if (maxCap < 0) {
            return CrossCastNbt.PROXY_POOL_SIZE;
        }
        return Math.min(maxCap, CrossCastNbt.PROXY_POOL_SIZE);
    }
}
