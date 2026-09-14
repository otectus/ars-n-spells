package com.otectus.arsnspells.progression;

import com.otectus.arsnspells.bridge.AnsModifierIdentities;
import com.otectus.arsnspells.contract.AnsModifierIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Shared helper for the cross-mod progression system. Looks up the Iron's
 * Spellbooks {@code <school>_spell_power} attribute and applies a transient
 * additive modifier sized by {@code bonus}.
 *
 * Used by both Ars-side {@link com.otectus.arsnspells.events.ProgressionHandler}
 * and Iron's-side {@link com.otectus.arsnspells.events.IronsProgressionHandler}
 * so both directions share the exact same modifier UUID and naming.
 *
 * <p>Iron's-only: this helper imports nothing from Iron's APIs directly, but
 * the attribute it looks up only exists when Iron's is loaded. Callers must
 * be gated on {@link com.otectus.arsnspells.compat.IronsCompat#isLoaded()}.
 */
public final class ProgressionAttributes {
    // V07/V14: one identity for this bonus, and it is the shared registry's. Both the Ars-side
    // and the Iron's-side handler reach the attribute through this helper, so there is exactly
    // one UUID to clean up.
    public static final UUID ELEMENT_XP_ID =
        AnsModifierIdentities.uuid(AnsModifierIds.CROSS_MOD_SCHOOL_PROGRESSION);
    private static final String IRONS_NAMESPACE = "irons_spellbooks";
    private static final String MODIFIER_NAME = "Cross-Mod School Progression";

    private ProgressionAttributes() {}

    public static void applyTransientBonus(ServerPlayer player, String school, double bonus) {
        if (player == null || school == null || school.isEmpty()) {
            return;
        }
        if (!com.otectus.arsnspells.compat.IronsCompat.isLoaded()) return;
        Attribute attribute = com.otectus.arsnspells.compat.IronsSchoolAttributes.power(school);
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(ELEMENT_XP_ID);
        if (Double.isFinite(bonus) && bonus > 0) {
            instance.addTransientModifier(new AttributeModifier(
                ELEMENT_XP_ID, MODIFIER_NAME, bonus, AttributeModifier.Operation.ADDITION));
        }
    }
}
