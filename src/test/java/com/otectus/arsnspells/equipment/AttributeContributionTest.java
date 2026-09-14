package com.otectus.arsnspells.equipment;

import net.minecraft.world.entity.ai.attributes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttributeContributionTest {
    private static AttributeModifier modifier(double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("fixture", UUID.randomUUID().toString()), amount, operation);
    }

    @Test void allOperationsMatchNativeReferenceAndOwnAmplifiedBonusIsExcluded() {
        AttributeInstance instance = new AttributeInstance(net.minecraft.core.Holder.direct(new RangedAttribute("fixture", 100, 0, 10000)), ignored -> {});
        var add = modifier(40, AttributeModifier.Operation.ADD_VALUE);
        var base = modifier(.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        var total = modifier(1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        var own = modifier(250, AttributeModifier.Operation.ADD_VALUE);
        for (var modifier : List.of(add, base, total, own)) instance.addTransientModifier(modifier);
        assertEquals(instance.getValue(), AttributeContribution.evaluate(100, instance.getModifiers()), 1e-8);
        assertEquals(320, AttributeContribution.equipmentDelta(instance, List.of(add, base, total), Set.of(own.id())), 1e-8);
        for (int repeat=0; repeat<10; repeat++) {
            assertEquals(320, AttributeContribution.equipmentDelta(instance, List.of(add, base, total), Set.of(own.id())), 1e-8);
            assertEquals(4, instance.getModifiers().size());
        }
        assertEquals(3, AttributeContribution.additiveAmplification(List.of(add, base, total)), 1e-8);
    }

    @Test void negativeContributionsAndNativeMultipliersArePreserved() {
        AttributeInstance instance = new AttributeInstance(net.minecraft.core.Holder.direct(new RangedAttribute("fixture", 100, 0, 10000)), ignored -> {});
        var curse = modifier(-20, AttributeModifier.Operation.ADD_VALUE);
        var nativeTotal = modifier(1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        instance.addTransientModifier(curse);
        instance.addTransientModifier(nativeTotal);
        assertEquals(-40, AttributeContribution.equipmentDelta(instance, List.of(curse), Set.of()), 1e-8);
    }
}
