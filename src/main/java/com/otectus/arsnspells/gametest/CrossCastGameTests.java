package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.CrossModSpellList;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.ModDataComponents;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyHiding;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
import com.otectus.arsnspells.spell.irons.CarrierReconciler;
import com.otectus.arsnspells.spell.irons.IronsInscriptionPolicy;
import com.otectus.arsnspells.spell.irons.IronsProxySlotWriter;
import com.otectus.arsnspells.spell.irons.IronsScrollFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.Set;

/**
 * The cross-cast lifecycle on real {@link ItemStack}s: bind, allocate a proxy pool id, cycle,
 * reconcile a legacy item, unbind, and refuse an ANS carrier at Iron's own inscription table.
 *
 * <p>The pure decision cores - {@code planAppend}, the validator, the list round trip - are
 * covered by the bootstrap-free JUnit suite. What can only be checked here is the behaviour
 * against real stacks with the mod's data components registered, and against the real Iron's
 * runtime when it is present.
 *
 * <p>Tests whose names begin {@code ironsLoaded_} self-skip when Iron's Spellbooks is absent,
 * which is the default {@code runGameTestServer} profile. That is deliberate: the Iron's-less
 * path is itself the thing most of the rest of this class is proving.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CrossCastGameTests {

    private CrossCastGameTests() {}

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    // ---- Binding and pool allocation ----

    @GameTest(template = "platform")
    public static void bindAllocatesTheSmallestFreePoolId(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("first"));
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("second"));

        Set<Integer> used = CrossModSpellComponents.usedProxyPoolIds(
            CrossModSpellComponents.get(book));
        if (!used.contains(1) || !used.contains(2)) {
            helper.fail("binding must allocate pool ids from 1 upward, got " + used);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void poolIdsAreReusedAfterRemoval(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("a"));
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("b"));

        if (!CrossModSpellComponents.removeEntryByProxyPoolId(book, 1)) {
            helper.fail("removing an existing pool id must report success");
            return;
        }
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("c"));

        Set<Integer> used = CrossModSpellComponents.usedProxyPoolIds(
            CrossModSpellComponents.get(book));
        if (!used.contains(1)) {
            helper.fail("a freed pool id must be reused rather than the pool leaking upward "
                + "until the book reports full; used=" + used);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void bindThenUnbind_leavesNoTrace(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("glyph_heal"));
        if (!CrossModSpellComponents.has(book)) {
            helper.fail("fixture did not bind");
            return;
        }

        IronsBookBindingUtil.removeAllArsEntries(book);

        if (CrossModSpellComponents.has(book)) {
            helper.fail("the sidecar must be gone after uninscribe");
            return;
        }
        if (book.has(ModDataComponents.SCHEMA_VERSION.get())) {
            helper.fail("the schema stamp is ANS-owned and must go with the rest");
            return;
        }
        if (!ItemStack.isSameItemSameComponents(book, new ItemStack(Items.BOOK))) {
            helper.fail("an uninscribed item must be indistinguishable from a fresh one: "
                + book.getComponents());
            return;
        }
        helper.succeed();
    }

    // ---- Cycling ----

    @GameTest(template = "platform")
    public static void cycle_advancesSelectedIndexAndWraps(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("a"));
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("b"));

        CrossModSpellComponents.setSelectedIndex(book, 0);
        if (CrossModSpellComponents.get(book).selectedIndex() != 0) {
            helper.fail("the selected index must round-trip through the component");
            return;
        }
        CrossModSpellComponents.setSelectedIndex(book, 1);
        if (CrossModSpellComponents.get(book).selectedIndex() != 1) {
            helper.fail("advancing the selection must persist");
            return;
        }

        // Out-of-range selections must be refused rather than stored: the wheel and the cast
        // path both index straight into the list with whatever is here.
        CrossModSpellComponents.setSelectedIndex(book, 99);
        int stored = CrossModSpellComponents.get(book).selectedIndex();
        if (stored < 0 || stored >= CrossModSpellComponents.get(book).size()) {
            helper.fail("an out-of-range selection must be clamped or ignored, stored " + stored);
            return;
        }
        helper.succeed();
    }

    // ---- Proxy-only stack detection (the JEI/creative hiding rule) ----

    @GameTest(template = "platform")
    public static void ironsLoaded_plainItems_areNotProxyOnlyStacks(GameTestHelper helper) {
        // ArsCrossProxyHiding is Iron's-isolated by contract - it resolves ISpellContainer at
        // the top of every method, and both real callers gate on IronsCompat.isLoaded(). So
        // this must gate too; calling it Iron's-less is a contract violation, not a bug found.
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        if (ArsCrossProxyHiding.isProxyOnlyStack(new ItemStack(Items.BOOK))) {
            helper.fail("a vanilla book must not be treated as a generated proxy scroll");
            return;
        }
        if (ArsCrossProxyHiding.isProxyOnlyStack(ItemStack.EMPTY)) {
            helper.fail("an empty stack must not be treated as a generated proxy scroll");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_aBoundBook_isNotAProxyOnlyStack(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        // The hiding rule must catch ANS's generated proxy scrolls without also hiding a real
        // player-owned book that happens to carry a bound entry.
        ItemStack book = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .map(ItemStack::new).filter(IronsBookBindingUtil::isIronsSpellBook).findFirst().orElse(ItemStack.EMPTY);
        if (book.isEmpty()) helper.fail("Expected a real registered Iron's spellbook in the loaded profile");
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("glyph_heal"));
        if (ArsCrossProxyHiding.isProxyOnlyStack(book)) {
            helper.fail("a real book with a bound entry must stay visible - hiding it would "
                + "make the player's own spellbook vanish from JEI and the creative tab");
            return;
        }
        helper.succeed();
    }

    // ---- Lootability: the proxies must never reach Iron's random-spell rolls ----

    /**
     * The flag itself. Asserted against {@code allowLooting()} directly rather than through
     * {@code getDefaultConfig()} - unlike {@code allowCrafting}, this one does not route
     * through {@code SpellConfigManager}, so there is no server-config seeding to work around
     * and the live method is the honest thing to check.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_proxies_areNotLootable(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        for (int poolId = 1; poolId <= ArsCrossProxyRegistry.POOL_SIZE; poolId++) {
            var proxy = ArsCrossProxyRegistry.get(poolId);
            if (proxy == null) {
                helper.fail("proxy pool " + poolId + " is not registered");
                return;
            }
            if (proxy.allowLooting()) {
                helper.fail("ars_cross_" + poolId + " must opt out of looting - the ENDER school "
                    + "it uses for requiresLearning=false defaults allowLooting to true, which is "
                    + "how dud proxy scrolls reached chest loot in 3.2.1");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The behaviour, through Iron's own code. {@code SpellFilter} is what every
     * {@code irons_spellbooks:randomize_spell} loot function, the wandering-trader scroll
     * trade and the enhancement-ring imbuer consult, so asking it directly is the test that
     * actually proves loot is closed - rather than asserting our own flag back to ourselves.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_ironsLootFilter_neverOffersAProxy(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        var applicable = new io.redspace.ironsspellbooks.loot.SpellFilter().getApplicableSpells();
        if (applicable.isEmpty()) {
            helper.fail("Iron's unfiltered loot pool came back empty - the filter is not being "
                + "exercised, so this test would pass for the wrong reason");
            return;
        }
        for (var spell : applicable) {
            if (ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
                helper.fail("Iron's loot pool still offers " + spell.getSpellResource()
                    + "; a randomly generated scroll can therefore still be a dud proxy");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_strayProxyScroll_isBlankedOnContact(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemStack stray = lootedProxyScroll(1);
        if (stray.isEmpty()) {
            helper.fail("could not build the looted-proxy shape to test against");
            return;
        }
        if (!ArsCrossProxyHiding.isProxyOnlyStack(stray)) {
            helper.fail("the shape RandomizeSpellFunction produces must be recognised as a "
                + "proxy-only stack, or nothing downstream can clean it up");
            return;
        }
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

    @GameTest(template = "platform")
    public static void ironsLoaded_neutralizer_leavesRealItemsAlone(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        // A genuine Iron's spell scroll - blanking one would delete real player content.
        ItemStack real = nativeIronsScroll();
        if (real.isEmpty()) {
            helper.fail("no non-proxy Iron's spell is registered; the negative control cannot run");
            return;
        }
        if (ArsCrossProxyHiding.neutralizeStrayProxyScroll(real)) {
            helper.fail("a genuine Iron's spell scroll must never be blanked");
            return;
        }

        // An ANS carrier: sidecar payload plus the deliberately empty native container. It can
        // never match the proxy-only rule, but the sidecar guard must refuse it regardless -
        // blanking one would strip the container Iron's inscription code dereferences.
        ItemStack carrier = blankIronsScroll();
        if (carrier.isEmpty()) {
            helper.fail("Iron's scroll item is not registered");
            return;
        }
        IronsScrollFactory.initializeCarrierContainer(carrier);
        CrossModSpellComponents.addArsEntryWithMeta(carrier,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, arsPayload("carried"),
            1, null, null, null);
        if (ArsCrossProxyHiding.neutralizeStrayProxyScroll(carrier)) {
            helper.fail("an ANS carrier scroll must survive the neutralizer untouched");
            return;
        }

        // A plain book is not a scroll at all; the neutralizer must not reach for its container.
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

    /** A blank {@code irons_spellbooks:scroll}, or {@link ItemStack#EMPTY} without Iron's. */
    private static ItemStack blankIronsScroll() {
        return BuiltInRegistries.ITEM.getOptional(ArsSpellExportUtil.IRONS_SCROLL_ID)
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);
    }

    /** A scroll carrying a genuine (non-proxy) Iron's spell, or EMPTY if none is registered. */
    private static ItemStack nativeIronsScroll() {
        for (var spell : io.redspace.ironsspellbooks.api.registry.SpellRegistry.getEnabledSpells()) {
            if (spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()
                || ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
                continue;
            }
            ItemStack scroll = blankIronsScroll();
            if (scroll.isEmpty()) {
                return scroll;
            }
            io.redspace.ironsspellbooks.api.spells.ISpellContainer.createScrollContainer(
                spell, spell.getMinLevel(), scroll);
            return scroll;
        }
        return ItemStack.EMPTY;
    }

    /** The exact stack shape Iron's {@code RandomizeSpellFunction} produced for a proxy. */
    private static ItemStack lootedProxyScroll(int poolId) {
        ItemStack scroll = blankIronsScroll();
        if (scroll.isEmpty()) {
            return scroll;
        }
        io.redspace.ironsspellbooks.api.spells.ISpellContainer.createScrollContainer(
            ArsCrossProxyRegistry.get(poolId), 1, scroll);
        return scroll;
    }

    // ---- The reconciler ----

    @GameTest(template = "platform")
    public static void reconciler_isANoOpOnAPlainItem(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.BOOK);
        CarrierReconciler.Outcome outcome = CarrierReconciler.reconcile(plain);
        if (outcome != CarrierReconciler.Outcome.UNCHANGED) {
            helper.fail("an item with no ANS data has nothing to reconcile, got " + outcome);
            return;
        }
        if (!ItemStack.isSameItemSameComponents(plain, new ItemStack(Items.BOOK))) {
            helper.fail("the reconciler must not mutate an item it has nothing to do with");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_reconciler_stampsTheSchemaVersion(GameTestHelper helper) {
        // The reconciler returns UNCHANGED immediately without Iron's: both repairs it can
        // make are about native proxy slots, so there is nothing to look over and nothing to
        // record having looked at.
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("glyph_heal"));
        book.remove(ModDataComponents.SCHEMA_VERSION.get());

        CarrierReconciler.reconcile(book);

        if (CrossModSpellComponents.schemaVersion(book)
            != CrossModSpellComponents.SCHEMA_VERSION) {
            helper.fail("having looked an item over, the reconciler must record that it did - "
                + "otherwise a later pass cannot tell 'checked' from 'never seen'");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_reconciler_removesOrphanProxiesButKeepsLiveOnes(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("live"));

        // An orphan: a native wheel slot with no sidecar entry behind it. This is what the
        // pre-fix uninscribe path left behind, and it is selectable and casts nothing.
        IronsProxySlotWriter.addProxySlot(book, 7, 1);

        CarrierReconciler.reconcile(book);

        CrossModSpellList list = CrossModSpellComponents.get(book);
        if (list.size() != 1) {
            helper.fail("the live entry must survive reconciliation, got " + list.size());
            return;
        }
        Set<Integer> used = CrossModSpellComponents.usedProxyPoolIds(list);
        if (used.contains(7)) {
            helper.fail("the orphan slot must be gone");
            return;
        }
        helper.succeed();
    }

    // ---- Iron's inscription-table guard ----

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
        ItemStack carrier = com.otectus.arsnspells.spell.ArsSpellExportUtil.createIronsScrollCarrier(
            new com.hollingsworth.arsnouveau.api.spell.Spell(
                com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE));
        if (carrier.isEmpty()) helper.fail("The loaded profile must produce an actual exported Iron's scroll");

        IronsInscriptionPolicy.Verdict verdict = IronsInscriptionPolicy.evaluate(carrier);
        if (verdict != IronsInscriptionPolicy.Verdict.BIND_CARRIER) {
            helper.fail("a valid ANS carrier must be routed to the ANS binder as BIND_CARRIER "
                + "(Iron's own inscription would eat it and write a dud), got " + verdict);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void plainItem_isAllowedThroughTheInscriptionGuard(GameTestHelper helper) {
        IronsInscriptionPolicy.Verdict verdict =
            IronsInscriptionPolicy.evaluate(new ItemStack(Items.BOOK));
        if (verdict.isRejection()) {
            helper.fail("the guard must only ever reject ANS's own carriers - refusing an "
                + "unrelated item breaks Iron's own table; got " + verdict);
            return;
        }
        helper.succeed();
    }

    // ---- Malformed data must never crash a cast path ----

    @GameTest(template = "platform")
    public static void proxyCastWithoutSidecarEntry_isANoopWithoutCrash(GameTestHelper helper) {
        // The wheel can hold a selectable proxy slot whose sidecar entry is gone. Resolving it
        // must produce nothing, not an exception on the server thread.
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("only"));
        try {
            Optional<CrossModSpell> missing =
                CrossModSpellComponents.findEntryByProxyPoolId(book, 6);
            if (missing.isPresent()) {
                helper.fail("a pool id that was never allocated must not resolve to an entry");
                return;
            }
        } catch (Throwable t) {
            helper.fail("resolving an absent proxy entry must not throw: " + t);
            return;
        }
        helper.succeed();
    }

    // ---- Behavioural proxy casts (Forge parity) ----
    //
    // These drive real Iron's cast machinery through IronsProxyCastDriver and assert an
    // observable world effect: the bound spell is Self+Heal, so a resolved cast raises health.
    // The Iron's-typed bodies live in ProxyCasts so the GameTest scanner never links Iron's
    // classes on the Iron's-absent profile.

    /** A Curios-equipped book must resolve its bound Ars spell through the proxy (creative). */
    @GameTest(template = "platform")
    public static void ironsLoaded_boundArsSpell_creativeCastResolvesThroughProxy(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ProxyCasts.creative(helper);
        helper.succeed();
    }

    /** Same path in survival with mana to spare: the resource authority must not deny a funded cast. */
    @GameTest(template = "platform")
    public static void ironsLoaded_boundArsSpell_survivalCastWithManaResolves(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ProxyCasts.survival(helper);
        helper.succeed();
    }

    /** A book held in the main hand must still resolve, via the hand fallback. */
    @GameTest(template = "platform")
    public static void ironsLoaded_boundArsSpell_heldBookResolvesViaHandFallback(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ProxyCasts.heldBook(helper);
        helper.succeed();
    }

    /**
     * Guards the assumption {@code ArsCrossProxySpell}'s casting-book lookup is built on: Iron's
     * records no casting item for spellbook casts.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_spellbookCast_leavesCastingItemEmpty(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ProxyCasts.castingItemEmpty(helper);
        helper.succeed();
    }

    /** Spellbook detection uses Iron's type, not a path substring. */
    @GameTest(template = "platform")
    public static void ironsLoaded_spellbookDetection_usesTypeNotNaming(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = IronsTableDriver.freshBook();
        helper.assertTrue(!book.isEmpty(), "no Iron's spell book is registered despite Iron's being loaded");
        helper.assertTrue(IronsBookBindingUtil.isIronsSpellBook(book), "a real Iron's spell book must be recognized");
        var scroll = BuiltInRegistries.ITEM.getOptional(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "scroll"));
        helper.assertTrue(scroll.isEmpty() || !IronsBookBindingUtil.isIronsSpellBook(new ItemStack(scroll.get())),
            "an Iron's scroll must not be mistaken for a spell book");
        helper.assertTrue(!IronsBookBindingUtil.isIronsSpellBook(new ItemStack(Items.BOOK)),
            "a vanilla book must not be mistaken for an Iron's spell book");
        helper.succeed();
    }

    /**
     * The bind command must refuse a payload that cannot deserialize to a castable spell,
     * rather than consuming the scroll and binding a wheel entry that does nothing. The
     * positive control runs first so a command that silently binds nothing cannot make the
     * rejection assertion pass vacuously. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_bindCommand_rejectsUncastablePayload(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ProxyCasts.bindCommandRejectsUncastable(helper);
        helper.succeed();
    }

    /**
     * Every scroll ANS hands out must satisfy Iron's container invariant, asserted by the exact
     * dereference that crashed the Inscription Table. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_exportedCarrier_survivesInscriptionDereference(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ProxyCasts.exportedCarrierDereference(helper);
        helper.succeed();
    }

    /**
     * Unbinding an exported scroll leaves a valid, blank Iron's scroll: the native container is
     * Iron's and must survive. (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_unbindCarrier_leavesValidBlankScroll(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ProxyCasts.unbindCarrierLeavesBlankScroll(helper);
        helper.succeed();
    }

    /** A generated proxy scroll - what the creative tab and JEI produce - is hideable. (Forge parity.) */
    @GameTest(template = "platform")
    public static void ironsLoaded_generatedProxyScroll_isRecognizedAsGhost(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        helper.assertTrue(ArsCrossProxyHiding.isProxyOnlyStack(IronsProxyCastDriver.makeProxyScroll(1)),
            "a scroll whose only spell is an ars_cross_* proxy must be recognized as a generated ghost, "
                + "or it stays visible in JEI/EMI and the creative menu");
        helper.succeed();
    }

    /**
     * Proxies stay registered (the native wheel resolves them by id) but declare
     * allowCrafting=false so Iron's Scroll Forge and the recipes it feeds JEI skip them.
     * (Forge parity.)
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_proxies_declareNonCraftableButStayRegistered(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ProxyCasts.proxiesNonCraftable(helper);
        helper.succeed();
    }

    private static final class ProxyCasts {
        private static final com.mojang.authlib.GameProfile FAKE_PROFILE = new com.mojang.authlib.GameProfile(
            java.util.UUID.fromString("0a000000-0000-0000-0000-00000000a115"), "ans_gametest");

        /** Bind a real, castable Self+Heal onto a real spellbook; returns the bound book. */
        static ItemStack healBook(GameTestHelper helper) {
            ItemStack book = IronsTableDriver.freshBook();
            helper.assertTrue(!book.isEmpty(), "no Iron's spell book is registered despite Iron's being loaded");
            var bound = IronsBookBindingUtil.appendArsSpellToBook(book,
                com.otectus.arsnspells.spell.CrossCastingHandler.encodeArsSpell(new com.hollingsworth.arsnouveau.api.spell.Spell(
                    com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                    com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE)),
                "E2E Heal", "water", "heart", -1);
            helper.assertTrue(bound.wasAdded(), "binding a real Ars spell onto a real spellbook must ADD, got " + bound);
            helper.assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(book, 1).isPresent(),
                "the first bind must land in proxy pool 1");
            return book;
        }

        /** A hurt, empty-handed caster with clean cast state; every gating property is reset. */
        static net.minecraft.server.level.ServerPlayer emptyHandedPlayer(GameTestHelper helper,
                                                                         com.mojang.authlib.GameProfile profile) {
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(), profile);
            player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.0, 2.0, 1.0)));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setHealth(10.0f);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            IronsProxyCastDriver.setIronsMana(player, 10000.0f);
            IronsProxyCastDriver.equipSpellbook(player, ItemStack.EMPTY);
            IronsProxyCastDriver.resetCastingState(player);
            return player;
        }

        static ItemStack ironsScroll(GameTestHelper helper) {
            var item = BuiltInRegistries.ITEM.getOptional(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "scroll"));
            helper.assertTrue(item.isPresent(), "Iron's scroll item must be registered when Iron's is loaded");
            return new ItemStack(item.get());
        }

        static void runBindCommand(GameTestHelper helper, ItemStack scroll, ItemStack book) {
            var player = emptyHandedPlayer(helper, FAKE_PROFILE);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, scroll);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, book);
            helper.getLevel().getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(2), "ans bind_scroll_to_irons_book");
        }

        static void bindCommandRejectsUncastable(GameTestHelper helper) {
            ItemStack goodScroll = ironsScroll(helper);
            CrossModSpellComponents.addArsEntryWithMeta(goodScroll, CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1,
                com.otectus.arsnspells.spell.CrossCastingHandler.encodeArsSpell(new com.hollingsworth.arsnouveau.api.spell.Spell(
                    com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                    com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE)),
                CrossModSpellComponents.NO_PROXY_POOL_ID, null, null, null);
            ItemStack goodBook = IronsTableDriver.freshBook();
            runBindCommand(helper, goodScroll, goodBook);
            helper.assertTrue(CrossModSpellComponents.has(goodBook), "positive control failed: the bind command "
                + "must bind a readable payload. The rejection assertion below would be vacuous.");

            ItemStack scroll = ironsScroll(helper);
            CompoundTag garbage = new CompoundTag();
            garbage.putString("recipe", "not_a_real_spell");
            CrossModSpellComponents.addArsEntryWithMeta(scroll, CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1,
                garbage, CrossModSpellComponents.NO_PROXY_POOL_ID, null, null, null);
            ItemStack book = IronsTableDriver.freshBook();
            runBindCommand(helper, scroll, book);
            helper.assertTrue(!CrossModSpellComponents.has(book), "the bind command must reject a payload that "
                + "cannot deserialize to a castable Ars spell instead of binding a silent dud");
        }

        static void exportedCarrierDereference(GameTestHelper helper) {
            ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(new com.hollingsworth.arsnouveau.api.spell.Spell(
                com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE));
            helper.assertTrue(!carrier.isEmpty() && IronsBookBindingUtil.isIronsScroll(carrier),
                "export must yield a real irons_spellbooks:scroll when Iron's is loaded");
            helper.assertTrue(IronsProxyCastDriver.scrollContainerDereferenceSucceeds(carrier),
                "an exported carrier must survive ISpellContainer.get(...).getSpellAtIndex(0) - this is the "
                    + "Inscription Table NPE");
            var container = io.redspace.ironsspellbooks.api.spells.ISpellContainer.get(carrier);
            helper.assertTrue(container == null || !container.isSpellWheel(),
                "the carrier's native container must not add it to Iron's spell wheel");
            helper.assertTrue(IronsBookBindingUtil.extractSingleArsEntry(carrier).isPresent(),
                "initializing the native container must not disturb the ANS sidecar payload");
        }

        static void unbindCarrierLeavesBlankScroll(GameTestHelper helper) {
            ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(new com.hollingsworth.arsnouveau.api.spell.Spell(
                com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE));
            helper.assertTrue(!carrier.isEmpty(), "export must yield a carrier when Iron's is loaded");
            IronsBookBindingUtil.removeAllArsEntries(carrier);
            helper.assertTrue(!carrier.isEmpty(), "unbinding must not destroy the scroll stack");
            helper.assertTrue(!carrier.has(ModDataComponents.EXPORT_MODE.get()), "unbinding must remove ANS's own export marker");
            helper.assertTrue(IronsProxyCastDriver.scrollContainerDereferenceSucceeds(carrier),
                "unbinding stripped the native Iron's container - removing it recreates the Inscription Table NPE");
            helper.assertTrue(IronsInscriptionPolicy.evaluate(carrier) == IronsInscriptionPolicy.Verdict.ALLOW,
                "an unbound carrier is an ordinary blank Iron's scroll and must pass the inscription guard");
        }

        static void proxiesNonCraftable(GameTestHelper helper) {
            var proxy = ArsCrossProxyRegistry.get(1);
            helper.assertTrue(proxy != null, "proxy pool 1 must be registered - the native wheel resolves it by id");
            helper.assertTrue(!proxy.getDefaultConfig().allowCrafting, "proxies must declare allowCrafting=false so "
                + "Iron's Scroll Forge, and the recipes it feeds JEI, skip them");
            helper.assertTrue(ArsCrossProxyRegistry.poolIdOf(proxy.getSpellResource()) == 1,
                "the registered proxy must still resolve by its ars_cross_1 id");
        }

        static void creative(GameTestHelper helper) {
            ItemStack book = healBook(helper);
            var player = emptyHandedPlayer(helper, FAKE_PROFILE);
            IronsProxyCastDriver.castViaEquippedSpellbook(player, book, 1);
            helper.assertTrue(player.getHealth() > 10.0f, "creative proxy cast of a Curios-equipped book must "
                + "resolve the bound Ars spell (heal); health unchanged means the proxy could not locate the "
                + "book it was cast from, or the data path is broken");
        }

        static void survival(GameTestHelper helper) {
            ItemStack book = healBook(helper);
            var player = emptyHandedPlayer(helper, new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("ans_gametest/funded_ars_proxy".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "funded_ars_proxy"));
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.otectus.arsnspells.bridge.NativeManaAccess.with(player,
                com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA, () -> {
                    var mana = com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player);
                    mana.setMaxMana(10000); mana.setMana(10000); return null;
                });
            com.otectus.arsnspells.bridge.BridgeManager.getNativeIronsBridge().setMana(player, 10000);
            IronsProxyCastDriver.castViaEquippedSpellbook(player, book, 1);
            helper.assertTrue(player.getHealth() > 10.0f, "survival proxy cast with ample mana must resolve the "
                + "bound Ars spell; health unchanged means the resource-authority leg denies a funded cast");
        }

        static void heldBook(GameTestHelper helper) {
            ItemStack book = healBook(helper);
            var player = emptyHandedPlayer(helper, FAKE_PROFILE);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, book);
            IronsProxyCastDriver.castViaArmedProxyWithoutEquipping(player, 1);
            helper.assertTrue(player.getHealth() > 10.0f, "a bound book held in the main hand must resolve via the hand fallback");
        }

        static void castingItemEmpty(GameTestHelper helper) {
            ItemStack book = healBook(helper);
            var player = emptyHandedPlayer(helper, FAKE_PROFILE);
            int index = IronsProxyCastDriver.proxySlotIndex(book, 1);
            helper.assertTrue(index >= 0, "the bound proxy must occupy a real slot in the book's Iron's container");
            helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, book, index),
                "Utils.serverSideInitiateCast refused the selection; the proxy slot or the SpellSelection "
                    + "index no longer lines up with Iron's SpellSelectionManager");
            helper.assertTrue(IronsProxyCastDriver.castingItemIsEmpty(player),
                "Iron's now records a real casting item for spellbook casts - revisit the proxy's casting-book "
                    + "lookup, which exists only because it did not");
        }
    }
}
