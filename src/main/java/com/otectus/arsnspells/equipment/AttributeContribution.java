package com.otectus.arsnspells.equipment;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Counterfactual native attribute calculation; never mutates or dirties the attribute. */
public final class AttributeContribution {
    private AttributeContribution() {}

    public static double evaluate(double base, Collection<AttributeModifier> modifiers) {
        double added = base;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) added += modifier.amount();
        }
        double result = added;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result += added * modifier.amount();
        }
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result *= 1 + modifier.amount();
        }
        return result;
    }

    public static double equipmentDelta(AttributeInstance instance, Collection<AttributeModifier> equipment,
                                         Set<ResourceLocation> excluded) {
        if (instance == null) return 0;
        Set<ResourceLocation> equipmentIds = equipment.stream().map(AttributeModifier::id)
            .collect(java.util.stream.Collectors.toSet());
        var nativeOnly = new ArrayList<AttributeModifier>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!excluded.contains(modifier.id()) && !equipmentIds.contains(modifier.id())) nativeOnly.add(modifier);
        }
        double without = instance.getAttribute().value().sanitizeValue(evaluate(instance.getBaseValue(), nativeOnly));
        nativeOnly.addAll(equipment);
        double with = instance.getAttribute().value().sanitizeValue(evaluate(instance.getBaseValue(), nativeOnly));
        return Double.isFinite(with - without) ? with - without : 0;
    }

    public static double additiveAmplification(Collection<AttributeModifier> modifiers) {
        double baseMultiplier = 1;
        double totalMultiplier = 1;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) baseMultiplier += modifier.amount();
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) totalMultiplier *= 1 + modifier.amount();
        }
        return baseMultiplier * totalMultiplier;
    }
}
