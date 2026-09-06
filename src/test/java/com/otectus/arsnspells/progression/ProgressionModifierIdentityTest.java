package com.otectus.arsnspells.progression;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.data.ProgressionData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V07 - one progression bonus, one modifier identity.
 *
 * <p>The port shipped two: {@code ProgressionAttributes} wrote
 * {@code ars_n_spells:cross_mod_school_progression} and {@code ProgressionHandler} wrote
 * {@code ars_n_spells:progression_element_xp}, each removing only its own. An Ars cast followed
 * by an Iron's cast of the same school therefore left both on {@code <school>_spell_power} at
 * once - the bonus paid twice, and half of it surviving every removal path this mod has.
 *
 * <p>These drive {@link ProgressionModifiers#applyTo} against a bare {@link AttributeInstance}
 * rather than a player: the invariant is about the modifier map, and the modifier map needs no
 * server.
 */
class ProgressionModifierIdentityTest {

    private static final ResourceLocation LEGACY_ELEMENT_XP =
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "progression_element_xp");
    private static final ResourceLocation THIRD_PARTY =
        ResourceLocation.fromNamespaceAndPath("some_other_mod", "spell_power_charm");

    private static AttributeInstance spellPower() {
        Holder<Attribute> attribute = Holder.direct(
            new RangedAttribute("attribute.irons_spellbooks.fire_spell_power", 1.0, 0.0, 1024.0));
        return new AttributeInstance(attribute, ignored -> {});
    }

    private static void add(AttributeInstance instance, ResourceLocation id, double amount) {
        instance.addTransientModifier(
            new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static int ansModifierCount(AttributeInstance instance) {
        int count = 0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (ArsNSpells.MODID.equals(modifier.id().getNamespace())) {
                count++;
            }
        }
        return count;
    }

    @Test
    void bothHistoricalIdsAreKnownToTheMapper() {
        assertTrue(ProgressionModifiers.ALL_IDS.contains(ProgressionModifiers.CANONICAL_ID),
            "the canonical id must be in the removal set, or applying twice would stack it");
        assertTrue(ProgressionModifiers.ALL_IDS.contains(LEGACY_ELEMENT_XP),
            "progression_element_xp is a shipped identity; a build that forgets it can never "
                + "remove the modifier an older build applied");
    }

    @Test
    void applyLeavesExactlyOneAnsModifier() {
        AttributeInstance instance = spellPower();

        ProgressionModifiers.applyTo(instance, 0.20);

        assertEquals(1, ansModifierCount(instance),
            "one progression bonus is one modifier");
        AttributeModifier applied = instance.getModifier(ProgressionModifiers.CANONICAL_ID);
        assertNotNull(applied, "and it is the canonical one");
        assertEquals(0.20, applied.amount(), 1.0e-9);
    }

    @Test
    void bothLegacyIdsPreAppliedCollapseToOne() {
        AttributeInstance instance = spellPower();
        add(instance, ProgressionModifiers.CANONICAL_ID, 0.10);
        add(instance, LEGACY_ELEMENT_XP, 0.10);
        add(instance, THIRD_PARTY, 0.05);
        assertEquals(2, ansModifierCount(instance), "fixture: the V07 double-application");

        ProgressionModifiers.applyTo(instance, 0.20);

        assertEquals(1, ansModifierCount(instance),
            "applying must remove every historical id first, not only the one it writes");
        assertNull(instance.getModifier(LEGACY_ELEMENT_XP),
            "the second identity is exactly what compounded across builds");
        assertNotNull(instance.getModifier(THIRD_PARTY),
            "another mod's modifier on the same attribute is none of our business");
        assertEquals(1.0 + 0.20 + 0.05, instance.getValue(), 1.0e-9,
            "the bonus is paid once");
    }

    @Test
    void aZeroBonusRemovesBothIdentities() {
        AttributeInstance instance = spellPower();
        add(instance, ProgressionModifiers.CANONICAL_ID, 0.10);
        add(instance, LEGACY_ELEMENT_XP, 0.10);

        ProgressionModifiers.applyTo(instance, 0.0);

        assertEquals(0, ansModifierCount(instance),
            "no bonus means no modifier, under any id we have ever used");
    }

    @Test
    void rebuildingTheModifierDoesNotTouchCastCounts() {
        ProgressionData data = new ProgressionData();
        for (int i = 0; i < 30; i++) {
            data.incrementCastCount("fire");
        }
        data.incrementCastCount("ice");
        Map<String, Integer> before = data.getAllCastCounts();

        AttributeInstance instance = spellPower();
        Map<String, Double> bonuses = ProgressionModifiers.bonuses(data);
        for (int round = 0; round < 3; round++) {
            ProgressionModifiers.applyTo(instance, bonuses.get("fire"));
        }

        assertEquals(before, data.getAllCastCounts(),
            "progression is persistent; only the transient modifier is rebuilt");
        assertEquals(30, data.getCastCount("fire"));
        assertEquals(1, ansModifierCount(instance),
            "three rebuilds still leave one modifier");
    }
}
