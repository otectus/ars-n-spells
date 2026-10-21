package com.otectus.arsnspells.gametest;

import com.google.gson.JsonParser;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphItem;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlanner;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import com.otectus.arsnspells.network.GlyphPressRequestHandler;
import com.otectus.arsnspells.network.GlyphPressRevision;
import com.otectus.arsnspells.network.LoomRequestContext;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 3.4.0: the Glyph Press, end to end in a loaded world.
 *
 * <p>Every conversion runs through the press's own job: real Iron's scrolls in, the server's
 * planner deciding, Source reserved and spent, inputs held until an atomic completion. Requests
 * go through the same handler the network packet uses. Tests that publish a different behaviour
 * snapshot or explode things own their batches, because both reach every press in the world.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class GlyphPressGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphPressGameTests.class);
    private static final String BATCH = "ans_glyph_press";
    private static final BlockPos PRESS = new BlockPos(2, 2, 2);
    private static final String FIREBOLT = "irons_spellbooks:firebolt";

    private GlyphPressGameTests() {}

    // ---- Every profile -----------------------------------------------------------------------

    /** The press, its plate and its stored glyphs are ANS objects: they load and stay dormant without Iron's. */
    @GameTest(template = "platform", batch = BATCH)
    public static void press_storesAndReloadsItsContentsInEveryProfile(GameTestHelper helper) {
        GlyphPressBlockEntity press = place(helper);
        ItemStack glyph = new ItemStack(ForgeRegistries.ITEMS.getValue(new ImportedGlyphKey(new ResourceLocation(FIREBOLT), 2).glyphId()));
        helper.assertTrue(glyph.getItem() instanceof ImportedGlyphItem, "the imported glyph item must exist in every profile");
        IItemHandler unsided = press.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(IllegalStateException::new);
        helper.assertTrue(unsided.insertItem(GlyphPressBlockEntity.SLOT_SCROLL, new ItemStack(Items.PAPER), false).getCount() == 1,
            "a non-scroll must never enter the scroll slot");
        helper.assertTrue(unsided.insertItem(GlyphPressBlockEntity.SLOT_PLATE, plates(2), false).isEmpty(), "plates go in");
        helper.assertTrue(unsided.insertItem(GlyphPressBlockEntity.SLOT_CATALYST, gems(3), false).isEmpty(), "gems go in");
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT, glyph.copy());
        press.addSource(1234);
        CompoundTag saved = press.saveWithFullMetadata();
        GlyphPressBlockEntity copy = new GlyphPressBlockEntity(press.getBlockPos(), press.getBlockState());
        copy.load(saved);
        helper.assertTrue(snapshot(copy).equals(snapshot(press)), "a save/load round trip must keep every slot and the Source");
        GlyphAvailability.Reason reason = GlyphAvailability.status(((ImportedGlyphItem) glyph.getItem()).definition()).reason();
        if (!IronsCompat.isLoaded()) {
            helper.assertTrue(reason == GlyphAvailability.Reason.IRONS_ABSENT, "without Iron's a stored glyph is dormant, got " + reason);
            helper.assertTrue(press.plan(null).reason() == GlyphPressPlan.Reason.NO_SCROLL, "with no scroll there is nothing to plan");
        }
        helper.succeed();
    }

    // ---- Conversion ------------------------------------------------------------------------

    /** The worn notebook's worked example, exactly: Firebolt I from 1 gem and 500 Source in 5 seconds. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 300)
    public static void ironsLoaded_bookExample_fireboltI_consumesExactlyItsCost(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_book_ex");
        load(helper, press, scroll(FIREBOLT, 1), plates(3), gems(5));
        press.addSource(2000);
        GlyphPressPlan plan = press.plan(player);
        helper.assertTrue(plan.ok(), "a Firebolt I scroll with plates, gems and Source must plan, got " + plan.reason());
        helper.assertTrue(plan.tier() == 1 && plan.terms().sourceCost() == 500 && plan.terms().catalystCount() == 1
                && plan.terms().durationTicks() == 100,
            "the book's example: tier I, 1 gem, 500 Source, 100 ticks; got tier " + plan.tier() + " " + plan.terms());
        helper.assertTrue(isGlyph(plan.output(), FIREBOLT, 1), "the result must be exactly Firebolt I, got " + plan.output());
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        helper.assertTrue(press.state() == GlyphPressBlockEntity.State.PROCESSING && press.reservedSource() == 500
            && press.getSource() == 1500, "500 Source is reserved from the buffer at start: " + press.describe());
        helper.assertTrue(count(press, GlyphPressBlockEntity.SLOT_PLATE) == 3 && count(press, GlyphPressBlockEntity.SLOT_CATALYST) == 5
            && !press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty(),
            "the inputs stay in the press, untouched, until completion");
        helper.runAfterDelay(50, () -> helper.assertTrue(press.working() && press.progress() > 0 && press.progress() < 100,
            "half way the job is still engraving: " + press.describe()));
        helper.succeedWhen(() -> {
            helper.assertTrue(!press.working(), "still engraving: " + press.describe());
            helper.assertTrue(isGlyph(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT), FIREBOLT, 1)
                && count(press, GlyphPressBlockEntity.SLOT_OUTPUT) == 1, "exactly one Firebolt I glyph");
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty(), "the scroll is used up");
            helper.assertTrue(count(press, GlyphPressBlockEntity.SLOT_PLATE) == 2, "exactly one plate is used");
            helper.assertTrue(count(press, GlyphPressBlockEntity.SLOT_CATALYST) == 4, "exactly one gem is used");
            helper.assertTrue(press.getSource() == 1500 && press.reservedSource() == 0, "exactly 500 Source is spent: " + press.describe());
            helper.assertTrue(press.completedCount() == 1, "one completion");
        });
    }

    /** A higher-tier level keeps its exact level and pays tier II terms (Firebolt IV is Uncommon). */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 400)
    public static void ironsLoaded_levelIsPreserved_atTierII(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_tier_two");
        load(helper, press, scroll(FIREBOLT, 4), plates(1), gems(2));
        press.addSource(1500);
        GlyphPressPlan plan = press.plan(player);
        helper.assertTrue(plan.ok() && plan.tier() == 2 && plan.terms().catalystCount() == 2 && plan.terms().sourceCost() == 1500,
            "Firebolt IV is tier II: 2 gems and 1,500 Source; got " + plan.reason() + " tier " + plan.tier() + " " + plan.terms());
        press.getItems().extractItem(GlyphPressBlockEntity.SLOT_CATALYST, 1, false);
        helper.assertTrue(press.plan(player).reason() == GlyphPressPlan.Reason.NEEDS_CATALYST,
            "one gem short must be refused as needing tier II materials");
        press.getItems().insertItem(GlyphPressBlockEntity.SLOT_CATALYST, gems(1), false);
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        helper.succeedWhen(() -> {
            helper.assertTrue(!press.working(), "still engraving: " + press.describe());
            ItemStack out = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(isGlyph(out, FIREBOLT, 4), "the glyph must be exactly level 4, got " + ForgeRegistries.ITEMS.getKey(out.getItem()));
            helper.assertTrue(count(press, GlyphPressBlockEntity.SLOT_CATALYST) == 0 && press.getSource() == 0, "tier II terms are spent");
        });
    }

    /** Every input that is not exactly one valid spell is refused with its own reason, and nothing moves. */
    @GameTest(template = "platform", batch = BATCH)
    public static void ironsLoaded_refusesEveryScrollThatIsNotOneValidSpell(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_refusals");
        int max = IronsGlyphTestSupport.maxLevel(FIREBOLT);
        Map<String, Object[]> cases = new java.util.LinkedHashMap<>();
        cases.put("bare scroll", new Object[]{IronsGlyphTestSupport.bareScroll(), GlyphPressPlan.Reason.BLANK_SCROLL});
        cases.put("empty container", new Object[]{IronsGlyphTestSupport.emptyContainerScroll(), GlyphPressPlan.Reason.BLANK_SCROLL});
        cases.put("none spell", new Object[]{IronsGlyphTestSupport.noneScroll(), GlyphPressPlan.Reason.BLANK_SCROLL});
        cases.put("two spells", new Object[]{IronsGlyphTestSupport.scrollWith(2, new String[]{FIREBOLT, "irons_spellbooks:heal"},
            new int[]{1, 1}), GlyphPressPlan.Reason.MULTIPLE_SPELLS});
        cases.put("two-slot scroll, one spell", new Object[]{IronsGlyphTestSupport.scrollWith(2, new String[]{FIREBOLT}, new int[]{1}),
            GlyphPressPlan.Reason.MULTIPLE_SPELLS});
        cases.put("level above max", new Object[]{IronsGlyphTestSupport.scrollHolding(FIREBOLT, max + 1), GlyphPressPlan.Reason.INVALID_LEVEL});
        cases.put("ANS proxy", new Object[]{IronsGlyphTestSupport.proxyScroll(), GlyphPressPlan.Reason.PROXY_SPELL});
        cases.put("exported Ars spell", new Object[]{ArsSpellExportUtil.createIronsScrollCarrier(
            new com.hollingsworth.arsnouveau.api.spell.Spell(com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE), "Export", "arcane", "spell/heal", ""),
            GlyphPressPlan.Reason.ARS_EXPORT});
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_PLATE, plates(2));
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST, gems(8));
        press.addSource(4000);
        for (Map.Entry<String, Object[]> entry : cases.entrySet()) {
            ItemStack input = (ItemStack) entry.getValue()[0];
            GlyphPressPlan.Reason expected = (GlyphPressPlan.Reason) entry.getValue()[1];
            helper.assertTrue(!input.isEmpty(), entry.getKey() + ": fixture must be a real scroll");
            press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL, input.copy());
            String before = snapshot(press);
            String reason = press.start(player);
            helper.assertTrue(expected.code().equals(reason), entry.getKey() + " must be refused as " + expected.code() + ", got " + reason);
            helper.assertTrue(!press.working() && before.equals(snapshot(press)), entry.getKey() + ": a refusal must move nothing");
        }
        // A non-scroll never reaches the slot, and the planner refuses one handed to it directly.
        helper.assertTrue(GlyphPressPlanner.plan(new ItemStack(Items.BOOK), plates(1), gems(1), ItemStack.EMPTY, player, 4000).reason()
            == GlyphPressPlan.Reason.NOT_A_SCROLL, "a book is not an Iron's scroll");
        helper.succeed();
    }

    /** Iron's learning requirement binds the player who starts the job; an upstream disable binds everyone. */
    @GameTest(template = "platform", batch = BATCH)
    public static void ironsLoaded_enforcesIronsLearningAndUpstreamDisables(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_learning");
        load(helper, press, scroll("irons_spellbooks:abyssal_shroud", 1), plates(1), gems(4));
        press.addSource(3000);
        String before = snapshot(press);
        String refused = press.start(player);
        helper.assertTrue(GlyphPressPlan.Reason.REQUIRES_LEARNING.code().equals(refused),
            "an unlearned eldritch spell must be refused for this player, got " + refused);
        helper.assertTrue(before.equals(snapshot(press)), "the refusal moved items");
        IronsGlyphTestSupport.learn(player, "irons_spellbooks:abyssal_shroud");
        helper.assertTrue(press.plan(player).ok(), "once learned in Iron's, the same press plans it: " + press.plan(player).reason());

        // Cloud of Regeneration ships disabled in Iron's own spell config.
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL, scroll("irons_spellbooks:cloud_of_regeneration", 1));
        helper.assertTrue(press.plan(player).reason() == GlyphPressPlan.Reason.SPELL_DISABLED,
            "a spell Iron's disables must be refused, got " + press.plan(player).reason());
        helper.succeed();
    }

    // ---- Requests, viewers and locks -------------------------------------------------------

    /** Two viewers see one job; replayed, stale and duplicate requests change nothing; either can cancel. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void ironsLoaded_twoViewersShareOneJobAndRequestsAreGuarded(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer a = player(helper, "press_viewer_a");
        ServerPlayer b = player(helper, "press_viewer_b");
        load(helper, press, scroll(FIREBOLT, 1), plates(1), gems(1));
        press.addSource(500);
        GlyphPressMenu menuA = new GlyphPressMenu(41, a.getInventory(), press, UUID.randomUUID());
        GlyphPressMenu menuB = new GlyphPressMenu(42, b.getInventory(), press, UUID.randomUUID());
        a.containerMenu = menuA;
        b.containerMenu = menuB;
        String inputs = snapshot(press);
        UUID replayed = UUID.randomUUID();
        helper.startSequence()
            .thenExecute(() -> {
                String started = GlyphPressRequestHandler.execute(a, context(menuA, replayed, GlyphPressRevision.of(menuA)),
                    GlyphPressRequestHandler.ACTION_START);
                helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(started), "viewer A starts the job, got " + started);
                helper.assertTrue(menuA.state() == GlyphPressBlockEntity.State.PROCESSING
                    && menuB.state() == GlyphPressBlockEntity.State.PROCESSING, "both viewers see the one job");
                String replay = GlyphPressRequestHandler.execute(a, context(menuA, replayed, GlyphPressRevision.of(menuA)),
                    GlyphPressRequestHandler.ACTION_START);
                helper.assertTrue(GlyphPressRequestHandler.REJECTED.equals(replay), "a replayed request id is rejected, got " + replay);
            })
            .thenExecuteAfter(5, () -> {
                String second = GlyphPressRequestHandler.execute(b, context(menuB, UUID.randomUUID(), GlyphPressRevision.of(menuB)),
                    GlyphPressRequestHandler.ACTION_START);
                helper.assertTrue(GlyphPressBlockEntity.ALREADY_WORKING.equals(second), "viewer B cannot start a second job, got " + second);
            })
            .thenExecuteAfter(5, () -> {
                String stale = GlyphPressRequestHandler.execute(b, context(menuB, UUID.randomUUID(), "0".repeat(64)),
                    GlyphPressRequestHandler.ACTION_CANCEL);
                helper.assertTrue(GlyphPressRequestHandler.STALE.equals(stale), "a stale revision is refused, got " + stale);
                helper.assertTrue(press.working(), "a stale cancel must not cancel");
            })
            .thenExecuteAfter(5, () -> {
                String cancelled = GlyphPressRequestHandler.execute(b, context(menuB, UUID.randomUUID(), GlyphPressRevision.of(menuB)),
                    GlyphPressRequestHandler.ACTION_CANCEL);
                helper.assertTrue(GlyphPressBlockEntity.CANCELLED.equals(cancelled), "viewer B may cancel, got " + cancelled);
                helper.assertTrue(!press.working() && inputs.equals(snapshot(press)),
                    "cancel returns the inputs untouched and the reserved Source to the buffer: " + press.describe());
                a.containerMenu = a.inventoryMenu;
                b.containerMenu = b.inventoryMenu;
            })
            .thenSucceed();
    }

    /** A running job's inputs cannot be taken, swapped, thrown or topped up through the menu. */
    @GameTest(template = "platform", batch = BATCH)
    public static void ironsLoaded_menuCannotTouchLockedInputs(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_menu_lock");
        load(helper, press, scroll(FIREBOLT, 1), plates(2), gems(3));
        press.addSource(500);
        GlyphPressMenu menu = new GlyphPressMenu(43, player.getInventory(), press, UUID.randomUUID());
        player.containerMenu = menu;
        player.getInventory().setItem(0, gems(10));
        int hotbarZero = GlyphPressBlockEntity.SLOT_COUNT + 27;
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        String locked = snapshot(press);
        helper.assertTrue(menu.quickMoveStack(player, hotbarZero).isEmpty() && player.getInventory().getItem(0).getCount() == 10,
            "shift-click must not top up a locked catalyst stack");
        menu.clicked(GlyphPressBlockEntity.SLOT_CATALYST, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "a locked input cannot be picked up");
        menu.clicked(GlyphPressBlockEntity.SLOT_SCROLL, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(GlyphPressBlockEntity.SLOT_PLATE, 0, ClickType.SWAP, player);
        menu.clicked(GlyphPressBlockEntity.SLOT_PLATE, 0, ClickType.THROW, player);
        helper.assertTrue(locked.equals(snapshot(press)) && press.working(), "no menu action may change a running job's inputs");
        press.cancel("test");
        helper.assertTrue(!menu.quickMoveStack(player, hotbarZero).isEmpty() && count(press, GlyphPressBlockEntity.SLOT_CATALYST) > 3,
            "once cancelled the catalyst slot accepts shift-clicked gems again");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    /** Hoppers stage inputs by face and take only the result; a job's inputs refuse them, simulated or real. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 400)
    public static void ironsLoaded_hoppersStageInputsAndTakeOnlyTheGlyph(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_hoppers");
        BlockPos above = PRESS.above(), below = PRESS.below(), west = PRESS.west();
        helper.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(west, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity top = (HopperBlockEntity) helper.getBlockEntity(above);
        HopperBlockEntity side = (HopperBlockEntity) helper.getBlockEntity(west);
        HopperBlockEntity bottom = (HopperBlockEntity) helper.getBlockEntity(below);
        top.setItem(0, scroll(FIREBOLT, 1));
        side.setItem(0, plates(1));
        side.setItem(1, gems(1));
        press.addSource(500);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(count(press, GlyphPressBlockEntity.SLOT_PLATE) == 1
                && count(press, GlyphPressBlockEntity.SLOT_CATALYST) == 1
                && !press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty(),
                "the top hopper supplies the scroll and the side hopper the plate and gem"))
            .thenExecute(() -> {
                helper.assertTrue(isEmpty(bottom), "the bottom hopper must never pull an input");
                helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
                top.setItem(0, scroll(FIREBOLT, 2));
                side.setItem(1, gems(4));
                for (Direction face : Direction.values()) {
                    IItemHandler handler = press.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElseThrow(IllegalStateException::new);
                    for (int slot = GlyphPressBlockEntity.SLOT_SCROLL; slot <= GlyphPressBlockEntity.SLOT_CATALYST; slot++) {
                        helper.assertTrue(handler.extractItem(slot, 64, true).isEmpty(), face + " may not extract input " + slot);
                        helper.assertTrue(handler.insertItem(slot, slot == 0 ? scroll(FIREBOLT, 3) : slot == 1 ? plates(1) : gems(1), true)
                            .getCount() == 1, face + " may not insert into locked input " + slot);
                    }
                }
            })
            .thenWaitUntil(() -> helper.assertTrue(press.completedCount() == 1 && !press.working(), "the job completes"))
            .thenWaitUntil(() -> helper.assertTrue(countIn(bottom, null) == 1, "the bottom hopper takes the glyph"))
            .thenExecute(() -> helper.assertTrue(countIn(bottom, glyphItem(FIREBOLT, 1)) == 1,
                "the bottom hopper holds exactly the Firebolt I glyph and nothing else"))
            .thenSucceed();
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    /** Save mid-job, load into a fresh entity: the same job resumes; a completed one never replays. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 300)
    public static void ironsLoaded_persistsMidJobAndNeverReplaysACompletedOne(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_persist");
        load(helper, press, scroll(FIREBOLT, 1), plates(1), gems(1));
        press.addSource(700);
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        helper.startSequence()
            .thenExecuteAfter(30, () -> {
                CompoundTag saved = press.saveWithFullMetadata();
                GlyphPressBlockEntity copy = new GlyphPressBlockEntity(press.getBlockPos(), press.getBlockState());
                copy.load(saved);
                helper.assertTrue(press.jobId() != null && press.jobId().equals(copy.jobId()), "the job identity persists");
                helper.assertTrue(copy.progress() == press.progress() && copy.reservedSource() == press.reservedSource()
                    && copy.getSource() == press.getSource() && snapshot(copy).equals(snapshot(press)),
                    "progress, reservation, Source and items persist together");
                // A save whose inputs no longer match the job (an edited world) is cancelled, never completed.
                CompoundTag edited = saved.copy();
                CompoundTag items = edited.getCompound("items");
                net.minecraft.nbt.ListTag list = items.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
                list.removeIf(tag -> ((CompoundTag) tag).getInt("Slot") == GlyphPressBlockEntity.SLOT_SCROLL);
                BlockPos other = new BlockPos(0, 2, 0);
                GlyphPressBlockEntity restored = place(helper, other);
                restored.load(edited);
                helper.assertTrue(restored.working(), "the edited job loads");
                GlyphPressBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(other), restored.getBlockState(), restored);
                helper.assertTrue(!restored.working() && restored.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).isEmpty(),
                    "a restored job whose inputs changed is cancelled on its first tick, with no output");
                helper.assertTrue(restored.getSource() == 700, "and its reserved Source returns to the buffer: " + restored.describe());
            })
            .thenWaitUntil(() -> helper.assertTrue(press.completedCount() == 1, "the original job completes"))
            .thenExecute(() -> {
                GlyphPressBlockEntity copy = new GlyphPressBlockEntity(press.getBlockPos(), press.getBlockState());
                copy.load(press.saveWithFullMetadata());
                helper.assertTrue(!copy.working() && count(copy, GlyphPressBlockEntity.SLOT_OUTPUT) == 1,
                    "a completed job is not in the save: reloading never produces a second glyph");
            })
            .thenSucceed();
    }

    /** Breaking a working press drops its inputs once, no glyph, and itself. */
    @GameTest(template = "platform", batch = BATCH)
    public static void ironsLoaded_breakingAWorkingPressDropsItsInputsOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_break");
        load(helper, press, scroll(FIREBOLT, 1), plates(3), gems(5));
        press.addSource(500);
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        helper.getLevel().destroyBlock(helper.absolutePos(PRESS), true);
        Map<Item, Integer> drops = drops(helper, 3);
        assertDroppedOnce(helper, drops);
        helper.assertTrue(drops.getOrDefault(ModBlocksRegistry.GLYPH_PRESS_ITEM.get(), 0) == 1, "the press itself drops once, got " + drops);
        helper.succeed();
    }

    /** The same through an explosion. Its own batch: the blast reaches past this test's platform. */
    @GameTest(template = "platform", batch = "ans_glyph_press_explosion")
    public static void ironsLoaded_explodingAWorkingPressDropsItsInputsOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_explode");
        load(helper, press, scroll(FIREBOLT, 1), plates(3), gems(5));
        press.addSource(500);
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        var center = helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(PRESS));
        helper.getLevel().explode(null, center.x, center.y, center.z, 4.0f, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(!(helper.getBlockEntity(PRESS) instanceof GlyphPressBlockEntity), "the blast must destroy the press");
        Map<Item, Integer> drops = drops(helper, 8);
        assertDroppedOnce(helper, drops);
        // The block's own loot is subject to explosion decay; its contents never are.
        helper.assertTrue(drops.getOrDefault(ModBlocksRegistry.GLYPH_PRESS_ITEM.get(), 0) <= 1, "the press drops at most once, got " + drops);
        helper.succeed();
    }

    // ---- Source -----------------------------------------------------------------------------

    /** An idle press never looks for Source; a working one draws exactly its cost from a nearby jar. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 400)
    public static void ironsLoaded_drawsSourceFromANearbyJarOnlyWhileWorking(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_source");
        BlockPos jarPos = new BlockPos(2, 2, 4);
        helper.setBlock(jarPos, com.hollingsworth.arsnouveau.setup.registry.BlockRegistry.SOURCE_JAR.get().defaultBlockState());
        var jar = (com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile) helper.getBlockEntity(jarPos);
        jar.setSource(2000);
        load(helper, press, scroll(FIREBOLT, 1), plates(1), gems(1));
        helper.startSequence()
            .thenExecuteAfter(60, () -> {
                helper.assertTrue(press.sourceScanCount() == 0 && jar.getSource() == 2000,
                    "an idle press, however long it sits, never looks for Source: scans " + press.sourceScanCount());
                helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job starts with an empty buffer");
                helper.assertTrue(press.reservedSource() == 0 && press.getSource() == 0,
                    "the buffer is empty, so nothing is reserved yet: " + press.describe());
            })
            .thenWaitUntil(() -> helper.assertTrue(press.completedCount() == 1, "the job completes on the jar's Source: " + press.describe()))
            .thenExecute(() -> {
                helper.assertTrue(jar.getSource() == 1500, "exactly 500 Source must leave the jar, it holds " + jar.getSource());
                helper.assertTrue(isGlyph(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT), FIREBOLT, 1), "the glyph");
                int scans = press.sourceScanCount();
                helper.assertTrue(scans >= 1 && scans <= 3, "lookups are bounded by the scan interval, made " + scans);
            })
            .thenSucceed();
    }

    /**
     * 100 idle presses cost nothing but their ticker's early return: no Source lookups, no plans.
     * Logged as {@code [ANS-PressPerf]} so the run's log records the measurement.
     */
    @GameTest(template = "press_hall", batch = BATCH, timeoutTicks = 200)
    public static void ironsLoaded_performance_hundredIdlePressesDoNoWork(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        List<GlyphPressBlockEntity> presses = new ArrayList<>();
        for (int x = 1; x <= 10; x++)
            for (int z = 1; z <= 10; z++) presses.add(place(helper, new BlockPos(x, 2, z)));
        BlockPos jarPos = new BlockPos(0, 2, 0);
        helper.setBlock(jarPos, com.hollingsworth.arsnouveau.setup.registry.BlockRegistry.SOURCE_JAR.get().defaultBlockState());
        ((com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile) helper.getBlockEntity(jarPos)).setSource(10000);
        helper.startSequence()
            .thenExecuteAfter(100, () -> {
                int scans = presses.stream().mapToInt(GlyphPressBlockEntity::sourceScanCount).sum();
                helper.assertTrue(scans == 0, "100 idle presses over 100 ticks must make no Source lookups, made " + scans);
                Level level = helper.getLevel();
                long started = System.nanoTime();
                int rounds = 200;
                for (int round = 0; round < rounds; round++)
                    for (GlyphPressBlockEntity press : presses)
                        GlyphPressBlockEntity.serverTick(level, press.getBlockPos(), press.getBlockState(), press);
                double nanosPerTick = (System.nanoTime() - started) / (double) (rounds * presses.size());
                LOGGER.info("[ANS-PressPerf] idle_presses={} rounds={} avg_ns_per_idle_press_tick={} source_lookups={}",
                    presses.size(), rounds, String.format(java.util.Locale.ROOT, "%.1f", nanosPerTick), scans);
            })
            .thenSucceed();
    }

    // ---- Behaviour reloads (own batch: a published snapshot reaches every press) -------------

    /** A reload that disables the spell or changes its price cancels the running job at once; an unrelated one does not. */
    @GameTest(template = "platform", batch = "ans_glyph_press_reload", timeoutTicks = 200)
    public static void ironsLoaded_behaviourReloadInvalidatesOnlyAffectedJobs(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = place(helper);
        ServerPlayer player = player(helper, "press_reload");
        load(helper, press, scroll(FIREBOLT, 1), plates(1), gems(1));
        press.addSource(500);
        String inputs = snapshot(press);
        GlyphMappingSnapshot original = GlyphMappingSnapshot.current();
        helper.startSequence()
            .thenExecute(restoring(original, () -> {
                helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
                publish("{\"schema_version\":1,\"type\":\"ars_n_spells:policy\",\"deny_spells\":[\"irons_spellbooks:heal\"]}");
            }))
            .thenExecuteAfter(3, restoring(original, () -> helper.assertTrue(press.working(), "a reload about another spell must leave the job running")))
            .thenExecute(restoring(original, () -> publish("{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"levels\":{\"1\":{\"source_cost\":600}}}")))
            .thenExecuteAfter(2, restoring(original, () -> {
                helper.assertTrue(!press.working(), "a reload that reprices this level must end the job: " + press.describe());
                helper.assertTrue(inputs.equals(snapshot(press)), "cleanly: inputs and Source as before the start");
                GlyphMappingSnapshot.publish(original);
                helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job starts again");
                publish("{\"schema_version\":1,\"type\":\"ars_n_spells:policy\",\"deny_spells\":[\"irons_spellbooks:firebolt\"]}");
            }))
            .thenExecuteAfter(2, restoring(original, () -> {
                helper.assertTrue(!press.working() && inputs.equals(snapshot(press)), "a reload that disables the spell ends the job cleanly");
                helper.assertTrue(press.plan(player).reason() == GlyphPressPlan.Reason.SPELL_DISABLED, "and the press now refuses it");
                // An allowlist naming a spell Iron's itself disables cannot enable it.
                publish("{\"schema_version\":1,\"type\":\"ars_n_spells:policy\",\"allow_spells\":[\"irons_spellbooks:cloud_of_regeneration\"]}");
                press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL, scroll("irons_spellbooks:cloud_of_regeneration", 1));
                helper.assertTrue(press.plan(player).reason() == GlyphPressPlan.Reason.SPELL_DISABLED,
                    "an ANS allowlist must not override Iron's own disable, got " + press.plan(player).reason());
                GlyphMappingSnapshot.publish(original);
            }))
            .thenSucceed();
    }

    // ---- Fixtures --------------------------------------------------------------------------

    static GlyphPressBlockEntity place(GameTestHelper helper) {
        return place(helper, PRESS);
    }

    static GlyphPressBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocksRegistry.GLYPH_PRESS.get().defaultBlockState());
        return (GlyphPressBlockEntity) helper.getBlockEntity(pos);
    }

    static ServerPlayer player(GameTestHelper helper, String scenario) {
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, scenario);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    static ItemStack scroll(String spell, int level) {
        ItemStack scroll = IronGlyphBridges.get().filledScroll(new ResourceLocation(spell), level);
        if (scroll.isEmpty()) throw new IllegalStateException("Iron's could not build a " + spell + " " + level + " scroll");
        return scroll;
    }

    static ItemStack plates(int count) {
        return new ItemStack(ModItemsRegistry.blankGlyphPlate().get(), count);
    }

    static ItemStack gems(int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("ars_nouveau", "source_gem")), count);
    }

    static void load(GameTestHelper helper, GlyphPressBlockEntity press, ItemStack scroll, ItemStack plates, ItemStack gems) {
        helper.assertTrue(press.getItems().insertItem(GlyphPressBlockEntity.SLOT_SCROLL, scroll, false).isEmpty(), "scroll accepted");
        helper.assertTrue(press.getItems().insertItem(GlyphPressBlockEntity.SLOT_PLATE, plates, false).isEmpty(), "plates accepted");
        helper.assertTrue(press.getItems().insertItem(GlyphPressBlockEntity.SLOT_CATALYST, gems, false).isEmpty(), "gems accepted");
    }

    static Item glyphItem(String spell, int level) {
        return ForgeRegistries.ITEMS.getValue(new ImportedGlyphKey(new ResourceLocation(spell), level).glyphId());
    }

    static boolean isGlyph(ItemStack stack, String spell, int level) {
        return !stack.isEmpty() && stack.is(glyphItem(spell, level));
    }

    static int count(GlyphPressBlockEntity press, int slot) {
        return press.getItems().getStackInSlot(slot).getCount();
    }

    /** Every slot (item, count, tag) and the Source buffer, for "nothing moved" assertions. */
    static String snapshot(GlyphPressBlockEntity press) {
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < GlyphPressBlockEntity.SLOT_COUNT; slot++) {
            ItemStack stack = press.getItems().getStackInSlot(slot);
            out.append(slot).append('=').append(ForgeRegistries.ITEMS.getKey(stack.getItem())).append('x').append(stack.getCount())
                .append(stack.hasTag() ? stack.getTag().toString() : "").append(';');
        }
        return out.append("source=").append(press.getSource()).toString();
    }

    private static LoomRequestContext context(GlyphPressMenu menu, UUID request, String revision) {
        return new LoomRequestContext(menu.containerId, menu.sessionId(), request, revision, GlyphMappingSnapshot.current().digest());
    }

    private static void publish(String json) {
        GlyphMappingSnapshot candidate = GlyphMappingSnapshot.build(List.of(new GlyphMappingSnapshot.Source(
            new ResourceLocation("ans_gametest", "behaviour"), "gametest", 1000, JsonParser.parseString(json))),
            ImportedGlyphCatalog.get(), GlyphMappingSnapshot.nextGeneration());
        if (!candidate.errors().isEmpty()) throw new IllegalStateException("test behaviour file invalid: " + candidate.errors());
        GlyphMappingSnapshot.publish(candidate);
    }

    /** Run {@code step}; if it fails, put the original behaviour snapshot back before the failure propagates. */
    private static Runnable restoring(GlyphMappingSnapshot original, Runnable step) {
        return () -> {
            try {
                step.run();
            } catch (RuntimeException | Error failure) {
                GlyphMappingSnapshot.publish(original);
                throw failure;
            }
        };
    }

    private static boolean isEmpty(HopperBlockEntity hopper) {
        return countIn(hopper, null) == 0;
    }

    private static int countIn(HopperBlockEntity hopper, Item item) {
        int total = 0;
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            ItemStack stack = hopper.getItem(i);
            if (!stack.isEmpty() && (item == null || stack.is(item))) total += stack.getCount();
        }
        return total;
    }

    private static Map<Item, Integer> drops(GameTestHelper helper, int radius) {
        AABB area = new AABB(helper.absolutePos(PRESS)).inflate(radius);
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area))
            counts.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
        return counts;
    }

    private static void assertDroppedOnce(GameTestHelper helper, Map<Item, Integer> drops) {
        Item scroll = ForgeRegistries.ITEMS.getValue(GlyphPressPlanner.IRONS_SCROLL);
        helper.assertTrue(drops.getOrDefault(scroll, 0) == 1, "exactly one scroll must drop, got " + drops);
        helper.assertTrue(drops.getOrDefault(ModItemsRegistry.blankGlyphPlate().get(), 0) == 3, "exactly the three plates, got " + drops);
        helper.assertTrue(drops.getOrDefault(gems(1).getItem(), 0) == 5, "exactly the five gems, got " + drops);
        helper.assertTrue(drops.getOrDefault(glyphItem(FIREBOLT, 1), 0) == 0, "no glyph from an unfinished job, got " + drops);
    }
}
