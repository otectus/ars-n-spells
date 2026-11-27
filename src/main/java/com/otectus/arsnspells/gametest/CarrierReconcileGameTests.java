package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Audit ANS-010: the reconciler repairs an Iron's book whose ANS sidecar is entirely gone, or
 * empty, by removing the ANS proxy slots no entry backs, while native spells, the other entries,
 * future schemas and undecodable sidecars are left alone.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CarrierReconcileGameTests {
    private CarrierReconcileGameTests() {}

    @GameTest(template = "platform")
    public static void ironsLoaded_reconciler_repairsBooksWithoutASidecar(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.missingAndEmptySidecar(helper);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_reconciler_keepsTheSurvivorAndRemovesAnotherOrphan(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.survivorAndOrphan(helper);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_reconciler_leavesFutureAndUndecodableSidecarsAlone(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.conservative(helper);
        helper.succeed();
    }

    private static final class Loaded {
        private static final String NATIVE = "irons_spellbooks:fireball@3";
        private static final String TAG = com.otectus.arsnspells.spell.CrossCastNbt.TAG_CROSS_MOD_SPELLS;

        private static net.minecraft.world.item.ItemStack book(GameTestHelper helper, int arsSpells) {
            var item = CrossCastGameTests.findIronsSpellBook();
            helper.assertTrue(item != null, "an Iron's spellbook is registered");
            var book = new net.minecraft.world.item.ItemStack(item);
            Glyph341IronsSupport.addNativeSpells(book, new String[]{"irons_spellbooks:fireball"}, new int[]{3});
            var forms = new com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod[]{
                com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                com.hollingsworth.arsnouveau.common.spell.method.MethodTouch.INSTANCE};
            for (int i = 0; i < arsSpells; i++) {
                var spell = new com.hollingsworth.arsnouveau.api.spell.Spell(forms[i],
                    com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE);
                var added = com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(book, spell.serialize(), "Bound " + i, "water", "heart", -1);
                helper.assertTrue(added == com.otectus.arsnspells.spell.IronsBookBindingUtil.AppendResult.ADDED, "fixture bind " + i + ": " + added);
            }
            return book;
        }

        private static java.util.Set<Integer> proxies(net.minecraft.world.item.ItemStack book) {
            return com.otectus.arsnspells.spell.irons.IronsProxySlotWriter.nativeProxyPoolIds(book);
        }

        private static com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome reconcile(net.minecraft.world.item.ItemStack book) {
            return com.otectus.arsnspells.spell.irons.CarrierReconciler.reconcile(book);
        }

        static void missingAndEmptySidecar(GameTestHelper helper) {
            for (boolean empty : new boolean[]{false, true}) {
                String which = empty ? "empty sidecar" : "missing sidecar";
                var book = book(helper, 1);
                helper.assertTrue(proxies(book).equals(java.util.Set.of(1)), "fixture: one ANS proxy, got " + proxies(book));
                if (empty) book.getTag().put(TAG, new net.minecraft.nbt.ListTag());
                else book.getTag().remove(TAG);
                var outcome = reconcile(book);
                helper.assertTrue(outcome == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.ORPHANS_REMOVED,
                    which + ": the repair is reported, got " + outcome);
                helper.assertTrue(proxies(book).isEmpty(), which + ": the orphan proxy is removed, left " + proxies(book));
                helper.assertTrue(Glyph341IronsSupport.nativeSpells(book).equals(java.util.List.of(NATIVE)),
                    which + ": the native spell is preserved, got " + Glyph341IronsSupport.nativeSpells(book));
                var after = book.getTag().copy();
                helper.assertTrue(reconcile(book) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                    && after.equals(book.getTag()), which + ": reconciliation is idempotent");
            }
            // A plain Iron's book with no ANS data at all is not touched.
            var item = CrossCastGameTests.findIronsSpellBook();
            var plain = new net.minecraft.world.item.ItemStack(item);
            Glyph341IronsSupport.addNativeSpells(plain, new String[]{"irons_spellbooks:fireball"}, new int[]{3});
            var before = plain.getTag().copy();
            helper.assertTrue(reconcile(plain) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                && before.equals(plain.getTag()), "a book with no ANS data is left exactly as it was");
        }

        static void survivorAndOrphan(GameTestHelper helper) {
            var book = book(helper, 2);
            helper.assertTrue(proxies(book).equals(java.util.Set.of(1, 2)), "fixture: two ANS proxies, got " + proxies(book));
            var list = book.getTag().getList(TAG, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                if (list.getCompound(i).getInt(com.otectus.arsnspells.spell.CrossCastNbt.TAG_PROXY_POOL_ID) == 2) {
                    list.remove(i);
                    break;
                }
            }
            reconcile(book);
            helper.assertTrue(proxies(book).equals(java.util.Set.of(1)),
                "the surviving entry keeps its wheel slot and the other orphan goes, got " + proxies(book));
            helper.assertTrue(Glyph341IronsSupport.nativeSpells(book).equals(java.util.List.of(NATIVE)), "the native spell is preserved");
            helper.assertTrue(com.otectus.arsnspells.spell.CrossCastNbt.usedProxyPoolIds(book.getTag()).equals(java.util.Set.of(1)),
                "the surviving sidecar entry is untouched");
            // A current, valid book is unchanged.
            var valid = book(helper, 1);
            reconcile(valid);
            var settled = valid.getTag().copy();
            helper.assertTrue(reconcile(valid) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                && settled.equals(valid.getTag()), "a valid book is unchanged");
        }

        static void conservative(GameTestHelper helper) {
            var future = book(helper, 1);
            future.getTag().remove(TAG);
            future.getTag().putInt(com.otectus.arsnspells.spell.CrossCastNbt.TAG_SCHEMA_VERSION, com.otectus.arsnspells.spell.CrossCastNbt.SCHEMA_VERSION + 1);
            var futureBefore = future.getTag().copy();
            helper.assertTrue(reconcile(future) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                && futureBefore.equals(future.getTag()), "a future schema is preserved as it is");

            var odd = book(helper, 1);
            odd.getTag().putString(TAG, "not a list");
            var oddBefore = odd.getTag().copy();
            helper.assertTrue(reconcile(odd) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                && oddBefore.equals(odd.getTag()), "an undecodable sidecar is not guessed at");
            helper.assertTrue(proxies(odd).equals(java.util.Set.of(1)), "and its proxy is not removed");
        }
    }
}
