package com.otectus.arsnspells.gametest;

import com.google.gson.JsonParser;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.block.tile.RitualBrazierTile;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectLight;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.GlyphAdmission;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.GlyphSessions;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.RegistrationOrderProbe;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.rituals.InscriptionInputs;
import com.otectus.arsnspells.rituals.SpellUninscriptionRitual;
import com.otectus.arsnspells.rituals.SpellbookBindingRitual;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 3.4.1 behaviour, observed in a loaded world: zero and custom Press catalysts, one revalidation
 * per behaviour generation, restored jobs waiting for Iron's configuration, Source conservation,
 * uninscription of a real bound book through the brazier, the level and payment contracts, live
 * maximum changes, session cleanup, and the registry fill order behind the early identity catalog.
 *
 * <p>Tests that publish behaviour, change Iron's configuration or the readiness flag own batches:
 * those are globals every press and cast in the run would see.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class Glyph341GameTests {
    /** One batch per behaviour-publishing test: a published snapshot replaces the global one for every job in the run. */
    private static final String ZERO_BATCH = "ans_341_press_zero";
    private static final String CUSTOM_BATCH = "ans_341_press_custom";
    private static final String REVALIDATION_BATCH = "ans_341_press_revalidation";
    private static final String CONFIG_BATCH = "ans_341_irons_config";
    private static final String READINESS_BATCH = "ans_341_readiness";
    private static final String SCHOOL_BATCH = "ans_341_irons_school";
    private static final String FIREBOLT = "irons_spellbooks:firebolt";
    private static final String HEAL = "irons_spellbooks:heal";

    private Glyph341GameTests() {}

    // ---- D1 zero catalyst --------------------------------------------------------------------

    @GameTest(template = "platform", batch = ZERO_BATCH, timeoutTicks = 300)
    public static void ironsLoaded_press_zeroCatalystConversionCompletesWithAnEmptySlot(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphMappingSnapshot original = GlyphMappingSnapshot.current();
        publish("{\"schema_version\":2,\"spell\":\"irons_spellbooks:firebolt\",\"levels\":{\"1\":{\"catalyst\":{\"count\":0}}}}");
        GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "press_zero_cat");
        helper.assertTrue(press.getItems().insertItem(GlyphPressBlockEntity.SLOT_SCROLL, GlyphPressGameTests.scroll(FIREBOLT, 1), false).isEmpty(), "scroll in");
        helper.assertTrue(press.getItems().insertItem(GlyphPressBlockEntity.SLOT_PLATE, GlyphPressGameTests.plates(1), false).isEmpty(), "plate in");
        press.addSource(500);
        GlyphPressPlan plan = press.plan(player);
        if (!plan.ok() || plan.terms().catalystCount() != 0) {
            GlyphMappingSnapshot.publish(original);
            helper.fail("a zero-catalyst conversion with an empty catalyst slot must plan, got " + plan.reason() + " " + plan.terms());
            return;
        }
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        helper.succeedWhen(() -> {
            try {
                helper.assertTrue(!press.working(), "still engraving: " + press.describe());
                helper.assertTrue(GlyphPressGameTests.isGlyph(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT), FIREBOLT, 1),
                    "exactly Firebolt I comes out");
                helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).isEmpty(), "nothing was needed or consumed");
                helper.assertTrue(press.getSource() == 0 && press.reservedSource() == 0, "500 Source spent: " + press.describe());
            } finally {
                if (!press.working()) GlyphMappingSnapshot.publish(original);
            }
        });
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_press_positiveCatalystWithAnEmptySlotRefusesAndMovesNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "press_pos_cat");
        press.getItems().insertItem(GlyphPressBlockEntity.SLOT_SCROLL, GlyphPressGameTests.scroll(FIREBOLT, 1), false);
        press.getItems().insertItem(GlyphPressBlockEntity.SLOT_PLATE, GlyphPressGameTests.plates(1), false);
        press.addSource(500);
        String before = GlyphPressGameTests.snapshot(press);
        helper.assertTrue(GlyphPressPlan.Reason.NEEDS_CATALYST.code().equals(press.start(player)), "one gem is required by default");
        helper.assertTrue(before.equals(GlyphPressGameTests.snapshot(press)), "a refusal moves nothing");
        press.getItems().insertItem(GlyphPressBlockEntity.SLOT_CATALYST, new ItemStack(Items.OAK_PLANKS, 4), false);
        helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).isEmpty(),
            "an item of no configured catalyst tag is not admitted");
        helper.succeed();
    }

    // ---- D2 custom catalyst tag --------------------------------------------------------------

    @GameTest(template = "platform", batch = CUSTOM_BATCH, timeoutTicks = 300)
    public static void ironsLoaded_press_customCatalystTagIsAdmittedByClickShiftAndHoppers(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphMappingSnapshot original = GlyphMappingSnapshot.current();
        publish("{\"schema_version\":2,\"spell\":\"irons_spellbooks:firebolt\",\"defaults\":{\"catalyst\":{\"tag\":\"minecraft:planks\",\"count\":2}}}");
        try {
            GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
            ItemStack planks = new ItemStack(Items.OAK_PLANKS, 3);
            helper.assertTrue(GlyphPressBlockEntity.accepts(GlyphPressBlockEntity.SLOT_CATALYST, planks), "the slot rule admits the configured tag");
            IItemHandler side = press.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(IllegalStateException::new);
            ItemStack simulated = side.insertItem(GlyphPressBlockEntity.SLOT_CATALYST, planks.copy(), true);
            helper.assertTrue(simulated.isEmpty() && press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).isEmpty(),
                "a simulated hopper insert is accepted and changes nothing");
            helper.assertTrue(side.insertItem(GlyphPressBlockEntity.SLOT_CATALYST, planks.copy(), false).isEmpty(), "a hopper inserts it");
            helper.assertTrue(GlyphPressGameTests.count(press, GlyphPressBlockEntity.SLOT_CATALYST) == 3, "three planks in the slot");
            press.getItems().insertItem(GlyphPressBlockEntity.SLOT_SCROLL, GlyphPressGameTests.scroll(FIREBOLT, 1), false);
            press.getItems().insertItem(GlyphPressBlockEntity.SLOT_PLATE, GlyphPressGameTests.plates(1), false);
            press.addSource(500);
            ServerPlayer player = GlyphPressGameTests.player(helper, "press_custom_cat");
            GlyphPressPlan plan = press.plan(player);
            helper.assertTrue(plan.ok() && plan.terms().catalystCount() == 2
                    && plan.terms().catalystTag().equals(new ResourceLocation("minecraft", "planks")),
                "the conversion plans on the custom tag: " + plan.reason() + " " + plan.terms());
            // Shift-click from a player's inventory goes through the same rule.
            com.otectus.arsnspells.menu.GlyphPressMenu menu = new com.otectus.arsnspells.menu.GlyphPressMenu(1, player.getInventory(), press,
                java.util.UUID.randomUUID());
            player.getInventory().setItem(0, new ItemStack(Items.BIRCH_PLANKS, 1));
            press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST, ItemStack.EMPTY);
            int hotbar = GlyphPressBlockEntity.SLOT_COUNT + 27;
            menu.quickMoveStack(player, hotbar);
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).is(Items.BIRCH_PLANKS),
                "shift-click moves a custom-tag catalyst into the press");
        } finally {
            GlyphMappingSnapshot.publish(original);
        }
        helper.succeed();
    }

    // ---- D3 one revalidation per generation ---------------------------------------------------

    @GameTest(template = "platform", batch = REVALIDATION_BATCH, timeoutTicks = 300)
    public static void ironsLoaded_press_unrelatedReloadRevalidatesOnceAndKeepsTheJob(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphMappingSnapshot original = GlyphMappingSnapshot.current();
        GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "press_reval");
        GlyphPressGameTests.load(helper, press, GlyphPressGameTests.scroll(FIREBOLT, 1), GlyphPressGameTests.plates(1), GlyphPressGameTests.gems(1));
        press.addSource(500);
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        int[] before = {0};
        helper.startSequence()
            .thenExecuteAfter(2, () -> {
                before[0] = press.revalidationCount();
                publish("{\"schema_version\":2,\"type\":\"ars_n_spells:policy\",\"deny_spells\":[\"irons_spellbooks:heal\"]}");
            })
            .thenExecuteAfter(10, () -> {
                try {
                    helper.assertTrue(press.working(), "an unrelated reload leaves the job running: " + press.describe());
                    helper.assertTrue(press.revalidationCount() == before[0] + 1,
                        "it is revalidated exactly once for the new generation, not every tick: " + (press.revalidationCount() - before[0]));
                } finally {
                    GlyphMappingSnapshot.publish(original);
                }
            })
            .thenExecuteAfter(2, () -> helper.assertTrue(press.working(), "restoring the original behaviour leaves it running too"))
            .thenSucceed();
    }

    // ---- D4 restored job waits for Iron's configuration ---------------------------------------

    @GameTest(template = "platform", batch = READINESS_BATCH, timeoutTicks = 400)
    public static void ironsLoaded_press_restoredJobWaitsForIronsConfigurationThenCompletes(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "press_ready");
        // Firebolt IV: a level Iron's built-in fallbacks (maximum 1) would wrongly call invalid.
        GlyphPressGameTests.load(helper, press, GlyphPressGameTests.scroll(FIREBOLT, 4), GlyphPressGameTests.plates(1), GlyphPressGameTests.gems(2));
        press.addSource(1500);
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start: " + press.plan(player).reason());
        CompoundTag saved = press.saveWithFullMetadata();
        int[] progress = {0};
        helper.startSequence()
            .thenExecute(() -> {
                GlyphBehavior.ironsConfigReadyForTesting(false);
                press.load(saved);
                progress[0] = press.progress();
            })
            .thenExecuteAfter(20, () -> {
                try {
                    helper.assertTrue(press.working() && press.pause() == GlyphPressBlockEntity.Pause.AWAITING_CONFIG,
                        "before Iron's configuration is built the restored job waits, it is not cancelled: " + press.describe());
                    helper.assertTrue(press.progress() == progress[0] && press.reservedSource() == 1500,
                        "and it neither advances nor gives up its reservation: " + press.describe());
                } finally {
                    GlyphBehavior.ironsConfigReadyForTesting(true);
                }
            })
            .thenWaitUntil(() -> helper.assertTrue(!press.working(), "the job resumes and completes once configuration is ready: " + press.describe()))
            .thenExecute(() -> helper.assertTrue(GlyphPressGameTests.isGlyph(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT), FIREBOLT, 4),
                "exactly Firebolt IV, the scroll's level"))
            .thenSucceed();
    }

    // ---- D5 Source conservation ---------------------------------------------------------------

    @GameTest(template = "platform", batch = READINESS_BATCH)
    public static void ironsLoaded_press_refillThenCancelReturnsTheReservationInFull(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "press_refill");
        GlyphPressGameTests.load(helper, press, GlyphPressGameTests.scroll(FIREBOLT, 4), GlyphPressGameTests.plates(1), GlyphPressGameTests.gems(2));
        press.addSource(300);
        helper.assertTrue(GlyphPressBlockEntity.STARTED.equals(press.start(player)), "the job must start");
        helper.assertTrue(press.reservedSource() == 300 && press.getSource() == 0, "300 reserved toward 1500: " + press.describe());
        int capacity = press.capacity();
        press.addSource(1_000_000);
        helper.assertTrue(press.getSource() + press.reservedSource() == capacity, "intake stops at the capacity counting the reservation: "
            + press.describe());
        helper.assertTrue(!press.canAcceptSource() && press.getMaxSource() >= press.getSource(), "a full press accepts nothing and never reports a negative room");
        int total = press.getSource() + press.reservedSource();
        press.cancel("test");
        helper.assertTrue(press.getSource() == total, "the cancelled reservation returns in full, nothing clamped away: " + press.describe());
        helper.assertTrue(press.getMaxSource() >= press.getSource(), "Ars sizes a push as max - held; it must not go negative");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void press_aMalformedSavedJobReturnsOnlyAValidatedReservation(GameTestHelper helper) {
        GlyphPressBlockEntity press = GlyphPressGameTests.place(helper);
        CompoundTag valid = new CompoundTag();
        valid.putInt("schema", GlyphPressBlockEntity.SCHEMA_VERSION);
        valid.putInt("source", 100);
        CompoundTag job = new CompoundTag();
        job.putInt("source_cost", 1500);
        job.putInt("reserved_source", 700);
        job.putString("pause", "NOT_A_PAUSE");
        valid.put("job", job);
        press.load(valid);
        helper.assertTrue(!press.working() && press.getSource() == 800, "an unreadable job is cancelled and its validated 700 returned: " + press.describe());
        CompoundTag forged = valid.copy();
        forged.getCompound("job").putInt("reserved_source", 99_999);
        press.load(forged);
        helper.assertTrue(press.getSource() == 100, "a reservation above its own cost is not trusted, so nothing is minted: " + press.describe());
        CompoundTag huge = valid.copy();
        huge.putInt("source", Integer.MAX_VALUE);
        huge.remove("job");
        press.load(huge);
        helper.assertTrue(press.getSource() <= 200_000, "a corrupt buffer is bounded: " + press.describe());
        helper.succeed();
    }

    // ---- E uninscription of a real bound book --------------------------------------------------

    /**
     * Issue #14: a book bound through the normal workflow (the binding ritual, twice, onto a book
     * that also holds native spells) is uninscribed by the real brazier ritual: every ANS
     * inscription and proxy slot goes, the native spells, name and capacity stay.
     */
    @GameTest(template = "platform", batch = "ans_rituals_uninscribe_book", timeoutTicks = 600)
    public static void ironsLoaded_uninscription_removesOnlyAnsInscriptionsFromARealBoundBook(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = new ItemStack(CrossCastGameTests.findIronsSpellBook());
        Glyph341IronsSupport.addNativeSpells(book, new String[] {HEAL, FIREBOLT}, new int[] {2, 3});
        book.setHoverName(Component.literal("Grimoire of Tests"));
        List<String> natives = Glyph341IronsSupport.nativeSpells(book);
        int capacity = Glyph341IronsSupport.maxSpellCount(book);
        helper.assertTrue(natives.size() == 2, "fixture: two native spells, got " + natives);
        BlockPos brazierPos = new BlockPos(2, 1, 2);
        ItemEntity[] bookEntity = {null};
        RitualBrazierTile[] tile = {null};
        helper.startSequence()
            .thenExecute(() -> {
                ItemEntity carrier = RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 1.5), carrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE)));
                bookEntity[0] = RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 3.5), book);
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellbookBindingRitual.REGISTRY_PATH);
                tile[0].startRitual(CrossCastGameTests.scenarioPlayer(helper, "uninscribe_bind1"));
                helper.assertTrue(carrier.isAlive(), "fixture: carrier dropped");
            })
            // Each phase waits for its ritual's outcome rather than a fixed delay: a freshly generated world runs slower.
            .thenWaitUntil(() -> helper.assertTrue(CrossCastNbt.countArsEntries(bookEntity[0].getItem().getOrCreateTag()) == 1,
                "first bind: one Ars entry"))
            .thenExecute(() -> {
                RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 1.5), carrier(new Spell(MethodSelf.INSTANCE, EffectLight.INSTANCE)));
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellbookBindingRitual.REGISTRY_PATH);
                tile[0].startRitual(CrossCastGameTests.scenarioPlayer(helper, "uninscribe_bind2"));
            })
            .thenWaitUntil(() -> helper.assertTrue(CrossCastNbt.countArsEntries(bookEntity[0].getItem().getOrCreateTag()) == 2,
                "second bind: two Ars entries"))
            .thenExecute(() -> {
                ItemStack bound = bookEntity[0].getItem();
                helper.assertTrue(Glyph341IronsSupport.proxySlots(bound) == 2, "two native proxy slots, got " + Glyph341IronsSupport.proxySlots(bound));
                // The classification that broke issue #14: the bound book is inscribed for uninscription.
                InscriptionInputs inputs = InscriptionInputs.classify(List.of(bookEntity[0]), InscriptionInputs.Operation.UNINSCRIPTION);
                helper.assertTrue(inputs.inscribed.size() == 1 && inputs.sources.isEmpty(),
                    "a bound book is an inscribed item for uninscription, not a spell source");
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellUninscriptionRitual.REGISTRY_PATH);
                tile[0].startRitual(CrossCastGameTests.scenarioPlayer(helper, "uninscribe_run"));
            })
            .thenWaitUntil(() -> helper.assertTrue(tile[0].ritual == null, "the uninscription ritual finished"))
            .thenExecute(() -> {
                ItemStack cleaned = bookEntity[0].getItem();
                helper.assertTrue(CrossCastNbt.countArsEntries(cleaned.getOrCreateTag()) == 0, "every Ars inscription is removed");
                helper.assertTrue(Glyph341IronsSupport.proxySlots(cleaned) == 0, "every proxy slot is removed");
                helper.assertTrue(Glyph341IronsSupport.nativeSpells(cleaned).equals(natives),
                    "the native spells stay exactly: " + Glyph341IronsSupport.nativeSpells(cleaned) + " vs " + natives);
                helper.assertTrue(Glyph341IronsSupport.maxSpellCount(cleaned) == capacity, "the book's own capacity is restored: "
                    + Glyph341IronsSupport.maxSpellCount(cleaned) + " vs " + capacity);
                helper.assertTrue("Grimoire of Tests".equals(cleaned.getHoverName().getString()), "the custom name stays");
                helper.assertTrue(bookEntity[0].isAlive() && cleaned.getCount() == 1, "the book is the same single item, not consumed");
            })
            .thenSucceed();
    }

    /** An unrelated item in range refuses the uninscription and leaves the bound book unchanged, in either drop order. */
    @GameTest(template = "platform", batch = "ans_rituals_uninscribe_stray", timeoutTicks = 600)
    public static void ironsLoaded_uninscription_strayItemRefusesWithoutMutationInEitherOrder(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = new ItemStack(CrossCastGameTests.findIronsSpellBook());
        Glyph341IronsSupport.addNativeSpells(book, new String[] {HEAL}, new int[] {1});
        ItemStack carrierScroll = carrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        helper.assertTrue(com.otectus.arsnspells.spell.IronsBookBindingUtil.extractSingleArsEntry(carrierScroll)
            .map(entry -> com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(book, entry)).orElse(false),
            "fixture: the book is bound through the binding utility the ritual uses");
        String before = book.save(new CompoundTag()).toString();
        BlockPos brazierPos = new BlockPos(2, 1, 2);
        ItemEntity[] entities = {null, null};
        RitualBrazierTile[] tile = {null};
        helper.startSequence()
            .thenExecute(() -> {
                // Stray first, book second.
                entities[1] = RitualLifecycleGameTests.dropNear(helper, new Vec3(1.5, 2.25, 2.5), new ItemStack(Items.TORCH));
                entities[0] = RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 3.5), book.copy());
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellUninscriptionRitual.REGISTRY_PATH);
                tile[0].startRitual(CrossCastGameTests.scenarioPlayer(helper, "uninscribe_stray"));
            })
            .thenExecuteAfter(AnsRitualTicks.SETTLE, () -> {
                assertRefusedUnchanged(helper, tile[0], entities[0], before);
                entities[0].discard();
                entities[1].discard();
                // Book first, stray second.
                entities[0] = RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 3.5), book.copy());
                entities[1] = RitualLifecycleGameTests.dropNear(helper, new Vec3(1.5, 2.25, 2.5), new ItemStack(Items.TORCH));
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellUninscriptionRitual.REGISTRY_PATH);
                tile[0].startRitual(CrossCastGameTests.scenarioPlayer(helper, "uninscribe_stray2"));
            })
            .thenExecuteAfter(AnsRitualTicks.SETTLE, () -> assertRefusedUnchanged(helper, tile[0], entities[0], before))
            .thenSucceed();
    }

    private static void assertRefusedUnchanged(GameTestHelper helper, RitualBrazierTile tile, ItemEntity book, String before) {
        helper.assertTrue(tile.ritual == null, "the ritual ran and ended");
        helper.assertTrue(book.isAlive(), "the book is still there");
        helper.assertTrue(before.equals(book.getItem().save(new CompoundTag()).toString()), "a refused ritual changes nothing");
        helper.assertTrue(CrossCastNbt.countArsEntries(book.getItem().getOrCreateTag()) == 1, "the inscription is still there");
    }

    private static ItemStack carrier(Spell spell) {
        ItemStack scroll = ArsSpellExportUtil.createIronsScrollCarrier(spell);
        if (scroll.isEmpty()) throw new IllegalStateException("exporting an Ars spell must yield a carrier scroll");
        return scroll;
    }

    /** The ritual's own duration plus settling time. */
    private static final class AnsRitualTicks {
        static final int SETTLE = com.otectus.arsnspells.rituals.AnsRitual.DEFAULT_DURATION_TICKS + 20;
    }

    // ---- C1/C3 levels and money ------------------------------------------------------------------

    /**
     * Iron's level event fires once per occurrence, and the level and cost Iron's cast event
     * settles on are the ones cast and paid: an affinity-style +1 makes Heal I cast at 2, a halved
     * cost is what the pool loses.
     */
    @GameTest(template = "platform", batch = "ans_glyph_modes", timeoutTicks = 100)
    public static void ironsLoaded_levels_nativeModifiersApplyOnceAndTheEventLevelIsCommitted(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_levels");
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        IronsGlyphTestSupport.clearCooldowns(player);
        AutoCloseable probe = Glyph341IronsSupport.probe(player, 1, 1.0);
        try {
            ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
            helper.assertTrue(Glyph341IronsSupport.levelEvents(probe) == 1,
                "ModifySpellLevelEvent fires once for the occurrence, got " + Glyph341IronsSupport.levelEvents(probe));
            helper.assertTrue(Glyph341IronsSupport.castEvents(probe) == 1, "SpellOnCastEvent fires once, got " + Glyph341IronsSupport.castEvents(probe));
            helper.assertTrue(Glyph341IronsSupport.lastCastLevel(probe) == 2, "the +1 bonus reaches the cast event: " + Glyph341IronsSupport.lastCastLevel(probe));
            List<GlyphSessions.CastRecord> records = GlyphSessions.records(player.getUUID());
            GlyphSessions.CastRecord last = records.isEmpty() ? null : records.get(records.size() - 1);
            helper.assertTrue(last != null && last.base() == 1 && last.augmented() == 1 && last.nativeLevel() == 2 && last.finalLevel() == 2,
                "the explanation records base 1, augmented 1, native 2, cast 2: " + last);
        } finally {
            Glyph341IronsSupport.close(probe);
        }
        helper.succeed();
    }

    /**
     * The monetary contract: admission secures Iron's native price, so a cast-event discount
     * cannot rescue a caster who cannot afford it (refused, nothing spent); with enough mana the
     * discounted price is what is charged, exactly, once.
     */
    @GameTest(template = "platform", batch = "ans_glyph_modes", timeoutTicks = 100)
    public static void ironsLoaded_payment_discountSettlesExactlyButCannotRescueAdmission(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        String previousMode = com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE.get();
        TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, "separate");
        com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
        AutoCloseable probe = null;
        try {
            ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_discount");
            player.setGameMode(GameType.SURVIVAL);
            TouchRegressionGameTests.fundArs(player);
            IronsGlyphTestSupport.clearCooldowns(player);
            Spell spell = new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1));
            var rules = com.otectus.arsnspells.casting.QuoteService.currentRules();
            double base = com.otectus.arsnspells.glyph.EmissionPayment.quote(player, rules, Glyph341IronsSupport.manaCost(HEAL, 1))
                .total(com.otectus.arsnspells.contract.ResourceUnit.IRONS_MANA);
            double half = com.otectus.arsnspells.glyph.EmissionPayment.quote(player, rules, Math.round(Glyph341IronsSupport.manaCost(HEAL, 1) * 0.5))
                .total(com.otectus.arsnspells.contract.ResourceUnit.IRONS_MANA);
            helper.assertTrue(base > 0 && half < base, "fixture: Heal I costs Iron's mana in separate mode: base " + base + " half " + half);
            probe = Glyph341IronsSupport.probe(player, 0, 0.5);
            IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
            IronsProxyCastDriver.setIronsMana(player, (float) (base * 0.6));
            double arsBefore = TouchRegressionGameTests.arsMana(player);
            ImportedGlyphGameTests.cast(helper, player, spell);
            helper.assertTrue(Math.abs(IronsProxyCastDriver.ironsMana(player) - base * 0.6) < 1.0e-3 && TouchRegressionGameTests.arsMana(player) == arsBefore,
                "below the native price the cast is refused at admission and nothing is spent: irons " + IronsProxyCastDriver.ironsMana(player));
            helper.assertTrue(Glyph341IronsSupport.castEvents(probe) == 0, "no cast event was posted as a probe");
            IronsGlyphTestSupport.clearCooldowns(player);
            IronsProxyCastDriver.setIronsMana(player, (float) (base * 3));
            ImportedGlyphGameTests.cast(helper, player, spell);
            double spent = base * 3 - IronsProxyCastDriver.ironsMana(player);
            helper.assertTrue(Math.abs(spent - half) < 1.0e-3, "the discounted price is charged exactly once: spent " + spent + ", expected " + half);
        } finally {
            if (probe != null) Glyph341IronsSupport.close(probe);
            TestConfig.set(com.otectus.arsnspells.config.AnsConfig.MANA_UNIFICATION_MODE, previousMode);
            com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
        }
        helper.succeed();
    }

    // ---- B2 live maximum changes --------------------------------------------------------------

    @GameTest(template = "platform", batch = CONFIG_BATCH, timeoutTicks = 100)
    public static void ironsLoaded_liveMaximum_lowerRaiseAndBeyondTheEnvelope(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        ResourceLocation bolt = new ResourceLocation(FIREBOLT);
        int original = IronsGlyphTestSupport.maxLevel(FIREBOLT);
        try {
            Glyph341IronsSupport.setMaxLevel(FIREBOLT, 3);
            GlyphBehavior.rebuild();
            ImportedGlyphDefinition five = catalog.byKey(new ImportedGlyphKey(bolt, 5)).orElseThrow();
            ImportedGlyphDefinition three = catalog.byKey(new ImportedGlyphKey(bolt, 3)).orElseThrow();
            helper.assertTrue(GlyphAvailability.status(five).reason() == GlyphAvailability.Reason.LEVEL_UNSUPPORTED,
                "lowered to 3, Firebolt V is dormant: " + GlyphAvailability.status(five).reason());
            helper.assertTrue(GlyphAvailability.status(three).available(), "Firebolt III stays available");
            var one = ImportedGlyphGameTests.glyph(FIREBOLT, 1);
            helper.assertTrue(one.getAugmentLimit(AugmentAmplify.INSTANCE.getRegistryName()) == 2, "Amplify now reaches only level 3");
            Spell overshoot = new Spell(MethodSelf.INSTANCE, one, AugmentAmplify.INSTANCE, AugmentAmplify.INSTANCE, AugmentAmplify.INSTANCE);
            ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_lowered");
            helper.assertTrue(GlyphAdmission.requestedLevel(overshoot, 1, one.definition(), player) == 4, "the saved spell asks for level 4");
            player.setGameMode(GameType.SURVIVAL);
            TouchRegressionGameTests.fundArs(player);
            IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
            IronsGlyphTestSupport.clearCooldowns(player);
            double ars = TouchRegressionGameTests.arsMana(player);
            float irons = IronsProxyCastDriver.ironsMana(player);
            int projectiles = IronsGlyphTestSupport.magicProjectilesNear(player, player.getBoundingBox().inflate(16));
            ImportedGlyphGameTests.cast(helper, player, overshoot);
            helper.assertTrue(TouchRegressionGameTests.arsMana(player) == ars && IronsProxyCastDriver.ironsMana(player) == irons,
                "a low-level glyph cannot pass the lowered maximum through Amplify: refused before anything is paid");
            helper.assertTrue(IronsGlyphTestSupport.magicProjectilesNear(player, player.getBoundingBox().inflate(16)) == projectiles,
                "and nothing was cast");

            // Two levels above whatever envelope is registered (X with the shipped catalog, XV with irons_envelope_15).
            int envelope = catalog.registeredMax(bolt);
            Glyph341IronsSupport.setMaxLevel(FIREBOLT, envelope + 5);
            GlyphBehavior.rebuild();
            ImportedGlyphDefinition ten = catalog.byKey(new ImportedGlyphKey(bolt, 10)).orElseThrow();
            helper.assertTrue(GlyphAvailability.status(ten).available(), "raised above the envelope, the registered level 10 is available");
            var press = com.otectus.arsnspells.glyph.recipe.GlyphPressPlanner.plan(GlyphPressGameTests.scroll(FIREBOLT, 10),
                GlyphPressGameTests.plates(1), GlyphPressGameTests.gems(64), ItemStack.EMPTY, null, 100000);
            helper.assertTrue(press.ok() && GlyphPressGameTests.isGlyph(press.output(), FIREBOLT, 10), "a level-10 scroll presses into exactly level 10: " + press.reason());
            var beyond = com.otectus.arsnspells.glyph.recipe.GlyphPressPlanner.plan(GlyphPressGameTests.scroll(FIREBOLT, envelope + 2),
                GlyphPressGameTests.plates(1), GlyphPressGameTests.gems(64), ItemStack.EMPTY, null, 100000);
            helper.assertTrue(beyond.reason() == GlyphPressPlan.Reason.LEVEL_NOT_CATALOGUED,
                "a level-" + (envelope + 2) + " scroll Iron's now accepts has no identity: reported, never downcast: " + beyond.reason());
        } finally {
            Glyph341IronsSupport.setMaxLevel(FIREBOLT, original);
            GlyphBehavior.rebuild();
        }
        helper.succeed();
    }

    // ---- B3 live school --------------------------------------------------------------------------

    /**
     * A school changed through Iron's own configuration is the school ANS uses: school policy,
     * progression and discount resolution and the diagnostics follow it, while the catalogue keeps
     * recording the school the identity was published with.
     */
    @GameTest(template = "platform", batch = SCHOOL_BATCH, timeoutTicks = 100)
    public static void ironsLoaded_school_liveSchoolDrivesPolicyAndProgression(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ImportedGlyphDefinition bolt = ImportedGlyphCatalog.get().byKey(new ImportedGlyphKey(new ResourceLocation(FIREBOLT), 1)).orElseThrow();
        var part = ImportedGlyphGameTests.glyph(FIREBOLT, 1);
        helper.assertTrue(com.otectus.arsnspells.util.SchoolResolver.resolveKeys(part)
            .equals(List.of(com.otectus.arsnspells.util.SchoolKeys.normalize("irons_spellbooks:fire"))), "fixture: Firebolt starts as fire");
        String original = Glyph341IronsSupport.setSchool(FIREBOLT, "irons_spellbooks:ice");
        try {
            GlyphBehavior.rebuild();
            helper.assertTrue(GlyphBehavior.school(bolt).toString().equals("irons_spellbooks:ice"), "the live school is Iron's: " + GlyphBehavior.school(bolt));
            helper.assertTrue(bolt.school().toString().equals("irons_spellbooks:fire"), "the catalogue still records the published school");
            helper.assertTrue(com.otectus.arsnspells.util.SchoolResolver.resolveKeys(part)
                .equals(List.of(com.otectus.arsnspells.util.SchoolKeys.normalize("irons_spellbooks:ice"))),
                "progression and discounts resolve the live school: " + com.otectus.arsnspells.util.SchoolResolver.resolveKeys(part));
            helper.assertTrue(com.otectus.arsnspells.util.SchoolResolver.resolve(part) == com.otectus.arsnspells.util.SchoolKeys.builtin("irons_spellbooks:ice"),
                "and so does the primary school: " + com.otectus.arsnspells.util.SchoolResolver.resolve(part));
            publish("{\"schema_version\":2,\"type\":\"ars_n_spells:policy\",\"deny_schools\":[\"irons_spellbooks:fire\"]}");
            helper.assertTrue(GlyphAvailability.status(bolt).available(), "denying the catalogued school no longer touches it: " + GlyphAvailability.status(bolt).reason());
            publish("{\"schema_version\":2,\"type\":\"ars_n_spells:policy\",\"deny_schools\":[\"irons_spellbooks:ice\"]}");
            helper.assertTrue(GlyphAvailability.status(bolt).reason() == GlyphAvailability.Reason.DISABLED_BY_DATAPACK,
                "denying the live school does: " + GlyphAvailability.status(bolt).reason());
        } finally {
            Glyph341IronsSupport.setSchool(FIREBOLT, original);
            GlyphBehavior.rebuild();
            restoreMappings();
        }
        helper.succeed();
    }

    // ---- H2 sessions return to baseline --------------------------------------------------------

    @GameTest(template = "platform", batch = "ans_glyph_modes", timeoutTicks = 100)
    public static void ironsLoaded_sessions_instantCastsReturnToBaselineAtOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "glyph_baseline");
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        for (int i = 0; i < 5; i++) {
            IronsGlyphTestSupport.clearCooldowns(player);
            ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
            helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(),
                "an instant cast's session closes when it returns, not at the lifetime bound (" + GlyphSessions.bookkeeping() + ")");
        }
        helper.succeed();
    }

    // ---- A1.5 / A3 identity evidence ----------------------------------------------------------

    /**
     * The proof behind the early catalog: when Ars registered its glyph items, Iron's spell
     * registry had not been filled yet, so no spell read from it at runtime could get an item.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_identity_ironsSpellsAreRegisteredAfterGlyphItems(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        List<String> order = RegistrationOrderProbe.order();
        int items = order.indexOf("minecraft:item"), spells = order.indexOf(RegistrationOrderProbe.IRONS_SPELLS.toString());
        helper.assertTrue(items >= 0 && spells > items, "Iron's spell registry fills after the item registry: " + order);
        helper.assertTrue(RegistrationOrderProbe.ironsSpellsAtItemRegistration() == 0,
            "no Iron's spell existed when glyph items were registered, got " + RegistrationOrderProbe.ironsSpellsAtItemRegistration());
        helper.assertTrue(RegistrationOrderProbe.glyphPartsAtItemRegistration() >= ImportedGlyphCatalog.get().size(),
            "every catalogued glyph part was already in Ars's registry then");
        helper.succeed();
    }

    /** Every native spell in the loaded registry is accounted for, and the base catalog leaves none uncatalogued. */
    @GameTest(template = "platform")
    public static void ironsLoaded_identity_reportAccountsForEveryNativeSpell(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        var rows = com.otectus.arsnspells.commands.GlyphReportCommands.rows();
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (var row : rows) listed.add(row.id());
        for (ResourceLocation spell : IronsGlyphTestSupport.playerSpells().keySet())
            helper.assertTrue(listed.contains(spell.toString()), spell + " is missing from the report");
        helper.assertTrue(rows.stream().noneMatch(row -> row.status().equals("not_catalogued")),
            "the base catalog leaves no loaded Iron's spell without an identity: "
                + rows.stream().filter(row -> row.status().equals("not_catalogued")).map(row -> row.id()).toList());
        helper.assertTrue(rows.stream().anyMatch(row -> row.status().equals("excluded")), "proxies and none are reported as excluded");
        helper.succeed();
    }

    // ---- Batch teardown: globals go back even when a test in the batch fails or times out ------

    private static volatile GlyphMappingSnapshot mappingsBeforeBatch;

    @net.minecraft.gametest.framework.BeforeBatch(batch = ZERO_BATCH)
    public static void beforeZeroBatch(net.minecraft.server.level.ServerLevel level) { mappingsBeforeBatch = GlyphMappingSnapshot.current(); }

    @net.minecraft.gametest.framework.AfterBatch(batch = ZERO_BATCH)
    public static void afterZeroBatch(net.minecraft.server.level.ServerLevel level) { restoreMappings(); }

    @net.minecraft.gametest.framework.BeforeBatch(batch = CUSTOM_BATCH)
    public static void beforeCustomBatch(net.minecraft.server.level.ServerLevel level) { mappingsBeforeBatch = GlyphMappingSnapshot.current(); }

    @net.minecraft.gametest.framework.AfterBatch(batch = CUSTOM_BATCH)
    public static void afterCustomBatch(net.minecraft.server.level.ServerLevel level) { restoreMappings(); }

    @net.minecraft.gametest.framework.BeforeBatch(batch = REVALIDATION_BATCH)
    public static void beforeRevalidationBatch(net.minecraft.server.level.ServerLevel level) { mappingsBeforeBatch = GlyphMappingSnapshot.current(); }

    @net.minecraft.gametest.framework.AfterBatch(batch = REVALIDATION_BATCH)
    public static void afterRevalidationBatch(net.minecraft.server.level.ServerLevel level) { restoreMappings(); }

    private static void restoreMappings() {
        if (mappingsBeforeBatch != null && GlyphMappingSnapshot.current() != mappingsBeforeBatch) GlyphMappingSnapshot.publish(mappingsBeforeBatch);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch = SCHOOL_BATCH)
    public static void beforeSchoolBatch(net.minecraft.server.level.ServerLevel level) { mappingsBeforeBatch = GlyphMappingSnapshot.current(); }

    @net.minecraft.gametest.framework.AfterBatch(batch = SCHOOL_BATCH)
    public static void afterSchoolBatch(net.minecraft.server.level.ServerLevel level) { restoreMappings(); }

    @net.minecraft.gametest.framework.AfterBatch(batch = READINESS_BATCH)
    public static void afterReadinessBatch(net.minecraft.server.level.ServerLevel level) {
        if (IronsCompat.isLoaded() && !GlyphBehavior.ironsConfigReady()) GlyphBehavior.ironsConfigReadyForTesting(true);
    }

    // ---- Fixtures ----------------------------------------------------------------------------

    private static void publish(String json) {
        GlyphMappingSnapshot candidate = GlyphMappingSnapshot.build(List.of(new GlyphMappingSnapshot.Source(
            new ResourceLocation("ans_gametest", "behaviour_341"), "gametest", 1000, JsonParser.parseString(json))),
            ImportedGlyphCatalog.get(), GlyphMappingSnapshot.nextGeneration());
        if (!candidate.errors().isEmpty()) throw new IllegalStateException("test behaviour file invalid: " + candidate.errors());
        GlyphMappingSnapshot.publish(candidate);
    }
}
