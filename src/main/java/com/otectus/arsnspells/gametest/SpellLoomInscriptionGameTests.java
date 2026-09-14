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
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.ScrollKind;
import com.otectus.arsnspells.spell.irons.IronsScrollFactory;
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

    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_invalidTargetsAndRepeatedRequestsPreserveStacks(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        SpellLoomBlockEntity loom = placeLoom(helper);
        seed(loom, filledArsSpellBook(helper), new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
        String before = snapshot(loom);
        helper.assertTrue(LoomInscription.REASON_INVALID_TARGET.equals(LoomInscription.apply(loom, false)),
            "an arbitrary disposable target must not create an Iron's scroll");
        helper.assertTrue(before.equals(snapshot(loom)), "invalid target consumed inventory");
        ItemStack blank = IronsLoomFixtures.blankScroll();
        blank.setCount(2);
        seed(loom, filledArsSpellBook(helper), blank);
        ItemStack sourceBefore = loom.getItems().getStackInSlot(0).copy();
        helper.assertTrue(InscriptionPlan.REASON_OK.equals(LoomInscription.apply(loom, false)), "first export failed");
        before = snapshot(loom);
        helper.assertTrue(LoomInscription.REASON_OUTPUT_OCCUPIED.equals(LoomInscription.plan(loom).reasonCode()),
            "preview must report occupied output");
        helper.assertTrue(LoomInscription.REASON_OUTPUT_OCCUPIED.equals(LoomInscription.apply(loom, false)),
            "a repeated request must refuse while output is occupied");
        helper.assertTrue(before.equals(snapshot(loom)), "repeated request consumed inventory");
        helper.assertTrue(ItemStack.matches(sourceBefore, loom.getItems().getStackInSlot(0)),
            "reusable source spells/upgrades/NBT changed");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_automationCannotBypassInputOrOutputPolicy(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        SpellLoomBlockEntity loom = placeLoom(helper);
        var top = loom.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
            net.minecraft.core.Direction.UP).orElseThrow(() -> new AssertionError("missing top capability"));
        var side = loom.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
            net.minecraft.core.Direction.NORTH).orElseThrow(() -> new AssertionError("missing side capability"));
        var bottom = loom.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
            net.minecraft.core.Direction.DOWN).orElseThrow(() -> new AssertionError("missing output capability"));
        ItemStack source = filledArsSpellBook(helper);
        ItemStack target = IronsLoomFixtures.blankScroll();
        helper.assertTrue(!top.insertItem(2, source, false).isEmpty(), "top inserted into output");
        helper.assertTrue(!side.insertItem(0, source, false).isEmpty(), "side inserted into source");
        helper.assertTrue(!bottom.insertItem(1, target, false).isEmpty(), "bottom inserted a target");
        helper.assertTrue(top.insertItem(0, source, false).isEmpty(), "top refused valid source");
        helper.assertTrue(side.insertItem(1, target, false).isEmpty(), "side refused valid target");
        helper.assertTrue(top.extractItem(0, 1, false).isEmpty(), "automation removed reusable source");
        helper.assertTrue(InscriptionPlan.REASON_OK.equals(LoomInscription.apply(loom, false)), "automated inputs failed");
        helper.assertTrue(top.extractItem(2, 1, false).isEmpty(), "top extracted output");
        helper.assertTrue(!bottom.extractItem(2, 1, false).isEmpty(), "bottom did not extract output");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_sourceDiscoveryIsReadOnlyAndFilledIronScrollIsUsableSource(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack blank = new ItemStack(net.minecraft.world.item.Items.PAPER);
        com.otectus.arsnspells.rituals.InscriptionInputs.readSource(blank);
        helper.assertTrue(blank.getTag() == null, "source discovery stamped NBT onto blank paper");
        ItemStack filled = IronsLoomFixtures.nativelyFilledScroll();
        var plan = com.otectus.arsnspells.contract.InscriptionPlanner.plan(
            new com.otectus.arsnspells.inscription.StackInscriptionView(filled, blank));
        helper.assertTrue(plan.isPermitted() && plan.consumedUnits() == 1,
            "native Iron's scroll must remain usable as a disposable transcription source");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3.3.3: the ANS blank scroll. Stock Iron's ships no recipe for a bare
    // irons_spellbooks:scroll, so before this item the only obtainable loom target was a
    // scroll that already held a spell -- which the loom correctly refuses. The blank is a
    // target, not the output: what comes out is still a real Iron's scroll carrier, because
    // Iron's doInscription requires `scroll instanceof Scroll`.
    // ------------------------------------------------------------------

    /** ANS blank + Ars source produces a real, readable Iron's carrier and eats the blank. */
    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_blankScrollProducesAnIronsCarrier(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        SpellLoomBlockEntity loom = placeLoom(helper);
        seed(loom, filledArsSpellBook(helper),
            new ItemStack(ModItemsRegistry.blankScroll().get()));

        String applied = LoomInscription.apply(loom, false);
        if (!InscriptionPlan.REASON_OK.equals(applied)) {
            helper.fail("the ANS blank scroll must be a legal loom target, got '" + applied + "'");
        }
        ItemStackHandler items = loom.getItems();
        ItemStack output = items.getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT);
        if (!ForgeRegistries.ITEMS.getKey(output.getItem())
                .equals(new ResourceLocation("irons_spellbooks", "scroll"))) {
            helper.fail("the output must be a real irons_spellbooks:scroll, not the ANS blank; "
                + "got " + ForgeRegistries.ITEMS.getKey(output.getItem()));
        }
        if (ScrollKind.classify(output) != ScrollKind.ANS_CARRIER) {
            helper.fail("the output must classify as an ANS carrier, got "
                + ScrollKind.classify(output));
        }
        if (!IronsScrollFactory.hasReadableContainer(output)) {
            helper.fail("the carrier needs a readable native container or Iron's own code "
                + "dereferences null on it");
        }
        if (CrossCastNbt.countArsEntries(output.getOrCreateTag()) != 1) {
            helper.fail("the carrier must hold exactly one Ars entry, found "
                + CrossCastNbt.countArsEntries(output.getOrCreateTag()));
        }
        if (!items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL).isEmpty()) {
            helper.fail("the one blank scroll paid for the inscription and should be gone");
        }
        helper.succeed();
    }

    /** A blank scroll with nothing to weave onto it is refused, and stays in the slot. */
    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_blankScrollWithoutASourceIsRefused(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        SpellLoomBlockEntity loom = placeLoom(helper);
        seed(loom, ItemStack.EMPTY, new ItemStack(ModItemsRegistry.blankScroll().get()));
        String before = snapshot(loom);

        String applied = LoomInscription.apply(loom, false);
        if (!LoomInscription.REASON_NO_ARS_SPELL.equals(applied)) {
            helper.fail("a blank scroll with no Ars source must be refused for lack of a spell, "
                + "got '" + applied + "'");
        }
        if (!before.equals(snapshot(loom))) {
            helper.fail("a refused inscription consumed the blank scroll");
        }
        helper.succeed();
    }

    /**
     * The conversion action is the escape hatch for the filled scroll that plain inscribe
     * refuses (see {@link #Loom_filledScrollIsNotAcceptedAsBlank}): it blanks the target as
     * part of the same operation and produces a carrier.
     */
    @GameTest(template = "platform", batch = "ans_loom")
    public static void Loom_filledScrollIsAcceptedByConversion(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        SpellLoomBlockEntity loom = placeLoom(helper);
        ItemStack filled = IronsLoomFixtures.nativelyFilledScroll();
        if (filled.isEmpty() || IronsLoomFixtures.nativeContainerIsEmpty(filled)) {
            helper.fail("the fixture scroll must be natively filled, or this test proves nothing");
        }
        seed(loom, filledArsSpellBook(helper), filled);

        String applied = LoomInscription.apply(loom, true);
        if (!InscriptionPlan.REASON_OK.equals(applied)) {
            helper.fail("Convert must accept a filled Iron's scroll, got '" + applied + "'");
        }
        ItemStack output = loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT);
        if (ScrollKind.classify(output) != ScrollKind.ANS_CARRIER) {
            helper.fail("conversion must produce an ANS carrier, got " + ScrollKind.classify(output));
        }
        if (!loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL).isEmpty()) {
            helper.fail("the converted scroll should have been consumed as the target");
        }
        helper.succeed();
    }
}
