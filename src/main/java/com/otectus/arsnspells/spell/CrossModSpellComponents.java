package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure-helper façade over the {@link ModDataComponents#CROSS_SPELLS}
 * data component. Replaces the 1.20.1-era {@code CrossCastNbt} class that
 * worked directly on {@link CompoundTag}. Same operational shape (mutate
 * the cross-cast payload in place), now via component reads/writes.
 */
public final class CrossModSpellComponents {
    private CrossModSpellComponents() {}

    /** True when the stack carries at least one inscribed spell. */
    public static boolean has(ItemStack stack) {
        CrossModSpellList list = stack.get(ModDataComponents.CROSS_SPELLS.get());
        return list != null && !list.isEmpty();
    }

    /** Read the inscribed-spell payload, never null. */
    public static CrossModSpellList get(ItemStack stack) {
        CrossModSpellList list = stack.get(ModDataComponents.CROSS_SPELLS.get());
        return list != null ? list : CrossModSpellList.EMPTY;
    }

    /** Drop every inscription artifact from the stack. */
    public static void clear(ItemStack stack) {
        stack.remove(ModDataComponents.CROSS_SPELLS.get());
    }

    /**
     * Append an entry. Mirrors the legacy
     * {@code CrossModSpellComponents.addCrossModSpell} contract: spellId may be
     * null for Ars-spell entries that carry data only in {@code arsSpellTag}.
     */
    public static void addCrossModSpell(ItemStack stack,
                                        @Nullable ResourceLocation spellId,
                                        int spellLevel,
                                        @Nullable CrossSpellType type,
                                        @Nullable CompoundTag arsSpellTag) {
        CrossModSpellList existing = get(stack);
        ResourceLocation safeId = spellId != null
            ? spellId
            : ResourceLocation.fromNamespaceAndPath("ars_nouveau", "spell");
        String typeName = type != null ? type.name() : CrossSpellType.ARS_NOUVEAU.name();
        CrossModSpell entry = new CrossModSpell(
            safeId,
            spellLevel,
            typeName,
            Optional.ofNullable(arsSpellTag),
            Optional.empty()
        );
        List<CrossModSpell> next = new ArrayList<>(existing.spells());
        next.add(entry);
        stack.set(ModDataComponents.CROSS_SPELLS.get(),
            new CrossModSpellList(List.copyOf(next), existing.selectedIndex()));
    }

    /** Replace just the selected index, preserving the spell list. */
    public static void setSelectedIndex(ItemStack stack, int index) {
        CrossModSpellList existing = get(stack);
        if (existing.isEmpty()) return;
        int clamped = Math.max(0, Math.min(index, existing.size() - 1));
        stack.set(ModDataComponents.CROSS_SPELLS.get(),
            new CrossModSpellList(existing.spells(), clamped));
    }
}
