package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Audit ANS-010 (same names as the Forge tests): the reconciler repairs an Iron's book whose ANS
 * sidecar component is entirely gone, or empty, by removing the ANS proxy slots no entry backs, while
 * native spells, the other entries and future schemas are left alone. On NeoForge the sidecar is a
 * typed data component, so an undecodable sidecar cannot exist; the future-schema half of the
 * conservative test is the part that applies.
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

        private static net.minecraft.world.item.ItemStack book(GameTestHelper helper, int arsSpells) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "netherite_spell_book"));
            helper.assertTrue(item != net.minecraft.world.item.Items.AIR, "Iron's netherite spell book is registered");
            var book = new net.minecraft.world.item.ItemStack(item);
            var container = io.redspace.ironsspellbooks.api.spells.ISpellContainer.getOrCreate(book).mutableCopy();
            container.addSpellAtIndex(io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fireball")), 3, 0, false);
            io.redspace.ironsspellbooks.api.spells.ISpellContainer.set(book, container.toImmutable());
            var forms = new com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod[]{
                com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
                com.hollingsworth.arsnouveau.common.spell.method.MethodTouch.INSTANCE};
            for (int i = 0; i < arsSpells; i++) {
                var spell = new com.hollingsworth.arsnouveau.api.spell.Spell(forms[i],
                    com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE);
                var added = com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(book,
                    com.otectus.arsnspells.spell.CrossCastingHandler.encodeArsSpell(spell), "Bound " + i, "water", "spell/heal", -1);
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

        private static net.minecraft.core.component.DataComponentMap components(net.minecraft.world.item.ItemStack book) {
            return book.getComponents();
        }

        static void missingAndEmptySidecar(GameTestHelper helper) {
            var spells = com.otectus.arsnspells.spell.ModDataComponents.CROSS_SPELLS.get();
            for (boolean empty : new boolean[]{false, true}) {
                String which = empty ? "empty sidecar" : "missing sidecar";
                var book = book(helper, 1);
                helper.assertTrue(proxies(book).equals(java.util.Set.of(1)), "fixture: one ANS proxy, got " + proxies(book));
                if (empty) book.set(spells, com.otectus.arsnspells.spell.CrossModSpellList.EMPTY);
                else book.remove(spells);
                var outcome = reconcile(book);
                helper.assertTrue(outcome == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.ORPHANS_REMOVED,
                    which + ": the repair is reported, got " + outcome);
                helper.assertTrue(proxies(book).isEmpty(), which + ": the orphan proxy is removed, left " + proxies(book));
                helper.assertTrue(Glyph341IronsSupport.nativeSpells(book).equals(java.util.List.of(NATIVE)),
                    which + ": the native spell is preserved, got " + Glyph341IronsSupport.nativeSpells(book));
                var after = components(book);
                helper.assertTrue(reconcile(book) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                    && after.equals(components(book)), which + ": reconciliation is idempotent");
            }
            var plain = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "netherite_spell_book")));
            var before = components(plain);
            helper.assertTrue(reconcile(plain) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                && before.equals(components(plain)), "a book with no ANS data is left exactly as it was");
        }

        static void survivorAndOrphan(GameTestHelper helper) {
            var book = book(helper, 2);
            helper.assertTrue(proxies(book).equals(java.util.Set.of(1, 2)), "fixture: two ANS proxies, got " + proxies(book));
            helper.assertTrue(com.otectus.arsnspells.spell.CrossModSpellComponents.removeEntryByProxyPoolId(book, 2),
                "fixture: the sidecar entry for pool 2 is removed, leaving its proxy");
            reconcile(book);
            helper.assertTrue(proxies(book).equals(java.util.Set.of(1)),
                "the surviving entry keeps its wheel slot and the other orphan goes, got " + proxies(book));
            helper.assertTrue(Glyph341IronsSupport.nativeSpells(book).equals(java.util.List.of(NATIVE)), "the native spell is preserved");
            helper.assertTrue(com.otectus.arsnspells.spell.CrossModSpellComponents.usedProxyPoolIds(
                com.otectus.arsnspells.spell.CrossModSpellComponents.get(book)).equals(java.util.Set.of(1)), "the surviving sidecar entry is untouched");
            var valid = book(helper, 1);
            reconcile(valid);
            var settled = components(valid);
            helper.assertTrue(reconcile(valid) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                && settled.equals(components(valid)), "a valid book is unchanged");
        }

        static void conservative(GameTestHelper helper) {
            var future = book(helper, 1);
            future.remove(com.otectus.arsnspells.spell.ModDataComponents.CROSS_SPELLS.get());
            future.set(com.otectus.arsnspells.spell.ModDataComponents.SCHEMA_VERSION.get(),
                com.otectus.arsnspells.spell.CrossModSpellComponents.SCHEMA_VERSION + 1);
            var before = components(future);
            helper.assertTrue(reconcile(future) == com.otectus.arsnspells.spell.irons.CarrierReconciler.Outcome.UNCHANGED
                && before.equals(components(future)), "a future schema is preserved as it is");
            helper.assertTrue(proxies(future).equals(java.util.Set.of(1)), "and its proxy is not removed");
        }
    }
}
