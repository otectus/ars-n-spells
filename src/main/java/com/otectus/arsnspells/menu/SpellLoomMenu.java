package com.otectus.arsnspells.menu;

import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Server/client menu for the {@link SpellLoomBlockEntity}: three working slots
 * (Ars source, blank scroll, output) plus the player inventory. The actual
 * inscribe action is a separate server-authoritative packet; this menu only
 * exposes the slots and validates reach.
 */
public class SpellLoomMenu extends AbstractContainerMenu {
    // ---- Layout constants (single source of truth for slot geometry; the
    // screen derives every dependent coordinate from these). The GUI is
    // 176x208: taller than a vanilla chest so the name field, recipe row, and
    // two button rows fit above the player inventory without overlap.
    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 208;
    public static final int SLOT_SIZE = 18;
    /** Working-slot columns (16px inner boxes at these x positions). */
    public static final int SLOT_SOURCE_X = 44;
    public static final int SLOT_SCROLL_X = 80;
    public static final int SLOT_OUTPUT_X = 134;
    /** Working-slot row. */
    public static final int RECIPE_ROW_Y = 40;
    /** Player inventory: vanilla formulas for a 208-tall container. */
    public static final int INV_LEFT = 8;
    public static final int INV_TOP = GUI_HEIGHT - 82;   // 126
    public static final int HOTBAR_Y = GUI_HEIGHT - 24;  // 184

    private final SpellLoomBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public SpellLoomMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf.readBlockPos()));
    }

    public SpellLoomMenu(int id, Inventory inv, SpellLoomBlockEntity be) {
        super(ModMenus.SPELL_LOOM.get(), id);
        this.blockEntity = be;
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        if (be != null) {
            addSlot(new SlotItemHandler(be.getItems(), SpellLoomBlockEntity.SLOT_SOURCE,
                SLOT_SOURCE_X, RECIPE_ROW_Y));
            addSlot(new SlotItemHandler(be.getItems(), SpellLoomBlockEntity.SLOT_SCROLL,
                SLOT_SCROLL_X, RECIPE_ROW_Y));
            addSlot(new SlotItemHandler(be.getItems(), SpellLoomBlockEntity.SLOT_OUTPUT,
                SLOT_OUTPUT_X, RECIPE_ROW_Y) {
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
                addSlot(new Slot(inv, col + row * 9 + 9,
                    INV_LEFT + col * SLOT_SIZE, INV_TOP + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_LEFT + col * SLOT_SIZE, HOTBAR_Y));
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
