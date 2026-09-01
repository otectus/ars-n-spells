package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.util.ArsSpellIntegrity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Binds an exported Ars spell carried by a real Iron's scroll onto a real Iron's
 * spellbook. This is the second leg of the 3.0.0 Ars &rarr; scroll &rarr;
 * spellbook workflow.
 *
 * <p>The bound entry is appended to the spellbook's
 * {@link ModDataComponents#CROSS_SPELLS} sidecar component, which coexists with
 * Iron's own spell-container component on the same item. The spell casts through
 * Iron's native wheel via the {@code ars_cross_k} proxy spells (see
 * {@code spell.irons.ArsCrossProxySpell}); the sidecar remains the storage of
 * record.
 *
 * <p>No top-level Iron's imports: scrolls and spellbooks are recognized by
 * registry id, and the only Iron's API touch ({@code IronsProxySlotWriter})
 * is FQN-referenced behind {@link IronsCompat#isLoaded()}.
 */
public final class IronsBookBindingUtil {
    private static final ResourceLocation IRONS_SCROLL_ID =
        ResourceLocation.fromNamespaceAndPath(IronsCompat.MODID, "scroll");

    private IronsBookBindingUtil() {}

    /** True when {@code stack} is the real Iron's scroll item. */
    public static boolean isIronsScroll(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return IRONS_SCROLL_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
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
        // Gated + FQN so the Iron's-importing helper only classloads when Iron's is present.
        if (IronsCompat.isLoaded()
            && com.otectus.arsnspells.spell.irons.IronsScrollFactory.isSpellBookItem(stack)) {
            return true;
        }
        try {
            return stack.is(com.otectus.arsnspells.registry.ModTags.IRONS_SPELL_BOOKS);
        } catch (Exception e) {
            // Tag lookups throw if consulted before tags bind (early registry, some tests).
            // Falling through to false is correct: with Iron's loaded the interface check
            // above has already answered, and without it there is no book to bind onto anyway.
            return false;
        }
    }

    /**
     * Extracts the serialized Ars spell from a carrier scroll, requiring exactly
     * one cross-cast entry and that it is an Ars entry with a non-empty
     * {@code arsSpellTag} payload. Returns empty otherwise (ambiguous or non-Ars
     * carriers are rejected rather than guessed at).
     */
    public static Optional<CompoundTag> extractSingleArsEntry(ItemStack carrierScroll) {
        return extractSingleEntry(carrierScroll)
            .flatMap(CrossModSpell::arsSpellTag)
            .map(CompoundTag::copy);
    }

    /**
     * Like {@link #extractSingleArsEntry(ItemStack)} but returns the <em>whole</em>
     * cross-spell entry (including any Spell Loom display metadata — custom name,
     * nature, icon), not just the {@code arsSpellTag} payload. Used by the binding
     * step so a scroll's chosen name/nature/icon ride onto the book.
     */
    public static Optional<CrossModSpell> extractSingleEntry(ItemStack carrierScroll) {
        if (carrierScroll == null || carrierScroll.isEmpty()) {
            return Optional.empty();
        }
        return extractSingleEntry(CrossModSpellComponents.get(carrierScroll));
    }

    /**
     * List-level companion to {@link #extractSingleEntry(ItemStack)} so the
     * carrier-validation contract is unit-testable without bootstrapping the
     * item registry: exactly one entry, resolved type ARS_NOUVEAU, non-empty
     * {@code arsSpellTag}.
     */
    public static Optional<CrossModSpell> extractSingleEntry(CrossModSpellList list) {
        if (list == null || list.size() != 1) {
            return Optional.empty();
        }
        CrossModSpell entry = list.spells().get(0);
        if (CrossCastValidator.resolveType(entry) != CrossSpellType.ARS_NOUVEAU) {
            return Optional.empty();
        }
        if (entry.arsSpellTag().isEmpty() || entry.arsSpellTag().get().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /**
     * True when {@code book} already carries an Ars entry whose payload equals
     * {@code arsTag}. Dedup keys off the serialized payload, not the shared
     * placeholder id.
     */
    public static boolean containsEquivalentArsSpell(ItemStack book, CompoundTag arsTag) {
        if (book == null || book.isEmpty() || arsTag == null) {
            return false;
        }
        return CrossModSpellComponents.containsEquivalentArsSpell(
            CrossModSpellComponents.get(book), arsTag);
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
        if (book == null || book.isEmpty()) {
            return AppendResult.FAILED;
        }
        CrossModSpellList list = CrossModSpellComponents.get(book);
        PlannedAppend plan = planAppend(list, arsTag, maxCap);
        if (plan.result() != AppendResult.ADDED) {
            return plan.result();
        }
        CrossModSpellComponents.addArsEntryWithMeta(book,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, arsTag.copy(),
            plan.poolId(), customName, nature, iconSymbol);
        // Mirror into Iron's native container so the entry shows in the wheel.
        // Gated + referenced by FQN so IronsProxySlotWriter (which imports Iron's
        // API) only classloads when Iron's is present.
        if (IronsCompat.isLoaded()) {
            com.otectus.arsnspells.spell.irons.IronsProxySlotWriter.addProxySlot(book, plan.poolId(), 1);
        }
        return AppendResult.ADDED;
    }

    /** Decision + allocated pool id for a bind attempt (pure; unit-testable). */
    public record PlannedAppend(AppendResult result, int poolId) {}

    /**
     * The pure decision core of {@link #appendArsSpellToBook}: dedup check and
     * proxy pool allocation against the book's current list, without touching an
     * {@code ItemStack} or any registry.
     */
    public static PlannedAppend planAppend(CrossModSpellList list, CompoundTag arsTag, int maxCap) {
        if (arsTag == null || arsTag.isEmpty()) {
            return new PlannedAppend(AppendResult.FAILED, CrossModSpellComponents.NO_PROXY_POOL_ID);
        }
        if (CrossModSpellComponents.containsEquivalentArsSpell(list, arsTag)) {
            return new PlannedAppend(AppendResult.DUPLICATE, CrossModSpellComponents.NO_PROXY_POOL_ID);
        }
        int ceiling = effectiveProxyCeiling(maxCap);
        int poolId = CrossModSpellComponents.allocateProxyPoolId(list, ceiling);
        if (poolId == CrossModSpellComponents.NO_PROXY_POOL_ID) {
            return new PlannedAppend(AppendResult.BOOK_FULL, CrossModSpellComponents.NO_PROXY_POOL_ID);
        }
        return new PlannedAppend(AppendResult.ADDED, poolId);
    }

    /**
     * The effective per-book Ars ceiling: a negative {@code maxCap} means
     * "no cap" (still bounded by {@link CrossModSpellComponents#PROXY_POOL_SIZE},
     * the number of distinct native-wheel slots that can exist).
     */
    /**
     * True when {@code arsTag} decodes to a spell that can actually be cast: a non-empty
     * recipe with a cast method, whose glyphs all still resolve.
     *
     * <p>Checked before anything is consumed - by the binding ritual, the bind command and
     * the cast gate - because the failure it guards against is silent. Ars 5.x substitutes
     * {@code EffectBreak} for a glyph whose mod has been removed, so the payload still
     * decodes, still passes {@code isValid()}, and casts something the player never built.
     * See {@link ArsSpellIntegrity}.
     */
    public static boolean isCastableArsPayload(CompoundTag arsTag) {
        if (arsTag == null || arsTag.isEmpty()) {
            return false;
        }
        try {
            if (!ArsSpellIntegrity.isIntact(arsTag)) {
                return false;
            }
            Spell spell = CrossCastingHandler.decodeArsSpell(arsTag);
            return spell != null && !spell.isEmpty() && spell.getCastMethod() != null;
        } catch (Throwable t) {
            // Throwable: a linkage error from an Ars/addon skew must read as "not castable",
            // not propagate into a ritual that is about to consume the player's items.
            return false;
        }
    }

    /**
     * Remove every ANS-owned artifact from {@code stack}: native wheel proxy slots first, then
     * the sidecar entries, then ANS's own export marker.
     *
     * <p><b>Order matters.</b> The pool ids live in the sidecar, so clearing the sidecar first
     * loses the only record of which native slots belong to ANS - leaving selectable wheel
     * entries that cast nothing. That is exactly the bug this exists to prevent, and it is what
     * a bare {@code CrossModSpellComponents.clear(stack)} does.
     *
     * @return how many native proxy slots were removed
     */
    public static int removeAllArsEntries(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !CrossModSpellComponents.has(stack)) {
            return 0;
        }
        int removed = 0;
        if (IronsCompat.isLoaded()) {
            // Read the pool ids BEFORE clearing - see the ordering note above.
            for (int poolId : CrossModSpellComponents.usedProxyPoolIds(
                    CrossModSpellComponents.get(stack))) {
                if (com.otectus.arsnspells.spell.irons.IronsProxySlotWriter
                        .removeProxySlot(stack, poolId)) {
                    removed++;
                }
            }
        }
        CrossModSpellComponents.clear(stack);
        // The cosmetic export marker is ANS-owned too; leaving it behind means the result is
        // not byte-identical to a never-inscribed item and it survives a re-transcribe.
        stack.remove(ModDataComponents.EXPORT_MODE.get());
        return removed;
    }

    public static int effectiveProxyCeiling(int maxCap) {
        if (maxCap < 0) {
            return CrossModSpellComponents.PROXY_POOL_SIZE;
        }
        return Math.min(maxCap, CrossModSpellComponents.PROXY_POOL_SIZE);
    }
}
