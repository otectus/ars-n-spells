package com.otectus.arsnspells.menu;

import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Server/client menu for the {@link SpellLoomBlockEntity}: three working slots
 * (Ars source, blank scroll, output) plus the player inventory. The actual
 * inscribe action is a separate server-authoritative payload; this menu only
 * exposes the slots and validates reach.
 */
public class SpellLoomMenu extends AbstractContainerMenu {
    private final SpellLoomBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public SpellLoomMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf.readBlockPos()));
    }

    public SpellLoomMenu(int id, Inventory inv, SpellLoomBlockEntity be) {
        super(ModMenus.SPELL_LOOM.get(), id);
        this.blockEntity = be;
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        if (be != null) {
            addSlot(new SlotItemHandler(be.getItems(), SpellLoomBlockEntity.SLOT_SOURCE, 44, 35));
            addSlot(new SlotItemHandler(be.getItems(), SpellLoomBlockEntity.SLOT_SCROLL, 80, 35));
            addSlot(new SlotItemHandler(be.getItems(), SpellLoomBlockEntity.SLOT_OUTPUT, 134, 35) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        addPlayerInventory(inv);
    }

    private static SpellLoomBlockEntity resolve(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof SpellLoomBlockEntity loom) {
            return loom;
        }
        return null;
    }

    public SpellLoomBlockEntity getBlockEntity() {
        return blockEntity;
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();

        final int containerSlots = SpellLoomBlockEntity.SLOT_COUNT;
        if (index < containerSlots) {
            // From a working slot into the player inventory.
            if (!moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From the player inventory into the source or scroll input slots.
            if (!moveItemStackTo(stack, SpellLoomBlockEntity.SLOT_SOURCE,
                    SpellLoomBlockEntity.SLOT_OUTPUT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, ModBlocksRegistry.SPELL_LOOM.get());
    }
}
