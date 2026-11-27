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
    // ---- Layout constants ----
    // Single source of truth for slot geometry; the screen derives every dependent coordinate
    // from these. The GUI is 176x240, taller than a vanilla chest, so the name field, recipe
    // row, the school row, two button rows and two status lines fit ABOVE the player inventory,
    // while still fitting Minecraft's minimum 320x240 scaled viewport. At the vanilla 166 height the
    // Inscribe button (y+74..92) overlapped the inventory's top row (y+84..100), and
    // AbstractContainerScreen draws widgets before slot items and the hover highlight - so
    // items and a pale highlight square painted over the button, and the top 9 pixels of nine
    // inventory slots could not be clicked.
    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 240;
    public static final int SLOT_SIZE = 18;
    /** Working-slot columns (16px inner boxes at these x positions). */
    public static final int SLOT_SOURCE_X = 26;
    public static final int SLOT_SCROLL_X = 62;
    public static final int SLOT_OUTPUT_X = 120;
    /** Working-slot row. */
    public static final int RECIPE_ROW_Y = 40;
    /** Player inventory: vanilla formulas for a 240-tall container. */
    public static final int INV_LEFT = 8;
    public static final int INV_TOP = GUI_HEIGHT - 82;
    public static final int HOTBAR_Y = GUI_HEIGHT - 24;

    private final java.util.UUID sessionId;
    public java.util.UUID sessionId() { return sessionId; }
    /** The authoritative Loom; the server's menu only. Null on the client, which never plans against it. */
    private final SpellLoomBlockEntity blockEntity;
    /** The three working slots' contents: the block entity's on the server, a synchronized copy on the client. */
    private final net.neoforged.neoforge.items.IItemHandler workItems;
    private final net.minecraft.core.HolderLookup.Provider registries;
    private final ContainerLevelAccess access;

    public SpellLoomMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf.readBlockPos()), buf.readUUID());
    }

    public SpellLoomMenu(int id, Inventory inv, SpellLoomBlockEntity be) {
        this(id, inv, be, java.util.UUID.randomUUID());
    }

    /**
     * Audit ANS-012: both sides always build the same 39 slots, three working slots then the 36
     * player slots. Only the server's Loom is authoritative; the client always works on its own
     * three-slot copy, filled by the server's menu synchronization, as the Glyph Press does. Its
     * layout therefore never depends on whether the client happened to have the block entity when
     * the menu opened (it used to build only the 36 player slots then, so the server's slot indices
     * described different slots on each side).
     */
    public SpellLoomMenu(int id, Inventory inv, SpellLoomBlockEntity be, java.util.UUID sessionId) {
        super(ModMenus.SPELL_LOOM.get(), id);
        this.sessionId = sessionId;
        this.registries = inv.player.level().registryAccess();
        boolean server = be != null && be.getLevel() != null && !be.getLevel().isClientSide();
        this.blockEntity = server ? be : null;
        this.access = server ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()) : ContainerLevelAccess.NULL;
        this.workItems = server ? be.getItems() : new net.neoforged.neoforge.items.ItemStackHandler(SpellLoomBlockEntity.SLOT_COUNT) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return switch (slot) {
                    case SpellLoomBlockEntity.SLOT_SOURCE -> com.otectus.arsnspells.inscription.LoomInscription.isSource(stack);
                    case SpellLoomBlockEntity.SLOT_SCROLL -> com.otectus.arsnspells.inscription.LoomInscription.isTarget(stack);
                    default -> false;
                };
            }
        };

        addSlot(new SlotItemHandler(workItems, SpellLoomBlockEntity.SLOT_SOURCE,
            SLOT_SOURCE_X, RECIPE_ROW_Y));
        addSlot(new SlotItemHandler(workItems, SpellLoomBlockEntity.SLOT_SCROLL,
            SLOT_SCROLL_X, RECIPE_ROW_Y));
        addSlot(new SlotItemHandler(workItems, SpellLoomBlockEntity.SLOT_OUTPUT,
            SLOT_OUTPUT_X, RECIPE_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        addPlayerInventory(inv);
    }

    private static SpellLoomBlockEntity resolve(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof SpellLoomBlockEntity loom) {
            return loom;
        }
        return null;
    }

    /** The authoritative Loom: the server's menu only, null on the client. */
    public SpellLoomBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /**
     * The three working slots as this side knows them: the Loom's own inventory on the server, the
     * synchronized copy on the client. What the client's planning and fingerprints read.
     */
    public net.neoforged.neoforge.items.IItemHandler workItems() {
        return workItems;
    }

    /** The registries of the player's level, for item data on either side. */
    public net.minecraft.core.HolderLookup.Provider registries() {
        return registries;
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
        if (index < 0 || index >= this.slots.size()) return result;
        // Audit E5 / ANS-012: the layout is always the three working slots then the 36 player
        // slots; this guard only protects the index math below against a future layout change.
        if (this.slots.size() < SpellLoomBlockEntity.SLOT_COUNT + 36) {
            return result;
        }
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
