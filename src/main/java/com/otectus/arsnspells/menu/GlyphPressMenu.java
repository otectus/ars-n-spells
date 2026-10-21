package com.otectus.arsnspells.menu;

import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.UUID;

/**
 * Server/client menu for the {@link GlyphPressBlockEntity}: four working slots and the player
 * inventory, plus a synchronized view of the press's state. Every player looking at a press sees
 * the same job; the plan reason and "already known" flag are computed for this menu's own player.
 * Starting and cancelling are separate, request-guarded packets.
 */
public class GlyphPressMenu extends AbstractContainerMenu {
    public static final int GUI_WIDTH = 232;
    public static final int GUI_HEIGHT = 232;
    public static final int SLOT_SIZE = 18;
    public static final int SCROLL_X = 8, SCROLL_Y = 22;
    public static final int PLATE_X = 8, PLATE_Y = 48;
    public static final int CATALYST_X = 28, CATALYST_Y = 48;
    public static final int OUTPUT_X = 82, OUTPUT_Y = 48;
    public static final int INV_LEFT = (GUI_WIDTH - 9 * SLOT_SIZE) / 2;
    public static final int INV_TOP = GUI_HEIGHT - 82;
    public static final int HOTBAR_Y = GUI_HEIGHT - 24;

    /**
     * Indices into the synchronized data. Vanilla sends each value as a 16-bit short, so every
     * quantity that can exceed 32,767 (Source amounts reach 100,000) travels as a low and a high half.
     */
    public static final int D_PROGRESS = 0, D_DURATION = 1, D_SOURCE_LO = 2, D_SOURCE_HI = 3, D_CAPACITY_LO = 4,
        D_CAPACITY_HI = 5, D_STATE = 6, D_PAUSE = 7, D_REASON = 8, D_TIER = 9, D_SOURCE_COST_LO = 10,
        D_SOURCE_COST_HI = 11, D_CATALYSTS = 12, D_KNOWN = 13, D_RESERVED_LO = 14, D_RESERVED_HI = 15, D_COUNT = 16;

    private final UUID sessionId;
    private final GlyphPressBlockEntity press;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Player player;
    private int cachedRevision = Integer.MIN_VALUE;
    private long cachedAt;
    private GlyphPressPlan cachedPlan;

    /** Client: the press may not have reached this client yet; the menu is built the same either way. */
    public GlyphPressMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf.readBlockPos()), buf.readUUID());
    }

    public GlyphPressMenu(int id, Inventory inv, GlyphPressBlockEntity press, UUID sessionId) {
        super(ModMenus.GLYPH_PRESS.get(), id);
        this.sessionId = sessionId;
        this.player = inv.player;
        boolean server = press != null && press.getLevel() != null && !press.getLevel().isClientSide();
        // Only the server's press is authoritative. A client always works on its own copy of the
        // slots, filled by the server's sync, so its slot layout never depends on whether the
        // block entity happened to arrive before the menu did.
        this.press = server ? press : null;
        this.access = server ? ContainerLevelAccess.create(press.getLevel(), press.getBlockPos()) : ContainerLevelAccess.NULL;
        this.data = server ? new ServerData() : new SimpleContainerData(D_COUNT);
        IItemHandler handler = server ? press.getItems() : new ItemStackHandler(GlyphPressBlockEntity.SLOT_COUNT);
        addSlot(new PressSlot(handler, GlyphPressBlockEntity.SLOT_SCROLL, SCROLL_X, SCROLL_Y));
        addSlot(new PressSlot(handler, GlyphPressBlockEntity.SLOT_PLATE, PLATE_X, PLATE_Y));
        addSlot(new PressSlot(handler, GlyphPressBlockEntity.SLOT_CATALYST, CATALYST_X, CATALYST_Y));
        addSlot(new PressSlot(handler, GlyphPressBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, INV_LEFT + col * SLOT_SIZE, INV_TOP + row * SLOT_SIZE));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_LEFT + col * SLOT_SIZE, HOTBAR_Y));
        addDataSlots(data);
    }

    /**
     * A press slot that a running job locks against pickup and placement. The server asks the
     * press; a client predicts the same from the synchronized job state and the press's own
     * per-slot item rules, and the server's answer always wins.
     */
    private final class PressSlot extends SlotItemHandler {
        private final int pressSlot;

        PressSlot(IItemHandler handler, int slot, int x, int y) {
            super(handler, slot, x, y);
            this.pressSlot = slot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !locked(pressSlot) && GlyphPressBlockEntity.accepts(pressSlot, stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !locked(pressSlot);
        }
    }

    /** Whether a running job holds {@code slot}: the press itself on the server, the synced state on a client. */
    public boolean locked(int slot) {
        if (press != null) return press.locked(slot);
        return slot != GlyphPressBlockEntity.SLOT_OUTPUT && state() == GlyphPressBlockEntity.State.PROCESSING;
    }

    /** Server-side data computed from the press and this menu's player. */
    private final class ServerData implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_PROGRESS -> press.progress();
                case D_DURATION -> press.duration();
                case D_SOURCE_LO -> low(press.getSource());
                case D_SOURCE_HI -> high(press.getSource());
                case D_CAPACITY_LO -> low(press.capacity());
                case D_CAPACITY_HI -> high(press.capacity());
                case D_STATE -> press.state().ordinal();
                case D_PAUSE -> press.pause().ordinal();
                case D_REASON -> plan().reason().ordinal();
                case D_TIER -> plan().tier();
                case D_SOURCE_COST_LO -> low(sourceCost());
                case D_SOURCE_COST_HI -> high(sourceCost());
                case D_CATALYSTS -> plan().terms() == null ? 0 : plan().terms().catalystCount();
                case D_KNOWN -> knownForPlayer();
                case D_RESERVED_LO -> low(press.reservedSource());
                case D_RESERVED_HI -> high(press.reservedSource());
                default -> 0;
            };
        }

        private int sourceCost() {
            return press.working() ? press.jobSourceCost() : plan().terms() == null ? 0 : plan().terms().sourceCost();
        }

        @Override public void set(int index, int value) { }
        @Override public int getCount() { return D_COUNT; }
    }

    /** The plan for this menu's player, recomputed when the inventory changes or once a second. */
    private GlyphPressPlan plan() {
        long now = press.getLevel() == null ? 0 : press.getLevel().getGameTime();
        if (cachedPlan == null || cachedRevision != press.revision() || now - cachedAt >= 20) {
            cachedPlan = press.plan(player);
            cachedRevision = press.revision();
            cachedAt = now;
        }
        return cachedPlan;
    }

    /** 1 known, 0 unknown, 2 no glyph identified. */
    private int knownForPlayer() {
        GlyphPressPlan plan = plan();
        if (!plan.identified()) return 2;
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player).orElse(null);
        if (cap == null) return 2;
        return ImportedGlyphRegistrar.part(plan.definition().key()).map(part -> cap.knowsGlyph(part) ? 1 : 0).orElse(2);
    }

    private static GlyphPressBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof GlyphPressBlockEntity press ? press : null;
    }

    private static int low(int value) { return value & 0xFFFF; }
    private static int high(int value) { return (value >>> 16) & 0xFFFF; }
    /** Rejoin a value split by {@link #low}/{@link #high}; the client receives each half sign-extended. */
    private int joined(int lo, int hi) { return (data.get(lo) & 0xFFFF) | ((data.get(hi) & 0xFFFF) << 16); }

    public UUID sessionId() { return sessionId; }
    /** The press this menu drives, on the server only; null on a client. */
    public GlyphPressBlockEntity getBlockEntity() { return press; }
    public int data(int index) { return data.get(index); }
    public int progress() { return data.get(D_PROGRESS); }
    public int duration() { return data.get(D_DURATION); }
    public int source() { return joined(D_SOURCE_LO, D_SOURCE_HI); }
    public int capacity() { return joined(D_CAPACITY_LO, D_CAPACITY_HI); }
    public int sourceCost() { return joined(D_SOURCE_COST_LO, D_SOURCE_COST_HI); }
    public int reserved() { return joined(D_RESERVED_LO, D_RESERVED_HI); }
    public int catalysts() { return data.get(D_CATALYSTS); }
    public int tier() { return data.get(D_TIER); }
    /** 1 known, 0 unknown, 2 no glyph identified. */
    public int known() { return data.get(D_KNOWN); }
    public GlyphPressBlockEntity.State state() {
        GlyphPressBlockEntity.State[] states = GlyphPressBlockEntity.State.values();
        int ordinal = data.get(D_STATE);
        return ordinal >= 0 && ordinal < states.length ? states[ordinal] : GlyphPressBlockEntity.State.IDLE;
    }
    public GlyphPressBlockEntity.Pause pause() {
        GlyphPressBlockEntity.Pause[] pauses = GlyphPressBlockEntity.Pause.values();
        int ordinal = data.get(D_PAUSE);
        return ordinal >= 0 && ordinal < pauses.length ? pauses[ordinal] : GlyphPressBlockEntity.Pause.NONE;
    }
    public GlyphPressPlan.Reason reason() { return GlyphPressPlan.Reason.fromOrdinal(data.get(D_REASON)); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || slots.size() < GlyphPressBlockEntity.SLOT_COUNT + 36)
            return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int pressSlots = GlyphPressBlockEntity.SLOT_COUNT;
        if (index < pressSlots) {
            if (!moveItemStackTo(stack, pressSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Vanilla's merge into a non-empty slot never asks mayPlace, so a running job's locked
            // inputs must be excluded here or shift-click would top up the catalyst stack.
            if (locked(GlyphPressBlockEntity.SLOT_SCROLL)) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, GlyphPressBlockEntity.SLOT_SCROLL, GlyphPressBlockEntity.SLOT_OUTPUT, false))
                return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, ModBlocksRegistry.GLYPH_PRESS.get());
    }
}
