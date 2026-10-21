package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphMappings;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import com.otectus.arsnspells.network.GlyphPressActionPayload;
import com.otectus.arsnspells.network.GlyphPressRequestHandler;
import com.otectus.arsnspells.network.LoomRequestContext;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The Glyph Press end to end, against the real block entity, menu, request handler, Ars Source
 * capability and Iron's scrolls: correct conversion, every refusal leaving the inputs untouched,
 * Source supply, locking on every route, two viewers, cancellation, persistence, recipe changes,
 * breaking, request replay, and one complete scroll -> glyph -> learn -> cast slice.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class GlyphPressGameTests {
    static final BlockPos PRESS = new BlockPos(3, 2, 2);
    private static final ResourceLocation FIREBOLT = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");

    private GlyphPressGameTests() {
    }

    // ---- Conversion ----

    @GameTest(template = "platform", batch = "ans_glyph_press", timeoutTicks = 400)
    public static void ironsLoaded_press_convertsTheExactSpellAndLevel(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_exact");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 3);
        VariantBehavior terms = GlyphBehavior.current().variant(key);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 3), 2, terms.pressGems() + 1);
        fillSource(press, terms.pressSource() + 100);
        GlyphPressMenu menu = open(player, press);
        helper.assertTrue(press.plan(player).ok(), "a Firebolt III scroll with its materials is ready: " + press.plan(player).reason());
        helper.assertTrue(request(player, menu, GlyphPressActionPayload.ACTION_INSCRIBE).equals(GlyphPressPlan.Reason.OK.translationKey()),
            "Inscribe Glyph starts the job");
        helper.assertTrue(press.job() != null && press.phase() == GlyphPressBlockEntity.Phase.RUNNING,
            "with enough Source in the buffer the job reserves it and runs at once");
        helper.assertTrue(press.freeSource() == 100 && press.reservedSource() == terms.pressSource(), "the job's Source is reserved");
        helper.succeedWhen(() -> {
            helper.assertTrue(press.job() == null, "the job finishes after " + terms.pressTicks() + " ticks");
            ItemStack output = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.is(ImportedGlyphRegistration.effect(key).getGlyph()) && output.getCount() == 1,
                "the result is exactly the Glyph of Firebolt III, once: " + output);
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty(), "the scroll is consumed");
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_PLATE).getCount() == 1, "one plate is consumed");
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).getCount() == 1,
                "exactly " + terms.pressGems() + " catalysts are consumed");
            helper.assertTrue(press.freeSource() == 100 && press.reservedSource() == 0, "exactly the job's Source is spent");
        });
    }

    /** Every unusable input is refused by name, and nothing is consumed or moved. */
    @GameTest(template = "platform", batch = "ans_glyph_press")
    public static void ironsLoaded_press_refusesEveryInvalidInputUnchanged(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        fillSource(press, 4000);
        // One player per refusal: a single player sending this many requests in one tick would
        // (rightly) hit the request rate bound instead.
        expect(helper, press, "press_ref_blank", IronsProxyCastDriver.blankIronsScroll(), GlyphPressPlan.Reason.BLANK_SCROLL);
        expect(helper, press, "press_ref_export", com.otectus.arsnspells.spell.ArsSpellExportUtil.createIronsScrollCarrier(
            new com.hollingsworth.arsnouveau.api.spell.Spell(MethodTouch.INSTANCE,
                com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak.INSTANCE)), GlyphPressPlan.Reason.EXPORTED_ARS_SPELL);
        expect(helper, press, "press_ref_proxy", IronsProxyCastDriver.makeProxyScroll(1), GlyphPressPlan.Reason.EXPORTED_ARS_SPELL);
        expect(helper, press, "press_ref_multi", IronsProxyCastDriver.twoSpellScroll(), GlyphPressPlan.Reason.MULTIPLE_SPELLS);
        expect(helper, press, "press_ref_low", IronsProxyCastDriver.rawScroll("irons_spellbooks:firebolt", 0), GlyphPressPlan.Reason.INVALID_LEVEL);
        expect(helper, press, "press_ref_high", IronsProxyCastDriver.rawScroll("irons_spellbooks:firebolt", 11), GlyphPressPlan.Reason.INVALID_LEVEL);
        expect(helper, press, "press_ref_none", IronsProxyCastDriver.rawScroll("irons_spellbooks:none", 1), GlyphPressPlan.Reason.BLANK_SCROLL);
        // Iron's own default config disables Cloud of Regeneration: an upstream disable wins over the Press.
        expect(helper, press, "press_ref_off", IronGlyphBridges.get().scrollStack(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "cloud_of_regeneration"), 1), GlyphPressPlan.Reason.SPELL_DISABLED);
        expect(helper, press, "press_ref_learn", IronGlyphBridges.get().scrollStack(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "abyssal_shroud"), 1), GlyphPressPlan.Reason.NOT_LEARNED);
        ServerPlayer player = player(helper, "press_refuse");
        GlyphPressMenu menu = open(player, press);
        // Without a plate, then with too few catalysts.
        ItemStack firebolt = IronGlyphBridges.get().scrollStack(FIREBOLT, 1);
        load(press, firebolt.copy(), 0, 4);
        helper.assertTrue(press.plan(player).reason() == GlyphPressPlan.Reason.INSERT_PLATE, "no plate: " + press.plan(player).reason());
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        load(press, firebolt.copy(), 1, Math.max(0, GlyphBehavior.current().variant(key).pressGems() - 1));
        helper.assertTrue(press.plan(player).reason() == GlyphPressPlan.Reason.NEEDS_CATALYST, "too few catalysts: " + press.plan(player).reason());
        String outcome = request(player, menu, GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(outcome.equals(GlyphPressPlan.Reason.NEEDS_CATALYST.translationKey()) && press.job() == null,
            "the refusal is returned and no job starts: " + outcome);
        // A non-scroll item never enters the scroll slot, by hand or by hopper.
        ItemStack book = IronsTableDriver.freshBook();
        helper.assertTrue(!press.getItems().isItemValid(GlyphPressBlockEntity.SLOT_SCROLL, book), "a spell book is not a scroll");
        clear(press);
        helper.assertTrue(!press.automation(Direction.UP).insertItem(GlyphPressBlockEntity.SLOT_SCROLL, book, false).isEmpty(),
            "automation cannot insert a spell book either");
        helper.succeed();
    }

    /**
     * Shift-clicking more catalysts onto a non-empty catalyst slot merges the stack in place
     * (vanilla only calls Slot.setChanged). The Press must still see it: the open menu re-plans at
     * once, from "needs catalyst" to Ready, and the Press is marked changed so the count is saved.
     */
    @GameTest(template = "platform", batch = "ans_glyph_press")
    public static void ironsLoaded_press_shiftClickMergeReplansTheOpenMenu(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_merge");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 10);
        int gems = GlyphBehavior.current().variant(key).pressGems();
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 10), 1, gems - 1);
        GlyphPressMenu menu = open(player, press);
        helper.assertTrue(menu.reason() == GlyphPressPlan.Reason.NEEDS_CATALYST, "one catalyst short: " + menu.reason());
        int revision = press.revision();
        player.getInventory().setItem(9, new ItemStack(ItemsRegistry.SOURCE_GEM.get(), 1));
        menu.quickMoveStack(player, GlyphPressBlockEntity.SLOT_COUNT);
        helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).getCount() == gems,
            "the catalyst merged into the existing stack");
        helper.assertTrue(press.revision() != revision, "the in-place merge reached the Press");
        helper.assertTrue(menu.reason() == GlyphPressPlan.Reason.OK, "the open menu re-planned to Ready: " + menu.reason());
        helper.succeed();
    }

    // ---- Source ----

    /** A job without Source waits, pulls from a loaded Source Jar in range at the scan interval, and then runs. */
    @GameTest(template = "platform", batch = "ans_glyph_press", timeoutTicks = 400)
    public static void ironsLoaded_press_waitsForAndPullsSourceFromAJar(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_jar");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        VariantBehavior terms = GlyphBehavior.current().variant(key);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, terms.pressGems());
        GlyphPressMenu menu = open(player, press);
        request(player, menu, GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(press.phase() == GlyphPressBlockEntity.Phase.WAITING_SOURCE, "no Source yet: the job waits");
        BlockPos jarPos = new BlockPos(1, 2, 0);
        helper.setBlock(jarPos, BlockRegistry.SOURCE_JAR.get());
        if (helper.getBlockEntity(jarPos) instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) {
            jar.setSource(terms.pressSource() + 50);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(press.job() == null && !press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).isEmpty(),
                "after pulling Source from the jar the glyph is made");
            if (helper.getBlockEntity(jarPos) instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) {
                helper.assertTrue(jar.getSource() == 50, "exactly the job's Source left the jar: " + jar.getSource());
            }
        });
    }

    /** Ars relays fill the Press through Ars's source capability; nothing can draw Source back out. */
    @GameTest(template = "platform", batch = "ans_glyph_press")
    public static void press_sourceCapabilityReceivesButNeverProvides(GameTestHelper helper) {
        GlyphPressBlockEntity press = press(helper);
        var cap = helper.getLevel().getCapability(CapabilityRegistry.SOURCE_CAPABILITY, helper.absolutePos(PRESS), null);
        helper.assertTrue(cap != null, "the Press exposes Ars's source capability");
        int accepted = cap.receiveSource(1500, false);
        helper.assertTrue(accepted == 1000 && press.freeSource() == 1000, "one transfer is bounded to 1,000: " + accepted);
        helper.assertTrue(cap.extractSource(500, false) == 0 && press.freeSource() == 1000, "Source never leaves the Press");
        fillSource(press, 100_000);
        helper.assertTrue(press.freeSource() == press.sourceCapacity(), "the buffer stops at its capacity " + press.sourceCapacity());
        helper.succeed();
    }

    // ---- Machine lifecycle ----

    /** Two viewers see one job; neither can start a second; locks hold on GUI, shift-click and hopper routes. */
    @GameTest(template = "platform", batch = "ans_glyph_press", timeoutTicks = 400)
    public static void ironsLoaded_press_twoViewersOneLockedJob(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer first = player(helper, "press_viewer_a");
        ServerPlayer second = player(helper, "press_viewer_b");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        VariantBehavior terms = GlyphBehavior.current().variant(key);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 3, terms.pressGems() + 2);
        fillSource(press, 4000);
        GlyphPressMenu a = open(first, press);
        GlyphPressMenu b = open(second, press);
        request(first, a, GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(b.value(GlyphPressMenu.D_STATE) == GlyphPressBlockEntity.State.PROCESSING.ordinal(),
            "the second viewer sees the same job");
        String again = request(second, b, GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(again.equals(GlyphPressPlan.Reason.BUSY.translationKey()), "a second Inscribe is refused: " + again);
        ItemStack[] before = snapshot(press);
        // GUI: no pickup, no placement, no swap.
        helper.assertTrue(!b.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).mayPickup(second), "a locked input cannot be picked up");
        helper.assertTrue(!b.getSlot(GlyphPressBlockEntity.SLOT_CATALYST).mayPlace(new ItemStack(ItemsRegistry.SOURCE_GEM.get())),
            "nothing can be placed into a locked input");
        // Shift-click from the inventory must not merge into the locked catalyst stack.
        second.getInventory().setItem(9, new ItemStack(ItemsRegistry.SOURCE_GEM.get(), 8));
        b.quickMoveStack(second, GlyphPressBlockEntity.SLOT_COUNT);
        helper.assertTrue(second.getInventory().getItem(9).getCount() == 8, "shift-click leaves the gems in the inventory");
        // Hoppers and pipes: simulated and real, every face.
        for (Direction side : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH}) {
            IItemHandler handler = press.automation(side);
            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(handler.extractItem(slot, 1, true).isEmpty() && handler.extractItem(slot, 1, false).isEmpty(),
                    "automation cannot extract locked input " + slot + " from " + side);
            }
            ItemStack gem = new ItemStack(ItemsRegistry.SOURCE_GEM.get());
            helper.assertTrue(handler.insertItem(GlyphPressBlockEntity.SLOT_CATALYST, gem, true).getCount() == 1
                && handler.insertItem(GlyphPressBlockEntity.SLOT_CATALYST, gem.copy(), false).getCount() == 1,
                "automation cannot add to a locked input from " + side);
        }
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL, ItemStack.EMPTY);
        helper.assertTrue(same(before, snapshot(press)), "direct handler writes cannot replace locked inputs");
        helper.succeedWhen(() -> {
            helper.assertTrue(press.job() == null, "the job completes");
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).getCount() == 1, "exactly one glyph");
            ItemStack fromBelow = press.automation(Direction.DOWN).extractItem(GlyphPressBlockEntity.SLOT_OUTPUT, 1, false);
            helper.assertTrue(fromBelow.getCount() == 1, "the result is extracted from below");
            helper.assertTrue(press.automation(Direction.UP).extractItem(GlyphPressBlockEntity.SLOT_OUTPUT, 1, false).isEmpty(),
                "but not from the top");
        });
    }

    /** Cancelling unlocks the inputs in place and returns the reservation to the buffer. */
    @GameTest(template = "platform", batch = "ans_glyph_press")
    public static void ironsLoaded_press_cancelReturnsEverything(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_cancel");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, GlyphBehavior.current().variant(key).pressGems());
        fillSource(press, 2000);
        ItemStack[] before = snapshot(press);
        GlyphPressMenu menu = open(player, press);
        request(player, menu, GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(press.reservedSource() > 0, "the job reserved its Source");
        String outcome = request(player, menu, GlyphPressActionPayload.ACTION_CANCEL);
        helper.assertTrue(outcome.equals(GlyphPressRequestHandler.CANCELLED) && press.job() == null, "Cancel ends the job: " + outcome);
        helper.assertTrue(press.freeSource() == 2000 && press.reservedSource() == 0, "the whole reservation is back in the buffer");
        helper.assertTrue(same(before, snapshot(press)), "every input is back, unchanged");
        helper.assertTrue(!press.locked(GlyphPressBlockEntity.SLOT_SCROLL), "and unlocked");
        helper.succeed();
    }

    /** A request id can be used once, and a request made against an inventory that changed is stale. */
    @GameTest(template = "platform", batch = "ans_glyph_press")
    public static void ironsLoaded_press_replayedAndStaleRequestsDoNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_replay");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, GlyphBehavior.current().variant(key).pressGems());
        fillSource(press, 2000);
        GlyphPressMenu menu = open(player, press);
        LoomRequestContext stale = context(menu);
        press.getItems().insertItem(GlyphPressBlockEntity.SLOT_PLATE, new ItemStack(ModItemsRegistry.blankGlyphPlate().get()), false);
        helper.assertTrue(GlyphPressRequestHandler.execute(player, stale, GlyphPressActionPayload.ACTION_INSCRIBE)
            .equals(GlyphPressRequestHandler.STALE) && press.job() == null, "a request made before the inventory changed is stale");
        LoomRequestContext fresh = context(menu);
        helper.assertTrue(GlyphPressRequestHandler.execute(player, fresh, GlyphPressActionPayload.ACTION_INSCRIBE)
            .equals(GlyphPressPlan.Reason.OK.translationKey()), "a fresh request starts the job");
        press.cancel("test");
        helper.assertTrue(GlyphPressRequestHandler.execute(player, fresh, GlyphPressActionPayload.ACTION_INSCRIBE)
            .equals(GlyphPressRequestHandler.REJECTED) && press.job() == null, "the same request id replayed is refused");
        player.containerMenu = player.inventoryMenu;
        helper.assertTrue(GlyphPressRequestHandler.execute(player, context(menu), GlyphPressActionPayload.ACTION_INSCRIBE) == null,
            "a player without the Press's menu open does nothing");
        helper.succeed();
    }

    /**
     * Save mid-job, load the saved data into a fresh Press elsewhere (as a chunk reload or a server
     * restart does), and let that copy finish: it resumes from its progress, keeps its reservation
     * and makes the glyph exactly once. An unreadable saved job is cancelled with its items kept.
     */
    @GameTest(template = "platform", batch = "ans_glyph_press", timeoutTicks = 400)
    public static void ironsLoaded_press_resumesASavedJobOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_resume");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        VariantBehavior terms = GlyphBehavior.current().variant(key);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, terms.pressGems());
        fillSource(press, terms.pressSource());
        request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        BlockPos copyPos = new BlockPos(1, 2, 4);
        GlyphPressBlockEntity[] copy = new GlyphPressBlockEntity[1];
        helper.runAfterDelay(20, () -> {
            var registries = helper.getLevel().registryAccess();
            CompoundTag saved = press.saveWithFullMetadata(registries);
            int progress = press.progress();
            helper.assertTrue(progress > 0 && saved.contains("job"), "the running job is saved with its progress");
            // The original stops here, as a server does at shutdown; only the reloaded copy may finish.
            press.cancel("test: replaced by its saved copy");
            clear(press);
            helper.setBlock(copyPos, ModBlocksRegistry.GLYPH_PRESS.get());
            copy[0] = (GlyphPressBlockEntity) helper.getBlockEntity(copyPos);
            copy[0].loadWithComponents(saved, registries);
            helper.assertTrue(copy[0].job() != null && copy[0].progress() == progress && copy[0].reservedSource() == terms.pressSource(),
                "job, progress and reservation survive the reload");
            helper.assertTrue(copy[0].locked(GlyphPressBlockEntity.SLOT_SCROLL), "its inputs are still locked");
            // A saved job that cannot be read is cancelled on load, its items kept and Source returned.
            CompoundTag broken = saved.copy();
            broken.getCompound("job").putString("spell", "not a valid id");
            GlyphPressBlockEntity cancelled = (GlyphPressBlockEntity) BlockEntity.loadStatic(helper.absolutePos(PRESS),
                press.getBlockState(), broken, registries);
            helper.assertTrue(cancelled.job() == null && cancelled.freeSource() == terms.pressSource()
                && !cancelled.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty(),
                "an unreadable job is cancelled with its items and Source preserved");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(copy[0] != null && copy[0].job() == null,
                "the reloaded job finishes (last end: " + (copy[0] == null ? "-" : copy[0].lastEnd()) + ")");
            ItemStack output = copy[0].getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.is(ImportedGlyphRegistration.effect(key).getGlyph()) && output.getCount() == 1,
                "the reloaded job makes exactly one glyph, got " + output + " (last end: " + copy[0].lastEnd() + ")");
            helper.assertTrue(copy[0].getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty(), "and consumes its scroll");
        });
    }

    /** Breaking a working Press drops its original inputs exactly once, never a glyph, and the Press itself. */
    @GameTest(template = "platform", batch = "ans_glyph_press")
    public static void ironsLoaded_press_breakingDropsInputsOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_break");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        int gems = GlyphBehavior.current().variant(key).pressGems();
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 2, gems + 1);
        fillSource(press, 2000);
        request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.getLevel().destroyBlock(helper.absolutePos(PRESS), true);
        AABB around = new AABB(helper.absolutePos(PRESS)).inflate(2);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
        int scrolls = 0, plates = 0, gemCount = 0, glyphs = 0, presses = 0;
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (IronGlyphBridges.get().isScrollItem(stack)) scrolls += stack.getCount();
            else if (GlyphPressBlockEntity.isPlate(stack)) plates += stack.getCount();
            else if (stack.is(ItemsRegistry.SOURCE_GEM.get())) gemCount += stack.getCount();
            else if (stack.getItem() instanceof com.otectus.arsnspells.glyph.ImportedGlyphItem) glyphs += stack.getCount();
            else if (stack.is(ModBlocksRegistry.GLYPH_PRESS_ITEM.get())) presses += stack.getCount();
        }
        helper.assertTrue(scrolls == 1 && plates == 2 && gemCount == gems + 1 && glyphs == 0,
            "inputs come back exactly once and no glyph appears: scrolls " + scrolls + ", plates " + plates + ", gems " + gemCount
                + ", glyphs " + glyphs);
        helper.assertTrue(presses == 1, "the Press drops itself once: " + presses);
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** An explosion that destroys a working Press drops its original inputs exactly once and never a glyph. */
    @GameTest(template = "platform", batch = "ans_glyph_press_explosion")
    public static void ironsLoaded_press_explosionDropsInputsOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_explode");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        int gems = GlyphBehavior.current().variant(key).pressGems();
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 2, gems + 1);
        fillSource(press, 2000);
        request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(PRESS));
        helper.getLevel().explode(null, center.x, center.y, center.z, 4.0f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        helper.assertTrue(!(helper.getLevel().getBlockEntity(helper.absolutePos(PRESS)) instanceof GlyphPressBlockEntity),
            "the explosion destroyed the Press");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(PRESS)).inflate(6));
        int scrolls = 0, plates = 0, gemCount = 0, glyphs = 0;
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (IronGlyphBridges.get().isScrollItem(stack)) scrolls += stack.getCount();
            else if (GlyphPressBlockEntity.isPlate(stack)) plates += stack.getCount();
            else if (stack.is(ItemsRegistry.SOURCE_GEM.get())) gemCount += stack.getCount();
            else if (stack.getItem() instanceof com.otectus.arsnspells.glyph.ImportedGlyphItem) glyphs += stack.getCount();
        }
        helper.assertTrue(scrolls == 1 && plates == 2 && gemCount == gems + 1 && glyphs == 0,
            "an exploded job returns its inputs once and no glyph: scrolls " + scrolls + ", plates " + plates + ", gems " + gemCount
                + ", glyphs " + glyphs);
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** A blocked output pauses at completion without consuming anything, then finishes once room appears. */
    @GameTest(template = "platform", batch = "ans_glyph_press", timeoutTicks = 500)
    public static void ironsLoaded_press_blockedOutputPausesThenFinishes(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_blocked");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        VariantBehavior terms = GlyphBehavior.current().variant(key);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, terms.pressGems());
        fillSource(press, terms.pressSource());
        request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        // Something else takes the output slot while the job runs (as a full stack of another item would).
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DIRT));
        ItemStack[] inputs = snapshot(press);
        helper.runAfterDelay(terms.pressTicks() + 20, () -> {
            helper.assertTrue(press.job() != null && press.phase() == GlyphPressBlockEntity.Phase.OUTPUT_BLOCKED,
                "a blocked output pauses the finished job: " + press.phase());
            helper.assertTrue(same(inputs, snapshot(press)), "nothing is consumed while it is blocked");
            press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(press.job() == null, "once there is room the glyph is made");
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).is(ImportedGlyphRegistration.effect(key).getGlyph()),
                "and it is the right glyph");
        });
    }

    /** Scroll -> Press -> glyph -> learned with the normal glyph action -> composed -> cast at a target. */
    @GameTest(template = "platform", batch = "ans_glyph_press", timeoutTicks = 400)
    public static void ironsLoaded_press_verticalSliceFromScrollToCast(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, "press_slice", GameType.SURVIVAL);
        ImportedGlyphGameTests.fund(player);
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        VariantBehavior terms = GlyphBehavior.current().variant(key);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, terms.pressGems());
        fillSource(press, terms.pressSource());
        request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.runAfterDelay(terms.pressTicks() + 5, () -> {
            ItemStack glyph = press.automation(Direction.DOWN).extractItem(GlyphPressBlockEntity.SLOT_OUTPUT, 1, false);
            helper.assertTrue(!glyph.isEmpty(), "the Press made the glyph");
            var effect = ImportedGlyphRegistration.effect(key);
            var knowledge = CapabilityRegistry.getPlayerDataCap(player);
            helper.assertTrue(!knowledge.knowsGlyph(effect), "the player does not know it yet");
            player.setItemInHand(InteractionHand.MAIN_HAND, glyph);
            glyph.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(knowledge.knowsGlyph(effect) && player.getMainHandItem().isEmpty(), "using the glyph learns it and consumes it");
            Pig pig = ImportedGlyphGameTests.pig(helper);
            float before = pig.getHealth();
            ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, effect));
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(pig.getHealth() < before || pig.isOnFire(), "the learned glyph casts Firebolt at the touched pig");
                helper.succeed();
            });
        });
    }

    // ---- Performance ----

    /**
     * Idle Presses do nothing per tick: no Source discovery, no planning. Waiting Presses scan only
     * at the configured interval. The timings are logged for the release record ({@code ANS-PERF}).
     */
    @GameTest(template = "platform", batch = "ans_glyph_press_perf", timeoutTicks = 400)
    public static void ironsLoaded_press_idleCostsNothingAndScansAreBounded(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        var level = helper.getLevel();
        var state = ModBlocksRegistry.GLYPH_PRESS.get().defaultBlockState();
        java.util.List<GlyphPressBlockEntity> idle = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            GlyphPressBlockEntity press = new GlyphPressBlockEntity(helper.absolutePos(new BlockPos(i % 5, 2, i / 5 % 5)), state);
            press.setLevel(level);
            idle.add(press);
        }
        long scans = GlyphPressBlockEntity.SOURCE_SCANS.get();
        long plans = GlyphPressBlockEntity.PLANS.get();
        long start = System.nanoTime();
        for (int tick = 0; tick < 200; tick++) {
            for (GlyphPressBlockEntity press : idle) GlyphPressBlockEntity.serverTick(level, press.getBlockPos(), state, press);
        }
        long idleNanos = System.nanoTime() - start;
        helper.assertTrue(GlyphPressBlockEntity.SOURCE_SCANS.get() == scans && GlyphPressBlockEntity.PLANS.get() == plans,
            "100 idle Presses over 200 ticks made " + (GlyphPressBlockEntity.SOURCE_SCANS.get() - scans) + " scans and "
                + (GlyphPressBlockEntity.PLANS.get() - plans) + " plans");
        // Five real Presses waiting for Source that never comes: each scans once per interval.
        java.util.List<GlyphPressBlockEntity> waiting = new java.util.ArrayList<>();
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        int gems = GlyphBehavior.current().variant(key).pressGems();
        for (int i = 0; i < 5; i++) {
            BlockPos pos = new BlockPos(i, 2, 4);
            helper.setBlock(pos, ModBlocksRegistry.GLYPH_PRESS.get());
            GlyphPressBlockEntity press = (GlyphPressBlockEntity) helper.getBlockEntity(pos);
            load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, gems);
            ServerPlayer player = player(helper, "press_perf_" + i);
            request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
            waiting.add(press);
        }
        long before = GlyphPressBlockEntity.SOURCE_SCANS.get();
        int interval = AnsConfig.GLYPH_PRESS_SOURCE_SCAN_INTERVAL_TICKS.get();
        helper.runAfterDelay(200, () -> {
            long made = GlyphPressBlockEntity.SOURCE_SCANS.get() - before;
            long bound = 5L * (200 / interval + 1);
            com.otectus.arsnspells.ArsNSpells.LOGGER.info("ANS-PERF idle_presses=100 ticks=200 total_ms={} ns_per_press_tick={} waiting_presses=5 scans={} bound={} interval={}",
                String.format(java.util.Locale.ROOT, "%.3f", idleNanos / 1.0e6), idleNanos / 20000, made, bound, interval);
            helper.assertTrue(made > 0 && made <= bound, "waiting Presses scan at most once per interval: " + made + " scans, bound " + bound);
            waiting.forEach(press -> press.cancel("test"));
            helper.succeed();
        });
    }

    // ---- Without Iron's ----

    /** Without Iron's the Press and the glyph ids still load; nothing can be pressed and the glyphs are dormant. */
    @GameTest(template = "platform", batch = "ans_glyph_press")
    public static void ironsAbsent_pressAndGlyphsStayRegisteredAndDormant(GameTestHelper helper) {
        if (IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        GlyphPressBlockEntity press = press(helper);
        load(press, ItemStack.EMPTY, 1, 4);
        helper.assertTrue(press.plan(null).reason() == GlyphPressPlan.Reason.INSERT_SCROLL, "no scroll can be inserted without Iron's");
        helper.assertTrue(!press.getItems().isItemValid(GlyphPressBlockEntity.SLOT_SCROLL, new ItemStack(Items.PAPER)), "paper is no scroll");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 3);
        var effect = ImportedGlyphRegistration.effect(key);
        helper.assertTrue(effect != null && new ItemStack(effect.getGlyph()).getItem() == effect.getGlyph(), "the glyph item is registered");
        helper.assertTrue(GlyphBehavior.current().variant(key).dormancy() == Dormancy.IRONS_ABSENT, "and dormant because Iron's is absent");
        helper.succeed();
    }

    // ---- Server settings (own batch: they change shared state) ----

    /** Turning the Press off refuses new jobs and cancels a running one with everything returned. */
    @GameTest(template = "platform", batch = "ans_glyph_press_settings", timeoutTicks = 60)
    public static void ironsLoaded_press_disablingCancelsSafely(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_disable");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, GlyphBehavior.current().variant(key).pressGems());
        fillSource(press, 2000);
        ItemStack[] before = snapshot(press);
        request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        boolean enabled = AnsConfig.ENABLE_GLYPH_PRESS.get();
        TestConfig.set(AnsConfig.ENABLE_GLYPH_PRESS, false);
        GlyphBehavior.rebuild();
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(press.job() == null && press.freeSource() == 2000, "the running job is cancelled and its Source returned");
                helper.assertTrue(same(before, snapshot(press)), "its inputs are intact");
                helper.assertTrue(press.plan(player).reason() == GlyphPressPlan.Reason.PRESS_DISABLED, "and no new job can start");
            } finally {
                TestConfig.set(AnsConfig.ENABLE_GLYPH_PRESS, enabled);
                GlyphBehavior.rebuild();
            }
            helper.succeed();
        });
    }

    /**
     * Before Iron's spell config is built (a dedicated server before its first login), a job that
     * reaches its end waits unchanged instead of being judged on Iron's fallbacks, and completes
     * once the config is ready.
     */
    @GameTest(template = "platform", batch = "ans_glyph_press_settings", timeoutTicks = 300)
    public static void ironsLoaded_press_waitsForIronsConfigInsteadOfCancelling(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_pending");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 2);
        VariantBehavior behavior = GlyphBehavior.current().variant(key);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 2), 1, behavior.pressGems());
        fillSource(press, behavior.pressSource());
        request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(press.job() != null, "the Firebolt II job starts");
        int ticks = press.job().ticks();
        GlyphBehavior.ironsConfigReadyForTesting(false);
        try {
            GlyphBehavior.rebuild();
            helper.assertTrue(GlyphBehavior.current().ironsConfigPending(), "the rebuilt snapshot is pending");
            helper.assertTrue(GlyphBehavior.current().variant(key).equals(behavior), "and uses the catalogued terms, which match Iron's defaults");
        } catch (RuntimeException failure) {
            GlyphBehavior.ironsConfigReadyForTesting(true);
            GlyphBehavior.rebuild();
            throw failure;
        }
        helper.startSequence()
            .thenExecuteAfter(ticks + 40, () -> {
                try {
                    helper.assertTrue(press.job() != null && press.progress() == ticks, "the finished job waits at its end");
                    helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).isEmpty(), "without a glyph");
                    helper.assertTrue(press.lastEnd().isEmpty(), "and is not cancelled");
                } finally {
                    GlyphBehavior.ironsConfigReadyForTesting(true);
                    GlyphBehavior.rebuild();
                }
            })
            .thenExecuteAfter(5, () -> {
                ItemStack output = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT);
                helper.assertTrue(press.job() == null, "once Iron's config is ready the job completes");
                helper.assertTrue(output.getCount() == 1 && output.is(ImportedGlyphRegistration.effect(key).getGlyph()),
                    "with exactly one Firebolt II glyph");
            })
            .thenSucceed();
    }

    /** Turning imported glyphs off makes them dormant; turning them back on restores them without relearning. */
    @GameTest(template = "platform", batch = "ans_glyph_press_settings")
    public static void ironsLoaded_disablingGlyphsMakesThemDormantAndReenablingRestores(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        boolean enabled = AnsConfig.ENABLE_IRON_GLYPHS.get();
        ImportedGlyphKey key = new ImportedGlyphKey(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "heal"), 1);
        try {
            TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, false);
            GlyphBehavior.rebuild();
            helper.assertTrue(GlyphBehavior.current().variant(key).dormancy() == Dormancy.FEATURE_DISABLED, "turned off, the glyph is dormant");
            ServerPlayer player = ImportedGlyphGameTests.caster(helper, "glyph_toggle", GameType.SURVIVAL);
            ImportedGlyphGameTests.fund(player);
            player.setHealth(10.0f);
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                ImportedGlyphRegistration.effect(key)));
            helper.assertTrue(player.getHealth() == 10.0f, "a dormant glyph does nothing");
            TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, true);
            GlyphBehavior.rebuild();
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                ImportedGlyphRegistration.effect(key)));
            helper.assertTrue(player.getHealth() > 10.0f, "turned back on, the same spell works at once");
        } finally {
            TestConfig.set(AnsConfig.ENABLE_IRON_GLYPHS, enabled);
            GlyphBehavior.rebuild();
        }
        helper.succeed();
    }

    /** A reload that changes a running job's recipe cancels it cleanly instead of making an obsolete glyph. */
    @GameTest(template = "platform", batch = "ans_glyph_press_settings", timeoutTicks = 400)
    public static void ironsLoaded_press_recipeChangeCancelsTheJob(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = press(helper);
        ServerPlayer player = player(helper, "press_reload");
        ImportedGlyphKey key = new ImportedGlyphKey(FIREBOLT, 1);
        VariantBehavior terms = GlyphBehavior.current().variant(key);
        load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, terms.pressGems());
        fillSource(press, 4000);
        ItemStack[] before = snapshot(press);
        request(player, open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        GlyphMappings.Result previous = GlyphBehavior.mappings();
        String json = "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"levels\":{\"1\":{\"source_cost\":"
            + (terms.pressSource() + 7) + "}}}";
        GlyphMappings.Result changed = GlyphMappings.parse(List.of(new GlyphMappings.Source(
            ResourceLocation.fromNamespaceAndPath("ans_test", "ans_iron_glyphs/firebolt.json"), "gametest",
            com.google.gson.JsonParser.parseString(json))), ImportedGlyphCatalog.get());
        helper.assertTrue(changed.valid(), "the test mapping is valid: " + changed.errors());
        GlyphBehavior.acceptMappings(changed);
        GlyphBehavior.rebuild();
        helper.runAfterDelay(terms.pressTicks() + 10, () -> {
            try {
                helper.assertTrue(press.job() == null && press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).isEmpty(),
                    "the job under the old terms is cancelled, not completed");
                helper.assertTrue(same(before, snapshot(press)) && press.freeSource() == 4000, "inputs and Source are returned");
            } finally {
                GlyphBehavior.acceptMappings(previous);
                GlyphBehavior.rebuild();
            }
            helper.succeed();
        });
    }

    // ---- Fixtures ----

    static GlyphPressBlockEntity press(GameTestHelper helper) {
        helper.setBlock(PRESS, ModBlocksRegistry.GLYPH_PRESS.get());
        return (GlyphPressBlockEntity) helper.getBlockEntity(PRESS);
    }

    static ServerPlayer player(GameTestHelper helper, String scenario) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)),
            scenario.length() > 16 ? scenario.substring(0, 16) : scenario));
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 2.5)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    static GlyphPressMenu open(ServerPlayer player, GlyphPressBlockEntity press) {
        GlyphPressMenu menu = new GlyphPressMenu(java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 100), player.getInventory(), press, UUID.randomUUID());
        player.containerMenu = menu;
        return menu;
    }

    static LoomRequestContext context(GlyphPressMenu menu) {
        return new LoomRequestContext(menu.containerId, menu.sessionId(), UUID.randomUUID(), GlyphPressRequestHandler.revision(menu),
            GlyphBehavior.current().digest());
    }

    static String request(ServerPlayer player, GlyphPressMenu menu, int action) {
        return GlyphPressRequestHandler.execute(player, context(menu), action);
    }

    /** Put the inputs in place (replacing whatever is there). */
    static void load(GlyphPressBlockEntity press, ItemStack scroll, int plates, int gems) {
        clear(press);
        var items = press.getItems();
        items.setStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL, scroll);
        items.setStackInSlot(GlyphPressBlockEntity.SLOT_PLATE, plates <= 0 ? ItemStack.EMPTY : new ItemStack(ModItemsRegistry.blankGlyphPlate().get(), plates));
        items.setStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST, gems <= 0 ? ItemStack.EMPTY : new ItemStack(ItemsRegistry.SOURCE_GEM.get(), gems));
    }

    static void clear(GlyphPressBlockEntity press) {
        for (int i = 0; i < GlyphPressBlockEntity.SLOT_COUNT; i++) press.getItems().setStackInSlot(i, ItemStack.EMPTY);
    }

    /** Fill the buffer as a relay would, through Ars's source capability. */
    static void fillSource(GlyphPressBlockEntity press, int amount) {
        int left = amount;
        while (left > 0) {
            int accepted = press.source().receiveSource(Math.min(1000, left), false);
            if (accepted <= 0) break;
            left -= accepted;
        }
    }

    private static void expect(GameTestHelper helper, GlyphPressBlockEntity press, String scenario,
                               ItemStack scroll, GlyphPressPlan.Reason reason) {
        ServerPlayer player = player(helper, scenario);
        GlyphPressMenu menu = open(player, press);
        load(press, scroll, 1, 4);
        ItemStack[] before = snapshot(press);
        GlyphPressPlan.Reason planned = press.plan(player).reason();
        helper.assertTrue(planned == reason, "expected " + reason + " for " + scroll + ", got " + planned);
        String outcome = request(player, menu, GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(outcome.equals(reason.translationKey()) && press.job() == null, "Inscribe refuses with " + reason + ": " + outcome);
        helper.assertTrue(same(before, snapshot(press)), "a refused scroll leaves every slot unchanged (" + reason + ")");
    }

    static ItemStack[] snapshot(GlyphPressBlockEntity press) {
        ItemStack[] out = new ItemStack[GlyphPressBlockEntity.SLOT_COUNT];
        for (int i = 0; i < out.length; i++) out[i] = press.getItems().getStackInSlot(i).copy();
        return out;
    }

    static boolean same(ItemStack[] a, ItemStack[] b) {
        for (int i = 0; i < a.length; i++) if (!ItemStack.matches(a[i], b[i])) return false;
        return true;
    }
}
