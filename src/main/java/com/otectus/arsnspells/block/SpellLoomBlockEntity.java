package com.otectus.arsnspells.block;

import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import com.otectus.arsnspells.inscription.LoomInscription;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_SOURCE -> LoomInscription.isSource(stack);
                case SLOT_SCROLL -> LoomInscription.isTarget(stack);
                default -> false;
            };
        }

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

    /** Top supplies sources; horizontal faces supply scrolls; bottom extracts output.
     * Unsided pipes may supply either input and extract output. Only the menu can remove inputs. */
    public IItemHandler automation(@Nullable Direction side) {
        return new IItemHandler() {
            @Override public int getSlots() { return SLOT_COUNT; }
            @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                boolean face = side == null || (side == Direction.UP && slot == SLOT_SOURCE)
                    || (side != Direction.UP && side != Direction.DOWN && slot == SLOT_SCROLL);
                return face && items.isItemValid(slot, stack);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isItemValid(slot, stack) ? items.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot == SLOT_OUTPUT && (side == null || side == Direction.DOWN)
                    ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
        };
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

    /** Drops the three working slots into the world (called on block removal). */
    public void dropContents() {
        if (level == null) {
            return;
        }
        SimpleContainer container = new SimpleContainer(items.getSlots());
        for (int i = 0; i < items.getSlots(); i++) {
            container.setItem(i, items.getStackInSlot(i).copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
        Containers.dropContents(level, worldPosition, container);
    }
}
