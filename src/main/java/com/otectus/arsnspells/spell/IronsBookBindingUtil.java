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
     * True when {@code stack} is a spell book that can accept a bound Ars entry.
     *
     * <p>Two sources, in order of authority:
     * <ol>
     *   <li>Iron's own {@code ISpellbook} interface — what the item <em>is</em>, published in
     *       Iron's {@code api} package and identical across 3.15.0 and 3.16.2.</li>
     *   <li>The {@code ars_n_spells:irons_spell_books} item tag — additive, so a pack can
     *       declare a spellbook-like item from a third mod that does not implement Iron's
     *       interface.</li>
     * </ol>
     *
     * <p>This replaces {@code path.contains("spell_book") || path.contains("spellbook")}, which
     * keyed off naming convention rather than type: any Iron's item whose path happened to
     * contain the fragment qualified, and a book not following the convention did not. The tag
     * shipped with audit F1 was never actually consulted by any code until now.
     */
    public static boolean isIronsSpellBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        // Gated + FQN so the Iron's-importing helper only classloads when Iron's is present.
        if (IronsCompat.isLoaded()
            && com.otectus.arsnspells.spell.irons.IronsScrollFactory.isSpellBookItem(stack)) {
            return true;
        }
        try {
            return stack.is(com.otectus.arsnspells.registry.ModTags.IRONS_SPELL_BOOKS);
        } catch (Exception e) {
            // Tag lookups throw if consulted before tags bind (early registry, some tests).
            // Falling through to false is correct: with Iron's loaded the interface check above
            // has already answered, and without it there is no book to bind onto anyway.
            return false;
        }
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
        if (book == null || book.isEmpty()) return AppendResult.FAILED;
        ItemStack working = book.copy();
        AppendResult result = appendToWorkingCopy(working, arsTag, customName, nature, iconSymbol, maxCap);
        if (result.wasAdded()) book.setTag(working.getTag() == null ? null : working.getTag().copy());
        return result;
    }

    private static AppendResult appendToWorkingCopy(ItemStack book, CompoundTag arsTag,
                                                    String customName, String nature,
                                                    String iconSymbol, int maxCap) {
        if (book == null || book.isEmpty() || arsTag == null || arsTag.isEmpty()
            || CrossCastNbt.schemaVersion(book.getTag()) > CrossCastNbt.SCHEMA_VERSION) {
            return AppendResult.FAILED;
        }
        // Binding is a natural repair point: the book is in hand and about to be rewritten
        // anyway, so clear out any orphan wheel slots a pre-fix uninscribe left behind before
        // allocating a new pool id — otherwise a stale slot can hold an id this bind wants.
        if (IronsCompat.isLoaded() && isIronsSpellBook(book)) {
            com.otectus.arsnspells.spell.irons.CarrierReconciler.reconcile(book);
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
        //
        // The native write is the half that can fail (container refused the grown
        // slot). Ignoring its result used to leave a sidecar entry with no wheel
        // slot: invisible, uncastable, and reported to the player as success. Roll
        // the sidecar back so the book is byte-identical to before the attempt.
        if (IronsCompat.isLoaded() && isIronsSpellBook(book)
            && !com.otectus.arsnspells.spell.irons.IronsProxySlotWriter.addProxySlot(book, poolId, 1)) {
            CrossCastNbt.removeEntryByProxyPoolId(bookTag, poolId);
            return AppendResult.FAILED;
        }
        return AppendResult.ADDED;
    }

    /**
     * True when {@code arsTag} deserializes to a spell that can actually be cast.
     *
     * <p>Checked before any resource is spent, so a payload written by a different
     * Ars Nouveau version (or by a glyph whose mod has since been removed) is
     * rejected with a translated message instead of binding a wheel entry that
     * silently does nothing when selected.
     */
    public static boolean isCastableArsPayload(CompoundTag arsTag) {
        if (arsTag == null || arsTag.isEmpty()) {
            return false;
        }
        try {
            // A payload with glyphs from an uninstalled mod deserializes to a SHORTER recipe
            // that Ars still reports as valid, so this check has to come first — see
            // ArsSpellIntegrity. Binding such a payload would produce an entry that casts
            // something other than what the player built.
            if (!com.otectus.arsnspells.util.ArsSpellIntegrity.isIntact(arsTag)) {
                return false;
            }
            com.hollingsworth.arsnouveau.api.spell.Spell spell =
                com.hollingsworth.arsnouveau.api.spell.Spell.fromTag(arsTag);
            return spell != null && spell.recipe != null && !spell.recipe.isEmpty()
                && spell.getCastMethod() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Remove every ANS-owned artifact from {@code stack}: native wheel proxy slots first,
     * then the sidecar entries, then ANS's own export marker.
     *
     * <p><b>Order matters.</b> The pool ids live in the sidecar, so clearing the sidecar first
     * loses the only record of which native slots belong to ANS — which is exactly the bug this
     * replaces. Uninscription used to call {@code clearCrossModSpells} alone, leaving the
     * proxy slots behind: {@code IronsProxySlotWriter.removeProxySlot} had no callers anywhere
     * in the codebase. The result was an orphan wheel entry that stayed selectable and did
     * nothing — the same symptom as the binding bug, reached from the other direction.
     *
     * <p>Only ANS-owned keys are touched. Custom names and third-party NBT are left alone: a
     * hover name cannot be attributed to ANS after the fact (the player may have renamed the
     * item at an anvil), and destroying another mod's data to clean up our own would be a
     * worse bug than the one being fixed. A scroll carrier therefore comes out of this as a
     * valid, blank Iron's scroll — its native container is Iron's, not ours, and stays.
     *
     * @return the number of native proxy slots removed
     */
    public static int removeAllArsEntries(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return 0;
        }
        int removed = 0;
        if (IronsCompat.isLoaded()) {
            for (int poolId : CrossCastNbt.usedProxyPoolIds(tag)) {
                if (com.otectus.arsnspells.spell.irons.IronsProxySlotWriter
                        .removeProxySlot(stack, poolId)) {
                    removed++;
                }
            }
        }
        if (IronsCompat.isLoaded()) {
            com.otectus.arsnspells.spell.irons.IronsProxySlotWriter.restoreBaseCapacity(stack);
        }
        CrossCastNbt.clearCrossModSpells(stack);
        // clearCrossModSpells may have dropped the root tag entirely; re-read before
        // touching the marker so this cannot resurrect an empty compound.
        if (stack.hasTag() && stack.getTag() != null) {
            stack.getTag().remove(ArsSpellExportUtil.TAG_EXPORT_MODE);
            stack.getTag().remove(CrossCastNbt.TAG_NATIVE_BASE_CAPACITY);
            if (stack.getTag().isEmpty()) {
                stack.setTag(null);
            }
        }
        return removed;
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
