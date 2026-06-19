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

    /**
     * Appends {@code arsTag} as a new Ars entry on {@code book}. Returns false
     * without mutating when the payload is missing or already present (dedup).
     */
    public static boolean appendArsSpellToBook(ItemStack book, CompoundTag arsTag) {
        if (book == null || book.isEmpty() || arsTag == null || arsTag.isEmpty()) {
            return false;
        }
        if (containsEquivalentArsSpell(book, arsTag)) {
            return false;
        }
        CrossCastNbt.addCrossModSpellToTag(book.getOrCreateTag(), ARS_PLACEHOLDER_ID, 1,
            CrossSpellType.ARS_NOUVEAU, arsTag.copy());
        return true;
    }
}
