package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.contract.InscriptionSourceKind;
import com.otectus.arsnspells.rituals.LoomInscriptionView;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Audit V18/V19 for the Spell Loom: what the loom decides, and what that decision costs the
 * player in items.
 *
 * <p>Three findings are covered. The loom used to read "blank" as "carries no ANS data", so a
 * scroll another mod had already filled was overwritten. It consumed one unit of the source
 * slot unconditionally, so a spellbook or focus used as a source was destroyed by reading it.
 * And it decided and mutated in one pass, so there was no state a caller could inspect before
 * items moved.
 *
 * <p>These run without Iron's Spellbooks, and deliberately so: the classification and the
 * slot arithmetic are ANS's own and must be correct on either install. Vanilla stacks stand in
 * as generic carriers exactly as in {@link ArsIronsExportGameTests}; the Iron's-specific half
 * (native container emptiness on a real book) is asserted by
 * {@link UninscribeTeardownGameTests} behind {@link OptionalModGate}.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SpellLoomInscriptionGameTests {

    private SpellLoomInscriptionGameTests() {}

    /** The Ars spellbook, resolved by id so no Ars item class is named here. */
    private static ItemStack arsSpellBook() {
        Item item = BuiltInRegistries.ITEM
            .getOptional(ResourceLocation.fromNamespaceAndPath("ars_nouveau", "novice_spell_book"))
            .orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** A stack that already carries a spell, in ANS's own schema. Never blank. */
    private static ItemStack filledScroll() {
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag payload = new CompoundTag();
        payload.putString("recipe", "someone_elses_spell");
        CrossModSpellComponents.addArsEntryWithMeta(stack,
            ResourceLocation.fromNamespaceAndPath("ars_n_spells", "gametest"), 1, payload,
            CrossModSpellComponents.NO_PROXY_POOL_ID, "Not Yours", null, null);
        return stack;
    }

    private static ItemStackHandler loaded(ItemStack source, ItemStack scroll) {
        ItemStackHandler items = new ItemStackHandler(SpellLoomBlockEntity.SLOT_COUNT);
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE, source);
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL, scroll);
        return items;
    }

    /** V18: a scroll another mod already filled is refused, not silently overwritten. */
    @GameTest(template = "platform")
    public static void Loom_filledScrollIsNotAcceptedAsBlank(GameTestHelper helper) {
        ItemStack source = arsSpellBook();
        if (source.isEmpty()) {
            helper.fail("Ars Nouveau's novice spell book is missing from the item registry");
            return;
        }
        ItemStack scroll = filledScroll();

        if (LoomInscriptionView.classify(scroll) != InscriptionSourceKind.FILLED_SCROLL) {
            helper.fail("a scroll carrying a spell classified as "
                + LoomInscriptionView.classify(scroll) + ", not FILLED_SCROLL");
            return;
        }

        InscriptionPlan plan = LoomInscriptionView.plan(source, scroll);
        if (plan.isPermitted()) {
            helper.fail("the loom would have overwritten a filled scroll");
            return;
        }
        if (!InscriptionPlan.REASON_NOT_BLANK.equals(plan.reasonCode())) {
            helper.fail("expected reason '" + InscriptionPlan.REASON_NOT_BLANK
                + "', got '" + plan.reasonCode() + "'");
            return;
        }

        ItemStackHandler items = loaded(source, scroll);
        if (SpellLoomBlockEntity.applyInscription(items, plan, new ItemStack(Items.PAPER))) {
            helper.fail("apply ran a refused plan");
            return;
        }
        if (!ItemStack.isSameItemSameComponents(
                items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL), scroll)) {
            helper.fail("the filled scroll was mutated by a refused inscription");
            return;
        }
        helper.succeed();
    }

    /** V18: reading a spellbook does not destroy it. */
    @GameTest(template = "platform")
    public static void Loom_reusableBookIsPreservedAsSource(GameTestHelper helper) {
        ItemStack source = arsSpellBook();
        if (source.isEmpty()) {
            helper.fail("Ars Nouveau's novice spell book is missing from the item registry");
            return;
        }
        ItemStack scroll = new ItemStack(Items.PAPER, 4);

        if (LoomInscriptionView.classify(source) != InscriptionSourceKind.REUSABLE_BOOK) {
            helper.fail("a spellbook classified as " + LoomInscriptionView.classify(source));
            return;
        }
        InscriptionPlan plan = LoomInscriptionView.plan(source, scroll);
        if (plan.consumedUnits() != 0) {
            helper.fail("the plan would consume " + plan.consumedUnits() + " of a reusable source");
            return;
        }

        ItemStackHandler items = loaded(source, scroll);
        if (!SpellLoomBlockEntity.applyInscription(items, plan, new ItemStack(Items.BOOK))) {
            helper.fail("a permitted plan did not apply");
            return;
        }
        ItemStack remainingSource = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE);
        if (remainingSource.isEmpty() || remainingSource.getCount() != 1) {
            helper.fail("the spellbook was consumed as if it were a scroll");
            return;
        }
        // V19: one unit of the target, and the rest handed back untouched.
        if (items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL).getCount() != 3) {
            helper.fail("expected 3 blanks returned, got "
                + items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL).getCount());
            return;
        }
        if (items.getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT).getCount() != 1) {
            helper.fail("expected exactly one inscribed output");
            return;
        }
        helper.succeed();
    }

    /** Planning is a read. Nothing in the loom moves until apply is called. */
    @GameTest(template = "platform")
    public static void Loom_previewChangesNoInventory(GameTestHelper helper) {
        ItemStack source = arsSpellBook();
        if (source.isEmpty()) {
            helper.fail("Ars Nouveau's novice spell book is missing from the item registry");
            return;
        }
        ItemStack scroll = new ItemStack(Items.PAPER, 64);
        ItemStackHandler items = loaded(source, scroll);

        ItemStack sourceBefore = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE).copy();
        ItemStack scrollBefore = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL).copy();

        for (int i = 0; i < 5; i++) {
            LoomInscriptionView.plan(
                items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE),
                items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL));
        }

        ItemStack sourceAfter = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE);
        ItemStack scrollAfter = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL);
        if (!ItemStack.matches(sourceBefore, sourceAfter)) {
            helper.fail("preview changed the source slot");
            return;
        }
        if (!ItemStack.matches(scrollBefore, scrollAfter)) {
            helper.fail("preview changed the scroll slot: " + scrollBefore.getCount()
                + " -> " + scrollAfter.getCount());
            return;
        }
        if (!items.getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT).isEmpty()) {
            helper.fail("preview filled the output slot");
            return;
        }
        helper.succeed();
    }
}
