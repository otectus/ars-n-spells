package com.otectus.arsnspells.block;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlanner;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import com.otectus.arsnspells.registry.ModBlockEntities;
import com.otectus.arsnspells.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The Glyph Press: presses a filled Iron's scroll into a learnable imported glyph.
 *
 * <p>Four slots: the scroll, a blank glyph plate, the catalyst stack (Source gems) and the output.
 * A conversion is a job with its own id, the fingerprint of the inputs it was planned from, and
 * the exact terms (Source, catalysts, duration) its plan quoted. While a job runs its inputs are
 * locked against every route (the screen, shift-click, hoppers, pipes), stay physically in their
 * slots, and are consumed only when the job completes, in one mutation that also consumes the
 * reserved Source and inserts the glyph. A blocked output pauses the job; a cancel, a setting
 * turned off or a plan that no longer holds after a reload ends it with the inputs untouched and
 * the reserved Source returned to the buffer.
 *
 * <p>Source comes through Ars's own interfaces: the press is an {@link ISourceTile} relays can
 * deliver to, and while (and only while) a job still needs Source it asks nearby jars for it
 * through {@link SourceUtil}, at {@code glyph_press_source_scan_interval_ticks} within
 * {@code glyph_press_source_radius}, which only considers loaded positions. An idle press does no
 * work at all on its tick.
 *
 * <p>Saved with a schema version; a job that cannot be resumed on load is cancelled with its
 * items kept, and a completed job's output is never produced twice.
 */
public class GlyphPressBlockEntity extends BlockEntity implements MenuProvider, ISourceTile {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphPressBlockEntity.class);
    public static final int SLOT_SCROLL = 0, SLOT_PLATE = 1, SLOT_CATALYST = 2, SLOT_OUTPUT = 3, SLOT_COUNT = 4;
    public static final int SCHEMA_VERSION = 1;
    private static final int TRANSFER_RATE = 1000;
    private static final int ENGRAVE_SOUND_INTERVAL = 40;

    /** Why a running job is not advancing. */
    public enum Pause { NONE, AWAITING_SOURCE, OUTPUT_BLOCKED }

    /** The display state, derived from the job and the inputs. */
    public enum State { IDLE, READY, PROCESSING, OUTPUT_READY }

    /** One running conversion. Mutable only on the server thread. */
    static final class Job {
        UUID id;
        String fingerprint;
        ResourceLocation glyph;
        int sourceCost;
        int duration;
        int catalysts;
        int reservedSource;
        int progress;
        Pause pause = Pause.NONE;
        UUID starter;
        String mappingDigest;
        int schoolColor;
    }

    private final PressItems items = new PressItems();
    private LazyOptional<IItemHandler> unsided = automation(null);
    private final EnumMap<Direction, LazyOptional<IItemHandler>> sided = new EnumMap<>(Direction.class);
    private int source;
    @Nullable private Job job;
    private long nextSourceScan;
    private int revision;
    /** Set by {@link #load} when a job was restored; cleared by its first tick's revalidation. */
    private boolean revalidateRestoredJob;
    /** Client view: whether a job is running, and its school colour, for the working visuals. */
    private boolean clientWorking;
    private int clientSchoolColor = 0x9966FF;

    public GlyphPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GLYPH_PRESS.get(), pos, state);
        for (Direction direction : Direction.values()) sided.put(direction, automation(direction));
    }

    // ---- Inventory ------------------------------------------------------------------------

    /** The press's own slots. Inputs are refused, for insertion and extraction alike, while a job holds them. */
    final class PressItems extends ItemStackHandler {
        PressItems() {
            super(SLOT_COUNT);
        }

        boolean locked(int slot) {
            return job != null && slot != SLOT_OUTPUT;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !locked(slot) && accepts(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_SCROLL ? 1 : 64;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (locked(slot)) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (locked(slot)) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            revision++;
            setChanged();
        }

        /** Direct access for the press's own completion; bypasses the locks it enforces on others. */
        void set(int slot, ItemStack stack) {
            stacks.set(slot, stack);
            onContentsChanged(slot);
        }
    }

    /** What each slot can ever hold, before any job lock: the menu's client prediction uses the same rule. */
    public static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_SCROLL -> GlyphPressPlanner.mayBeScroll(stack);
            case SLOT_PLATE -> GlyphPressPlanner.isPlate(stack);
            case SLOT_CATALYST -> GlyphPressPlanner.isCatalyst(stack, null);
            default -> false;
        };
    }

    public ItemStackHandler getItems() {
        return items;
    }

    /** Whether a running job holds {@code slot}. A client knows only that a job runs, from the update tag. */
    public boolean locked(int slot) {
        if (level != null && level.isClientSide()) return clientWorking && slot != SLOT_OUTPUT;
        return items.locked(slot);
    }

    /** Top supplies scrolls, horizontal faces supply plates and catalysts, bottom extracts the glyph; unsided may do both. */
    private LazyOptional<IItemHandler> automation(@Nullable Direction side) {
        return LazyOptional.of(() -> new IItemHandler() {
            @Override public int getSlots() { return SLOT_COUNT; }
            @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                boolean face = side == null || (side == Direction.UP && slot == SLOT_SCROLL)
                    || (side.getAxis().isHorizontal() && (slot == SLOT_PLATE || slot == SLOT_CATALYST));
                return face && items.isItemValid(slot, stack);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isItemValid(slot, stack) ? items.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot == SLOT_OUTPUT && (side == null || side == Direction.DOWN)
                    ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
        });
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return (side == null ? unsided : sided.get(side)).cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        unsided.invalidate();
        sided.values().forEach(LazyOptional::invalidate);
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        unsided = automation(null);
        for (Direction direction : Direction.values()) sided.put(direction, automation(direction));
    }

    // ---- Planning and jobs ----------------------------------------------------------------

    public int capacity() {
        try {
            return AnsConfig.GLYPH_PRESS_SOURCE_CAPACITY.get();
        } catch (IllegalStateException unloaded) {
            return 4000;
        }
    }

    /** What the press would do now for {@code player} (null skips Iron's learning requirement). */
    public GlyphPressPlan plan(@Nullable Player player) {
        return GlyphPressPlanner.plan(items.getStackInSlot(SLOT_SCROLL), items.getStackInSlot(SLOT_PLATE),
            items.getStackInSlot(SLOT_CATALYST), items.getStackInSlot(SLOT_OUTPUT), player, capacity());
    }

    public State state() {
        if (job != null) return State.PROCESSING;
        if (!items.getStackInSlot(SLOT_OUTPUT).isEmpty()) return State.OUTPUT_READY;
        return idlePlan().ok() ? State.READY : State.IDLE;
    }

    /** {@link #plan(Player) plan(null)} for the state view, cached per inventory revision, snapshot and second. */
    private GlyphPressPlan idlePlan() {
        long now = level == null ? 0 : level.getGameTime();
        GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.current();
        if (idlePlan == null || idlePlanRevision != revision || idlePlanSnapshot != snapshot
                || now - idlePlanAt >= 20 || now < idlePlanAt) {
            idlePlan = plan(null);
            idlePlanRevision = revision;
            idlePlanSnapshot = snapshot;
            idlePlanAt = now;
        }
        return idlePlan;
    }

    @Nullable private GlyphPressPlan idlePlan;
    private int idlePlanRevision = -1;
    @Nullable private GlyphMappingSnapshot idlePlanSnapshot;
    private long idlePlanAt;

    public boolean working() {
        return job != null;
    }

    public int progress() { return job == null ? 0 : job.progress; }
    public int duration() { return job == null ? 0 : job.duration; }
    public int reservedSource() { return job == null ? 0 : job.reservedSource; }
    public int jobSourceCost() { return job == null ? 0 : job.sourceCost; }
    public Pause pause() { return job == null ? Pause.NONE : job.pause; }
    public int revision() { return revision; }
    @Nullable public UUID jobId() { return job == null ? null : job.id; }

    /** Result reason codes the start action answers with, besides the plan's own. */
    public static final String STARTED = "started", ALREADY_WORKING = "already_working", NOT_WORKING = "not_working", CANCELLED = "cancelled";

    /**
     * Start a job for {@code player} from the current inputs. Everything is derived here from the
     * press's own inventory and the current snapshot; nothing the client sent is trusted.
     *
     * @return {@link #STARTED}, {@link #ALREADY_WORKING}, or the plan's refusal code
     */
    public String start(ServerPlayer player) {
        if (job != null) return ALREADY_WORKING;
        GlyphPressPlan plan = plan(player);
        if (!plan.ok()) return plan.reason().code();
        ImportedGlyphDefinition definition = plan.definition();
        Job created = new Job();
        created.id = UUID.randomUUID();
        created.fingerprint = inputFingerprint();
        created.glyph = definition.glyphId();
        created.sourceCost = plan.terms().sourceCost();
        created.duration = plan.terms().durationTicks();
        created.catalysts = plan.terms().catalystCount();
        created.starter = player.getUUID();
        created.mappingDigest = GlyphMappingSnapshot.current().digest();
        created.schoolColor = schoolColor(definition);
        job = created;
        takeFromBuffer();
        revision++;
        setChanged();
        showWorking(true);
        syncToClients();
        if (level != null) level.playSound(null, worldPosition, ModSounds.GLYPH_PRESS_ENGRAVE.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
        return STARTED;
    }

    /** Cancel the running job: inputs unlock untouched, reserved Source returns to the buffer. */
    public String cancel(String reason) {
        if (job == null) return NOT_WORKING;
        source = Math.min(capacity(), source + job.reservedSource);
        LOGGER.debug("[GlyphPress] {} job {} cancelled: {}", worldPosition, job.id, reason);
        job = null;
        revision++;
        setChanged();
        showWorking(false);
        syncToClients();
        return CANCELLED;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GlyphPressBlockEntity press) {
        press.showWorking(press.job != null);
        if (press.job == null) return;
        press.tickJob(level);
    }

    /** Keep the block state's WORKING flag (the lit rune plate) equal to whether a job runs. */
    private void showWorking(boolean working) {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(GlyphPressBlock.WORKING) || state.getValue(GlyphPressBlock.WORKING) == working) return;
        level.setBlock(worldPosition, state.setValue(GlyphPressBlock.WORKING, working), Block.UPDATE_CLIENTS);
    }

    private void tickJob(Level level) {
        Job running = job;
        if (!pressAllowed()) { cancel("disabled"); return; }
        if (revalidateRestoredJob) {
            revalidateRestoredJob = false;
            if (!running.fingerprint.equals(inputFingerprint()) || !stillPlanned(running)) {
                cancel("restored_job_no_longer_valid");
                return;
            }
        }
        // A reload that changed what this job quoted ends it cleanly rather than finishing an
        // obsolete conversion; an unchanged snapshot (same digest) leaves it alone.
        if (!running.mappingDigest.equals(GlyphMappingSnapshot.current().digest()) && !stillPlanned(running)) {
            cancel("mapping_changed");
            return;
        }
        if (running.reservedSource < running.sourceCost) {
            takeFromBuffer();
            if (running.reservedSource < running.sourceCost) {
                running.pause = Pause.AWAITING_SOURCE;
                long now = level.getGameTime();
                if (now >= nextSourceScan) {
                    nextSourceScan = now + Math.max(5, AnsConfig.GLYPH_PRESS_SOURCE_SCAN_INTERVAL_TICKS.get());
                    pullSource(level, running.sourceCost - running.reservedSource);
                    takeFromBuffer();
                }
                if (running.reservedSource < running.sourceCost) return;
            }
        }
        if (running.progress < running.duration) {
            running.pause = Pause.NONE;
            running.progress++;
            if (running.progress % ENGRAVE_SOUND_INTERVAL == 0)
                level.playSound(null, worldPosition, ModSounds.GLYPH_PRESS_ENGRAVE.get(), SoundSource.BLOCKS, 0.4f, 1.1f);
            if (running.progress < running.duration) {
                setChanged();
                return;
            }
        }
        complete(level, running);
    }

    /** Complete {@code running}: revalidate, then consume the inputs and Source and insert the glyph in one mutation. */
    private void complete(Level level, Job running) {
        GlyphPressPlan plan = plan(null);
        if (!running.fingerprint.equals(inputFingerprint()) || !plan.identified()
                || !running.glyph.equals(plan.definition().glyphId())) {
            cancel("inputs_changed");
            return;
        }
        if (plan.reason() == GlyphPressPlan.Reason.OUTPUT_FULL) {
            running.pause = Pause.OUTPUT_BLOCKED;
            return;
        }
        if (!plan.ok()) {
            cancel("no_longer_valid:" + plan.reason().code());
            return;
        }
        ItemStack scroll = items.getStackInSlot(SLOT_SCROLL).copy();
        ItemStack plate = items.getStackInSlot(SLOT_PLATE).copy();
        ItemStack catalyst = items.getStackInSlot(SLOT_CATALYST).copy();
        ItemStack output = items.getStackInSlot(SLOT_OUTPUT).copy();
        if (scroll.isEmpty() || plate.isEmpty() || catalyst.getCount() < running.catalysts) {
            cancel("inputs_missing");
            return;
        }
        scroll.shrink(1);
        plate.shrink(1);
        catalyst.shrink(running.catalysts);
        ItemStack result = plan.output().copy();
        if (output.isEmpty()) output = result;
        else output.grow(result.getCount());
        // One mutation: the job is cleared first, so nothing observes inputs consumed with the job alive.
        job = null;
        items.set(SLOT_SCROLL, scroll);
        items.set(SLOT_PLATE, plate);
        items.set(SLOT_CATALYST, catalyst);
        items.set(SLOT_OUTPUT, output);
        completed++;
        setChanged();
        showWorking(false);
        syncToClients();
        level.playSound(null, worldPosition, ModSounds.GLYPH_PRESS_COMPLETE.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    /** Conversions this press has completed since it loaded; read by diagnostics and tests. */
    private int completed;

    public int completedCount() {
        return completed;
    }

    /**
     * Whether the current snapshot still plans exactly this job: the same glyph at the same terms,
     * and still allowed (a full output only pauses it). A reload that disables the spell or
     * changes its price ends the job now, not at completion.
     */
    private boolean stillPlanned(Job running) {
        GlyphPressPlan plan = plan(null);
        return plan.identified() && running.glyph.equals(plan.definition().glyphId()) && plan.terms() != null
            && plan.terms().sourceCost() == running.sourceCost && plan.terms().durationTicks() == running.duration
            && plan.terms().catalystCount() == running.catalysts
            && (plan.ok() || plan.reason() == GlyphPressPlan.Reason.OUTPUT_FULL);
    }

    private boolean pressAllowed() {
        try {
            return AnsConfig.ENABLE_GLYPH_PRESS.get() && AnsConfig.ironGlyphsEnabled();
        } catch (IllegalStateException unloaded) {
            return true;
        }
    }

    private void takeFromBuffer() {
        if (job == null) return;
        int take = Math.min(source, job.sourceCost - job.reservedSource);
        if (take <= 0) return;
        source -= take;
        job.reservedSource += take;
        setChanged();
    }

    /** Ask nearby jars, through Ars's own lookup (loaded positions only), for up to {@code needed} Source. */
    private void pullSource(Level level, int needed) {
        int radius = Math.max(1, Math.min(8, AnsConfig.GLYPH_PRESS_SOURCE_RADIUS.get()));
        int room = capacity() - source;
        int want = Math.min(needed, room);
        if (want <= 0) return;
        scanCount++;
        List<ISpecialSourceProvider> providers = SourceUtil.canTakeSource(worldPosition, level, radius);
        for (ISpecialSourceProvider provider : providers) {
            if (want <= 0) break;
            ISourceTile tile = provider.getSource();
            if (tile == null || tile == this) continue;
            int take = Math.min(want, tile.getSource());
            if (take <= 0) continue;
            tile.removeSource(take);
            source += take;
            want -= take;
            sourceScans++;
        }
    }

    /** Source jars drained into this press, and nearby-Source lookups made, since it loaded; diagnostics and tests. */
    private int sourceScans;
    private int scanCount;

    public int sourceTransfers() {
        return sourceScans;
    }

    public int sourceScanCount() {
        return scanCount;
    }

    private int schoolColor(ImportedGlyphDefinition definition) {
        return switch (definition.school().getPath()) {
            case "fire" -> 0xFF7A2E;
            case "ice" -> 0x8FE3FF;
            case "lightning" -> 0x9FD8FF;
            case "holy" -> 0xFFE38A;
            case "ender" -> 0xC66BFF;
            case "blood" -> 0xC4202F;
            case "evocation" -> 0xA8F0A0;
            case "nature" -> 0x7BD14A;
            case "eldritch" -> 0x3FD0B0;
            default -> 0x9966FF;
        };
    }

    /** SHA-256 over the three input slots: what a job was planned from. */
    private String inputFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int slot = SLOT_SCROLL; slot <= SLOT_CATALYST; slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                digest.update(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))
                    .getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Integer.toString(stack.getCount()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                if (stack.hasTag()) digest.update(stack.getTag().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 1);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ---- Source ---------------------------------------------------------------------------

    @Override public int getTransferRate() { return TRANSFER_RATE; }
    @Override public boolean canAcceptSource() { return source < capacity(); }
    @Override public int getSource() { return source; }
    @Override public int getMaxSource() { return capacity(); }
    @Override public void setMaxSource(int max) { }

    @Override
    public int setSource(int amount) {
        source = Math.max(0, Math.min(capacity(), amount));
        setChanged();
        return source;
    }

    @Override
    public int addSource(int amount) {
        return setSource(source + Math.max(0, amount));
    }

    @Override
    public int removeSource(int amount) {
        return setSource(source - Math.max(0, amount));
    }

    // ---- Persistence ----------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("schema", SCHEMA_VERSION);
        tag.put("items", items.serializeNBT());
        tag.putInt("source", source);
        if (job != null) {
            CompoundTag jobTag = new CompoundTag();
            jobTag.putUUID("id", job.id);
            jobTag.putString("fingerprint", job.fingerprint);
            jobTag.putString("glyph", job.glyph.toString());
            jobTag.putInt("source_cost", job.sourceCost);
            jobTag.putInt("duration", job.duration);
            jobTag.putInt("catalysts", job.catalysts);
            jobTag.putInt("reserved_source", job.reservedSource);
            jobTag.putInt("progress", job.progress);
            jobTag.putString("pause", job.pause.name());
            jobTag.putUUID("starter", job.starter);
            jobTag.putString("mapping_digest", job.mappingDigest);
            jobTag.putInt("school_color", job.schoolColor);
            tag.put("job", jobTag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int schema = tag.contains("schema") ? tag.getInt("schema") : SCHEMA_VERSION;
        if (tag.contains("items")) items.deserializeNBT(tag.getCompound("items"));
        source = Math.max(0, tag.getInt("source"));
        job = null;
        if (tag.contains("job", Tag.TAG_COMPOUND)) {
            if (schema > SCHEMA_VERSION) {
                // A newer press format: keep every item and the Source it held, drop the job we cannot interpret.
                source = Math.min(capacity(), source + Math.max(0, tag.getCompound("job").getInt("reserved_source")));
                LOGGER.warn("[GlyphPress] {} was saved by a newer version (schema {}); its job is cancelled, items kept",
                    worldPosition, schema);
                return;
            }
            CompoundTag jobTag = tag.getCompound("job");
            try {
                Job restored = new Job();
                restored.id = jobTag.getUUID("id");
                restored.fingerprint = jobTag.getString("fingerprint");
                restored.glyph = new ResourceLocation(jobTag.getString("glyph"));
                restored.sourceCost = jobTag.getInt("source_cost");
                restored.duration = Math.max(1, jobTag.getInt("duration"));
                restored.catalysts = jobTag.getInt("catalysts");
                restored.reservedSource = Math.max(0, Math.min(restored.sourceCost, jobTag.getInt("reserved_source")));
                restored.progress = Math.max(0, Math.min(restored.duration, jobTag.getInt("progress")));
                restored.pause = Pause.valueOf(jobTag.getString("pause"));
                restored.starter = jobTag.getUUID("starter");
                restored.mappingDigest = jobTag.getString("mapping_digest");
                restored.schoolColor = jobTag.getInt("school_color");
                job = restored;
                // Resumed only if, on its first tick, the inputs and the current data still plan it.
                revalidateRestoredJob = true;
            } catch (RuntimeException malformed) {
                LOGGER.warn("[GlyphPress] {} had an unreadable job; it is cancelled with its items kept", worldPosition);
                job = null;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("working", job != null);
        tag.putInt("school_color", job == null ? 0x9966FF : job.schoolColor);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        clientWorking = tag.getBoolean("working");
        clientSchoolColor = tag.getInt("school_color");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    /** Client view, for the working visuals only. */
    public boolean clientWorking() { return clientWorking; }
    public int clientSchoolColor() { return clientSchoolColor; }

    // ---- Menu and removal -----------------------------------------------------------------

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
     * Drop every stack exactly once. The job is cleared first, so nothing that runs while the
     * items are being dropped can see inputs that are both in the world and still owned by a job.
     * Reserved and buffered Source dissipate with the block.
     */
    public void dropContents() {
        if (level == null) return;
        job = null;
        source = 0;
        SimpleContainer container = new SimpleContainer(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            container.setItem(slot, items.getStackInSlot(slot).copy());
            items.set(slot, ItemStack.EMPTY);
        }
        Containers.dropContents(level, worldPosition, container);
    }

    /** For the menu's shift-click and slot rules. */
    public static boolean isInputSlot(int slot) {
        return slot >= SLOT_SCROLL && slot <= SLOT_CATALYST;
    }

    /** Diagnostics line for /ans. */
    public String describe() {
        return "state=" + state() + " source=" + source + "/" + capacity() + (job == null ? ""
            : " job=" + job.id + " glyph=" + job.glyph + " progress=" + job.progress + "/" + job.duration
                + " reserved=" + job.reservedSource + "/" + job.sourceCost + " pause=" + job.pause);
    }
}
