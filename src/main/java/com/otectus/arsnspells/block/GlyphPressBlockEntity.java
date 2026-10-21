package com.otectus.arsnspells.block;

import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.entity.EntityFollowProjectile;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.glyph.recipe.ScrollReading;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import com.otectus.arsnspells.network.LoomInventoryRevision;
import com.otectus.arsnspells.registry.ModBlockEntities;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Glyph Press: four slots (filled Iron's scroll, blank glyph plate, catalyst, result glyph), a
 * Source buffer, and at most one job.
 *
 * <p>States: {@code IDLE} (nothing to inscribe), {@code READY} (Inscribe Glyph would start),
 * {@code PROCESSING} (a job holds the inputs), {@code OUTPUT_READY} (a result waits to be taken).
 * A job first waits for its Source (pulled from loaded nearby jars every
 * {@code glyph_press_source_scan_interval_ticks}, or pushed in by relays), reserves it, runs for its
 * duration, then completes in one mutation: inputs decremented, Source spent, exact glyph inserted,
 * job cleared. Inputs stay in their slots, locked in the item handler itself, so the menu, shift-
 * clicks and every automation route see the same lock. A blocked output pauses; a cancel, a
 * changed recipe, or a disabled Press returns the reservation and unlocks the inputs.
 *
 * <p>Persisted together under one schema version: inventory, free Source, the job and its
 * progress and reservation. A completed output is never replayed: completion clears the job in the
 * same mutation that inserts the result.
 */
public class GlyphPressBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_SCROLL = 0;
    public static final int SLOT_PLATE = 1;
    public static final int SLOT_CATALYST = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;
    public static final int SCHEMA = 1;

    public enum State { IDLE, READY, PROCESSING, OUTPUT_READY }

    public enum Phase { NONE, WAITING_SOURCE, RUNNING, OUTPUT_BLOCKED }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("ArsNSpells/GlyphPress");
    private static final Map<String, TagKey<Item>> TAGS = new ConcurrentHashMap<>();
    /** Diagnostics: Source discovery passes and plans made, across every Press (performance checks read these). */
    public static final java.util.concurrent.atomic.AtomicLong SOURCE_SCANS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong PLANS = new java.util.concurrent.atomic.AtomicLong();

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (locked(slot)) return false;
            return switch (slot) {
                case SLOT_SCROLL -> IronGlyphBridges.get().isScrollItem(stack);
                case SLOT_PLATE -> isPlate(stack);
                case SLOT_CATALYST -> isCatalyst(stack);
                default -> false;
            };
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return locked(slot) ? stack : super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return locked(slot) ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (locked(slot)) return;
            super.setStackInSlot(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }
    };

    private final GlyphPressSourceBuffer source = new GlyphPressSourceBuffer(this);
    @Nullable private GlyphPressJob job;
    private Phase phase = Phase.NONE;
    private int progress;
    private int reserved;
    private boolean completing;
    /** Bumped on every inventory, Source or job change, so open menus re-plan only when something moved. */
    private int revision;
    /** The last job's end, for the menu: a cancellation reason key, or empty. */
    private String lastEnd = "";
    /** School colour of the running job, synced to clients for the particle stream. */
    private int color = 0x9B6BD6;

    public GlyphPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GLYPH_PRESS.get(), pos, state);
    }

    // ---- Inventory ----

    public ItemStackHandler getItems() {
        return items;
    }

    /** Inputs are locked while a job holds them; the output never is. */
    public boolean locked(int slot) {
        return job != null && !completing && slot != SLOT_OUTPUT;
    }

    public static boolean isPlate(ItemStack stack) {
        var plate = ModItemsRegistry.blankGlyphPlate();
        return plate != null && plate.isBound() && stack.is(plate.get());
    }

    /** Any item in a catalyst tag the current glyph behaviour names. */
    public static boolean isCatalyst(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (String tag : GlyphBehavior.current().catalystTags()) {
            if (inTag(stack, tag)) return true;
        }
        return false;
    }

    static boolean inTag(ItemStack stack, String tagId) {
        ResourceLocation id = ResourceLocation.tryParse(tagId);
        if (id == null) return false;
        return stack.is(TAGS.computeIfAbsent(tagId, k -> TagKey.create(Registries.ITEM, id)));
    }

    /**
     * Top supplies scrolls, horizontal faces supply plates and catalysts, the bottom extracts the
     * result; an unsided handler may insert any input and extract the result. Locked inputs refuse
     * insertion and extraction on every face, simulated or not.
     */
    public IItemHandler automation(@Nullable Direction side) {
        return new IItemHandler() {
            @Override public int getSlots() { return SLOT_COUNT; }
            @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                boolean face = side == null || (side == Direction.UP && slot == SLOT_SCROLL)
                    || (side != Direction.UP && side != Direction.DOWN && (slot == SLOT_PLATE || slot == SLOT_CATALYST));
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

    // ---- Source ----

    public com.hollingsworth.arsnouveau.api.source.ISourceCap source() {
        return source;
    }

    public int sourceCapacity() {
        try {
            return AnsConfig.GLYPH_PRESS_SOURCE_CAPACITY.get();
        } catch (IllegalStateException notLoaded) {
            return 4000;
        }
    }

    public int freeSource() {
        return source.free();
    }

    public int reservedSource() {
        return reserved;
    }

    void sourceChanged() {
        changed();
    }

    // ---- Planning ----

    /** What Inscribe Glyph would do now for {@code player} (null: no acting player, prerequisites skipped). */
    public GlyphPressPlan.Plan plan(@Nullable ServerPlayer player) {
        PLANS.incrementAndGet();
        ScrollReading reading = IronGlyphBridges.get().readScroll(items.getStackInSlot(SLOT_SCROLL));
        Boolean learned = player == null || reading.spellId() == null ? null
            : IronGlyphBridges.get().hasLearned(player, reading.spellId());
        ItemStack catalyst = items.getStackInSlot(SLOT_CATALYST);
        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        GlyphPressPlan.Inputs inputs = new GlyphPressPlan.Inputs(reading, isPlate(items.getStackInSlot(SLOT_PLATE)),
            catalyst.getCount(), tag -> inTag(catalyst, tag),
            output.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(output.getItem()), output.getCount(),
            output.isEmpty() ? 64 : Math.min(output.getMaxStackSize(), items.getSlotLimit(SLOT_OUTPUT)), learned,
            sourceCapacity());
        return GlyphPressPlan.plan(inputs, ImportedGlyphCatalog.get(), GlyphBehavior.current());
    }

    public State state() {
        if (job != null) return State.PROCESSING;
        if (plan(null).ok()) return State.READY;
        return items.getStackInSlot(SLOT_OUTPUT).isEmpty() ? State.IDLE : State.OUTPUT_READY;
    }

    // ---- Jobs ----

    /**
     * Start a job for {@code player}: re-plan from the current authoritative inputs, fix the terms,
     * lock the inputs and reserve Source if the buffer already holds it.
     */
    public GlyphPressPlan.Reason inscribe(ServerPlayer player) {
        if (job != null) return GlyphPressPlan.Reason.BUSY;
        GlyphPressPlan.Plan plan = plan(player);
        if (!plan.ok()) return plan.reason();
        var behavior = plan.behavior();
        job = new GlyphPressJob(UUID.randomUUID(), player.getUUID(), plan.key(), behavior.tier(), behavior.pressGems(),
            behavior.pressSource(), behavior.pressTicks(), behavior.catalyst(), inputFingerprint(), GlyphBehavior.current().digest());
        phase = Phase.WAITING_SOURCE;
        progress = 0;
        reserved = 0;
        lastEnd = "";
        color = IronGlyphBridges.get().schoolColor(plan.key().ironSpellId());
        tryReserve();
        setWorking(true);
        sound(ModSounds.GLYPH_PRESS_ENGRAVE.get(), 0.7f);
        changed();
        return GlyphPressPlan.Reason.OK;
    }

    /** Cancel the running job: inputs unlock in place and the reservation returns to the buffer. */
    public void cancel(String reasonKey) {
        if (job == null) return;
        LOGGER.info("[GlyphPress] job {} at {} cancelled ({}); inputs unlocked, {} Source returned", job.operation(),
            worldPosition.toShortString(), reasonKey, reserved);
        source.setFree(source.free() + reserved);
        reserved = 0;
        job = null;
        phase = Phase.NONE;
        progress = 0;
        lastEnd = reasonKey;
        setWorking(false);
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GlyphPressBlockEntity press) {
        press.tickServer();
    }

    private void tickServer() {
        if (job == null || level == null) return;
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        if (!snapshot.settings().pressEnabled()) {
            cancel(GlyphPressPlan.Reason.PRESS_DISABLED.translationKey());
            return;
        }
        switch (phase) {
            case WAITING_SOURCE -> {
                if (!tryReserve() && level.getGameTime() % scanInterval() == 0) {
                    pullSource(job.source() - source.free());
                    tryReserve();
                }
            }
            case RUNNING -> {
                if (progress < job.ticks()) {
                    progress++;
                    setChanged();
                    if (progress % 40 == 0) sound(ModSounds.GLYPH_PRESS_ENGRAVE.get(), 0.45f);
                }
                if (progress >= job.ticks()) complete();
            }
            case OUTPUT_BLOCKED -> {
                if (level.getGameTime() % 10 == 0) complete();
            }
            case NONE -> phase = Phase.WAITING_SOURCE;
        }
    }

    private boolean tryReserve() {
        if (job == null || phase != Phase.WAITING_SOURCE || source.free() < job.source()) return false;
        source.setFree(source.free() - job.source());
        reserved = job.source();
        phase = Phase.RUNNING;
        changed();
        return true;
    }

    /** Pull up to {@code needed} Source from loaded jars within the configured radius, through Ars's own source API. */
    private void pullSource(int needed) {
        if (!(level instanceof ServerLevel server) || needed <= 0) return;
        int room = sourceCapacity() - source.free() - reserved;
        int want = Math.min(needed, room);
        if (want <= 0) return;
        int got = 0;
        SOURCE_SCANS.incrementAndGet();
        for (ISpecialSourceProvider provider : SourceUtil.canTakeSource(worldPosition, level, scanRadius())) {
            if (got >= want) break;
            var tile = provider.getSource();
            int available = tile instanceof com.hollingsworth.arsnouveau.common.block.tile.CreativeSourceJarTile ? want - got
                : Math.min(want - got, tile.getSource());
            if (available <= 0) continue;
            tile.removeSource(available);
            got += available;
            EntityFollowProjectile.spawn(server, provider.getCurrentPos(), worldPosition);
        }
        if (got > 0) source.setFree(source.free() + got);
    }

    private void complete() {
        GlyphPressJob running = job;
        if (running == null) return;
        // Iron's spell config is not built until the first data pack sync (on a dedicated server,
        // the first login or /reload). Judging the job on its fallbacks would cancel valid jobs,
        // so a finished job waits, unchanged, and is judged against the real config.
        if (GlyphBehavior.current().ironsConfigPending()) return;
        GlyphPressPlan.Plan now = plan(null);
        boolean sameTerms = now.key() != null && now.key().equals(running.key()) && now.behavior() != null
            && now.behavior().available() && now.behavior().tier() == running.tier() && now.behavior().pressGems() == running.gems()
            && now.behavior().pressSource() == running.source() && now.behavior().pressTicks() == running.ticks()
            && now.behavior().catalyst().equals(running.catalyst());
        if (now.reason() == GlyphPressPlan.Reason.OUTPUT_FULL && sameTerms) {
            if (phase != Phase.OUTPUT_BLOCKED) {
                phase = Phase.OUTPUT_BLOCKED;
                changed();
            }
            return;
        }
        if (!now.ok() || !sameTerms) {
            cancel(now.ok() ? "ars_n_spells.glyph_press.cancel.changed" : now.reason().translationKey());
            return;
        }
        if (!running.fingerprint().equals(inputFingerprint())) {
            cancel("ars_n_spells.glyph_press.cancel.inputs_changed");
            return;
        }
        ImportedIronEffect effect = ImportedGlyphRegistration.effect(running.key());
        if (effect == null) {
            cancel("ars_n_spells.glyph_press.reason.no_adapter");
            return;
        }
        completing = true;
        try {
            shrink(SLOT_SCROLL, 1);
            shrink(SLOT_PLATE, 1);
            shrink(SLOT_CATALYST, running.gems());
            ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
            items.setStackInSlot(SLOT_OUTPUT, output.isEmpty() ? new ItemStack(effect.getGlyph()) : output.copyWithCount(output.getCount() + 1));
            reserved = 0;
            job = null;
            phase = Phase.NONE;
            progress = 0;
            lastEnd = "";
        } finally {
            completing = false;
        }
        setWorking(false);
        sound(ModSounds.GLYPH_PRESS_COMPLETE.get(), 0.9f);
        changed();
    }

    private void shrink(int slot, int count) {
        ItemStack stack = items.getStackInSlot(slot);
        items.setStackInSlot(slot, stack.getCount() <= count ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - count));
    }

    /** SHA-256 over the three input slots' full saved data. */
    String inputFingerprint() {
        if (level == null) return "";
        return LoomInventoryRevision.digest(List.of(items.getStackInSlot(SLOT_SCROLL), items.getStackInSlot(SLOT_PLATE),
            items.getStackInSlot(SLOT_CATALYST)), level.registryAccess());
    }

    private int scanInterval() {
        try {
            return Math.max(1, AnsConfig.GLYPH_PRESS_SOURCE_SCAN_INTERVAL_TICKS.get());
        } catch (IllegalStateException notLoaded) {
            return 20;
        }
    }

    private int scanRadius() {
        try {
            return AnsConfig.GLYPH_PRESS_SOURCE_RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            return 4;
        }
    }

    private void setWorking(boolean working) {
        if (level == null) return;
        BlockState state = getBlockState();
        if (state.hasProperty(GlyphPressBlock.WORKING) && state.getValue(GlyphPressBlock.WORKING) != working) {
            level.setBlock(worldPosition, state.setValue(GlyphPressBlock.WORKING, working), 3);
        }
    }

    private void sound(net.minecraft.sounds.SoundEvent event, float volume) {
        if (level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, event, SoundSource.BLOCKS, volume, 0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    private void changed() {
        revision++;
        setChanged();
    }

    /**
     * A menu slot changed one of this Press's stacks in place. Vanilla container merging (shift-click
     * onto a non-empty slot) grows or shrinks the stored stack directly and only calls
     * {@code Slot.setChanged()}, which never reaches the item handler. Without this, open menus would
     * keep a stale plan and the Press would not be saved with the new count.
     */
    public void inventoryChangedInPlace() {
        changed();
    }

    // ---- Views for the menu ----

    @Nullable
    public GlyphPressJob job() {
        return job;
    }

    public Phase phase() {
        return phase;
    }

    public int progress() {
        return progress;
    }

    public int revision() {
        return revision;
    }

    public String lastEnd() {
        return lastEnd;
    }

    public int color() {
        return color;
    }

    // ---- Persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("press_schema", SCHEMA);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("source", source.free());
        tag.putInt("color", color);
        if (job != null) {
            CompoundTag saved = job.save();
            saved.putString("phase", phase.name());
            saved.putInt("progress", progress);
            saved.putInt("reserved", reserved);
            tag.put("job", saved);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        job = null;
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        source.setFree(tag.getInt("source"));
        color = tag.contains("color") ? tag.getInt("color") : color;
        int schema = tag.getInt("press_schema");
        if (!tag.contains("job")) return;
        CompoundTag saved = tag.getCompound("job");
        int savedReserve = Math.max(0, saved.getInt("reserved"));
        GlyphPressJob loaded = schema == SCHEMA ? GlyphPressJob.load(saved) : null;
        Phase loadedPhase = parsePhase(saved.getString("phase"));
        if (loaded == null || loadedPhase == Phase.NONE) {
            // An unreadable or future job is cancelled on load: its items are already in their
            // slots, unlocked, and its reservation goes back to the buffer.
            source.setFree(source.free() + savedReserve);
            lastEnd = "ars_n_spells.glyph_press.cancel.unreadable";
            return;
        }
        job = loaded;
        phase = loadedPhase;
        progress = Math.max(0, Math.min(saved.getInt("progress"), loaded.ticks()));
        reserved = phase == Phase.WAITING_SOURCE ? 0 : savedReserve;
        if (phase == Phase.WAITING_SOURCE) source.setFree(source.free() + savedReserve);
    }

    private static Phase parsePhase(String name) {
        try {
            return Phase.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return Phase.NONE;
        }
    }

    /** Clients only need the particle colour; whether the Press works is its block state. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("color", color);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        color = tag.getInt("color");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) handleUpdateTag(tag, registries);
    }

    // ---- Menu and removal ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ars_n_spells.glyph_press");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new GlyphPressMenu(id, inventory, this, UUID.randomUUID());
    }

    /**
     * Drop every slot exactly once when the block goes. A running job is cleared first, so its
     * inputs come back as themselves (never a glyph plus inputs) and its Source dissipates.
     */
    public void dropContents() {
        if (level == null) return;
        job = null;
        reserved = 0;
        phase = Phase.NONE;
        SimpleContainer container = new SimpleContainer(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            container.setItem(i, items.getStackInSlot(i).copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
        Containers.dropContents(level, worldPosition, container);
    }
}
