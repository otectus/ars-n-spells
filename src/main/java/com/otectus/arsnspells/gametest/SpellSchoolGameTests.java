package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectFreeze;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.inscription.LoomInscription;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.network.CrossCastRequestPacket;
import com.otectus.arsnspells.network.LoomInventoryRevision;
import com.otectus.arsnspells.network.LoomRequestContext;
import com.otectus.arsnspells.network.LoomRequestHandler;
import com.otectus.arsnspells.network.SpellLoomExportPacket;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.rituals.SpellbookBindingRitual;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.IronsSpellbookBinder;
import com.otectus.arsnspells.spell.SpellSchoolChoice;
import com.otectus.arsnspells.util.SchoolMappings;
import com.hollingsworth.arsnouveau.common.block.tile.RitualBrazierTile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Functional spell schools, end to end: what the Spell Loom writes, what every route carries, and
 * what a cast actually does with the choice.
 *
 * <p>Every cast here goes through the real ANS entry points ({@code serverHandleCast} for a
 * scroll, Iron's own proxy cast for a spellbook wheel slot) and the observables are world state:
 * affinity and progression records, health, and how much damage a villager actually took. The
 * damage tests compare against an Automatic hit by the same caster, so they hold whatever the
 * caster's global spell power or the Harm glyph's configured damage.
 *
 * <p>Iron's-only (the schools are Iron's), gated through {@link OptionalModGate}.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SpellSchoolGameTests {

    private static final String FIRE = "irons_spellbooks:fire";
    private static final String ICE = "irons_spellbooks:ice";
    private static final String HOLY = "irons_spellbooks:holy";
    private static final String NATURE = "irons_spellbooks:nature";
    private static final BlockPos LOOM = new BlockPos(2, 1, 2);
    /** Test-only spell-power boost identity, removed again by every test that adds it. */
    private static final UUID BOOST = UUID.fromString("0a000000-0000-0000-0000-00000005c401");
    private static final int RITUAL_SETTLE_TICKS = com.otectus.arsnspells.rituals.AnsRitual.DEFAULT_DURATION_TICKS + 20;

    private SpellSchoolGameTests() {}

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Spell heal() {
        return new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
    }

    /** Projectile + Harm: the projectile flies a couple of blocks, then Harm (a nature glyph) lands. */
    private static Spell projectileHarm() {
        return new Spell(MethodProjectile.INSTANCE, EffectHarm.INSTANCE);
    }

    private static ItemStack carrier(GameTestHelper helper, Spell spell, String school) {
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(spell, "School Test", "arcane", "spell/heal", school);
        helper.assertTrue(!carrier.isEmpty(), "export must yield a carrier with Iron's loaded");
        return carrier;
    }

    private static CompoundTag entryOf(ItemStack stack) {
        return stack.getOrCreateTag().getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND).getCompound(0);
    }

    private static int affinity(ServerPlayer player, String school) {
        return player.getCapability(AffinityData.AFFINITY_DATA).map(data -> data.getLevel(school)).orElse(-1);
    }

    private static int casts(ServerPlayer player, String school) {
        return player.getCapability(ProgressionData.PROGRESSION_DATA).map(data -> data.getCastCount(school)).orElse(-1);
    }

    private static void boost(ServerPlayer player, String school, double amount) {
        Attribute attribute = com.otectus.arsnspells.compat.IronsSchoolAttributes.power(school);
        AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
        if (instance == null) throw new IllegalStateException("no spell power attribute for " + school);
        instance.removeModifier(BOOST);
        instance.addTransientModifier(new AttributeModifier(BOOST, "ANS school test", amount, AttributeModifier.Operation.ADDITION));
    }

    private static void clearBoosts(ServerPlayer player) {
        for (String school : new String[] {FIRE, ICE, NATURE, HOLY}) {
            Attribute attribute = com.otectus.arsnspells.compat.IronsSchoolAttributes.power(school);
            AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(BOOST);
        }
    }

    /** Cast a carrier scroll the way the right-click packet does. */
    private static boolean castScroll(ServerPlayer player, ItemStack scroll) {
        player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
        return CrossCastingHandler.serverHandleCast(player, player.getMainHandItem(), InteractionHand.MAIN_HAND,
            CrossCastRequestPacket.Action.CAST, UUID.randomUUID());
    }

    /** A spellbook holding {@code spell} with the given school in proxy pool 1. */
    private static ItemStack boundBook(GameTestHelper helper, Spell spell, String school) {
        Item item = CrossCastGameTests.findIronsSpellBook();
        helper.assertTrue(item != null, "no Iron's spellbook item is registered");
        ItemStack book = new ItemStack(item);
        IronsBookBindingUtil.AppendResult result = IronsBookBindingUtil.appendArsSpellToBook(
            book, spell.serialize(), "Bound", "water", "spell/heal", school, -1);
        helper.assertTrue(result.wasAdded(), "binding must ADD, got " + result);
        return book;
    }

    /**
     * A NoAI villager with full health at a platform position, the damage observable. Immune to
     * knockback, so a hit cannot push it off the platform and add fall damage to the reading.
     */
    private static Villager villager(GameTestHelper helper, double x, double z) {
        // y 2 stands on the platform: relative y 1 is the floor layer itself, where a mob
        // suffocates and its health stops being a clean reading of the hit.
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(x, 2, z));
        AttributeInstance knockback = villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(1.0);
        villager.setHealth(villager.getMaxHealth());
        return villager;
    }

    /** Stand at the platform centre facing {@code yaw}: -90 east (+x), 90 west, 0 south (+z), 180 north. */
    private static void face(GameTestHelper helper, ServerPlayer player, float yaw) {
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        player.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        player.setYHeadRot(yaw);
    }

    private static SpellLoomBlockEntity placeLoom(GameTestHelper helper) {
        helper.setBlock(LOOM, ModBlocksRegistry.SPELL_LOOM.get().defaultBlockState());
        if (!(helper.getBlockEntity(LOOM) instanceof SpellLoomBlockEntity loom)) {
            throw new IllegalStateException("placing the Spell Loom produced no block entity");
        }
        return loom;
    }

    private static ItemStack arsBook(GameTestHelper helper, Spell spell) {
        Item book = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ars_nouveau", "novice_spell_book"));
        helper.assertTrue(book != null, "ars_nouveau:novice_spell_book is not registered");
        ItemStack stack = new ItemStack(book);
        SpellCaster caster = new SpellCaster(stack);
        caster.setSpell(spell);
        caster.writeItem(stack);
        return stack;
    }

    private static void seed(SpellLoomBlockEntity loom, ItemStack source, ItemStack scroll) {
        ItemStackHandler items = loom.getItems();
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE, source);
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL, scroll);
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
    }

    private static String snapshot(SpellLoomBlockEntity loom) {
        StringBuilder sb = new StringBuilder();
        for (int slot = 0; slot < SpellLoomBlockEntity.SLOT_COUNT; slot++) {
            ItemStack stack = loom.getItems().getStackInSlot(slot);
            sb.append(slot).append('=').append(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                .append('x').append(stack.getCount()).append('/').append(stack.getTag()).append(';');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // The Spell Loom
    // ------------------------------------------------------------------

    /** The chosen school is written on the carrier entry, apart from the cosmetic background. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_loomInscribesTheChosenSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        SpellLoomBlockEntity loom = placeLoom(helper);
        seed(loom, arsBook(helper, heal()), IronsLoomFixtures.blankScroll());
        helper.assertTrue(InscriptionPlan.REASON_OK.equals(
                LoomInscription.apply(loom, false, "Frost Mend", "fire", "spell/heal", "ice")),
            "a supported school must inscribe");
        ItemStack chosen = loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(ICE.equals(CrossCastNbt.storedSchool(entryOf(chosen))),
            "the bare name 'ice' must be stored as irons_spellbooks:ice, got " + CrossCastNbt.storedSchool(entryOf(chosen)));
        helper.assertTrue("fire".equals(entryOf(chosen).getString(CrossCastNbt.TAG_NATURE)),
            "the fire background is cosmetic and stored as chosen, separately from the ice school");

        seed(loom, arsBook(helper, heal()), IronsLoomFixtures.blankScroll());
        helper.assertTrue(InscriptionPlan.REASON_OK.equals(
            LoomInscription.apply(loom, false, "Mend", "fire", "spell/heal", "auto")), "Automatic must inscribe");
        ItemStack automatic = loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(!entryOf(automatic).contains(CrossCastNbt.TAG_SCHOOL),
            "Automatic must write no school, exactly like a carrier made before schools existed");
        helper.succeed();
    }

    /** A school this server cannot use is refused before anything moves. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_loomRefusesUnknownSchoolAndMovesNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        SpellLoomBlockEntity loom = placeLoom(helper);
        seed(loom, arsBook(helper, heal()), IronsLoomFixtures.blankScroll());
        String before = snapshot(loom);
        for (String bad : new String[] {"removed_addon:aqua", "not a school!", "generic"}) {
            helper.assertTrue(LoomInscription.REASON_SCHOOL_UNKNOWN.equals(
                    LoomInscription.apply(loom, false, "X", "", "", bad)),
                "'" + bad + "' must be refused as unknown");
            helper.assertTrue(LoomInscription.REASON_SCHOOL_UNKNOWN.equals(
                    LoomInscription.apply(loom, true, "X", "", "", bad)),
                "Convert must refuse '" + bad + "' too");
            helper.assertTrue(before.equals(snapshot(loom)), "a refused school moved items: " + bad);
        }
        helper.succeed();
    }

    /** Apply rewrites only the school of the carrier in the target slot, keeping everything else. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_loomAppliesSchoolInPlaceToACarrier(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        SpellLoomBlockEntity loom = placeLoom(helper);
        ItemStack original = carrier(helper, heal(), null);
        original.setCount(3);
        seed(loom, ItemStack.EMPTY, original.copy());

        helper.assertTrue(InscriptionPlan.REASON_OK.equals(LoomInscription.applySchoolChange(loom, FIRE)),
            "a carrier in the target slot must accept a supported school");
        ItemStack changed = loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL);
        helper.assertTrue(changed.getCount() == 3, "the whole stack keeps its size; got " + changed.getCount());
        helper.assertTrue(FIRE.equals(CrossCastNbt.storedSchool(entryOf(changed))), "the school must now be fire");
        CompoundTag withoutSchool = entryOf(changed).copy();
        withoutSchool.remove(CrossCastNbt.TAG_SCHOOL);
        helper.assertTrue(withoutSchool.equals(entryOf(original)),
            "the spell, name, background and icon must be untouched by a school change");
        helper.assertTrue(loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT).isEmpty()
                && loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE).isEmpty(),
            "a school change moves nothing into the source or output");

        String before = snapshot(loom);
        helper.assertTrue(LoomInscription.REASON_SCHOOL_UNCHANGED.equals(LoomInscription.applySchoolChange(loom, "fire")),
            "re-applying the same school must be refused");
        helper.assertTrue(before.equals(snapshot(loom)), "a refused school change altered the loom");

        helper.assertTrue(InscriptionPlan.REASON_OK.equals(LoomInscription.applySchoolChange(loom, "auto")),
            "returning to Automatic must be allowed");
        helper.assertTrue(ItemStack.isSameItemSameTags(loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL), original),
            "Automatic again must restore the carrier byte for byte");
        helper.succeed();
    }

    /** Every refused school change leaves all three slots exactly as they were. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_failedSchoolChangesMoveNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        SpellLoomBlockEntity loom = placeLoom(helper);
        for (ItemStack target : new ItemStack[] {IronsLoomFixtures.nativelyFilledScroll(), IronsLoomFixtures.blankScroll(),
                new ItemStack(Items.PAPER)}) {
            seed(loom, ItemStack.EMPTY, target);
            String before = snapshot(loom);
            helper.assertTrue(LoomInscription.REASON_NOT_A_CARRIER.equals(LoomInscription.applySchoolChange(loom, FIRE)),
                "only a Spell Loom carrier can change school: " + target);
            helper.assertTrue(before.equals(snapshot(loom)), "a refused change moved items");
        }
        seed(loom, ItemStack.EMPTY, carrier(helper, heal(), ICE));
        String before = snapshot(loom);
        helper.assertTrue(LoomInscription.REASON_SCHOOL_UNKNOWN.equals(
            LoomInscription.applySchoolChange(loom, "removed_addon:aqua")), "an uninstalled school must be refused");
        helper.assertTrue(before.equals(snapshot(loom)), "a refused change rewrote the carrier");
        helper.succeed();
    }

    /**
     * The server, not the client, decides: through the real request handler a bad school, a
     * replayed request id and a stale inventory revision all move nothing, and a valid request
     * inscribes and then re-schools.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "ans_school")
    public static void ironsLoaded_schoolChoice_loomRequestIsValidatedOnTheServer(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        SpellLoomBlockEntity loom = placeLoom(helper);
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_loom_request");
        Vec3 near = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(near.x, near.y, near.z);
        SpellLoomMenu menu = new SpellLoomMenu(7, player.getInventory(), loom);
        player.containerMenu = menu;
        seed(loom, arsBook(helper, heal()), IronsLoomFixtures.blankScroll());
        String before = snapshot(loom);
        UUID replayed = UUID.randomUUID();

        helper.startSequence()
            .thenExecute(() -> {
                String reason = LoomRequestHandler.execute(player, context(menu, replayed, LoomInventoryRevision.of(menu)),
                    SpellLoomExportPacket.ACTION_INSCRIBE, "Bad", "", "", "removed_addon:aqua");
                helper.assertTrue(LoomInscription.REASON_SCHOOL_UNKNOWN.equals(reason), "unknown school, got " + reason);
                helper.assertTrue(before.equals(snapshot(loom)), "a refused request moved items");
                String replay = LoomRequestHandler.execute(player, context(menu, replayed, LoomInventoryRevision.of(menu)),
                    SpellLoomExportPacket.ACTION_INSCRIBE, "Replay", "", "", ICE);
                helper.assertTrue(LoomRequestHandler.REJECTED.equals(replay), "a replayed request id must be rejected, got " + replay);
                helper.assertTrue(before.equals(snapshot(loom)), "a replayed request moved items");
            })
            .thenExecuteAfter(10, () -> {
                String stale = LoomRequestHandler.execute(player, context(menu, UUID.randomUUID(), "0".repeat(64)),
                    SpellLoomExportPacket.ACTION_INSCRIBE, "Stale", "", "", ICE);
                helper.assertTrue(LoomRequestHandler.STALE.equals(stale), "a stale revision must be refused, got " + stale);
                helper.assertTrue(before.equals(snapshot(loom)), "a stale request moved items");
            })
            .thenExecuteAfter(10, () -> {
                String ok = LoomRequestHandler.execute(player, context(menu, UUID.randomUUID(), LoomInventoryRevision.of(menu)),
                    SpellLoomExportPacket.ACTION_INSCRIBE, "Frost Mend", "water", "spell/heal", ICE);
                helper.assertTrue(InscriptionPlan.REASON_OK.equals(ok), "a valid request must inscribe, got " + ok);
                ItemStack out = loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT);
                helper.assertTrue(ICE.equals(CrossCastNbt.storedSchool(entryOf(out))), "the output must carry ice");
                seed(loom, ItemStack.EMPTY, out.copy());
            })
            .thenExecuteAfter(10, () -> {
                String set = LoomRequestHandler.execute(player, context(menu, UUID.randomUUID(), LoomInventoryRevision.of(menu)),
                    SpellLoomExportPacket.ACTION_SET_SCHOOL, "", "", "", FIRE);
                helper.assertTrue(InscriptionPlan.REASON_OK.equals(set), "set-school must succeed, got " + set);
                helper.assertTrue(FIRE.equals(CrossCastNbt.storedSchool(entryOf(
                    loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL)))), "the carrier must now be fire");
                player.containerMenu = player.inventoryMenu;
            })
            .thenSucceed();
    }

    private static LoomRequestContext context(SpellLoomMenu menu, UUID request, String revision) {
        return new LoomRequestContext(menu.containerId, menu.sessionId(), request, revision, SchoolMappings.get().digest());
    }

    // ------------------------------------------------------------------
    // Casting: affinity and progression follow the chosen school only
    // ------------------------------------------------------------------

    /** A heal (holy by its glyph) cast from a fire scroll trains fire and nothing else. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_scrollCastUsesOnlyTheChosenSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_scroll_cast");
        int fire = affinity(player, FIRE), holy = affinity(player, HOLY);
        int fireCasts = casts(player, FIRE), holyCasts = casts(player, HOLY);
        castScroll(player, carrier(helper, heal(), FIRE));
        helper.assertTrue(player.getHealth() > 10.0f, "the scroll's heal must still resolve: the glyphs are unchanged");
        helper.assertTrue(affinity(player, FIRE) == fire + 1, "fire affinity must rise by one");
        helper.assertTrue(affinity(player, HOLY) == holy, "the heal glyph's own holy school must gain nothing");
        helper.assertTrue(casts(player, FIRE) == fireCasts + 1 && casts(player, HOLY) == holyCasts,
            "progression must count the cast for fire only");
        helper.succeed();
    }

    /** The same through Iron's native spell wheel: the proxy cast carries the bound entry's school. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_wheelCastUsesOnlyTheChosenSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = boundBook(helper, heal(), FIRE);
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_wheel_cast");
        int fire = affinity(player, FIRE), holy = affinity(player, HOLY);
        IronsProxyCastDriver.castViaEquippedSpellbook(player, book, 1);
        helper.assertTrue(player.getHealth() > 10.0f, "the wheel cast must resolve the bound heal");
        helper.assertTrue(affinity(player, FIRE) == fire + 1 && affinity(player, HOLY) == holy,
            "a wheel cast must train the bound entry's chosen school only");
        helper.succeed();
    }

    /** A 3.3.x carrier with an ice background and no school stays Automatic: its heal trains holy. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_legacyNatureStaysAutomatic(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack legacy = ArsSpellExportUtil.createIronsScrollCarrier(heal(), "Old", "ice", "spell/heal");
        helper.assertTrue(!entryOf(legacy).contains(CrossCastNbt.TAG_SCHOOL), "the fixture must be a legacy entry");
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_legacy");
        int ice = affinity(player, ICE), holy = affinity(player, HOLY);
        castScroll(player, legacy);
        helper.assertTrue(affinity(player, HOLY) == holy + 1, "a legacy entry must train its glyph school");
        helper.assertTrue(affinity(player, ICE) == ice, "the ice background must never act as a school");
        helper.succeed();
    }

    /** A school whose addon is gone casts as Automatic and stays on the item for when it returns. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_removedAddonSchoolCastsAutomatic(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack scroll = carrier(helper, heal(), null);
        CrossCastNbt.setStoredSchool(entryOf(scroll), "removed_addon:aqua");
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_removed");
        int holy = affinity(player, HOLY);
        castScroll(player, scroll);
        helper.assertTrue(player.getHealth() > 10.0f, "the spell must still cast");
        helper.assertTrue(affinity(player, HOLY) == holy + 1, "an uninstalled school must fall back to the glyph school");
        helper.assertTrue("removed_addon:aqua".equals(CrossCastNbt.storedSchool(entryOf(player.getMainHandItem()))),
            "the stored choice must be preserved, not erased");
        helper.succeed();
    }

    /**
     * Native Ars and native Iron's casts are untouched, before and right after a chosen-school
     * cast: each trains exactly its own school, tracked as expected per-school increments so the
     * test holds whichever school Iron's own config gives its heal.
     */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_nativeCastsAreUnaffected(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_native");
        java.util.Map<String, Integer> expected = new java.util.HashMap<>();
        java.util.Map<String, Integer> baseline = new java.util.HashMap<>();
        for (String school : new String[] {FIRE, HOLY}) baseline.put(school, affinity(player, school));

        String ironsSchool = ironsHeal(helper, player, "before any Ars cast");
        baseline.putIfAbsent(ironsSchool, affinity(player, ironsSchool) - 1);
        expected.merge(ironsSchool, 1, Integer::sum);
        checkTracks(helper, player, baseline, expected, "after a native Iron's heal");

        castScroll(player, carrier(helper, heal(), FIRE));
        expected.merge(FIRE, 1, Integer::sum);
        checkTracks(helper, player, baseline, expected, "after a fire-school cast of a holy glyph");

        ItemStack arsBook = arsBook(helper, heal());
        player.setItemInHand(InteractionHand.MAIN_HAND, arsBook);
        player.setHealth(10.0f);
        new SpellCaster(arsBook).castSpell(player.level(), player, InteractionHand.MAIN_HAND, null, heal());
        helper.assertTrue(player.getHealth() > 10.0f, "the native Ars heal must resolve");
        expected.merge(HOLY, 1, Integer::sum);
        checkTracks(helper, player, baseline, expected, "after a native Ars heal");

        helper.assertTrue(ironsSchool.equals(ironsHeal(helper, player, "after the Ars casts")),
            "Iron's heal must keep its own school after a chosen-school cast");
        expected.merge(ironsSchool, 1, Integer::sum);
        checkTracks(helper, player, baseline, expected, "after a second native Iron's heal");
        helper.succeed();
    }

    /** Every tracked school has gained exactly its expected number of levels, and no other. */
    private static void checkTracks(GameTestHelper helper, ServerPlayer player, java.util.Map<String, Integer> baseline,
                                    java.util.Map<String, Integer> expected, String when) {
        for (String school : baseline.keySet()) {
            int gained = affinity(player, school) - baseline.get(school);
            helper.assertTrue(gained == expected.getOrDefault(school, 0),
                school + " gained " + gained + " levels " + when + ", expected " + expected.getOrDefault(school, 0));
        }
    }

    /**
     * Cast Iron's own heal from a native book with the hotkey path, check it resolved, and return
     * the school Iron's posted it under.
     */
    private static String ironsHeal(GameTestHelper helper, ServerPlayer player, String when) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setHealth(10.0f);
        boolean[] started = {false};
        java.util.List<String> events = IronsProxyCastDriver.onCastEvents(player, () -> started[0] =
            IronsProxyCastDriver.hotkeyCast(player, IronsProxyCastDriver.nativeBook("irons_spellbooks:heal"), 0));
        helper.assertTrue(started[0], "Iron's must start its own heal " + when);
        helper.assertTrue(player.getHealth() > 10.0f, "Iron's own heal must resolve " + when + ", health " + player.getHealth());
        helper.assertTrue(events.size() == 1 && events.get(0).startsWith("irons_spellbooks:heal@"),
            "Iron's must post exactly one cast event for its heal " + when + ", saw " + events);
        return events.get(0).substring("irons_spellbooks:heal@".length());
    }

    // ------------------------------------------------------------------
    // Casting: spell power scaling
    // ------------------------------------------------------------------

    /**
     * Projectile + Harm, whose glyph school is nature, fired at three villagers by one caster with
     * ice power 2 and fire power 1.5: Automatic deals the base hit, an ice scroll twice it, a fire
     * scroll one and a half times it. The hits land in the same tick, so anything that applies to
     * every hit equally cancels out of the ratios.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "ans_school")
    public static void ironsLoaded_schoolChoice_chosenSchoolScalesDamage(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_damage");
        Villager automatic = villager(helper, 4.5, 2.5);
        Villager ice = villager(helper, 0.5, 2.5);
        Villager fire = villager(helper, 2.5, 4.5);
        boost(player, ICE, 1.0);
        boost(player, FIRE, 0.5);
        face(helper, player, -90.0f);
        castScroll(player, carrier(helper, projectileHarm(), null));
        face(helper, player, 90.0f);
        castScroll(player, carrier(helper, projectileHarm(), ICE));
        face(helper, player, 0.0f);
        castScroll(player, carrier(helper, projectileHarm(), FIRE));
        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                try {
                    float base = automatic.getMaxHealth() - automatic.getHealth();
                    float iceHit = ice.getMaxHealth() - ice.getHealth();
                    float fireHit = fire.getMaxHealth() - fire.getHealth();
                    helper.assertTrue(base > 0.0f, "the Automatic control hit must land");
                    helper.assertTrue(Math.abs(iceHit / base - 2.0f) < 0.05f,
                        "an ice scroll must scale with ice power 2 alone: base " + base + ", ice " + iceHit);
                    helper.assertTrue(Math.abs(fireHit / base - 1.5f) < 0.05f,
                        "a fire scroll must scale with fire power 1.5 alone: base " + base + ", fire " + fireHit);
                } finally {
                    clearBoosts(player);
                }
            })
            .thenSucceed();
    }

    /**
     * Automatic keeps multi-school scaling: under the max policy Projectile + Freeze + Harm (ice and
     * nature glyphs) scales with the better of the two. Choosing ice gives ice alone, so the
     * stronger nature glyph adds nothing.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "ans_school_policy")
    public static void ironsLoaded_schoolChoice_automaticKeepsMultiSchoolScaling(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        String previous = AnsConfig.MULTI_SCHOOL_POWER_POLICY.get();
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_multi");
        Villager control = villager(helper, 4.5, 2.5);
        Villager automatic = villager(helper, 0.5, 2.5);
        Villager chosen = villager(helper, 2.5, 4.5);
        Spell freezeHarm = new Spell(MethodProjectile.INSTANCE, EffectFreeze.INSTANCE, EffectHarm.INSTANCE);
        TestConfig.set(AnsConfig.MULTI_SCHOOL_POWER_POLICY, "max");
        try {
            boost(player, NATURE, 1.0);
            face(helper, player, -90.0f);
            castScroll(player, carrier(helper, projectileHarm(), FIRE));
            face(helper, player, 90.0f);
            castScroll(player, carrier(helper, freezeHarm, null));
            face(helper, player, 0.0f);
            castScroll(player, carrier(helper, freezeHarm, ICE));
        } catch (RuntimeException setupFailed) {
            TestConfig.set(AnsConfig.MULTI_SCHOOL_POWER_POLICY, previous);
            clearBoosts(player);
            throw setupFailed;
        }
        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                try {
                    float base = control.getMaxHealth() - control.getHealth();
                    float auto = automatic.getMaxHealth() - automatic.getHealth();
                    float ice = chosen.getMaxHealth() - chosen.getHealth();
                    helper.assertTrue(base > 0.0f, "the unboosted fire control hit must land");
                    helper.assertTrue(Math.abs(auto / base - 2.0f) < 0.05f,
                        "Automatic must still take the stronger glyph school (nature 2) under max: base " + base + ", auto " + auto);
                    helper.assertTrue(Math.abs(ice / base - 1.0f) < 0.05f,
                        "a chosen ice school must not collect the nature glyph's power: base " + base + ", ice " + ice);
                } finally {
                    TestConfig.set(AnsConfig.MULTI_SCHOOL_POWER_POLICY, previous);
                    clearBoosts(player);
                }
            })
            .thenSucceed();
    }

    /**
     * A delayed hit keeps the school of the cast that fired it. The ice scroll's Projectile + Delay +
     * Harm flies, lands, waits a second and then harms, all after the caster has cast a fire scroll
     * and while they hold it, and is still scaled by ice: not by the latest cast, and not by the
     * held item. The projectile flight and the Delay glyph each carry the cast on in a new context.
     *
     * <p>Each hit is compared with an Automatic hit landing in the same tick, so anything that
     * applies to every hit equally cancels out of the ratio while a wrongly attributed school does
     * not.
     */
    @GameTest(template = "platform", timeoutTicks = 140, batch = "ans_school")
    public static void ironsLoaded_schoolChoice_delayedHitKeepsItsOriginatingSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_delay");
        Villager delayedIce = villager(helper, 4.5, 2.5);
        Villager delayedControl = villager(helper, 2.5, 0.5);
        Villager latest = villager(helper, 0.5, 2.5);
        Villager control = villager(helper, 2.5, 4.5);
        Spell delayedHarm = new Spell(MethodProjectile.INSTANCE, EffectDelay.INSTANCE, EffectHarm.INSTANCE);
        boost(player, ICE, 1.0);
        boost(player, FIRE, 1.5);
        face(helper, player, -90.0f);
        castScroll(player, carrier(helper, delayedHarm, ICE));
        face(helper, player, 180.0f);
        castScroll(player, carrier(helper, delayedHarm, null));
        face(helper, player, 90.0f);
        ItemStack fireScroll = carrier(helper, projectileHarm(), FIRE);
        castScroll(player, fireScroll);
        face(helper, player, 0.0f);
        castScroll(player, carrier(helper, projectileHarm(), null));
        player.setItemInHand(InteractionHand.MAIN_HAND, fireScroll);

        helper.startSequence()
            .thenExecuteAfter(12, () -> {
                float base = control.getMaxHealth() - control.getHealth();
                float latestHit = latest.getMaxHealth() - latest.getHealth();
                helper.assertTrue(base > 0.0f && latestHit > 0.0f, "the immediate projectile hits must land");
                helper.assertTrue(Math.abs(latestHit / base - 2.5f) < 0.06f,
                    "the later fire scroll scales with fire power 2.5: base " + base + ", fire " + latestHit);
                helper.assertTrue(delayedIce.getHealth() == delayedIce.getMaxHealth()
                    && delayedControl.getHealth() == delayedControl.getMaxHealth(),
                    "the delayed hits must still be waiting on their Delay glyph");
            })
            .thenExecuteAfter(40, () -> {
                try {
                    float delayedBase = delayedControl.getMaxHealth() - delayedControl.getHealth();
                    float delayedHit = delayedIce.getMaxHealth() - delayedIce.getHealth();
                    helper.assertTrue(delayedBase > 0.0f && delayedHit > 0.0f, "the delayed hits must land");
                    helper.assertTrue(Math.abs(delayedHit / delayedBase - 2.0f) < 0.06f,
                        "the delayed ice hit must scale with ice power 2, not the later fire cast or the held "
                            + "fire scroll: Automatic " + delayedBase + ", ice " + delayedHit);
                } finally {
                    clearBoosts(player);
                }
            })
            .thenSucceed();
    }

    // ------------------------------------------------------------------
    // Every route keeps the choice
    // ------------------------------------------------------------------

    /** Iron's Inscription Table and the bind command both carry the scroll's school onto the book. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_tableAndCommandBindKeepTheSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_bind_routes");
        Item bookItem = CrossCastGameTests.findIronsSpellBook();

        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, new ItemStack(bookItem));
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_SCROLL, carrier(helper, heal(), ICE));
        helper.assertTrue(IronsTableDriver.click(menu, player, -1), "the table must handle the bind");
        ItemStack tableBook = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        helper.assertTrue(ICE.equals(CrossCastNbt.storedSchool(CrossCastNbt.findEntryByProxyPoolId(tableBook.getOrCreateTag(), 1))),
            "the table route must keep the ice school");

        player.setItemInHand(InteractionHand.MAIN_HAND, carrier(helper, heal(), FIRE));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(bookItem));
        int result = helper.getLevel().getServer().getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "ans bind_scroll_to_irons_book");
        ItemStack commandBook = player.getOffhandItem();
        helper.assertTrue(result == 1, "the bind command must succeed, returned " + result);
        helper.assertTrue(FIRE.equals(CrossCastNbt.storedSchool(CrossCastNbt.findEntryByProxyPoolId(commandBook.getOrCreateTag(), 1))),
            "the command route must keep the fire school");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        int ice = affinity(player, ICE);
        IronsProxyCastDriver.castViaEquippedSpellbook(player, tableBook, 1);
        helper.assertTrue(affinity(player, ICE) == ice + 1, "the table-bound entry must cast with its ice school");
        helper.succeed();
    }

    /** The Spellbook Binding ritual, run on a real brazier, keeps the school too. */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "ans_school_ritual")
    public static void ironsLoaded_schoolChoice_ritualBindKeepsTheSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 1.3, 1.5), carrier(helper, heal(), ICE));
        ItemEntity book = RitualLifecycleGameTests.dropNear(helper, new Vec3(2.5, 1.3, 3.5),
            new ItemStack(CrossCastGameTests.findIronsSpellBook()));
        RitualBrazierTile tile = RitualLifecycleGameTests.lightAnsBrazier(helper, new BlockPos(2, 1, 2),
            SpellbookBindingRitual.REGISTRY_PATH);
        tile.startRitual(CrossCastGameTests.scenarioPlayer(helper, "school_ritual"));
        helper.startSequence()
            .thenExecuteAfter(RITUAL_SETTLE_TICKS, () -> {
                CompoundTag entry = CrossCastNbt.findEntryByProxyPoolId(book.getItem().getOrCreateTag(), 1);
                if (entry == null) {
                    StringBuilder nearby = new StringBuilder();
                    helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(3))
                        .forEach(item -> nearby.append(item.getItem()).append(' ').append(item.getItem().getTag()).append("; "));
                    helper.fail("the ritual must bind the scroll; items in range: " + nearby);
                }
                helper.assertTrue(ICE.equals(CrossCastNbt.storedSchool(entry)), "the ritual route must keep the ice school");
            })
            .thenSucceed();
    }

    /**
     * Dedup: a copy differing only in cosmetics is refused without touching the book; the same
     * glyphs with another school, or Automatic, are separate spells in separate wheel slots.
     */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_dedupAllowsSchoolVariantsAndRefusesCosmeticCopies(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_dedup");
        ItemStack book = new ItemStack(CrossCastGameTests.findIronsSpellBook());
        helper.assertTrue(IronsSpellbookBinder.bind(player, carrier(helper, heal(), ICE), book,
            IronsSpellbookBinder.Caller.RITUAL).wasAdded(), "the first ice bind must add");
        ItemStack bound = book.copy();

        Spell renamed = heal();
        renamed.name = "A different name";
        ItemStack cosmeticCopy = ArsSpellExportUtil.createIronsScrollCarrier(renamed, "Other", "fire", "spell/fireball", ICE);
        helper.assertTrue(IronsSpellbookBinder.bind(player, cosmeticCopy, book, IronsSpellbookBinder.Caller.RITUAL)
            == IronsSpellbookBinder.BindResult.DUPLICATE, "a cosmetic-only copy must be a duplicate");
        helper.assertTrue(ItemStack.isSameItemSameTags(book, bound), "a refused duplicate must not touch the book");

        helper.assertTrue(IronsSpellbookBinder.bind(player, carrier(helper, heal(), FIRE), book,
            IronsSpellbookBinder.Caller.RITUAL).wasAdded(), "the fire variant must add");
        helper.assertTrue(IronsSpellbookBinder.bind(player, carrier(helper, heal(), null), book,
            IronsSpellbookBinder.Caller.RITUAL).wasAdded(), "the Automatic variant must add");
        helper.assertTrue(CrossCastNbt.usedProxyPoolIds(book.getOrCreateTag()).size() == 3,
            "three variants, three wheel slots");
        helper.succeed();
    }

    /** Export, copy, save and reload, and an op command change keep or change exactly the school. */
    @GameTest(template = "platform", batch = "ans_school")
    public static void ironsLoaded_schoolChoice_exportCopySaveAndCommandsKeepTheSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_commands");
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, arsBook(helper, heal()));
        var commands = helper.getLevel().getServer().getCommands();
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        helper.assertTrue(commands.performPrefixedCommand(source, "ans export_to_irons_scroll removed_addon:aqua") == 0,
            "export must refuse an uninstalled school");
        helper.assertTrue(commands.performPrefixedCommand(source, "ans export_to_irons_scroll irons_spellbooks:ice") == 1,
            "export with a supported school must succeed");
        ItemStack exported = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (CrossCastNbt.hasCrossModSpells(stack.getTag())) exported = stack;
        }
        helper.assertTrue(ICE.equals(CrossCastNbt.storedSchool(entryOf(exported))), "the exported scroll must carry ice");

        ItemStack reloaded = ItemStack.of(exported.save(new CompoundTag()));
        helper.assertTrue(ItemStack.isSameItemSameTags(reloaded, exported) && ICE.equals(CrossCastNbt.storedSchool(entryOf(reloaded.copy()))),
            "save, reload and copy must keep the school");

        player.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
        helper.assertTrue(commands.performPrefixedCommand(source, "ans school set removed_addon:aqua") == 0,
            "an uninstalled school must be refused");
        helper.assertTrue(ICE.equals(CrossCastNbt.storedSchool(entryOf(player.getMainHandItem()))),
            "a refused command must not change the item");
        helper.assertTrue(commands.performPrefixedCommand(source, "ans school set irons_spellbooks:fire") == 1,
            "a supported school must be set");
        helper.assertTrue(FIRE.equals(CrossCastNbt.storedSchool(entryOf(player.getMainHandItem()))), "the scroll must now be fire");
        helper.assertTrue(commands.performPrefixedCommand(source, "ans school set auto") == 1, "Automatic must be settable");
        helper.assertTrue(!entryOf(player.getMainHandItem()).contains(CrossCastNbt.TAG_SCHOOL), "Automatic clears the field");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Server setting
    // ------------------------------------------------------------------

    /**
     * With {@code allow_spell_school_override=false} the Loom refuses chosen schools without
     * moving anything, and a scroll that already carries one casts as Automatic and keeps it.
     */
    @GameTest(template = "platform", batch = "ans_school_disabled")
    public static void ironsLoaded_schoolChoice_disabledServerRefusesAndCastsAutomatic(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        SpellLoomBlockEntity loom = placeLoom(helper);
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "school_disabled");
        try {
            TestConfig.set(AnsConfig.ALLOW_SPELL_SCHOOL_OVERRIDE, false);
            seed(loom, arsBook(helper, heal()), IronsLoomFixtures.blankScroll());
            String before = snapshot(loom);
            helper.assertTrue(LoomInscription.REASON_SCHOOL_DISABLED.equals(LoomInscription.apply(loom, false, "X", "", "", ICE)),
                "a chosen school must be refused while disabled");
            helper.assertTrue(before.equals(snapshot(loom)), "a refusal must move nothing");
            helper.assertTrue(InscriptionPlan.REASON_OK.equals(LoomInscription.apply(loom, false, "X", "", "", "auto")),
                "Automatic must still inscribe while disabled");

            ItemStack scroll = carrier(helper, heal(), FIRE);
            int fire = affinity(player, FIRE), holy = affinity(player, HOLY);
            castScroll(player, scroll);
            helper.assertTrue(affinity(player, HOLY) == holy + 1 && affinity(player, FIRE) == fire,
                "a stored choice must cast as Automatic while disabled");
            helper.assertTrue(FIRE.equals(CrossCastNbt.storedSchool(entryOf(player.getMainHandItem()))),
                "the stored choice must survive for when the setting returns");
        } finally {
            TestConfig.set(AnsConfig.ALLOW_SPELL_SCHOOL_OVERRIDE, true);
        }
        helper.succeed();
    }
}
