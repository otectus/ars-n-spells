package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ModDataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Spell Uninscription ritual must return an item that is <em>indistinguishable</em> from
 * one that was never inscribed.
 *
 * <p>The ritual used to call {@code CrossModSpellComponents.clear(stack)} and nothing else,
 * which leaves two artefacts behind: ANS's export marker, and — with Iron's installed — the
 * native wheel slots whose owning pool ids only existed in the sidecar that was just erased,
 * so they became unremovable entries that appear in the wheel and cast nothing.
 * {@link IronsBookBindingUtil#removeAllArsEntries} is the ordered teardown that replaced it.
 *
 * <p>This runs as a GameTest rather than a unit test because {@code ModDataComponents} are
 * {@code DeferredHolder}s: they only resolve once the mod is loaded. The proxy-slot half of
 * the teardown needs Iron's, which is {@code compileOnly} and therefore absent here; the
 * sidecar and export-marker halves are asserted unconditionally.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class UninscribeTeardownGameTests {

    private UninscribeTeardownGameTests() {}

    private static ItemStack inscribed() {
        ItemStack stack = new ItemStack(Items.BOOK);
        CompoundTag payload = new CompoundTag();
        payload.putString("recipe", "gametest_spell");
        CrossModSpellComponents.addArsEntryWithMeta(stack,
            ResourceLocation.fromNamespaceAndPath("ars_n_spells", "gametest"), 1, payload,
            1, "Test Spell", "fire", "flame");
        stack.set(ModDataComponents.EXPORT_MODE.get(),
            ArsSpellExportUtil.EXPORT_MODE_SCROLL_CARRIER);
        return stack;
    }

    /** The teardown leaves no ANS component of any kind on the item. */
    @GameTest(template = "platform")
    public static void teardown_leavesNoAnsComponents(GameTestHelper helper) {
        ItemStack stack = inscribed();
        if (!CrossModSpellComponents.has(stack)) {
            helper.fail("fixture did not attach the cross-mod component");
        }

        IronsBookBindingUtil.removeAllArsEntries(stack);

        if (CrossModSpellComponents.has(stack)) {
            helper.fail("the cross-mod spell component survived uninscription");
        }
        if (stack.has(ModDataComponents.EXPORT_MODE.get())) {
            helper.fail("the export marker survived uninscription - the item is still "
                + "flagged as an ANS carrier and re-transcribing it misbehaves");
        }
        helper.succeed();
    }

    /** The result matches a never-inscribed item exactly, not merely approximately. */
    @GameTest(template = "platform")
    public static void teardown_isIndistinguishableFromABlankItem(GameTestHelper helper) {
        ItemStack stack = inscribed();
        IronsBookBindingUtil.removeAllArsEntries(stack);

        ItemStack pristine = new ItemStack(Items.BOOK);
        if (!ItemStack.isSameItemSameComponents(stack, pristine)) {
            helper.fail("uninscribed item still differs from a blank one: "
                + stack.getComponents() + " vs " + pristine.getComponents());
        }
        helper.succeed();
    }

    /** A never-inscribed item is not damaged by running the teardown over it. */
    @GameTest(template = "platform")
    public static void teardown_isANoOpOnACleanItem(GameTestHelper helper) {
        ItemStack clean = new ItemStack(Items.BOOK);
        int removed = IronsBookBindingUtil.removeAllArsEntries(clean);
        if (removed != 0) {
            helper.fail("teardown reported removing " + removed + " slots from a clean item");
        }
        if (!ItemStack.isSameItemSameComponents(clean, new ItemStack(Items.BOOK))) {
            helper.fail("teardown mutated a clean item: " + clean.getComponents());
        }
        helper.succeed();
    }
}
