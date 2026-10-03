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

import java.nio.charset.StandardCharsets;
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

    static final GameProfile FAKE_PROFILE =
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
    static Item findIronsSpellBook() {
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
        // Iron's not on the runtime classpath — nothing to integrate against. Skip
        // rather than assert the Iron-loaded contract (the Iron-absent contract is
        // covered by ArsIronsExportGameTests#ironAbsent_predicatesAreSafe).
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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

        // Bind onto a real Iron's spellbook that already holds a genuine Iron's spell in its
        // MODERN container. Earlier revisions of this test wrote a hand-made "ISB_Spells"
        // compound — Iron's LEGACY key, which it still reads but no longer writes — so it
        // asserted coexistence with a format no current book actually uses. The real question
        // is whether binding disturbs a native spell in the container Iron's writes today.
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        ItemStack book = new ItemStack(bookItem);
        if (!IronsProxyCastDriver.addNativeSpellToBook(book)) {
            helper.fail("could not seed the book with a genuine Iron's spell");
        }
        int nativeSpellsBefore = IronsProxyCastDriver.nativeSpellCount(book);
        if (nativeSpellsBefore < 1) {
            helper.fail("test setup failed: the book should hold at least one real Iron's spell");
        }

        if (!IronsBookBindingUtil.appendArsSpellToBook(book, extracted.get())) {
            helper.fail("binding the extracted Ars entry onto a real spellbook must succeed");
        }

        // Coexistence: the ANS sidecar lands and the player's real spells are untouched.
        if (!CrossCastNbt.hasCrossModSpells(book.getOrCreateTag())) {
            helper.fail("bound spellbook must carry the ANS cross_spells sidecar");
        }
        if (IronsProxyCastDriver.nativeSpellCount(book) != nativeSpellsBefore) {
            helper.fail("binding must not add, remove or displace the player's genuine Iron's "
                + "spells; native count changed from " + nativeSpellsBefore + " to "
                + IronsProxyCastDriver.nativeSpellCount(book));
        }
        // And the legacy key must not be resurrected by anything ANS does.
        if (book.getOrCreateTag().contains("ISB_Spells")) {
            helper.fail("ANS must never write Iron's legacy ISB_Spells key");
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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

    /**
     * A hurt fake player holding nothing, with no spellbook equipped and clean cast state.
     *
     * <p><b>Every cast-gating property is reset explicitly, not inherited.</b>
     * {@code FakePlayerFactory.get} returns a cached instance per (level, profile), so all
     * tests in this class share ONE player object and whatever the previous test left on it.
     * That was a latent flake: a test that ran after a survival test inherited survival with no
     * mana, and Iron's then refused to initiate the cast for a reason unrelated to what was
     * being tested. It stayed hidden until loading addon profiles changed the test order.
     * Tests that specifically want survival downgrade from here.
     */
    static ServerPlayer emptyHandedPlayer(GameTestHelper helper) {
        return emptyHandedPlayer(helper, FAKE_PROFILE);
    }

    /**
     * The same player, but private to {@code scenario}: a distinct {@link GameProfile} means
     * {@code FakePlayerFactory} hands back a distinct instance, so two tests running concurrently
     * in one batch cannot see each other's inventory, health or cast state.
     */
    static ServerPlayer scenarioPlayer(GameTestHelper helper, String scenario) {
        String name = scenario.length() > 16 ? scenario.substring(0, 16) : scenario;
        return emptyHandedPlayer(helper,
            new GameProfile(UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), name));
    }

    private static ServerPlayer emptyHandedPlayer(GameTestHelper helper, GameProfile profile) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), profile);
        player.moveTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        // Hurt, so a resolved heal is observable as a health increase.
        player.setHealth(10.0f);
        // Permissive defaults so a cast can only be refused for the reason under test.
        player.setGameMode(GameType.CREATIVE);
        // Iron's-only state, and IronsProxyCastDriver's every line resolves Iron's classes: the
        // guard belongs HERE, at the call site, because a guard inside the driver would not stop
        // the JVM resolving the class on entry. Without it every caller of this helper failed on
        // the Iron's-absent run (audit V26/T0.2) -- including the Iron-agnostic ritual tests.
        if (IronsCompat.isLoaded()) {
            IronsProxyCastDriver.setIronsMana(player, 10000.0f);
            IronsProxyCastDriver.equipSpellbook(player, ItemStack.EMPTY);
            IronsProxyCastDriver.resetCastingState(player);
        }
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemStack book = bindHealSpellOntoRealBook(helper);
        ServerPlayer player = scenarioPlayer(helper, "funded_ars_proxy");
        player.setGameMode(GameType.SURVIVAL);
        // Seed both real pools: the profile may use Ars-primary routing, and a fresh
        // MagicData has not yet received the server-player link used by routed setMana.
        com.otectus.arsnspells.bridge.NativeManaAccess.with(player,
            com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA, () -> {
                var mana = com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).orElseThrow(IllegalStateException::new);
                mana.setMaxMana(10000); mana.setMana(10000); return null;
            });
        com.otectus.arsnspells.bridge.BridgeManager.getNativeIronsBridge().setMana(player, 10000);
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
     * payload — and would consume the scroll while inscribing an empty {@code none} spell. So
     * Iron's own inscription must never run for it: as of 3.3.3 the verdict is
     * {@code BIND_CARRIER} and the click is rerouted to the ANS binder. The property under
     * test is unchanged: Iron's own inscription does not get it.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_validCarrier_isRoutedToBindAtNativeTable(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(heal);
        if (carrier.isEmpty()) {
            helper.fail("export must yield a carrier when Iron's is loaded");
        }
        IronsInscriptionPolicy.Verdict verdict = IronsInscriptionPolicy.evaluate(carrier);
        if (verdict != IronsInscriptionPolicy.Verdict.BIND_CARRIER) {
            helper.fail("a valid ANS carrier must be routed to the ANS binder as BIND_CARRIER "
                + "(Iron's own inscription would eat it and write a dud), got " + verdict);
        }
        helper.succeed();
    }

    /** A genuine Iron's scroll must pass through the guard untouched. */
    @GameTest(template = "platform")
    public static void ironsLoaded_nativeScroll_isAllowedThroughGuard(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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
     * Proxies must also declare themselves unlootable.
     *
     * <p>Unlike {@code allowCrafting} above, {@code allowLooting()} does not route through
     * {@code SpellConfigManager} — its base implementation reads the school's flag directly —
     * so the live method can be asserted here with no config-plumbing caveat.
     *
     * <p>This is the flag Iron's loot actually consults. The ENDER school the proxies use for
     * {@code requiresLearning == false} defaults {@code allowLooting} to true, which is how
     * {@code ars_cross_*} scrolls reached chest loot, scroll pouches and wandering traders in
     * 3.2.1.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_proxies_declareUnlootable(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        for (int poolId = 1;
                poolId <= com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry.POOL_SIZE;
                poolId++) {
            var proxy = com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry.get(poolId);
            if (proxy == null) {
                helper.fail("proxy pool " + poolId + " is not registered");
                return;
            }
            if (proxy.allowLooting()) {
                helper.fail("ars_cross_" + poolId + " must opt out of looting; otherwise Iron's "
                    + "random-spell rolls can hand the player an uncastable scroll");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The behavioural half, through Iron's own code. {@code SpellFilter} is what every
     * {@code irons_spellbooks:randomize_spell} loot function, the wandering-trader scroll trade
     * and the enhancement-ring imbuer consult, so asking it directly is what actually proves
     * loot is closed — rather than asserting ANS's own flag back to itself.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_ironsLootFilter_neverOffersAProxy(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        var applicable = new io.redspace.ironsspellbooks.loot.SpellFilter().getApplicableSpells();
        if (applicable.isEmpty()) {
            helper.fail("Iron's unfiltered loot pool came back empty — the filter is not being "
                + "exercised, so this test would pass for the wrong reason");
            return;
        }
        for (var spell : applicable) {
            if (com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry
                    .poolIdOf(spell.getSpellResource()) >= 0) {
                helper.fail("Iron's loot pool still offers " + spell.getSpellResource()
                    + "; a randomly generated scroll can therefore still be a dud proxy");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Loot debris already in a player's world is blanked the first time it is touched.
     * {@link IronsProxyCastDriver#makeProxyScroll} builds the same stack shape Iron's
     * {@code RandomizeSpellFunction} produced.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_strayProxyScroll_isBlankedOnContact(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemStack stray = IronsProxyCastDriver.makeProxyScroll(1);
        if (!ArsCrossProxyHiding.neutralizeStrayProxyScroll(stray)) {
            helper.fail("a stray proxy scroll must be neutralized on contact");
            return;
        }
        if (IronsScrollFactory.hasNativeContainer(stray)) {
            helper.fail("neutralizing must strip the native container, leaving a blank scroll");
            return;
        }
        if (stray.isEmpty()) {
            helper.fail("neutralizing must not destroy the player's item");
            return;
        }
        helper.succeed();
    }

    /** The negative control: real items must survive the neutralizer untouched. */
    @GameTest(template = "platform")
    public static void ironsLoaded_neutralizer_leavesRealItemsAlone(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        // A genuine Iron's spell scroll — blanking one would delete real player content.
        ItemStack real = IronsProxyCastDriver.makeNativeScroll();
        if (real.isEmpty()) {
            helper.fail("no non-proxy Iron's spell is registered; the negative control cannot run");
            return;
        }
        if (ArsCrossProxyHiding.neutralizeStrayProxyScroll(real)) {
            helper.fail("a genuine Iron's spell scroll must never be blanked");
            return;
        }

        // An ANS carrier: sidecar payload plus the deliberately empty native container. The
        // sidecar guard must refuse it — stripping that container recreates the Inscription
        // Table NPE that IronsScrollFactory exists to prevent.
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
            new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        if (carrier == null || carrier.isEmpty()) {
            helper.fail("could not build an ANS carrier scroll for the negative control");
            return;
        }
        if (ArsCrossProxyHiding.neutralizeStrayProxyScroll(carrier)) {
            helper.fail("an ANS carrier scroll must survive the neutralizer untouched");
            return;
        }
        if (ArsCrossProxyHiding.neutralizeStrayProxyScroll(new ItemStack(Items.BOOK))) {
            helper.fail("a non-scroll must never be rewritten");
            return;
        }
        if (ArsCrossProxyHiding.neutralizeStrayProxyScroll(ItemStack.EMPTY)) {
            helper.fail("an empty stack must never be rewritten");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Legacy repair. Items written by older builds already exist in worlds and
    // cannot be reached by a fix at the creation site, so they are repaired when
    // something already holds them.
    // ------------------------------------------------------------------

    /** A legacy container-less carrier is repaired in place, not merely rejected. */
    @GameTest(template = "platform")
    public static void ironsLoaded_reconciler_repairsLegacyCarrierContainer(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        Item scrollItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "scroll"));
        if (scrollItem == null) {
            helper.fail("Iron's scroll item must be registered when Iron's is loaded");
        }
        ItemStack legacy = new ItemStack(scrollItem);
        CrossCastNbt.addArsEntryWithMetaToTag(legacy.getOrCreateTag(),
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, arsPayload("legacy"),
            CrossCastNbt.NO_PROXY_POOL_ID, null, null, null);
        // Strip the container the modern writer would have added, reproducing the old shape.
        legacy.getOrCreateTag().remove("irons_spellbooks:spell_container");

        if (IronsProxyCastDriver.scrollContainerDereferenceSucceeds(legacy)) {
            helper.fail("test setup no longer reproduces the legacy shape");
        }

        com.otectus.arsnspells.spell.irons.CarrierReconciler.reconcile(legacy);

        if (!IronsProxyCastDriver.scrollContainerDereferenceSucceeds(legacy)) {
            helper.fail("the reconciler must give a legacy carrier the native container it was "
                + "created without — these already exist in players' chests and cannot be fixed "
                + "at the creation site");
        }
        if (IronsBookBindingUtil.extractSingleArsEntry(legacy).isEmpty()) {
            helper.fail("repair must not disturb the Ars payload it was protecting");
        }
        helper.succeed();
    }

    /** An orphan wheel slot with no sidecar entry is removed; a live one is kept. */
    @GameTest(template = "platform")
    public static void ironsLoaded_reconciler_removesOrphanProxiesButKeepsLiveOnes(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemStack book = bindHealSpellOntoRealBook(helper); // occupies pool 1, sidecar + slot

        // Forge an orphan: a native slot for pool 2 with no matching sidecar entry, exactly what
        // the pre-fix uninscribe path left behind.
        com.otectus.arsnspells.spell.irons.IronsProxySlotWriter.addProxySlot(book, 2, 1);
        if (IronsProxyCastDriver.proxySlotIndex(book, 2) < 0) {
            helper.fail("test setup failed: could not forge an orphan proxy slot");
        }

        com.otectus.arsnspells.spell.irons.CarrierReconciler.reconcile(book);

        if (IronsProxyCastDriver.proxySlotIndex(book, 2) >= 0) {
            helper.fail("an orphan proxy slot with no sidecar entry must be removed; leaving it "
                + "gives the player a selectable wheel entry that casts nothing");
        }
        if (IronsProxyCastDriver.proxySlotIndex(book, 1) < 0) {
            helper.fail("the LIVE proxy slot must survive reconciliation — removing it would "
                + "unbind a spell the player legitimately owns");
        }
        helper.succeed();
    }

    /** Spellbook detection uses Iron's type, not a path substring. */
    @GameTest(template = "platform")
    public static void ironsLoaded_spellbookDetection_usesTypeNotNaming(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        if (!IronsBookBindingUtil.isIronsSpellBook(new ItemStack(bookItem))) {
            helper.fail("a real Iron's spell book must be recognized");
        }
        // A scroll lives in the same namespace and is emphatically not a book.
        Item scrollItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "scroll"));
        if (scrollItem != null && IronsBookBindingUtil.isIronsSpellBook(new ItemStack(scrollItem))) {
            helper.fail("an Iron's scroll must not be mistaken for a spell book");
        }
        if (IronsBookBindingUtil.isIronsSpellBook(new ItemStack(Items.BOOK))) {
            helper.fail("a vanilla book must not be mistaken for an Iron's spell book");
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
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
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

    // ---- NeoForge parity: pool reuse and the reconciler ----

    /** A freed proxy pool id must be reused rather than the pool leaking upward. */
    @GameTest(template = "platform")
    public static void poolIdsAreReusedAfterRemoval(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("a"));
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("b"));

        if (!CrossCastNbt.removeEntryByProxyPoolId(book.getOrCreateTag(), 1)) {
            helper.fail("removing an existing pool id must report success");
            return;
        }
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("c"));

        java.util.Set<Integer> used = CrossCastNbt.usedProxyPoolIds(book.getOrCreateTag());
        if (!used.contains(1)) {
            helper.fail("a freed pool id must be reused rather than the pool leaking upward "
                + "until the book reports full; used=" + used);
            return;
        }
        helper.succeed();
    }

    /** An item with no ANS data is left exactly as it was. */
    @GameTest(template = "platform")
    public static void reconciler_isANoOpOnAPlainItem(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.BOOK);
        com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome outcome =
            com.otectus.arsnspells.spell.irons.CarrierReconciler.reconcile(plain);
        if (outcome != com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED) {
            helper.fail("an item with no ANS data has nothing to reconcile, got " + outcome);
            return;
        }
        if (!ItemStack.isSameItemSameTags(plain, new ItemStack(Items.BOOK))) {
            helper.fail("the reconciler must not mutate an item it has nothing to do with");
            return;
        }
        helper.succeed();
    }

    /** Having looked an item over, the reconciler records that it did. */
    @GameTest(template = "platform")
    public static void ironsLoaded_reconciler_stampsTheSchemaVersion(GameTestHelper helper) {
        // Both repairs the reconciler can make are about native proxy slots, so without Iron's
        // there is nothing to look over and nothing to record having looked at.
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("glyph_heal"));
        book.getOrCreateTag().remove(CrossCastNbt.TAG_SCHEMA_VERSION);

        com.otectus.arsnspells.spell.irons.CarrierReconciler.reconcile(book);

        if (CrossCastNbt.schemaVersion(book.getTag()) != CrossCastNbt.SCHEMA_VERSION) {
            helper.fail("having looked an item over, the reconciler must record that it did - "
                + "otherwise a later pass cannot tell 'checked' from 'never seen'");
            return;
        }
        helper.succeed();
    }
}
