package com.otectus.arsnspells.block;

import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Backs the {@link SpellLoomBlock}. Holds three slots: an Ars spell source, a
 * blank Iron's scroll, and the inscribed-scroll output. The inscription itself is
 * driven server-side from {@link com.otectus.arsnspells.network.SpellLoomExportPayload}.
 *
 * <p>The item handler is exposed as a block capability via
 * {@code RegisterCapabilitiesEvent} in {@link com.otectus.arsnspells.ArsNSpells}
 * (NeoForge replaced {@code getCapability}/{@code LazyOptional}).
 */
public class SpellLoomBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_SOURCE = 0;
    public static final int SLOT_SCROLL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public SpellLoomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPELL_LOOM.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) {
            items.deserializeNBT(registries, tag.getCompound("items"));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ars_n_spells.spell_loom");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SpellLoomMenu(id, inv, this);
    }

    /**
     * Carry out an {@link InscriptionPlan} against the working slots: the whole mutation, in
     * one place, after every decision has already been made (audit V18/V19).
     *
     * <p>Static and taking the handler so the arithmetic is exercisable without a placed block.
     * Nothing here decides anything - a refused plan is refused, and the slots are not touched.
     *
     * <p>The source is charged {@link InscriptionPlan#consumedUnits()}, which is {@code 0} for a
     * reusable book or focus: reading a spellbook is not allowed to destroy it. The target is
     * charged {@link InscriptionPlan#outputCount()} units and the remainder stays in the slot -
     * a full stack of blanks yields one inscribed item and 63 blanks back, not 64 stamped ones.
     *
     * @param output the finished carrier; its count is set to the plan's output count
     * @return whether the inscription ran
     */
    public static boolean applyInscription(ItemStackHandler items, InscriptionPlan plan,
                                           ItemStack output) {
        if (items == null || plan == null || !plan.isPermitted()
            || output == null || output.isEmpty()) {
            return false;
        }
        if (!items.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            return false;
        }
        ItemStack source = items.getStackInSlot(SLOT_SOURCE);
        ItemStack target = items.getStackInSlot(SLOT_SCROLL);
        if (source.getCount() < plan.consumedUnits() || target.getCount() < plan.outputCount()) {
            return false;
        }
        if (plan.consumedUnits() > 0) {
            items.extractItem(SLOT_SOURCE, plan.consumedUnits(), false);
        }
        items.extractItem(SLOT_SCROLL, plan.outputCount(), false);
        output.setCount(plan.outputCount());
        items.setStackInSlot(SLOT_OUTPUT, output);
        return true;
    }

    /** Drops the three working slots into the world (called on block removal). */
    public void dropContents() {
        if (level == null) {
            return;
        }
        SimpleContainer container = new SimpleContainer(items.getSlots());
        for (int i = 0; i < items.getSlots(); i++) {
            container.setItem(i, items.getStackInSlot(i));
        }
        Containers.dropContents(level, worldPosition, container);
    }
}
