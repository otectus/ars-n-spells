package com.otectus.arsnspells.modifier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reads an attribute as if Ars 'n' Spells had never touched it (audit V13).
 *
 * <p><b>The defect this replaces.</b> The ANS-excluded value used to be computed as
 * {@code instance.getValue() - instance.getBaseValue() - ownModifier.amount()}: a raw
 * {@code ADD_VALUE} amount subtracted out of an already-multiplied total.
 * {@code AttributeInstance.calculateValue} sums {@code ADD_VALUE} first and <em>then</em>
 * applies every {@code ADD_MULTIPLIED_*} modifier, so as soon as a third-party mod put a
 * multiplier on Iron's {@code max_mana} the subtraction removed less than ANS had actually
 * contributed. In ARS_PRIMARY that residue was folded back into Ars's max, which the ceiling
 * sync then wrote back onto the same attribute, and the pool climbed on every recompute.
 *
 * <p><b>The rule.</b> Never subtract an amount from a multiplied result. Take the ANS-owned
 * modifiers off, let the attribute recompute, read it, put them back. The reading is then the
 * native value under whatever operations other mods use, exactly, and the ANS contribution is
 * the difference between the two readings rather than an amount pulled out of the middle of the
 * arithmetic.
 *
 * <p>Restoration is with {@code addTransientModifier} because every modifier ANS applies is
 * transient by construction; the mod writes no permanent modifiers on any attribute.
 *
 * <p>This is deliberately <em>not</em> a contribution ledger. Separating base from gear, perk
 * and effect sources is 3.4.0 work; this answers the one question the callers ask.
 */
public final class NativeAttributeSnapshot {

    private NativeAttributeSnapshot() {}

    /**
     * The attribute's value with every ANS-owned modifier temporarily removed.
     *
     * <p>The attribute is left exactly as it was found. Nothing may write to the entity between
     * the removal and the restore, so this must stay on the server thread and stay free of
     * callbacks - which is why it takes a plain id collection rather than a predicate.
     */
    public static double nativeValue(AttributeInstance instance, Collection<ResourceLocation> ansIds) {
        if (instance == null) {
            return 0.0;
        }
        List<AttributeModifier> removed = removeOwned(instance, ansIds);
        try {
            return instance.getValue();
        } finally {
            for (AttributeModifier modifier : removed) {
                instance.addTransientModifier(modifier);
            }
        }
    }

    /**
     * What everything other than ANS adds above the base value, measured against the isolated
     * native reading. Never negative: a third-party modifier that reduces the attribute below
     * its base is not a bonus to fold anywhere.
     */
    public static double foreignAdditiveDelta(AttributeInstance instance,
                                              Collection<ResourceLocation> ansIds) {
        if (instance == null) {
            return 0.0;
        }
        return Math.max(0.0, nativeValue(instance, ansIds) - instance.getBaseValue());
    }

    /** Take off every ANS modifier currently present, returning them in application order. */
    private static List<AttributeModifier> removeOwned(AttributeInstance instance,
                                                       Collection<ResourceLocation> ansIds) {
        List<AttributeModifier> removed = new ArrayList<>(2);
        if (ansIds == null) {
            return removed;
        }
        for (ResourceLocation id : ansIds) {
            AttributeModifier modifier = instance.getModifier(id);
            if (modifier != null) {
                instance.removeModifier(id);
                removed.add(modifier);
            }
        }
        return removed;
    }
}
