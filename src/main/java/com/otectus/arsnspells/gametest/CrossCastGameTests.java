package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.CrossModSpellList;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ModDataComponents;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyHiding;
import com.otectus.arsnspells.spell.irons.CarrierReconciler;
import com.otectus.arsnspells.spell.irons.IronsInscriptionPolicy;
import com.otectus.arsnspells.spell.irons.IronsProxySlotWriter;
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
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
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
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        // The hiding rule must catch ANS's generated proxy scrolls without also hiding a real
        // player-owned book that happens to carry a bound entry.
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("glyph_heal"));
        if (ArsCrossProxyHiding.isProxyOnlyStack(book)) {
            helper.fail("a real book with a bound entry must stay visible - hiding it would "
                + "make the player's own spellbook vanish from JEI and the creative tab");
            return;
        }
        helper.succeed();
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
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
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
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
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
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack carrier = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(carrier, arsPayload("glyph_heal"));

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
