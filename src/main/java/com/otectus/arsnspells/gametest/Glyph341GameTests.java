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
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.RegistrationOrderProbe;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphMappings;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import com.otectus.arsnspells.network.GlyphPressActionPayload;
import com.otectus.arsnspells.rituals.InscriptionInputs;
import com.otectus.arsnspells.rituals.SpellUninscriptionRitual;
import com.otectus.arsnspells.rituals.SpellbookBindingRitual;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 3.4.1 behaviour, observed in a loaded world: zero and custom Press catalysts, the level contract
 * (native modifiers once per occurrence, event levels committed, channel pulses at their event's
 * level), free native recasts at admission, uninscription of a real bound book through the brazier,
 * live maximum changes, validated Source recovery, and the registry fill order behind the early
 * identity catalog. Tests that publish behaviour or change Iron's configuration own their batches.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class Glyph341GameTests {
    private static final String ZERO_BATCH = "ans_341_press_zero";
    private static final String CONFIG_BATCH = "ans_341_irons_config";
    private static final String SCHOOL_BATCH = "ans_341_irons_school";
    private static final ResourceLocation FIREBOLT = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");
    private static final String FIREBOLT_ID = "irons_spellbooks:firebolt";

    private Glyph341GameTests() {
    }

    // ---- D1 zero catalyst --------------------------------------------------------------------

    @GameTest(template = "platform", batch = ZERO_BATCH, timeoutTicks = 400)
    public static void ironsLoaded_press_zeroCatalystConversionCompletesWithAnEmptySlot(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphMappings.Result original = GlyphBehavior.mappings();
        publish("{\"schema_version\": 2, \"spell\": \"irons_spellbooks:firebolt\", \"levels\": {\"1\": {\"catalyst\": {\"count\": 0}}}}");
        GlyphPressBlockEntity press = GlyphPressGameTests.press(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "press_zero_cat");
        GlyphPressGameTests.load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, 0);
        int source = GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 1)).pressSource();
        GlyphPressGameTests.fillSource(press, source);
        var plan = press.plan(player);
        if (!plan.ok() || plan.behavior().pressGems() != 0) {
            restore(original);
            helper.fail("a zero-catalyst conversion with an empty slot must plan, got " + plan.reason());
            return;
        }
        GlyphPressGameTests.request(player, GlyphPressGameTests.open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.succeedWhen(() -> {
            helper.assertTrue(press.job() == null, "still engraving");
            ItemStack output = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(!output.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem())
                .equals(new ImportedGlyphKey(FIREBOLT, 1).itemId()), "exactly Firebolt I comes out, got " + output);
            helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).isEmpty(), "nothing needed or consumed");
            restore(original);
        });
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch = SCHOOL_BATCH)
    public static void beforeSchoolBatch(net.minecraft.server.level.ServerLevel level) {
        mappingsBeforeBatch = GlyphBehavior.mappings();
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = SCHOOL_BATCH)
    public static void afterSchoolBatch(net.minecraft.server.level.ServerLevel level) {
        if (mappingsBeforeBatch != null && GlyphBehavior.mappings() != mappingsBeforeBatch) restore(mappingsBeforeBatch);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = ZERO_BATCH)
    public static void afterZeroBatch(net.minecraft.server.level.ServerLevel level) {
        if (mappingsBeforeBatch != null && GlyphBehavior.mappings() != mappingsBeforeBatch) restore(mappingsBeforeBatch);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch = ZERO_BATCH)
    public static void beforeZeroBatch(net.minecraft.server.level.ServerLevel level) {
        mappingsBeforeBatch = GlyphBehavior.mappings();
    }

    private static volatile GlyphMappings.Result mappingsBeforeBatch;

    @GameTest(template = "platform")
    public static void ironsLoaded_press_positiveCatalystWithAnEmptySlotRefusesAndMovesNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        GlyphPressBlockEntity press = GlyphPressGameTests.press(helper);
        ServerPlayer player = GlyphPressGameTests.player(helper, "press_pos_cat");
        GlyphPressGameTests.load(press, IronGlyphBridges.get().scrollStack(FIREBOLT, 1), 1, 0);
        GlyphPressGameTests.fillSource(press, 500);
        helper.assertTrue(press.plan(player).reason() == GlyphPressPlan.Reason.NEEDS_CATALYST, "one gem is required by default");
        ItemStack[] before = GlyphPressGameTests.snapshot(press);
        int source = press.freeSource();
        GlyphPressGameTests.request(player, GlyphPressGameTests.open(player, press), GlyphPressActionPayload.ACTION_INSCRIBE);
        helper.assertTrue(press.job() == null && GlyphPressGameTests.same(before, GlyphPressGameTests.snapshot(press)) && press.freeSource() == source,
            "a refused start moves nothing");
        ItemStack planks = new ItemStack(Items.OAK_PLANKS, 4);
        helper.assertTrue(press.getItems().insertItem(GlyphPressBlockEntity.SLOT_CATALYST, planks, false).getCount() == 4
                && press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).isEmpty(),
            "an item of no configured catalyst tag is not admitted");
        helper.succeed();
    }

    // ---- D5 validated recovery ---------------------------------------------------------------

    @GameTest(template = "platform")
    public static void press_aMalformedSavedJobReturnsOnlyAValidatedReservation(GameTestHelper helper) {
        GlyphPressBlockEntity press = GlyphPressGameTests.press(helper);
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = new CompoundTag();
        saved.putInt("press_schema", GlyphPressBlockEntity.SCHEMA);
        saved.putInt("source", 100);
        CompoundTag job = new CompoundTag();
        job.putString("spell", "not a valid id");
        job.putInt("source", 1500);
        job.putInt("reserved", 99_999);
        job.putString("phase", "RUNNING");
        saved.put("job", job);
        press.loadWithComponents(saved, registries);
        helper.assertTrue(press.job() == null && press.freeSource() == 100,
            "a reservation above its own cost is not trusted, so nothing is minted: " + press.freeSource());
        job.putInt("reserved", 700);
        press.loadWithComponents(saved, registries);
        helper.assertTrue(press.freeSource() == 800, "an unreadable job is cancelled and its validated reservation returned: " + press.freeSource());
        CompoundTag huge = new CompoundTag();
        huge.putInt("press_schema", GlyphPressBlockEntity.SCHEMA);
        huge.putInt("source", Integer.MAX_VALUE);
        press.loadWithComponents(huge, registries);
        helper.assertTrue(press.freeSource() <= 200_000, "a corrupt buffer is bounded: " + press.freeSource());
        helper.succeed();
    }

    // ---- C1 / C2 / C3 ------------------------------------------------------------------------

    /** Iron's level event fires once per occurrence and the cast event's level is the one committed. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_levels_nativeModifiersApplyOnceAndTheEventLevelIsCommitted(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, "glyph_levels_341", GameType.SURVIVAL);
        ImportedGlyphGameTests.fund(player);
        AutoCloseable probe = Glyph341IronsSupport.probe(player, 1, 0, 1.0);
        try {
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("heal", 1)));
            helper.assertTrue(Glyph341IronsSupport.levelEvents(probe) == 1,
                "ModifySpellLevelEvent fires once for the occurrence (it was never applied before 3.4.1), got " + Glyph341IronsSupport.levelEvents(probe));
            helper.assertTrue(Glyph341IronsSupport.castEvents(probe) == 1, "SpellOnCastEvent fires once");
            helper.assertTrue(Glyph341IronsSupport.lastCastLevel(probe) == 2, "the +1 native bonus reaches the cast: " + Glyph341IronsSupport.lastCastLevel(probe));
            // 3.4.1 review DOCS-01: /ans glyph sessions shows the committed level and the settled price, not the request.
            var outcomes = GlyphSessions.outcomes(player.getUUID());
            var last = outcomes.isEmpty() ? null : outcomes.get(outcomes.size() - 1);
            helper.assertTrue(last != null && last.outcome() == com.otectus.arsnspells.glyph.runtime.GlyphOutcome.EFFECT_APPLIED
                    && last.level() == 2 && last.detail().contains("Iron's price "),
                "the session record shows the committed level 2 and Iron's settled price: " + last);
        } finally {
            Glyph341IronsSupport.close(probe);
        }
        helper.succeed();
    }

    /** Every channel pulse runs at the level its own cast event settles on, not only at its cost. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 300)
    public static void ironsLoaded_channel_pulsesUseTheirEventLevel(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, "glyph_pulse_341", GameType.SURVIVAL);
        ImportedGlyphGameTests.fund(player);
        GlyphSessions.clearOutcomes(player.getUUID());
        AutoCloseable probe = Glyph341IronsSupport.probe(player, 0, 2, 1.0);
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("cone_of_cold", 1)));
        helper.succeedWhen(() -> {
            var pulses = GlyphSessions.outcomes(player.getUUID()).stream().filter(o -> o.detail().startsWith("pulse at level")).toList();
            helper.assertTrue(pulses.size() >= 2, "several pulses ran: " + pulses);
            helper.assertTrue(pulses.stream().allMatch(o -> o.level() == 3),
                "the first and every later pulse run at the event's level 3 (base 1 + 2): " + pulses);
            Glyph341IronsSupport.close(probe);
        });
    }

    /**
     * A native recast is free in Iron's: admission no longer demands the original spell price for it
     * (it used to quote and reserve it, refusing a caster who could not afford a cast that costs
     * nothing). The Ars parent leg is still charged, exactly once: the caster here can afford that
     * leg plus only half the original price, which 3.4.0 refused.
     */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_recast_isAdmittedWithoutItsOriginalPrice(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, "glyph_recast_cheap", GameType.SURVIVAL);
        ImportedGlyphGameTests.fund(player);
        IronsProxyCastDriver.learnSpell(player, "irons_spellbooks:eldritch_blast");
        var pig = ImportedGlyphGameTests.pig(helper);
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        Spell blast = ImportedGlyphGameTests.spell(com.hollingsworth.arsnouveau.common.spell.method.MethodTouch.INSTANCE,
            ImportedGlyphGameTests.glyph("eldritch_blast", 1));
        double glyph = ImportedGlyphGameTests.glyphPrice(player, "eldritch_blast", 1);
        double before = ImportedGlyphGameTests.pools(player);
        ImportedGlyphGameTests.cast(player, blast);
        double parentLeg = before - ImportedGlyphGameTests.pools(player) - glyph;
        int recasts = IronsProxyCastDriver.remainingRecasts(player, "irons_spellbooks:eldritch_blast");
        helper.assertTrue(recasts > 0 && glyph > 0 && parentLeg >= 0, "fixture: the first blast opens the recast window (glyph " + glyph
            + ", parent leg " + parentLeg + ")");
        // Afford the parent leg and half the original price, on whichever pool pays.
        float budget = (float) (parentLeg + glyph * 0.5);
        IronsProxyCastDriver.setIronsMana(player, budget);
        com.otectus.arsnspells.bridge.NativeManaAccess.with(player, com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA, () -> {
            com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player).setMana(budget);
            return null;
        });
        double beforeRecast = ImportedGlyphGameTests.pools(player);
        ImportedGlyphGameTests.cast(player, blast);
        helper.assertTrue(IronsProxyCastDriver.remainingRecasts(player, "irons_spellbooks:eldritch_blast") == recasts - 1,
            "the recast ran although the caster cannot afford the original price: admission did not demand it");
        double spent = beforeRecast - ImportedGlyphGameTests.pools(player);
        helper.assertTrue(Math.abs(spent - parentLeg) < 0.01, "only the Ars parent leg is charged, once: spent " + spent + ", leg " + parentLeg);
        helper.succeed();
    }

    // ---- B2 live maximum ----------------------------------------------------------------------

    @GameTest(template = "platform", batch = CONFIG_BATCH, timeoutTicks = 100)
    public static void ironsLoaded_liveMaximum_lowerRaiseAndBeyondTheEnvelope(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        int original = Glyph341IronsSupport.maxLevel(FIREBOLT_ID);
        try {
            Glyph341IronsSupport.setMaxLevel(FIREBOLT_ID, 3);
            GlyphBehavior.rebuild();
            helper.assertTrue(GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 5)).dormancy() == Dormancy.LEVEL_ABOVE_SERVER_MAX,
                "lowered to 3, Firebolt V is dormant");
            var one = ImportedGlyphGameTests.glyph("firebolt", 1);
            helper.assertTrue(one.getAugmentLimit(AugmentAmplify.INSTANCE.getRegistryName()) == 2, "Amplify reaches level 3 at most");
            ServerPlayer player = ImportedGlyphGameTests.caster(helper, "glyph_lowered_341", GameType.SURVIVAL);
            ImportedGlyphGameTests.fund(player);
            double before = ImportedGlyphGameTests.pools(player);
            ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("firebolt", 2),
                AugmentAmplify.INSTANCE, AugmentAmplify.INSTANCE));
            helper.assertTrue(ImportedGlyphGameTests.pools(player) == before,
                "Firebolt II with two Amplify asks for level 4 above the maximum 3: refused before anything is paid");
            // Two levels above whatever envelope is registered (X with the shipped catalog, XV with irons_envelope_15).
            int envelope = com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog.get().entry(FIREBOLT).registeredMax();
            Glyph341IronsSupport.setMaxLevel(FIREBOLT_ID, envelope + 5);
            GlyphBehavior.rebuild();
            helper.assertTrue(GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, envelope)).available(),
                "raised above the envelope, the highest registered level is available");
            ItemStack beyond = IronsProxyCastDriver.rawScroll(FIREBOLT_ID, envelope + 2);
            GlyphPressBlockEntity press = GlyphPressGameTests.press(helper);
            GlyphPressGameTests.load(press, beyond, 1, 64);
            helper.assertTrue(press.plan(null).reason() == GlyphPressPlan.Reason.LEVEL_NOT_CATALOGUED,
                "a level-" + (envelope + 2) + " scroll Iron's accepts has no identity: reported, never downcast, got " + press.plan(null).reason());
        } finally {
            Glyph341IronsSupport.setMaxLevel(FIREBOLT_ID, original);
            GlyphBehavior.rebuild();
        }
        helper.succeed();
    }

    // ---- B3 live school ------------------------------------------------------------------------

    /**
     * A school changed through Iron's own configuration is the school ANS uses: school policy and
     * progression and discount resolution follow it, while the catalogue keeps recording the
     * school the identity was published with.
     */
    @GameTest(template = "platform", batch = SCHOOL_BATCH, timeoutTicks = 100)
    public static void ironsLoaded_school_liveSchoolDrivesPolicyAndProgression(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        var part = ImportedGlyphGameTests.glyph("firebolt", 1);
        helper.assertTrue(com.otectus.arsnspells.util.SchoolResolver.resolveKeys(part)
            .equals(List.of(com.otectus.arsnspells.util.SchoolKeys.normalize("irons_spellbooks:fire"))), "fixture: Firebolt starts as fire");
        GlyphMappings.Result mappings = GlyphBehavior.mappings();
        String original = Glyph341IronsSupport.setSchool(FIREBOLT_ID, "irons_spellbooks:ice");
        try {
            GlyphBehavior.rebuild();
            helper.assertTrue("irons_spellbooks:ice".equals(String.valueOf(GlyphBehavior.current().spell(FIREBOLT).school())),
                "the live school is Iron's: " + GlyphBehavior.current().spell(FIREBOLT).school());
            helper.assertTrue("irons_spellbooks:ice".equals(part.ironsSchool().toString()), "the glyph reports its live school");
            helper.assertTrue("irons_spellbooks:fire".equals(part.entry().school().toString()), "the catalogue still records the published school");
            helper.assertTrue(com.otectus.arsnspells.util.SchoolResolver.resolveKeys(part)
                .equals(List.of(com.otectus.arsnspells.util.SchoolKeys.normalize("irons_spellbooks:ice"))),
                "progression and discounts resolve the live school: " + com.otectus.arsnspells.util.SchoolResolver.resolveKeys(part));
            publish("{\"schema_version\": 2, \"type\": \"ars_n_spells:policy\", \"deny_schools\": [\"irons_spellbooks:fire\"]}");
            helper.assertTrue(GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 1)).available(),
                "denying the catalogued school no longer touches it: " + GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 1)).dormancy());
            publish("{\"schema_version\": 2, \"type\": \"ars_n_spells:policy\", \"deny_schools\": [\"irons_spellbooks:ice\"]}");
            helper.assertTrue(GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 1)).dormancy() == Dormancy.DISABLED_BY_PACK,
                "denying the live school does");
        } finally {
            Glyph341IronsSupport.setSchool(FIREBOLT_ID, original);
            restore(mappings);
        }
        helper.succeed();
    }

    // ---- E uninscription through the brazier --------------------------------------------------

    @GameTest(template = "platform", batch = "ans_rituals_uninscribe_book", timeoutTicks = 600)
    public static void ironsLoaded_uninscription_removesOnlyAnsInscriptionsFromARealBoundBook(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "netherite_spell_book")));
        helper.assertTrue(IronsProxyCastDriver.addNativeSpellToBook(book), "fixture: a native spell in the book");
        book.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Grimoire of Tests"));
        List<String> natives = Glyph341IronsSupport.nativeSpells(book);
        int capacity = Glyph341IronsSupport.maxSpellCount(book);
        BlockPos brazierPos = new BlockPos(2, 1, 2);
        ItemEntity[] bookEntity = {null};
        RitualBrazierTile[] tile = {null};
        int settle = com.otectus.arsnspells.rituals.AnsRitual.DEFAULT_DURATION_TICKS + 20;
        helper.startSequence()
            .thenExecute(() -> {
                RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 1.5), carrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE)));
                bookEntity[0] = RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 3.5), book);
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellbookBindingRitual.REGISTRY_PATH);
                tile[0].startRitual(ImportedGlyphGameTests.caster(helper, "uninscribe_bind1", GameType.SURVIVAL));
            })
            // Each phase waits for its ritual's outcome rather than a fixed delay: a freshly generated world runs slower.
            .thenWaitUntil(() -> helper.assertTrue(entries(bookEntity[0]) == 1, "first bind: one Ars entry"))
            .thenExecute(() -> {
                RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 1.5), carrier(new Spell(MethodSelf.INSTANCE, EffectLight.INSTANCE)));
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellbookBindingRitual.REGISTRY_PATH);
                tile[0].startRitual(ImportedGlyphGameTests.caster(helper, "uninscribe_bind2", GameType.SURVIVAL));
            })
            .thenWaitUntil(() -> helper.assertTrue(entries(bookEntity[0]) == 2, "second bind: two Ars entries"))
            .thenExecute(() -> {
                helper.assertTrue(Glyph341IronsSupport.proxySlots(bookEntity[0].getItem()) == 2, "two native proxy slots");
                InscriptionInputs inputs = InscriptionInputs.classify(List.of(bookEntity[0]), InscriptionInputs.Operation.UNINSCRIPTION);
                helper.assertTrue(inputs.inscribed.size() == 1 && inputs.sources.isEmpty(),
                    "a bound book is an inscribed item for uninscription, not a spell source (issue #14)");
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellUninscriptionRitual.REGISTRY_PATH);
                tile[0].startRitual(ImportedGlyphGameTests.caster(helper, "uninscribe_run", GameType.SURVIVAL));
            })
            .thenWaitUntil(() -> helper.assertTrue(tile[0].ritual == null && entries(bookEntity[0]) == 0, "every Ars inscription is removed"))
            .thenExecute(() -> {
                ItemStack cleaned = bookEntity[0].getItem();
                helper.assertTrue(Glyph341IronsSupport.proxySlots(cleaned) == 0, "every proxy slot is removed");
                helper.assertTrue(Glyph341IronsSupport.nativeSpells(cleaned).equals(natives),
                    "the native spells stay exactly: " + Glyph341IronsSupport.nativeSpells(cleaned) + " vs " + natives);
                helper.assertTrue(Glyph341IronsSupport.maxSpellCount(cleaned) == capacity, "capacity restored: "
                    + Glyph341IronsSupport.maxSpellCount(cleaned) + " vs " + capacity);
                helper.assertTrue("Grimoire of Tests".equals(cleaned.getHoverName().getString()), "the custom name stays");
            })
            .thenSucceed();
    }

    /** An unrelated item in range refuses the uninscription and leaves the bound book unchanged, in either drop order. */
    @GameTest(template = "platform", batch = "ans_rituals_uninscribe_stray", timeoutTicks = 600)
    public static void ironsLoaded_uninscription_strayItemRefusesWithoutMutationInEitherOrder(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "netherite_spell_book")));
        helper.assertTrue(IronsProxyCastDriver.addNativeSpellToBook(book), "fixture: a native spell in the book");
        ItemStack carrierScroll = carrier(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        helper.assertTrue(com.otectus.arsnspells.spell.IronsBookBindingUtil.extractSingleArsEntry(carrierScroll)
            .map(entry -> com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(book, entry)).orElse(false),
            "fixture: the book is bound through the binding utility the ritual uses");
        ItemStack expected = book.copy();
        BlockPos brazierPos = new BlockPos(2, 1, 2);
        ItemEntity[] entities = {null, null};
        RitualBrazierTile[] tile = {null};
        int settle = com.otectus.arsnspells.rituals.AnsRitual.DEFAULT_DURATION_TICKS + 20;
        helper.startSequence()
            .thenExecute(() -> {
                // Stray first, book second.
                entities[1] = RitualLifecycleGameTests.dropNear(helper, new Vec3(1.5, 2.25, 2.5), new ItemStack(Items.TORCH));
                entities[0] = RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 3.5), book.copy());
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellUninscriptionRitual.REGISTRY_PATH);
                tile[0].startRitual(ImportedGlyphGameTests.caster(helper, "uninscribe_stray", GameType.SURVIVAL));
            })
            .thenExecuteAfter(settle, () -> {
                assertRefusedUnchanged(helper, tile[0], entities[0], expected);
                entities[0].discard();
                entities[1].discard();
                // Book first, stray second.
                entities[0] = RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 2.25, 3.5), book.copy());
                entities[1] = RitualLifecycleGameTests.dropNear(helper, new Vec3(1.5, 2.25, 2.5), new ItemStack(Items.TORCH));
                tile[0] = RitualLifecycleGameTests.lightAnsBrazier(helper, brazierPos, SpellUninscriptionRitual.REGISTRY_PATH);
                tile[0].startRitual(ImportedGlyphGameTests.caster(helper, "uninscribe_stray2", GameType.SURVIVAL));
            })
            .thenExecuteAfter(settle, () -> assertRefusedUnchanged(helper, tile[0], entities[0], expected))
            .thenSucceed();
    }

    private static void assertRefusedUnchanged(GameTestHelper helper, RitualBrazierTile tile, ItemEntity book, ItemStack expected) {
        helper.assertTrue(tile.ritual == null, "the ritual ran and ended");
        helper.assertTrue(book.isAlive(), "the book is still there");
        helper.assertTrue(ItemStack.matches(expected, book.getItem()), "a refused ritual changes nothing");
        helper.assertTrue(entries(book) == 1, "the inscription is still there");
    }

    private static int entries(ItemEntity entity) {
        return CrossModSpellComponents.countArsEntries(CrossModSpellComponents.get(entity.getItem()));
    }

    private static ItemStack carrier(Spell spell) {
        ItemStack scroll = ArsSpellExportUtil.createIronsScrollCarrier(spell);
        if (scroll.isEmpty()) throw new IllegalStateException("exporting an Ars spell must yield a carrier scroll");
        return scroll;
    }

    // ---- A identity evidence -------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void ironsLoaded_identity_ironsSpellsAreRegisteredAfterGlyphItems(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        List<String> order = RegistrationOrderProbe.order();
        int items = order.indexOf("minecraft:item"), spells = order.indexOf(RegistrationOrderProbe.IRONS_SPELLS.toString());
        helper.assertTrue(items >= 0 && spells > items, "Iron's spell registry fills after the item registry: " + order);
        helper.assertTrue(RegistrationOrderProbe.ironsSpellsAtItemRegistration() == 0,
            "no Iron's spell existed when glyph items were registered, got " + RegistrationOrderProbe.ironsSpellsAtItemRegistration());
        helper.assertTrue(RegistrationOrderProbe.glyphPartsAtItemRegistration() >= ImportedGlyphCatalog.get().variantCount(),
            "every catalogued glyph part was already in Ars's registry then");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_identity_reportAccountsForEveryNativeSpell(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        var rows = com.otectus.arsnspells.commands.GlyphReportCommands.rows();
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (var row : rows) listed.add(row.id());
        for (var spell : IronGlyphBridges.get().inventory()) helper.assertTrue(listed.contains(spell.spellId()), spell.spellId() + " missing");
        helper.assertTrue(rows.stream().noneMatch(row -> row.status().equals("not_catalogued")),
            "the base catalog leaves no loaded Iron's spell without an identity: "
                + rows.stream().filter(row -> row.status().equals("not_catalogued")).map(row -> row.id()).toList());
        helper.succeed();
    }

    // ---- Fixtures ----------------------------------------------------------------------------

    private static void publish(String json) {
        GlyphMappings.Result candidate = GlyphMappings.parse(List.of(new GlyphMappings.Source(
            ResourceLocation.fromNamespaceAndPath("ans_gametest", "behaviour_341"), "gametest", 1000, JsonParser.parseString(json))),
            ImportedGlyphCatalog.get());
        if (!candidate.valid()) throw new IllegalStateException("test behaviour file invalid: " + candidate.errors());
        GlyphBehavior.acceptMappings(candidate);
        GlyphBehavior.rebuild();
    }

    private static void restore(GlyphMappings.Result original) {
        GlyphBehavior.acceptMappings(original);
        GlyphBehavior.rebuild();
    }
}
