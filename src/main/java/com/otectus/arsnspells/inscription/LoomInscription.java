package com.otectus.arsnspells.inscription;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.contract.InscriptionPlanner;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * The Spell Loom's server-authoritative inscription logic, separated from its packet
 * (audits V18 and V19).
 *
 * <p><b>What changed and why.</b> The loom used to decide and act in one pass inside
 * {@code SpellLoomExportPacket}: it asked "is this scroll free of ANS data?" and, if so,
 * extracted one item from the source slot and one from the scroll slot. That cost players
 * items two ways. A reusable Ars spellbook or caster tome placed in the source slot was
 * consumed like a parchment. And a scroll Iron's itself had filled carries no ANS data, so it
 * passed the blankness test and was overwritten.
 *
 * <p>Now every decision is an {@link InscriptionPlan} produced by {@link InscriptionPlanner}
 * from a {@link StackInscriptionView}, and every movement is an {@link InscriptionOutcome}
 * derived from that plan:
 *
 * <ul>
 *   <li>{@link #plan} and {@link #planConversion} are pure. A preview calls one of them and
 *       moves nothing -- the conversion preview works on a blanked <em>copy</em> of the target,
 *       never the target itself.</li>
 *   <li>{@link #apply} validates everything first and mutates in one block at the end, so a
 *       refusal at any step leaves all three slots exactly as they were.</li>
 *   <li>A reusable source is never consumed, because the plan's {@code consumedUnits} is zero
 *       for one and {@link InscriptionPlan} refuses to be constructed saying otherwise.</li>
 *   <li>One inscription is one unit on both sides; the rest of either stack stays put.</li>
 * </ul>
 *
 * <p>Reason codes returned here are the planner's own where the planner decided, plus the four
 * loader-level codes below for pre-conditions the planner has no view of. They travel back to
 * the client by packet and are never written to a stack, so no saved item changes shape.
 */
public final class LoomInscription {

    /** The output slot still holds a previous carrier. */
    public static final String REASON_OUTPUT_OCCUPIED = "output occupied";
    /** The target is an Iron's scroll and Iron's is not installed. */
    public static final String REASON_IRONS_MISSING = "irons missing";
    /** The source slot holds no readable Ars spell. */
    public static final String REASON_NO_ARS_SPELL = "no ars spell";
    /** Building the carrier scroll failed; nothing was moved. */
    public static final String REASON_CARRIER_FAILED = "carrier failed";
    public static final String REASON_INVALID_TARGET = "invalid target";

    private LoomInscription() {
    }

    /**
     * What the loom would do with its current contents. Pure: reads the three slots and
     * mutates nothing.
     */
    public static InscriptionPlan plan(SpellLoomBlockEntity loom) {
        return plan(loom, false);
    }

    /**
     * What the loom would do if the player explicitly chose to convert an already-filled
     * scroll: the same plan, taken against a blanked copy of the target.
     *
     * <p>This is the intentional route audit V18 asks for in place of a silent overwrite. It
     * does not bypass the planner -- the planner still sees a genuine blank and still refuses
     * anything else -- and because the blanking happens on a copy, previewing a conversion is
     * as inert as previewing an ordinary inscription. The spell in the scroll is only actually
     * destroyed by {@link #apply} with {@code convert} set, which no default path passes.
     */
    public static InscriptionPlan planConversion(SpellLoomBlockEntity loom) {
        return plan(loom, true);
    }

    private static InscriptionPlan plan(SpellLoomBlockEntity loom, boolean convert) {
        ItemStackHandler items = loom.getItems();
        ItemStack source = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE);
        ItemStack target = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL);
        String failure = null;
        if (!items.getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT).isEmpty()) {
            failure = REASON_OUTPUT_OCCUPIED;
        } else if (!IronsCompat.isLoaded()) {
            failure = REASON_IRONS_MISSING;
        } else if (!isTarget(target)) {
            failure = REASON_INVALID_TARGET;
        } else if (!isSource(source)) {
            failure = REASON_NO_ARS_SPELL;
        }
        if (failure != null) {
            return new InscriptionPlan(InscriptionClassifier.classify(source),
                InscriptionClassifier.classify(target), 0, 0, failure);
        }
        ItemStack planned = convert ? InscriptionClassifier.blankedSingleCopy(target) : target;
        return InscriptionPlanner.plan(new StackInscriptionView(source, planned));
    }

    /** Shared menu/automation/server predicates. Filled targets require explicit conversion. */
    public static boolean isSource(ItemStack stack) {
        return ArsSpellExportUtil.extractArsSpell(stack).isPresent();
    }

    /**
     * Whether {@code stack} may sit in the loom's target slot: an Iron's scroll, or our own
     * blank scroll. The ANS blank exists because stock Iron's ships no recipe for a bare
     * {@code irons_spellbooks:scroll}, so without it the only obtainable targets were scrolls
     * that already held a spell. Either way the output is a real Iron's scroll carrier -- the
     * blank is consumed as the target, not reshaped -- so accepting it costs the planner
     * nothing: it carries no payload and no native container, and classifies as an honest
     * blank.
     */
    public static boolean isTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return IronsBookBindingUtil.isIronsScroll(stack)
            || stack.is(ModItemsRegistry.blankScroll().get());
    }

    /** {@link #apply(SpellLoomBlockEntity, boolean, String, String, String)} with no cosmetics. */
    public static String apply(SpellLoomBlockEntity loom, boolean convert) {
        return apply(loom, convert, "", "", "");
    }

    /**
     * Run one inscription. Returns {@link InscriptionPlan#REASON_OK} on success, or the reason
     * code that refused it -- in which case nothing at all has moved.
     *
     * @param convert when true, an already-filled target is blanked as part of this operation.
     *                Only ever passed by the explicit conversion action, never by the default
     *                inscribe button.
     */
    public static String apply(SpellLoomBlockEntity loom, boolean convert,
                               String name, String nature, String iconSymbol) {
        ItemStackHandler items = loom.getItems();
        ItemStack source = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE);
        ItemStack target = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL);

        if (!items.getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT).isEmpty()) {
            return REASON_OUTPUT_OCCUPIED;
        }
        if (!IronsCompat.isLoaded()) {
            return REASON_IRONS_MISSING;
        }

        // The stack the plan is made against: the real target, or a blanked copy of it when the
        // player asked for a conversion. Either way this is a read -- the commit below rebuilds
        // the output from scratch.
        InscriptionPlan plan = plan(loom, convert);
        if (!plan.isPermitted()) {
            return plan.reasonCode();
        }

        InscriptionOutcome outcome =
            InscriptionOutcome.of(plan, source.getCount(), target.getCount());
        if (!outcome.isPermitted()) {
            return outcome.reasonCode();
        }

        Optional<Spell> spell = ArsSpellExportUtil.extractArsSpell(source);
        if (spell.isEmpty()) {
            return REASON_NO_ARS_SPELL;
        }
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
            spell.get(), name, nature, iconSymbol);
        if (carrier.isEmpty()) {
            return REASON_CARRIER_FAILED;
        }
        carrier.setCount(outcome.transformedTargets());

        // Validation complete -- mutation begins here, and only here.
        if (outcome.consumedFromSource() > 0) {
            items.extractItem(SpellLoomBlockEntity.SLOT_SOURCE, outcome.consumedFromSource(), false);
        }
        items.extractItem(SpellLoomBlockEntity.SLOT_SCROLL, outcome.transformedTargets(), false);
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT, carrier);
        loom.setChanged();
        return InscriptionPlan.REASON_OK;
    }
}
