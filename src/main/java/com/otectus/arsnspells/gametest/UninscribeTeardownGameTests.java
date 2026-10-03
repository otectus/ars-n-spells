package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Spell Uninscription ritual must return an item that is <em>indistinguishable</em> from
 * one that was never inscribed. The Forge 1.20.1 counterpart of the NeoForge suite of the same
 * name: the NeoForge build stores the inscription in data components, this build in stack NBT.
 *
 * <p>{@link IronsBookBindingUtil#removeAllArsEntries} is the ordered teardown the ritual runs.
 * The proxy-slot half needs Iron's; the sidecar and export-marker halves are asserted
 * unconditionally.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class UninscribeTeardownGameTests {

    private UninscribeTeardownGameTests() {}

    private static ItemStack inscribed() {
        ItemStack stack = new ItemStack(Items.BOOK);
        CompoundTag payload = new CompoundTag();
        payload.putString("recipe", "gametest_spell");
        CrossCastNbt.addArsEntryWithMetaToTag(stack.getOrCreateTag(),
            new ResourceLocation("ars_n_spells", "gametest"), 1, payload,
            1, "Test Spell", "fire", "flame");
        stack.getOrCreateTag().putString(ArsSpellExportUtil.TAG_EXPORT_MODE,
            ArsSpellExportUtil.EXPORT_MODE_SCROLL_CARRIER);
        return stack;
    }

    /** The teardown leaves no ANS key of any kind on the item. */
    @GameTest(template = "platform")
    public static void teardown_leavesNoAnsComponents(GameTestHelper helper) {
        ItemStack stack = inscribed();
        if (!stack.hasTag() || !stack.getTag().contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS)) {
            helper.fail("fixture did not attach the cross-mod spell list");
        }

        IronsBookBindingUtil.removeAllArsEntries(stack);

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS)) {
            helper.fail("the cross-mod spell list survived uninscription");
        }
        if (tag != null && tag.contains(ArsSpellExportUtil.TAG_EXPORT_MODE)) {
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
        if (!ItemStack.isSameItemSameTags(stack, pristine)) {
            helper.fail("uninscribed item still differs from a blank one: "
                + stack.getTag() + " vs " + pristine.getTag());
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
        if (!ItemStack.isSameItemSameTags(clean, new ItemStack(Items.BOOK))) {
            helper.fail("teardown mutated a clean item: " + clean.getTag());
        }
        helper.succeed();
    }
}
