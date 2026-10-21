package com.otectus.arsnspells.menu;

import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Menu of the {@link GlyphPressBlockEntity}: its four slots plus the player inventory, and a
 * synchronized view of the server's state. The screen never decides anything: the reason line,
 * the "already known" flag, progress and Source all come from the server through
 * {@link ContainerData}, computed for the player viewing this menu. Two players viewing one Press
 * see the same job.
 */
public class GlyphPressMenu extends AbstractContainerMenu {
    public static final int GUI_WIDTH = 232;
    public static final int GUI_HEIGHT = 232;
    public static final int SLOT_SIZE = 18;
    public static final int INPUT_X = 8;
    public static final int SCROLL_Y = 22;
    public static final int PLATE_Y = 48;
    public static final int CATALYST_X = 28;
    public static final int CATALYST_Y = 48;
    public static final int OUTPUT_X = 82;
    public static final int OUTPUT_Y = 48;
    public static final int INV_LEFT = (GUI_WIDTH - 9 * SLOT_SIZE) / 2;
    public static final int INV_TOP = GUI_HEIGHT - 82;
    public static final int HOTBAR_Y = GUI_HEIGHT - 24;

    // ContainerData indices. Vanilla syncs each value as a short, so large ones are split in two.
    public static final int D_STATE = 0, D_REASON = 1, D_PROGRESS = 2, D_TICKS = 4, D_FREE = 6, D_CAPACITY = 8,
        D_NEED = 10, D_RESERVED = 12, D_PHASE = 14, D_KNOWN = 15, D_GEMS = 16, D_TIER = 17, D_COUNT = 18;

    private final UUID sessionId;
    @Nullable private final GlyphPressBlockEntity press;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public GlyphPressMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf.readBlockPos()), buf.readUUID());
    }

    public GlyphPressMenu(int id, Inventory inv, @Nullable GlyphPressBlockEntity press, UUID sessionId) {
        super(ModMenus.GLYPH_PRESS.get(), id);
        this.sessionId = sessionId;
        this.press = press;
        this.access = press == null || press.getLevel() == null ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(press.getLevel(), press.getBlockPos());
        boolean server = press != null && press.getLevel() != null && !press.getLevel().isClientSide()
            && inv.player instanceof ServerPlayer;
        this.data = server ? new ServerData(press, (ServerPlayer) inv.player) : new SimpleContainerData(D_COUNT);
        if (press != null) {
            addSlot(new InputSlot(press, GlyphPressBlockEntity.SLOT_SCROLL, INPUT_X, SCROLL_Y));
            addSlot(new InputSlot(press, GlyphPressBlockEntity.SLOT_PLATE, INPUT_X, PLATE_Y));
            addSlot(new InputSlot(press, GlyphPressBlockEntity.SLOT_CATALYST, CATALYST_X, CATALYST_Y));
            addSlot(new SlotItemHandler(press.getItems(), GlyphPressBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    press.inventoryChangedInPlace();
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_LEFT + col * SLOT_SIZE, INV_TOP + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_LEFT + col * SLOT_SIZE, HOTBAR_Y));
        addDataSlots(data);
    }

    @Nullable
    private static GlyphPressBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof GlyphPressBlockEntity press ? press : null;
    }

    public UUID sessionId() {
        return sessionId;
    }

    @Nullable
    public GlyphPressBlockEntity press() {
        return press;
    }

    public int value(int index) {
        return data.get(index);
    }

    /** A value split over two synced shorts. */
    public int wide(int index) {
        return (data.get(index) & 0xFFFF) | ((data.get(index + 1) & 0xFFFF) << 16);
    }

    public GlyphPressBlockEntity.State state() {
        GlyphPressBlockEntity.State[] values = GlyphPressBlockEntity.State.values();
        int i = data.get(D_STATE);
        return i >= 0 && i < values.length ? values[i] : GlyphPressBlockEntity.State.IDLE;
    }

    public GlyphPressPlan.Reason reason() {
        return GlyphPressPlan.Reason.byOrdinal(data.get(D_REASON));
    }

    public GlyphPressBlockEntity.Phase phase() {
        GlyphPressBlockEntity.Phase[] values = GlyphPressBlockEntity.Phase.values();
        int i = data.get(D_PHASE);
        return i >= 0 && i < values.length ? values[i] : GlyphPressBlockEntity.Phase.NONE;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int machine = GlyphPressBlockEntity.SLOT_COUNT;
        if (press == null || slots.size() < machine + 36 || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = targetSlot(stack);
            // Never merge into a locked input: vanilla merging grows a stack without asking the slot.
            if (target < 0 || press.locked(target) || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    private int targetSlot(ItemStack stack) {
        if (IronGlyphBridges.get().isScrollItem(stack)) return GlyphPressBlockEntity.SLOT_SCROLL;
        if (GlyphPressBlockEntity.isPlate(stack)) return GlyphPressBlockEntity.SLOT_PLATE;
        if (GlyphPressBlockEntity.isCatalyst(stack)) return GlyphPressBlockEntity.SLOT_CATALYST;
        return -1;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, ModBlocksRegistry.GLYPH_PRESS.get());
    }

    /** An input slot that follows the Press's lock (its handler refuses changes while a job holds it). */
    private static final class InputSlot extends SlotItemHandler {
        private final GlyphPressBlockEntity press;

        InputSlot(GlyphPressBlockEntity press, int index, int x, int y) {
            super(press.getItems(), index, x, y);
            this.press = press;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !press.locked(getSlotIndex()) && super.mayPickup(player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            press.inventoryChangedInPlace();
        }
    }

    /** The server's values for one viewer, re-planned only when the Press or the glyph behaviour changed. */
    private static final class ServerData implements ContainerData {
        private final GlyphPressBlockEntity press;
        private final ServerPlayer viewer;
        private int plannedRevision = Integer.MIN_VALUE;
        private int plannedGeneration = Integer.MIN_VALUE;
        private GlyphPressPlan.Plan plan;
        private boolean known;

        ServerData(GlyphPressBlockEntity press, ServerPlayer viewer) {
            this.press = press;
            this.viewer = viewer;
        }

        private GlyphPressPlan.Plan plan() {
            int generation = GlyphBehavior.current().generation();
            if (plan == null || plannedRevision != press.revision() || plannedGeneration != generation) {
                plan = press.plan(viewer);
                plannedRevision = press.revision();
                plannedGeneration = generation;
                ImportedIronEffect effect = plan.key() == null ? null : ImportedGlyphRegistration.effect(plan.key());
                var cap = effect == null ? null : CapabilityRegistry.getPlayerDataCap(viewer);
                known = cap != null && cap.knowsGlyph(effect);
            }
            return plan;
        }

        @Override
        public int get(int index) {
            var job = press.job();
            GlyphPressPlan.Plan current = plan();
            var behavior = current.behavior();
            return switch (index) {
                case D_STATE -> press.state().ordinal();
                case D_REASON -> job != null ? GlyphPressPlan.Reason.BUSY.ordinal() : current.reason().ordinal();
                case D_PROGRESS -> press.progress() & 0xFFFF;
                case D_PROGRESS + 1 -> press.progress() >>> 16;
                case D_TICKS -> ticks(job, behavior) & 0xFFFF;
                case D_TICKS + 1 -> ticks(job, behavior) >>> 16;
                case D_FREE -> press.freeSource() & 0xFFFF;
                case D_FREE + 1 -> press.freeSource() >>> 16;
                case D_CAPACITY -> press.sourceCapacity() & 0xFFFF;
                case D_CAPACITY + 1 -> press.sourceCapacity() >>> 16;
                case D_NEED -> need(job, behavior) & 0xFFFF;
                case D_NEED + 1 -> need(job, behavior) >>> 16;
                case D_RESERVED -> press.reservedSource() & 0xFFFF;
                case D_RESERVED + 1 -> press.reservedSource() >>> 16;
                case D_PHASE -> press.phase().ordinal();
                case D_KNOWN -> known ? 1 : 0;
                case D_GEMS -> job != null ? job.gems() : behavior == null ? 0 : behavior.pressGems();
                case D_TIER -> job != null ? job.tier() : behavior == null ? 0 : behavior.tier();
                default -> 0;
            };
        }

        private static int ticks(@Nullable com.otectus.arsnspells.block.GlyphPressJob job,
                                 @Nullable com.otectus.arsnspells.glyph.behavior.VariantBehavior behavior) {
            return job != null ? job.ticks() : behavior == null ? 0 : behavior.pressTicks();
        }

        private static int need(@Nullable com.otectus.arsnspells.block.GlyphPressJob job,
                                @Nullable com.otectus.arsnspells.glyph.behavior.VariantBehavior behavior) {
            return job != null ? job.source() : behavior == null ? 0 : behavior.pressSource();
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return D_COUNT;
        }
    }
}
