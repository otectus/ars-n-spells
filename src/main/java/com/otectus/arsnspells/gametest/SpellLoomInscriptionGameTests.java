package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.inscription.LoomInscription;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Audit V18 on the Spell Loom: what the loom eats, and what it calls blank.
 *
 * <p>Two item-loss defects are pinned here. The loom consumed its source slot unconditionally,
 * so a reusable Ars spellbook or caster tome fed in as a source was destroyed for one scroll.
 * And it decided "blank" by asking whether ANS had written to the scroll, which is a different
 * question from whether the scroll is empty -- a scroll filled by Iron's itself carries no ANS
 * data, so it was overwritten in place. Both questions now route through
 * {@link com.otectus.arsnspells.contract.InscriptionPlanner} via {@link LoomInscription}.
 *
 * <p>These drive {@link LoomInscription} against a real placed loom rather than
 * {@code SpellLoomExportPacket}, because the packet's only remaining job is transport: it
 * resolves the sender's open menu and forwards the same two calls. Everything that can lose an
 * item lives below that boundary.
 *
 * <p>Iron's-only: the loom's target is the Iron's scroll item and the emptiness answer comes
 * from Iron's own container. Runs under {@code -PwithIronsRuntimeGameTests}; gated through
 * {@link OptionalModGate} so an absent-profile pass is recorded as the skip it is.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SpellLoomInscriptionGameTests {

    private static final BlockPos LOOM = new BlockPos(2, 1, 2);

    private SpellLoomInscriptionGameTests() {
    }

    /** Place a real Spell Loom and hand back its block entity. */
    private static SpellLoomBlockEntity placeLoom(GameTestHelper helper) {
        helper.setBlock(LOOM, ModBlocksRegistry.SPELL_LOOM.get().defaultBlockState());
        if (!(helper.getBlockEntity(LOOM) instanceof SpellLoomBlockEntity loom)) {
            helper.fail("placing ars_n_spells:spell_loom produced no SpellLoomBlockEntity at " + LOOM);
            return null;
        }
        return loom;
    }

    /** An Ars spellbook carrying a real spell: the reusable source the loom used to eat. */
    private static ItemStack filledArsSpellBook(GameTestHelper helper) {
        Item book = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("ars_nouveau", "novice_spell_book"));
        if (book == null) {
            helper.fail("ars_nouveau:novice_spell_book is not registered");
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(book);
        SpellCaster caster = new SpellCaster(stack);
        caster.setSpell(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        caster.writeItem(stack);
        return stack;
    }

    private static void seed(SpellLoomBlockEntity loom, ItemStack source, ItemStack scroll) {
        ItemStackHandler items = loom.getItems();
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE, source);
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL, scroll);
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
    }

    /** A stable fingerprint of all three slots, so "changed nothing" is asserted, not assumed. */
    private static String snapshot(SpellLoomBlockEntity loom) {
        StringBuilder sb = new StringBuilder();
        ItemStackHandler items = loom.getItems();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            sb.append(slot).append('=').append(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                .append('x').append(stack.getCount()).append('/').append(stack.getTag()).append(';');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------

    /**
     * A scroll Iron's already filled is not blank, however little ANS data it carries. The old
     * check read {@code !isInscribed(scroll)} -- true for this stack -- and overwrote it.
     */
    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_filledScrollIsNotAcceptedAsBlank(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        SpellLoomBlockEntity loom = placeLoom(helper);
        ItemStack filled = IronsLoomFixtures.nativelyFilledScroll();
        if (filled.isEmpty()) {
            helper.fail("could not build a natively filled Iron's scroll fixture");
        }
        if (IronsLoomFixtures.nativeContainerIsEmpty(filled)) {
            helper.fail("the fixture scroll must be natively filled, or this test proves nothing");
        }
        seed(loom, filledArsSpellBook(helper), filled);
        String before = snapshot(loom);

        InscriptionPlan plan = LoomInscription.plan(loom);
        if (plan.isPermitted()) {
            helper.fail("a natively filled Iron's scroll was accepted as a blank target");
        }
        if (!InscriptionPlan.REASON_NOT_BLANK.equals(plan.reasonCode())) {
            helper.fail("expected reason '" + InscriptionPlan.REASON_NOT_BLANK
                + "' for a filled scroll, got '" + plan.reasonCode() + "'");
        }

        String applied = LoomInscription.apply(loom, false);
        if (!InscriptionPlan.REASON_NOT_BLANK.equals(applied)) {
            helper.fail("apply must refuse with the reason the plan gave, got '" + applied + "'");
        }
        if (!before.equals(snapshot(loom))) {
            helper.fail("a refused inscription changed the loom's slots");
        }
        helper.succeed();
    }

    /**
     * A spellbook is read, not eaten. The loom used to call
     * {@code extractItem(SLOT_SOURCE, 1, false)} whatever the source was.
     */
    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_reusableBookIsPreservedAsSource(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        SpellLoomBlockEntity loom = placeLoom(helper);
        seed(loom, filledArsSpellBook(helper), IronsLoomFixtures.blankScroll());

        String applied = LoomInscription.apply(loom, false);
        if (!InscriptionPlan.REASON_OK.equals(applied)) {
            helper.fail("inscribing from a filled Ars spellbook onto a blank scroll must "
                + "succeed, got '" + applied + "'");
        }
        ItemStackHandler items = loom.getItems();
        ItemStack source = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE);
        if (source.isEmpty()) {
            helper.fail("the Ars spellbook was consumed as an inscription source");
        }
        if (source.getCount() != 1) {
            helper.fail("the spellbook stack changed size: " + source.getCount());
        }
        if (items.getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT).isEmpty()) {
            helper.fail("a permitted inscription produced no carrier in the output slot");
        }
        if (!items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL).isEmpty()) {
            helper.fail("the one blank scroll paid for the inscription and should be gone");
        }
        helper.succeed();
    }

    /** Planning is a read. Asking the loom what it would do must move nothing. */
    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_previewChangesNoInventory(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        SpellLoomBlockEntity loom = placeLoom(helper);
        ItemStack scrolls = IronsLoomFixtures.blankScroll();
        scrolls.setCount(16);
        seed(loom, filledArsSpellBook(helper), scrolls);
        String before = snapshot(loom);

        // Both preview routes: the ordinary one, and the filled-scroll conversion preview,
        // which has to build a blanked copy of the target and must not blank the real one.
        InscriptionPlan plan = LoomInscription.plan(loom);
        InscriptionPlan conversion = LoomInscription.planConversion(loom);
        if (!plan.isPermitted()) {
            helper.fail("the preview fixture should describe a legal inscription, got '"
                + plan.reasonCode() + "'");
        }
        if (conversion == null) {
            helper.fail("planConversion must always return a plan, never null");
        }
        if (!before.equals(snapshot(loom))) {
            helper.fail("previewing an inscription mutated the loom's slots");
        }
        helper.succeed();
    }
}
