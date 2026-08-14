package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.network.CrossCastRequestPacket;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
// Iron's-gated helpers: imports are compile-time only, and every call below sits behind an
// IronsCompat.isLoaded() guard, so neither class is resolved on the Iron-absent run.
import com.otectus.arsnspells.spell.irons.ArsCrossProxyHiding;
import com.otectus.arsnspells.spell.irons.IronsInscriptionPolicy;
import com.otectus.arsnspells.spell.irons.IronsScrollFactory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

/**
 * GameTests for the cross-cast pipeline's runtime behavior on a live server.
 *
 * <p>Run with {@code ./gradlew runGameTestServer}. The {@code build.gradle}
 * {@code gameTestServer} run target gates on the {@code ars_n_spells} namespace so
 * unrelated mods' tests do not run.
 *
 * <p>{@link #crossCastCycle_advancesSelectedIndex} is Iron-agnostic and runs in every
 * profile. {@link #ironsLoaded_exportBindCoexist_roundTrip} is the 3.0.0 Iron-LOADED
 * integration scenario; it self-skips when Iron's is absent and only executes under the
 * opt-in {@code -PwithIronsRuntimeGameTests} profile (see {@code build.gradle}), where
 * real {@code irons_spellbooks} items are on the classpath.
 *
 * <p>The cross-cast <em>cost</em> regressions (CRIT-002 SEPARATE-mode one-way Ars drain,
 * CRIT-004 multiplier-before-ring) are guarded in production by inline server-side
 * assertions + {@code CrossCastTrace} logging in
 * {@link CrossCastingHandler#onArsSpellCost} and {@code CrossCastIronsHandler}. They are
 * validated manually per {@code TESTING_GUIDE.md} because reproducing them requires a live
 * mana/LP/Sanctified-Legacy runtime state that the GameTest harness does not stand up;
 * they are deliberately NOT represented here as fake-passing {@code helper.succeed()} stubs.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CrossCastGameTests {

    private static final GameProfile FAKE_PROFILE =
        new GameProfile(UUID.fromString("0a000000-0000-0000-0000-00000000a115"), "ans_gametest");

    private CrossCastGameTests() {
    }

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    private static int selectedIndex(ItemStack stack) {
        return stack.getOrCreateTag().getInt(CrossCastNbt.TAG_SPELL_INDEX);
    }

    /** Find any registered Iron's spellbook item (tier-agnostic), or null if none. */
    private static Item findIronsSpellBook() {
        for (Item item : ForgeRegistries.ITEMS) {
            if (IronsBookBindingUtil.isIronsSpellBook(new ItemStack(item))) {
                return item;
            }
        }
        return null;
    }

    /**
     * Sanity test: confirms the gameTestServer run target is wired and the test class is
     * discovered. Uses the {@code platform} template staged by the
     * {@code stageGameTestStructures} gradle task from
     * {@code src/test/resources/gameteststructures/platform.snbt} — vanilla and
     * Forge ship no gametest structures, so the run dir must supply it.
     */
    @GameTest(template = "platform")
    public static void scaffoldIsWired(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * Drives the real server-authoritative entry point ({@link CrossCastingHandler#serverHandleCast})
     * with a CYCLE action and asserts the selected-spell index advances and wraps. This is
     * the production sneak-right-click cycle path, exercised end-to-end with a real
     * {@link ServerPlayer} on a live server. Iron-agnostic: both entries are Ars payloads.
     */
    @GameTest(template = "platform")
    public static void crossCastCycle_advancesSelectedIndex(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("a"));
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("b"));

        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.get(level, FAKE_PROFILE);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);

        if (selectedIndex(book) != 0) {
            helper.fail("a freshly inscribed item must start at index 0");
        }

        // First cycle: 0 -> 1. The index is set before the (action-bar) feedback message,
        // so a FakePlayer's connectionless message send cannot prevent the mutation we assert.
        cycleTolerant(player, book);
        if (selectedIndex(book) != 1) {
            helper.fail("first cycle must advance the selected index to 1, got " + selectedIndex(book));
        }

        // Second cycle wraps: 1 -> 0 (two entries).
        cycleTolerant(player, book);
        if (selectedIndex(book) != 0) {
            helper.fail("second cycle must wrap the selected index back to 0, got " + selectedIndex(book));
        }

        helper.succeed();
    }

    private static void cycleTolerant(ServerPlayer player, ItemStack book) {
        try {
            CrossCastingHandler.serverHandleCast(player, book, InteractionHand.MAIN_HAND,
                CrossCastRequestPacket.Action.CYCLE, UUID.randomUUID());
        } catch (Throwable ignored) {
            // A FakePlayer has no client connection; the action-bar feedback send may throw.
            // The index mutation under test happens before that send, so tolerate it.
        }
    }

    /**
     * 3.0.0 Iron-LOADED round-trip: a real {@code irons_spellbooks:scroll} carries an Ars
     * spell, and a real Iron's spellbook accepts the bound Ars entry alongside an untouched
     * native {@code ISB_Spells} container, with payload-keyed dedup. Self-skips when Iron's
     * is absent (default run); executes under {@code -PwithIronsRuntimeGameTests}.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_exportBindCoexist_roundTrip(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            // Iron's not on the runtime classpath — nothing to integrate against. Skip
            // rather than assert the Iron-loaded contract (the Iron-absent contract is
            // covered by ArsIronsExportGameTests#ironAbsent_predicatesAreSafe).
            helper.succeed();
            return;
        }

        // Production export path yields a real, recognized Iron's scroll item.
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(new Spell());
        if (carrier.isEmpty() || !IronsBookBindingUtil.isIronsScroll(carrier)) {
            helper.fail("createIronsScrollCarrier must return a real irons_spellbooks:scroll when Iron's is loaded");
        }

        // A valid carrier (non-empty payload) extracts to a single Ars entry.
        ItemStack scroll = new ItemStack(carrier.getItem());
        CompoundTag payload = arsPayload("glyph_touch,glyph_break");
        if (!IronsBookBindingUtil.appendArsSpellToBook(scroll, payload)) {
            helper.fail("appending a fresh Ars payload to a real scroll must succeed");
        }
        Optional<CompoundTag> extracted = IronsBookBindingUtil.extractSingleArsEntry(scroll);
        if (extracted.isEmpty()) {
            helper.fail("a single-entry carrier scroll must yield its Ars payload");
        }

        // Bind onto a real Iron's spellbook carrying a native ISB_Spells container.
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        ItemStack book = new ItemStack(bookItem);
        CompoundTag isb = new CompoundTag();
        isb.putInt("maxSpells", 3);
        book.getOrCreateTag().put("ISB_Spells", isb);
        CompoundTag isbBaseline = isb.copy();

        if (!IronsBookBindingUtil.appendArsSpellToBook(book, extracted.get())) {
            helper.fail("binding the extracted Ars entry onto a real spellbook must succeed");
        }

        // Coexistence: the ANS sidecar lands and the native container is untouched.
        if (!CrossCastNbt.hasCrossModSpells(book.getOrCreateTag())) {
            helper.fail("bound spellbook must carry the ANS cross_spells sidecar");
        }
        if (!isbBaseline.equals(book.getOrCreateTag().getCompound("ISB_Spells"))) {
            helper.fail("native ISB_Spells container must be untouched by binding");
        }

        // Dedup by payload on the real item: re-binding the same payload is rejected.
        if (IronsBookBindingUtil.appendArsSpellToBook(book, extracted.get())) {
            helper.fail("re-binding an equal Ars payload must be rejected as a duplicate");
        }

        helper.succeed();
    }

    /**
     * 3.0.0 native-proxy: binding an Ars spell onto a real Iron's spellbook (Iron's
     * loaded) allocates a native-wheel proxy pool id on the sidecar entry and writes
     * the proxy slot into Iron's modern container without throwing — exercising the
     * real {@code IronsProxySlotWriter} path. Assertions stay NBT-only so this class
     * never classloads Iron's on the default (Iron-absent) gametest run.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_bindAllocatesProxyPoolId(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        ItemStack book = new ItemStack(bookItem);

        // Two distinct Ars spells -> two distinct proxy pool ids (1 then 2).
        IronsBookBindingUtil.AppendResult first = IronsBookBindingUtil.appendArsSpellToBook(
            book, arsPayload("glyph_touch"), "Alpha", "fire", "flame", -1);
        IronsBookBindingUtil.AppendResult second = IronsBookBindingUtil.appendArsSpellToBook(
            book, arsPayload("glyph_break"), "Beta", "ice", "spark", -1);
        if (first != IronsBookBindingUtil.AppendResult.ADDED
            || second != IronsBookBindingUtil.AppendResult.ADDED) {
            helper.fail("binding two distinct Ars spells onto a real book must both ADD");
        }

        CompoundTag tag = book.getOrCreateTag();
        java.util.Set<Integer> used = CrossCastNbt.usedProxyPoolIds(tag);
        if (!used.contains(1) || !used.contains(2)) {
            helper.fail("two binds must allocate distinct proxy pool ids 1 and 2; got " + used);
        }
        CompoundTag alpha = CrossCastNbt.findEntryByProxyPoolId(tag, 1);
        if (alpha == null || !"Alpha".equals(alpha.getString(CrossCastNbt.TAG_CUSTOM_NAME))
            || !"fire".equals(alpha.getString(CrossCastNbt.TAG_NATURE))) {
            helper.fail("proxy pool id 1 must carry the Alpha/fire display metadata");
        }

        // The modern Iron's container received the proxy slots (NBT-level check, no
        // Iron's classes): its serialized form must reference our proxy spell ids.
        String modern = tag.getCompound("irons_spellbooks:spell_container").toString();
        if (!modern.contains("ars_n_spells:ars_cross_")) {
            helper.fail("the native container must hold ars_cross_* proxy slots after binding");
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Behavioural cast tests. Unlike the NBT-shape tests above, these drive real
    // Iron's cast machinery through IronsProxyCastDriver and assert an observable
    // world effect (the bound spell is Self+Heal, so a resolved cast raises health).
    // They exist because every NBT assertion above passed while the "bound spell
    // casts but does nothing" bug was live: the shape was right, the runtime path
    // was not.
    // ------------------------------------------------------------------

    /** Bind a real, castable Self+Heal onto a real spellbook; returns the bound book. */
    private static ItemStack bindHealSpellOntoRealBook(GameTestHelper helper) {
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        ItemStack book = new ItemStack(bookItem);
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        IronsBookBindingUtil.AppendResult bound = IronsBookBindingUtil.appendArsSpellToBook(
            book, heal.serialize(), "E2E Heal", "water", "heart", -1);
        if (bound != IronsBookBindingUtil.AppendResult.ADDED) {
            helper.fail("binding a real Ars spell onto a real spellbook must ADD, got " + bound);
        }
        if (CrossCastNbt.findEntryByProxyPoolId(book.getOrCreateTag(), 1) == null) {
            helper.fail("the first bind must land in proxy pool 1");
        }
        return book;
    }

    /** A hurt fake player holding nothing, with no spellbook equipped and clean cast state. */
    private static ServerPlayer emptyHandedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), FAKE_PROFILE);
        player.moveTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        // Hurt, so a resolved heal is observable as a health increase.
        player.setHealth(10.0f);
        IronsProxyCastDriver.equipSpellbook(player, ItemStack.EMPTY);
        IronsProxyCastDriver.resetCastingState(player);
        return player;
    }

    private static ServerPlayer handHoldingPlayer(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = emptyHandedPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /**
     * The headline regression: a book equipped in the Curios spellbook slot (not a hand)
     * must resolve its bound Ars spell. This is exactly the configuration where
     * {@code MagicData.getPlayerCastingItem()} comes back empty, which used to make the
     * wheel entry a silent no-op.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_boundArsSpell_creativeCastResolvesThroughProxy(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack book = bindHealSpellOntoRealBook(helper);
        ServerPlayer player = emptyHandedPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        IronsProxyCastDriver.castViaEquippedSpellbook(player, book, 1);
        if (player.getHealth() <= 10.0f) {
            helper.fail("creative proxy cast of a Curios-equipped book must resolve the bound Ars "
                + "spell (heal); health unchanged means the proxy could not locate the book it was "
                + "cast from, or the data path (sidecar/deserialize/resolve) is broken");
        }
        helper.succeed();
    }

    /** Same path in survival with mana to spare: the resource authority must not deny a funded cast. */
    @GameTest(template = "platform")
    public static void ironsLoaded_boundArsSpell_survivalCastWithManaResolves(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack book = bindHealSpellOntoRealBook(helper);
        ServerPlayer player = emptyHandedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        IronsProxyCastDriver.setIronsMana(player, 10000.0f);
        IronsProxyCastDriver.castViaEquippedSpellbook(player, book, 1);
        if (player.getHealth() <= 10.0f) {
            helper.fail("survival proxy cast with ample mana must resolve the bound Ars spell; "
                + "health unchanged means the resource-authority leg denies a funded cast");
        }
        helper.succeed();
    }

    /** A book held in the main hand must still resolve, via the hand fallback. */
    @GameTest(template = "platform")
    public static void ironsLoaded_boundArsSpell_heldBookResolvesViaHandFallback(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack book = bindHealSpellOntoRealBook(helper);
        ServerPlayer player = handHoldingPlayer(helper, book);
        player.setGameMode(GameType.CREATIVE);
        IronsProxyCastDriver.castViaArmedProxyWithoutEquipping(player, 1);
        if (player.getHealth() <= 10.0f) {
            helper.fail("a bound book held in the main hand must resolve via the hand fallback");
        }
        helper.succeed();
    }

    /** A wheel slot with no backing sidecar entry must do nothing — and must not throw. */
    @GameTest(template = "platform")
    public static void ironsLoaded_proxyCastWithoutSidecarEntry_isNoopWithoutCrash(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        ItemStack book = new ItemStack(bookItem);
        ServerPlayer player = emptyHandedPlayer(helper);
        IronsProxyCastDriver.castViaEquippedSpellbook(player, book, 1);
        if (player.getHealth() != 10.0f) {
            helper.fail("a proxy cast with no backing sidecar entry must not resolve any spell");
        }
        helper.succeed();
    }

    /**
     * The bind command must refuse a payload that cannot deserialize to a castable spell,
     * rather than consuming the scroll and binding a wheel entry that does nothing. The
     * positive control runs first so a command that silently binds nothing at all cannot
     * make the rejection assertion pass vacuously.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_bindCommand_rejectsUncastablePayload(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Item scrollItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "scroll"));
        Item bookItem = findIronsSpellBook();
        if (scrollItem == null || bookItem == null) {
            helper.fail("Iron's scroll/spellbook items must be registered when Iron's is loaded");
        }

        // Positive control: a readable payload binds.
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack goodScroll = new ItemStack(scrollItem);
        CrossCastNbt.addArsEntryWithMetaToTag(goodScroll.getOrCreateTag(),
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, heal.serialize(),
            CrossCastNbt.NO_PROXY_POOL_ID, null, null, null);
        ItemStack goodBook = new ItemStack(bookItem);
        runBindCommand(helper, goodScroll, goodBook);
        if (!CrossCastNbt.hasCrossModSpells(goodBook.getOrCreateTag())) {
            helper.fail("positive control failed: the bind command must bind a readable payload. "
                + "The rejection assertion below would be vacuous.");
        }

        // The real assertion: garbage is refused.
        ItemStack scroll = new ItemStack(scrollItem);
        CrossCastNbt.addArsEntryWithMetaToTag(scroll.getOrCreateTag(),
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, arsPayload("not_a_real_spell"),
            CrossCastNbt.NO_PROXY_POOL_ID, null, null, null);
        ItemStack book = new ItemStack(bookItem);
        runBindCommand(helper, scroll, book);
        if (CrossCastNbt.hasCrossModSpells(book.getOrCreateTag())) {
            helper.fail("the bind command must reject a payload that cannot deserialize to a "
                + "castable Ars spell instead of binding a silent dud");
        }
        helper.succeed();
    }

    private static void runBindCommand(GameTestHelper helper, ItemStack scroll, ItemStack book) {
        ServerPlayer player = handHoldingPlayer(helper, scroll);
        player.setItemInHand(InteractionHand.OFF_HAND, book);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(2), "ans bind_scroll_to_irons_book");
    }

    // ------------------------------------------------------------------
    // Inscription Table crash (the reported NPE). Iron's dereferences
    // ISpellContainer.get(scroll).getSpellAtIndex(0) with no null check in three
    // places — one client, two server — and get() returns null for a scroll with
    // no container NBT. These cover the root fix and both rejection verdicts.
    // ------------------------------------------------------------------

    /**
     * The root fix: every scroll ANS hands out must satisfy Iron's container invariant.
     * Asserted by performing the exact dereference that crashed, not by checking a
     * different predicate that happens to agree today.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_exportedCarrier_survivesInscriptionDereference(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(heal);
        if (carrier.isEmpty() || !IronsBookBindingUtil.isIronsScroll(carrier)) {
            helper.fail("export must yield a real irons_spellbooks:scroll when Iron's is loaded");
        }
        if (!IronsProxyCastDriver.scrollContainerDereferenceSucceeds(carrier)) {
            helper.fail("an exported carrier must survive ISpellContainer.get(...).getSpellAtIndex(0) "
                + "— this is the Inscription Table NPE, and a carrier without a native container "
                + "crashes the client screen and the server menu alike");
        }
        // The container must not smuggle the carrier into Iron's spell wheel.
        if (carrier.getOrCreateTag().getCompound("irons_spellbooks:spell_container")
                .getBoolean("spellWheel")) {
            helper.fail("the carrier's native container must not add it to Iron's spell wheel");
        }
        // The Ars payload must still be there — the container is additive, not a replacement.
        if (IronsBookBindingUtil.extractSingleArsEntry(carrier).isEmpty()) {
            helper.fail("initializing the native container must not disturb the ANS sidecar payload");
        }
        helper.succeed();
    }

    /**
     * A legacy carrier — a real scroll with ANS sidecar NBT and no native container, exactly
     * what shipped before the fix — must be refused rather than crashing. These already exist
     * in players' inventories and chests, so the root fix cannot reach them.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_legacyContainerlessCarrier_isRejectedNotCrashed(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Item scrollItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "scroll"));
        if (scrollItem == null) {
            helper.fail("Iron's scroll item must be registered when Iron's is loaded");
        }
        // Reproduce the pre-fix shape: bare stack + sidecar, no container.
        ItemStack legacy = new ItemStack(scrollItem);
        CrossCastNbt.addArsEntryWithMetaToTag(legacy.getOrCreateTag(),
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, arsPayload("legacy"),
            CrossCastNbt.NO_PROXY_POOL_ID, null, null, null);

        // Precondition: this stack really is the crashing shape, or the test proves nothing.
        if (IronsProxyCastDriver.scrollContainerDereferenceSucceeds(legacy)) {
            helper.fail("test setup no longer reproduces the legacy shape: this stack already has a "
                + "readable container, so the rejection assertion below would be vacuous");
        }
        if (IronsInscriptionPolicy.evaluate(legacy) == IronsInscriptionPolicy.Verdict.ALLOW) {
            helper.fail("a container-less legacy carrier must be rejected by the inscription guard; "
                + "allowing it lets Iron's NPE on the client screen and the server menu");
        }
        helper.succeed();
    }

    /**
     * A well-formed post-fix carrier no longer crashes, but Iron's still cannot read its Ars
     * payload — and would consume the scroll while inscribing an empty {@code none} spell.
     * It must be refused, and refused with the carrier-specific message that points at the
     * supported binding workflow.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_validCarrier_isRejectedFromNativeTable(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(heal);
        if (carrier.isEmpty()) {
            helper.fail("export must yield a carrier when Iron's is loaded");
        }
        IronsInscriptionPolicy.Verdict verdict = IronsInscriptionPolicy.evaluate(carrier);
        if (verdict != IronsInscriptionPolicy.Verdict.ANS_CARRIER) {
            helper.fail("a valid ANS carrier must be refused as ANS_CARRIER (so the player is sent "
                + "to the binding workflow), got " + verdict);
        }
        helper.succeed();
    }

    /** A genuine Iron's scroll must pass through the guard untouched. */
    @GameTest(template = "platform")
    public static void ironsLoaded_nativeScroll_isAllowedThroughGuard(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Item scrollItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "scroll"));
        if (scrollItem == null) {
            helper.fail("Iron's scroll item must be registered when Iron's is loaded");
        }
        // A scroll with a native container and no ANS sidecar is none of our business.
        ItemStack native_ = new ItemStack(scrollItem);
        if (!IronsScrollFactory.initializeCarrierContainer(native_)) {
            helper.fail("could not give a plain scroll a native container; the factory is broken");
        }
        if (IronsInscriptionPolicy.evaluate(native_) != IronsInscriptionPolicy.Verdict.ALLOW) {
            helper.fail("a native Iron's scroll with no ANS sidecar must pass the guard untouched — "
                + "blocking it would break Iron's own inscription workflow");
        }
        helper.succeed();
    }

    /**
     * Unbinding must remove the native wheel slot, not just the sidecar.
     *
     * <p>The old path cleared the sidecar and left Iron's proxy slot in place, producing an
     * entry the player could still select that did nothing — the mirror image of the binding
     * bug. This asserts the slot is genuinely gone from Iron's container, and that unbinding
     * a book leaves no selectable residue.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_unbind_removesNativeProxySlotAndSidecar(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack book = bindHealSpellOntoRealBook(helper);

        // Precondition: the proxy really is in Iron's container, or the removal proves nothing.
        if (IronsProxyCastDriver.proxySlotIndex(book, 1) < 0) {
            helper.fail("test setup failed: the bound proxy is not in the book's native container");
        }

        IronsBookBindingUtil.removeAllArsEntries(book);

        if (IronsProxyCastDriver.proxySlotIndex(book, 1) >= 0) {
            helper.fail("unbinding must remove the native wheel slot; leaving it behind is the "
                + "orphan-proxy bug — a selectable entry with no payload that casts nothing");
        }
        if (book.hasTag() && CrossCastNbt.hasCrossModSpells(book.getTag())) {
            helper.fail("unbinding must also clear the ANS sidecar");
        }
        helper.succeed();
    }

    /**
     * Unbinding an exported scroll leaves a valid, blank Iron's scroll — the native container
     * belongs to Iron's and must survive, or we would recreate the Inscription Table crash on
     * the way out.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_unbindCarrier_leavesValidBlankScroll(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(heal);
        if (carrier.isEmpty()) {
            helper.fail("export must yield a carrier when Iron's is loaded");
        }

        IronsBookBindingUtil.removeAllArsEntries(carrier);

        if (carrier.isEmpty()) {
            helper.fail("unbinding must not destroy the scroll stack");
        }
        if (carrier.hasTag()
            && carrier.getTag().contains(ArsSpellExportUtil.TAG_EXPORT_MODE)) {
            helper.fail("unbinding must remove ANS's own export marker");
        }
        if (!IronsProxyCastDriver.scrollContainerDereferenceSucceeds(carrier)) {
            helper.fail("unbinding stripped the native Iron's container — that container is Iron's, "
                + "not ANS's, and removing it recreates the Inscription Table NPE");
        }
        if (IronsInscriptionPolicy.evaluate(carrier) != IronsInscriptionPolicy.Verdict.ALLOW) {
            helper.fail("an unbound carrier is an ordinary blank Iron's scroll and must pass the "
                + "inscription guard");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Recipe-viewer pollution. The proxies must be registered (the native wheel
    // resolves them by id) but must never appear as items or recipes. The critical
    // safety property is the negative one: a real player book that happens to carry
    // a bound Ars entry must NEVER be classified as a hideable ghost.
    // ------------------------------------------------------------------

    /** A generated proxy scroll — exactly what the creative tab and JEI produce — is hideable. */
    @GameTest(template = "platform")
    public static void ironsLoaded_generatedProxyScroll_isRecognizedAsGhost(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack ghost = IronsProxyCastDriver.makeProxyScroll(1);
        if (!ArsCrossProxyHiding.isProxyOnlyStack(ghost)) {
            helper.fail("a scroll whose only spell is an ars_cross_* proxy must be recognized as a "
                + "generated ghost, or it stays visible in JEI/EMI and the creative menu");
        }
        helper.succeed();
    }

    /** A genuine Iron's scroll must never be hidden. */
    @GameTest(template = "platform")
    public static void ironsLoaded_nativeScroll_isNotAGhost(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack real = IronsProxyCastDriver.makeNativeScroll();
        if (real.isEmpty()) {
            helper.fail("no non-proxy Iron's spell is registered; the negative control cannot run");
        }
        if (ArsCrossProxyHiding.isProxyOnlyStack(real)) {
            helper.fail("a genuine Iron's scroll must not be hidden — doing so would delete real "
                + "content from every recipe viewer");
        }
        helper.succeed();
    }

    /**
     * The one that matters: a player's spellbook holding a bound Ars entry AND a real Iron's
     * spell must not be classified as a ghost. Getting this wrong would erase real player
     * items from JEI and the creative menu.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_realBookWithBoundEntry_isNotAGhost(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack book = bindHealSpellOntoRealBook(helper);

        // Precondition: it really does carry a proxy slot, so this is the ambiguous case.
        if (IronsProxyCastDriver.proxySlotIndex(book, 1) < 0) {
            helper.fail("test setup failed: the book should carry a proxy slot");
        }
        if (!IronsProxyCastDriver.addNativeSpellToBook(book)) {
            helper.fail("could not add a genuine Iron's spell alongside the bound entry");
        }
        if (ArsCrossProxyHiding.isProxyOnlyStack(book)) {
            helper.fail("a real spellbook carrying both a bound Ars entry and a genuine Iron's "
                + "spell must never be treated as a generated ghost");
        }
        helper.succeed();
    }

    /**
     * Proxies must declare themselves non-craftable while staying registered.
     *
     * <p>Asserted against {@code getDefaultConfig().allowCrafting} rather than
     * {@code allowCrafting()}. The latter reads {@code SpellConfigManager}, which Iron's only
     * populates from {@code OnDatapackSyncEvent} — i.e. on a real player join or {@code /reload}.
     * A GameTest server never fires that, so {@code allowCrafting()} falls back to the global
     * parameter default ({@code true}) here regardless of what the spell declares. Asserting it
     * would test Iron's config plumbing under conditions that never occur in a real game;
     * asserting the declaration tests the part ANS actually controls, which Iron's then copies
     * via {@code config.setDefaultValue(ALLOW_CRAFTING, raw.allowCrafting)}.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_proxies_declareNonCraftableButStayRegistered(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        var proxy = com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry.get(1);
        if (proxy == null) {
            helper.fail("proxy pool 1 must be registered — the native wheel resolves it by id");
        }
        if (proxy.getDefaultConfig().allowCrafting) {
            helper.fail("proxies must declare allowCrafting=false so Iron's Scroll Forge, and the "
                + "recipes it feeds JEI, skip them");
        }
        if (com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry
                .poolIdOf(proxy.getSpellResource()) != 1) {
            helper.fail("the registered proxy must still resolve by its ars_cross_1 id; bound "
                + "spellbooks look it up that way at cast time");
        }
        helper.succeed();
    }

    /**
     * Guards the assumption {@code ArsCrossProxySpell.resolveCastingBook} is built on. If a
     * future Iron's release starts recording a real casting item for spellbook casts, this
     * fails and tells us the fallback chain can be simplified — rather than leaving dead
     * defensive code nobody dares remove.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_spellbookCast_leavesCastingItemEmpty(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack book = bindHealSpellOntoRealBook(helper);
        ServerPlayer player = emptyHandedPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        int index = IronsProxyCastDriver.proxySlotIndex(book, 1);
        if (index < 0) {
            helper.fail("the bound proxy must occupy a real slot in the book's Iron's container");
        }
        if (!IronsProxyCastDriver.initiateViaSpellSelection(player, book, index)) {
            helper.fail("Utils.serverSideInitiateCast refused the selection; the proxy slot or the "
                + "SpellSelection index no longer lines up with Iron's SpellSelectionManager");
        }
        if (!IronsProxyCastDriver.castingItemIsEmpty(player)) {
            helper.fail("Iron's now records a real casting item for spellbook casts — revisit "
                + "ArsCrossProxySpell.resolveCastingBook, which exists only because it did not");
        }
        helper.succeed();
    }
}
