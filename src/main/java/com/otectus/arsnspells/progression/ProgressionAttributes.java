package com.otectus.arsnspells.progression;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Shared helper for the cross-mod progression system. Looks up the Iron's
 * Spellbooks {@code <school>_spell_power} attribute and applies a transient
 * additive modifier sized by {@code bonus}.
 *
 * Used by both Ars-side {@link com.otectus.arsnspells.events.ProgressionHandler}
 * and Iron's-side {@link com.otectus.arsnspells.events.IronsProgressionHandler}
 * so both directions share the exact same modifier id.
 *
 * <p>Iron's-only at runtime: the attribute it looks up only exists when Iron's
 * is loaded. Callers must be gated on {@link com.otectus.arsnspells.compat.IronsCompat#isLoaded()}.
 */
public final class ProgressionAttributes {
    private static final String IRONS_NAMESPACE = "irons_spellbooks";
    public static final ResourceLocation MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath("ars_n_spells", "cross_mod_school_progression");

    private ProgressionAttributes() {}

    public static void applyTransientBonus(ServerPlayer player, String school, double bonus) {
        if (player == null || school == null || school.isEmpty()) {
            return;
        }
        ResourceLocation attrId = ResourceLocation.fromNamespaceAndPath(
            IRONS_NAMESPACE, school + "_spell_power");
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
            .getHolder(attrId)
            .orElse(null);
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(MODIFIER_ID);
        if (bonus > 0) {
            instance.addTransientModifier(new AttributeModifier(
                MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
