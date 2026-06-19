package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Classifies dropped items for the spellbook-binding ritual into three disjoint
 * buckets:
 * <ul>
 *     <li>{@link #carrierScrolls} &mdash; real Iron's scrolls carrying exactly one
 *         exported Ars spell entry, produced by the export step.</li>
 *     <li>{@link #spellbooks} &mdash; real Iron's spell books to bind onto.</li>
 *     <li>{@link #other} &mdash; everything else.</li>
 * </ul>
 *
 * <p>This is deliberately a separate classifier from {@link InscriptionInputs}:
 * the binding step requires recognizing real Iron's items, whereas transcription
 * targets generic blanks. Recognition is registry-id based and never loads Iron's
 * classes.
 */
public final class SpellbookBindingInputs {
    public final List<ItemEntity> carrierScrolls;
    public final List<ItemEntity> spellbooks;
    public final List<ItemEntity> other;

    private SpellbookBindingInputs(List<ItemEntity> carrierScrolls,
                                   List<ItemEntity> spellbooks,
                                   List<ItemEntity> other) {
        this.carrierScrolls = Collections.unmodifiableList(carrierScrolls);
        this.spellbooks = Collections.unmodifiableList(spellbooks);
        this.other = Collections.unmodifiableList(other);
    }

    public static SpellbookBindingInputs classify(List<ItemEntity> entities) {
        List<ItemEntity> scrolls = new ArrayList<>();
        List<ItemEntity> books = new ArrayList<>();
        List<ItemEntity> other = new ArrayList<>();
        for (ItemEntity entity : entities) {
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (IronsBookBindingUtil.isIronsScroll(stack)
                && IronsBookBindingUtil.extractSingleArsEntry(stack).isPresent()) {
                scrolls.add(entity);
            } else if (IronsBookBindingUtil.isIronsSpellBook(stack)) {
                books.add(entity);
            } else {
                other.add(entity);
            }
        }
        return new SpellbookBindingInputs(scrolls, books, other);
    }
}
