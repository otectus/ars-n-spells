package com.otectus.arsnspells.equipment;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Counterfactual native attribute calculation; never mutates or dirties the attribute. */
public final class AttributeContribution {
    private AttributeContribution() {}

    public static double evaluate(double base, Collection<AttributeModifier> modifiers) {
        double added = base;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) added += modifier.getAmount();
        }
        double result = added;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE) result += added * modifier.getAmount();
        }
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) result *= 1 + modifier.getAmount();
        }
        return result;
    }

    public static double equipmentDelta(AttributeInstance instance, Collection<AttributeModifier> equipment,
                                         Set<UUID> excluded) {
        if (instance == null) return 0;
        Set<UUID> equipmentIds = equipment.stream().map(AttributeModifier::getId)
            .collect(java.util.stream.Collectors.toSet());
        var nativeOnly = new ArrayList<AttributeModifier>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!excluded.contains(modifier.getId()) && !equipmentIds.contains(modifier.getId())) nativeOnly.add(modifier);
        }
        double without = instance.getAttribute().sanitizeValue(evaluate(instance.getBaseValue(), nativeOnly));
        nativeOnly.addAll(equipment);
        double with = instance.getAttribute().sanitizeValue(evaluate(instance.getBaseValue(), nativeOnly));
        return Double.isFinite(with - without) ? with - without : 0;
    }

    public static double additiveAmplification(Collection<AttributeModifier> modifiers) {
        double baseMultiplier = 1;
        double totalMultiplier = 1;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE) baseMultiplier += modifier.getAmount();
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) totalMultiplier *= 1 + modifier.getAmount();
        }
        return baseMultiplier * totalMultiplier;
    }
}
