package com.otectus.arsnspells.modifier;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.SharedPoolCeiling;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V13 - the ANS-excluded value must never be a raw amount subtracted from a multiplied total.
 *
 * <p>The ARS_PRIMARY mana loop is a feedback loop by design: {@code ArsManaCalcHandler} folds
 * the non-ANS part of Iron's {@code max_mana} into Ars's max, and the ceiling sync then writes
 * ANS's own modifier back onto that same attribute. It is only stable if the "non-ANS part" is
 * measured exactly. {@code EquipmentIntegration.foreignModifierTotal} measured it as
 * {@code getValue() - getBaseValue() - ownAmount}, and {@code AttributeInstance} applies every
 * {@code ADD_MULTIPLIED_*} modifier <em>after</em> summing {@code ADD_VALUE}, so one
 * third-party multiplier left part of ANS's own contribution in the answer and the pool grew
 * on every recompute.
 *
 * <p>The audit's scenario: a native maximum of 200, an Ars maximum of 300, and a third-party
 * x2 multiplier. Five recomputes must land on the same numbers as one.
 */
class NativeSnapshotCeilingFixedPointTest {

    private static final double NATIVE_MAX = 200.0;
    private static final double ARS_MAX = 300.0;

    private static final ResourceLocation CEILING_ID =
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "ars_gear_max_mana");
    private static final ResourceLocation THIRD_PARTY_MULTIPLIER =
        ResourceLocation.fromNamespaceAndPath("some_other_mod", "double_mana");

    private static final List<ResourceLocation> ANS_IDS = AnsModifierIdMapper.INSTANCE.allIds();

    private static AttributeInstance maxMana() {
        Holder<Attribute> attribute = Holder.direct(
            new RangedAttribute("attribute.irons_spellbooks.max_mana", NATIVE_MAX, 0.0, 100000.0));
        return new AttributeInstance(attribute, ignored -> {});
    }

    /** One full ARS_PRIMARY round: fold the foreign bonus into Ars's max, then re-sync. */
    private static void recompute(AttributeInstance instance) {
        double foreign = NativeAttributeSnapshot.foreignAdditiveDelta(instance, ANS_IDS);
        double arsMax = ARS_MAX + foreign;

        double nativeMax = NativeAttributeSnapshot.nativeValue(instance, ANS_IDS);
        instance.removeModifier(CEILING_ID);
        double needed = SharedPoolCeiling.modifierAmount(nativeMax, arsMax);
        if (needed != 0.0) {
            instance.addTransientModifier(new AttributeModifier(
                CEILING_ID, needed, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Test
    void withoutAnyThirdPartyModifierTheCeilingSettlesOnTheArsMax() {
        AttributeInstance instance = maxMana();

        for (int round = 0; round < 5; round++) {
            recompute(instance);
            assertEquals(ARS_MAX, instance.getValue(), 1.0e-9,
                "round " + round + ": the ceiling is max(native, Ars), not a running total");
        }
        assertEquals(ARS_MAX - NATIVE_MAX,
            instance.getModifier(CEILING_ID).amount(), 1.0e-9);
    }

    @Test
    void aThirdPartyDoublerDoesNotMakeTheCeilingClimb() {
        AttributeInstance instance = maxMana();
        instance.addTransientModifier(new AttributeModifier(
            THIRD_PARTY_MULTIPLIER, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

        recompute(instance);
        double afterFirstRound = instance.getValue();
        double firstAmount = instance.getModifier(CEILING_ID).amount();

        for (int round = 1; round < 5; round++) {
            recompute(instance);
            assertEquals(afterFirstRound, instance.getValue(), 1.0e-9,
                "round " + round + ": recomputing must be a fixed point, not a ratchet");
            assertEquals(firstAmount, instance.getModifier(CEILING_ID).amount(), 1.0e-9,
                "round " + round + ": the ANS modifier amount must not grow either");
        }
    }

    @Test
    void theIsolatedSnapshotDisagreesWithTheRawSubtractionItReplaces() {
        AttributeInstance instance = maxMana();
        instance.addTransientModifier(new AttributeModifier(
            THIRD_PARTY_MULTIPLIER, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        instance.addTransientModifier(new AttributeModifier(
            CEILING_ID, 100.0, AttributeModifier.Operation.ADD_VALUE));

        double isolated = NativeAttributeSnapshot.foreignAdditiveDelta(instance, ANS_IDS);
        double rawSubtraction = instance.getValue() - instance.getBaseValue() - 100.0;

        assertEquals(NATIVE_MAX, isolated, 1.0e-9,
            "the doubler contributes exactly the base again; ANS's 100 contributes nothing here");
        assertNotEquals(rawSubtraction, isolated,
            "if these ever agree the scenario has stopped reproducing the defect");
    }

    @Test
    void theSnapshotPutsEveryAnsModifierBackExactlyAsItFoundIt() {
        AttributeInstance instance = maxMana();
        instance.addTransientModifier(new AttributeModifier(
            CEILING_ID, 100.0, AttributeModifier.Operation.ADD_VALUE));
        double before = instance.getValue();

        NativeAttributeSnapshot.nativeValue(instance, ANS_IDS);

        assertEquals(before, instance.getValue(), 1.0e-9,
            "the snapshot is a read; leaving the modifier off would delete the ceiling");
        assertEquals(100.0, instance.getModifier(CEILING_ID).amount(), 1.0e-9);
        assertNull(instance.getModifier(ProgressionIdProbe.CANONICAL_PROGRESSION),
            "and it must not invent modifiers it did not find");
    }

    /** Named holder so the probe id above reads as intent rather than as a literal. */
    private static final class ProgressionIdProbe {
        static final ResourceLocation CANONICAL_PROGRESSION = ResourceLocation
            .fromNamespaceAndPath(ArsNSpells.MODID, "cross_mod_school_progression");

        private ProgressionIdProbe() {}
    }
}
