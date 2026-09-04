package com.otectus.arsnspells.combat;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The caster's Iron's Spellbooks spell-power attributes, flattened to plain
 * {@code name -> value} pairs for {@code /ans debug combat}.
 *
 * <p>Exists purely so {@link com.otectus.arsnspells.commands.ArsNSpellsCommands} can print
 * these numbers without naming an Iron's type. That command class is registered
 * unconditionally and loads on Iron's-less servers, where a single Iron's import in its
 * constant pool is enough to fail its class load; the rest of the tree isolates Iron's the
 * same way (see {@code compat/irons_spells/SchoolIndex}).
 *
 * <p>Iron's-only: every caller must be inside an
 * {@code ModPresence.isLoaded(CompatIds.IRONS_SPELLBOOKS)} branch.
 *
 * <p>Insertion order is the report order: generic power first, then the nine schools in the
 * order Iron's declares them, so two players' reports line up when compared.
 */
public final class IronsAttributeReport {

    private IronsAttributeReport() {}

    /**
     * Read every Iron's spell-power attribute off {@code player}.
     *
     * <p>Diagnostics must never be the thing that throws in front of an op who is already
     * chasing a bug, so a missing attribute (a datapack removed it, another mod stripped the
     * instance) reports as {@code 0.0} rather than propagating.
     */
    public static Map<String, Double> read(Player player) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (player == null) {
            return values;
        }
        put(values, player, "spell_power", AttributeRegistry.SPELL_POWER);
        put(values, player, "fire_spell_power", AttributeRegistry.FIRE_SPELL_POWER);
        put(values, player, "ice_spell_power", AttributeRegistry.ICE_SPELL_POWER);
        put(values, player, "lightning_spell_power", AttributeRegistry.LIGHTNING_SPELL_POWER);
        put(values, player, "holy_spell_power", AttributeRegistry.HOLY_SPELL_POWER);
        put(values, player, "ender_spell_power", AttributeRegistry.ENDER_SPELL_POWER);
        put(values, player, "blood_spell_power", AttributeRegistry.BLOOD_SPELL_POWER);
        put(values, player, "evocation_spell_power", AttributeRegistry.EVOCATION_SPELL_POWER);
        put(values, player, "nature_spell_power", AttributeRegistry.NATURE_SPELL_POWER);
        put(values, player, "eldritch_spell_power", AttributeRegistry.ELDRITCH_SPELL_POWER);
        return values;
    }

    /**
     * Reads one attribute into the report.
     *
     * <p>Only {@link IllegalArgumentException} is caught, and the failure is recorded as
     * {@link Double#NaN} rather than {@code 0.0}. That distinction matters more here than
     * anywhere else in the mod: this report exists so a player can tell "the attribute is
     * genuinely zero" apart from "the bridge cannot see the attribute at all", and reporting a
     * failed read as {@code 0.0} would disguise the second as the first — the exact confusion
     * this command was added to end. {@link Error} and every other unchecked exception
     * propagate, so a linkage failure against a mismatched Iron's build surfaces as a command
     * error with a stack trace instead of a page of plausible-looking zeroes.
     */
    private static void put(Map<String, Double> values, Player player, String name,
                            Holder<Attribute> attribute) {
        try {
            values.put(name, player.getAttributeValue(attribute));
        } catch (IllegalArgumentException unregistered) {
            values.put(name, Double.NaN);
        }
    }
}
