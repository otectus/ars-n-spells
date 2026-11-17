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
 *
 * <p><b>Source conservation (3.4.1).</b> Free Source plus a job's reservation never grows past
 * the capacity through intake, but Source the press already holds is never destroyed: a cancelled
 * job's reservation returns to the buffer in full even when a refill or a lowered capacity left no
 * room, and that over-capacity balance is simply not topped up again until it is spent. Ars sizes a
 * push as {@code getMaxSource() - getSource()}, so the reported maximum never drops below what is
 * held, which keeps that difference from going negative. A saved reservation is only recovered
 * when it is a validated amount (see {@link #recoverReservation}).
 */
public class GlyphPressBlockEntity extends BlockEntity implements MenuProvider, ISourceTile {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphPressBlockEntity.class);
    public static final int SLOT_SCROLL = 0, SLOT_PLATE = 1, SLOT_CATALYST = 2, SLOT_OUTPUT = 3, SLOT_COUNT = 4;
    /** 2 (3.4.1): same layout; a malformed or newer job's reservation is recovered only when validated. */
    public static final int SCHEMA_VERSION = 2;
    /**
     * Upper bound on Source a press can ever legitimately hold: the largest configurable capacity
     * plus one refunded reservation. Anything beyond it in saved data is corruption, not value.
     */
    static final int MAX_HELD_SOURCE = 100_000 + GlyphMappingSnapshot.MAX_SOURCE_COST;
    private static final int TRANSFER_RATE = 1000;
    private static final int ENGRAVE_SOUND_INTERVAL = 40;

    /**
     * Why a running job is not advancing. {@code AWAITING_CONFIG} (3.4.1, appended: menu data carries
     * ordinals): Iron's configuration is not authoritative yet, so the job is neither advanced nor
     * judged, its inputs stay locked and its reservation held.
     */
    public enum Pause { NONE, AWAITING_SOURCE, OUTPUT_BLOCKED, AWAITING_CONFIG }

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
        /**
         * The job's immutable terms ({@link #terms(GlyphPressPlan)}): glyph, tier, Source, duration,
         * catalyst tag and count. Null only for a job saved by 3.4.0, which adopts the terms its
         * first revalidation confirms.
         */
        @Nullable String terms;
        /** The behaviour generation these terms were last confirmed under; not saved (generations restart with the server). */
        int generation = -1;
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
            case SLOT_CATALYST -> GlyphPressPlanner.mayBeCatalyst(stack);
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
        created.mappingDigest = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.digest();
        created.schoolColor = schoolColor(definition);
        created.terms = terms(plan);
        created.generation = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.generation();
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
        // Returned in full, never clamped to the capacity: the reservation is Source this press
        // already holds. A refill or a lowered capacity can leave the buffer over its capacity,
        // which only stops intake until the balance is spent.
        source = Math.min(MAX_HELD_SOURCE, source + job.reservedSource);
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
        // Iron's configuration is built on a server's first datapack sync (its first login). Until
        // then Iron's answers with fallbacks, and judging a job on them would cancel a valid one:
        // the job waits, locked and reserved, and nothing is decided.
        if (!com.otectus.arsnspells.glyph.behavior.GlyphBehavior.live().authoritative()) {
            if (running.pause != Pause.AWAITING_CONFIG) {
                running.pause = Pause.AWAITING_CONFIG;
                setChanged();
            }
            return;
        }
        // Once per behaviour generation (a reload, a config change, a restart): the same terms are
        // acknowledged and the job continues; different terms end it with everything returned.
        int generation = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.generation();
        if (revalidateRestoredJob || running.generation != generation) {
            boolean restored = revalidateRestoredJob;
            revalidateRestoredJob = false;
            if (!termsStillHold(running)) {
                cancel(restored ? "restored_job_no_longer_valid" : "terms_changed");
                return;
            }
            running.generation = generation;
            if (running.pause == Pause.AWAITING_CONFIG) running.pause = Pause.NONE;
            revalidations++;
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
        // The final authoritative check, immediately before the one completion mutation.
        GlyphPressPlan plan = plan(null);
        if (!running.fingerprint.equals(inputFingerprint()) || !plan.identified()
                || !running.glyph.equals(plan.definition().glyphId())) {
            cancel("inputs_changed");
            return;
        }
        if (running.terms != null && !running.terms.equals(terms(plan))) {
            cancel("terms_changed");
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
    /** Behaviour-generation revalidations this press has made since it loaded; diagnostics and tests. */
    private int revalidations;

    public int revalidationCount() {
        return revalidations;
    }

    /**
     * A job's terms as one comparable string: the glyph, its tier, Source, duration, catalyst tag
     * and catalyst count. Null when the plan does not identify a conversion.
     */
    @Nullable
    static String terms(GlyphPressPlan plan) {
        if (!plan.identified() || plan.terms() == null) return null;
        GlyphMappingSnapshot.PressTerms terms = plan.terms();
        return plan.definition().glyphId() + "|tier=" + plan.tier() + "|source=" + terms.sourceCost() + "|ticks=" + terms.durationTicks()
            + "|catalyst=" + terms.catalystTag() + "x" + terms.catalystCount();
    }

    /**
     * Whether the current behaviour still plans exactly this job: the same inputs, the same terms,
     * and a conversion that is allowed (a full output only pauses it). A 3.4.0 job with no saved
     * terms is checked field by field once and then adopts its terms.
     */
    private boolean termsStillHold(Job running) {
        if (!running.fingerprint.equals(inputFingerprint())) return false;
        GlyphPressPlan plan = plan(null);
        if (!(plan.ok() || plan.reason() == GlyphPressPlan.Reason.OUTPUT_FULL)) return false;
        String now = terms(plan);
        if (now == null || !running.glyph.equals(plan.definition().glyphId())) return false;
        if (running.terms == null) {
            if (!stillPlanned(running)) return false;
            running.terms = now;
            return true;
        }
        return running.terms.equals(now);
    }

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
        int room = capacity() - source - reservedSource();
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

    /** The colour of the spell's effective school; a school of another namespace with the same path is a different school. */
    private int schoolColor(ImportedGlyphDefinition definition) {
        ResourceLocation school = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.school(definition);
        if (!"irons_spellbooks".equals(school.getNamespace())) return 0x9966FF;
        return switch (school.getPath()) {
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
    @Override public boolean canAcceptSource() { return room() > 0; }
    @Override public int getSource() { return source; }
    /** The free capacity, and never less than what is held, so Ars's push size is never negative. */
    @Override public int getMaxSource() { return Math.max(source, capacity() - reservedSource()); }
    @Override public void setMaxSource(int max) { }

    /** Room for new Source: the capacity less the free buffer and the running job's reservation. */
    private int room() {
        return Math.max(0, capacity() - source - reservedSource());
    }

    /**
     * Set the free buffer. An increase is limited to the room left (free plus reserved within the
     * capacity, or no increase at all over it); a decrease is always taken as asked, so nothing
     * held is clamped away by an unrelated write.
     */
    @Override
    public int setSource(int amount) {
        int target = Math.max(0, amount);
        if (target > source) target = Math.min(target, source + room());
        source = target;
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
            if (job.terms != null) jobTag.putString("terms", job.terms);
            tag.put("job", jobTag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int schema = tag.contains("schema") ? tag.getInt("schema") : SCHEMA_VERSION;
        if (tag.contains("items")) items.deserializeNBT(tag.getCompound("items"));
        source = Math.max(0, Math.min(MAX_HELD_SOURCE, tag.getInt("source")));
        job = null;
        if (tag.contains("job", Tag.TAG_COMPOUND)) {
            if (schema > SCHEMA_VERSION) {
                // A newer press format: keep every item and the Source it held, drop the job we cannot interpret.
                source = Math.min(MAX_HELD_SOURCE, source + recoverReservation(tag.getCompound("job")));
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
                if (restored.sourceCost < 0 || restored.sourceCost > GlyphMappingSnapshot.MAX_SOURCE_COST)
                    throw new IllegalArgumentException("source_cost out of range");
                restored.duration = Math.max(1, jobTag.getInt("duration"));
                restored.catalysts = jobTag.getInt("catalysts");
                restored.reservedSource = Math.max(0, Math.min(restored.sourceCost, jobTag.getInt("reserved_source")));
                restored.progress = Math.max(0, Math.min(restored.duration, jobTag.getInt("progress")));
                restored.pause = Pause.valueOf(jobTag.getString("pause"));
                restored.starter = jobTag.getUUID("starter");
                restored.mappingDigest = jobTag.getString("mapping_digest");
                restored.schoolColor = jobTag.getInt("school_color");
                restored.terms = jobTag.contains("terms", Tag.TAG_STRING) ? jobTag.getString("terms") : null;
                job = restored;
                // Resumed only if, on its first tick, the inputs and the current data still plan it.
                revalidateRestoredJob = true;
            } catch (RuntimeException malformed) {
                int recovered = recoverReservation(jobTag);
                source = Math.min(MAX_HELD_SOURCE, source + recovered);
                LOGGER.warn("[GlyphPress] {} had an unreadable job; it is cancelled with its items kept and {} reserved Source returned",
                    worldPosition, recovered);
                job = null;
            }
        }
    }

    /**
     * The reservation a saved job held, when it can be trusted: both {@code source_cost} and
     * {@code reserved_source} present as integers with {@code 0 <= reserved <= cost <= }
     * {@link GlyphMappingSnapshot#MAX_SOURCE_COST}. Anything else returns nothing, because
     * minting Source from a corrupt field would be worse than losing an unverifiable amount.
     */
    static int recoverReservation(CompoundTag jobTag) {
        if (jobTag == null || !jobTag.contains("reserved_source", Tag.TAG_INT) || !jobTag.contains("source_cost", Tag.TAG_INT)) return 0;
        int reserved = jobTag.getInt("reserved_source");
        int cost = jobTag.getInt("source_cost");
        return reserved >= 0 && reserved <= cost && cost <= GlyphMappingSnapshot.MAX_SOURCE_COST ? reserved : 0;
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
