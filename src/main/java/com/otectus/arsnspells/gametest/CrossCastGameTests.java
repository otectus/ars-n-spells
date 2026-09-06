package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.CompatIds;
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

    /**
     * Bind then unbind must leave no ANS-owned artifact on the carrier.
     *
     * <p><b>Two carriers, two contracts.</b> Without Iron's the bind touches nothing native,
     * so a plain item must come back byte-identical to a fresh one - that is the whole of the
     * generic teardown contract and {@link UninscribeTeardownGameTests} asserts it in detail.
     * With Iron's the carrier is a real spellbook whose native container is <em>not</em>
     * ANS-owned: {@code IronsProxySlotWriter.removeProxySlot} deliberately does not shrink the
     * grown max spell count, because doing so would re-index the player's own spells. So what
     * is asserted there is what ANS actually promises - its own components gone, no
     * {@code ars_cross_*} slot left selectable in the wheel, and the player's genuine spell
     * untouched.
     *
     * <p>The fixture used to be a vanilla {@code Items.BOOK} in both cases, which made the
     * byte-identity check fail under Iron's for a reason that exists nowhere in production:
     * ANS's own bind had created the only native container the book ever had, and teardown
     * leaves that container behind by design.
     */
    @GameTest(template = "platform")
    public static void bindThenUnbind_leavesNoTrace(GameTestHelper helper) {
        boolean irons = IronsCompat.isLoaded();
        ItemStack book = irons
            ? IronsCarrierSupport.spellBookWithNativeSpell()
            : new ItemStack(Items.BOOK);
        if (book.isEmpty()) {
            helper.fail("no Iron's spellbook holding a genuine spell could be built, so there "
                + "is no real carrier to tear down");
            return;
        }
        String nativeSpellId = irons ? IronsCarrierSupport.nativeSpellId() : null;

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
        if (!irons) {
            if (!ItemStack.isSameItemSameComponents(book, new ItemStack(Items.BOOK))) {
                helper.fail("an uninscribed item must be indistinguishable from a fresh one: "
                    + book.getComponents());
                return;
            }
            helper.succeed();
            return;
        }
        if (IronsCarrierSupport.hasAnyProxySlot(book)) {
            helper.fail("an ars_cross_* slot survived uninscribe: it is still selectable in "
                + "Iron's wheel and casts nothing");
            return;
        }
        if (nativeSpellId != null && !IronsCarrierSupport.holdsNativeSpell(book, nativeSpellId)) {
            helper.fail("uninscribe removed the player's own Iron's spell " + nativeSpellId
                + " along with ANS's proxy slot");
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
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
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
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        // The hiding rule must catch ANS's generated proxy scrolls without also hiding a real
        // player-owned book that happens to carry a bound entry. So the fixture is that book:
        // a genuine Iron's spellbook already holding a genuine Iron's spell. A vanilla
        // Items.BOOK is not one - the only native container it ever had was the one ANS's own
        // bind created, which is precisely the generated shape this rule is meant to catch.
        ItemStack book = IronsCarrierSupport.spellBookWithNativeSpell();
        if (book.isEmpty()) {
            helper.fail("no Iron's spellbook holding a genuine spell could be built; the "
                + "negative control cannot run");
            return;
        }
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
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
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
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
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
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
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
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
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
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
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
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
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

    @GameTest(template = "platform")
    public static void ironsLoaded_validCarrier_isRejectedFromNativeTable(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        // A real carrier: the irons_spellbooks:scroll item with the valid empty native
        // container plus the ANS sidecar. IronsInscriptionPolicy only ever looks at scrolls -
        // a vanilla book was never its concern, so asserting a rejection on one proved nothing
        // about the guard.
        ItemStack carrier = IronsCarrierSupport.scrollCarrier(arsPayload("glyph_heal"));
        if (carrier.isEmpty()) {
            helper.fail("could not build a real ANS scroll carrier to put in the table");
            return;
        }

        IronsInscriptionPolicy.Verdict verdict = IronsInscriptionPolicy.evaluate(carrier);
        if (!verdict.isRejection()) {
            helper.fail("Iron's inscription table cannot read an ANS payload, so it must be "
                + "refused rather than allowed to overwrite the carrier; got " + verdict);
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
}
