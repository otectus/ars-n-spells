package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.SchoolKeys;
import com.otectus.arsnspells.util.SpellSchoolId;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The explicit mechanical school a player can give a custom Ars spell, and the one place that
 * decides whether a requested or stored school may be used.
 *
 * <p><b>Two fields, never confused.</b> {@link CrossCastNbt#TAG_NATURE} is a cosmetic wheel
 * background and is never read here. {@link CrossCastNbt#TAG_SCHOOL} is mechanical: when present
 * and usable it replaces the schools the glyphs resolve to for Ars 'n Spells' own school-keyed
 * mechanics, which are spell-power scaling, affinity, progression and school-matched discounts.
 * Glyph effects, damage types and cooldown categories always come from the glyphs.
 *
 * <p><b>Automatic</b> is the absence of the field. Explicit mode is exactly one namespaced Iron's
 * school id, so the schools the glyphs would have given add nothing on top of it.
 *
 * <p><b>Validated at request and again at use.</b> A request is checked when it is made (the
 * Spell Loom, a command). A stored value is checked again at every cast: if its addon has been
 * removed, or the server has turned overrides off, the spell casts as Automatic and the item is
 * left alone, so the choice applies again when the school or the setting returns.
 *
 * <p><b>Carried by the cast.</b> {@link #stamp} writes the validated school into the cast's own
 * {@code SpellContext.tag}. Ars copies that tag into every child context, projectile resolver and
 * delayed event the cast spawns, so a hit that lands later is attributed to the school of the
 * cast that fired it, not to whatever the caster has cast or held since.
 */
public final class SpellSchoolChoice {

    /** The packet and command spelling of Automatic. Never stored. */
    public static final String AUTOMATIC = "auto";

    /** {@code SpellContext.tag} key holding the validated explicit school of one cast. */
    public static final String CONTEXT_KEY = "ars_n_spells:mechanical_school";

    /** What a requested selection amounts to. */
    public enum Verdict {
        /** No explicit school: the glyphs decide. Always allowed. */
        AUTOMATIC,
        /** A well-formed, installed school on a server that allows overrides. */
        ACCEPTED,
        /** Not a namespaced school id, or the generic non-school. */
        MALFORMED,
        /** Well-formed, but no installed Iron's school with a spell-power attribute has this id. */
        NOT_INSTALLED,
        /** {@code allow_spell_school_override} is off on this server. */
        DISABLED;

        /** Whether an operation asking for this selection may proceed. */
        public boolean usable() {
            return this == AUTOMATIC || this == ACCEPTED;
        }
    }

    private SpellSchoolChoice() {}

    /**
     * The canonical form of a requested or stored selection: {@code ""} for Automatic (null,
     * blank or {@code auto}), otherwise the lower-cased, namespaced school key. A bare built-in
     * name such as {@code fire} becomes {@code irons_spellbooks:fire}; anything else keeps its
     * own namespace, so an addon school is never folded into a built-in one.
     */
    public static String normalize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || AUTOMATIC.equals(trimmed)) {
            return "";
        }
        return SchoolKeys.normalize(trimmed);
    }

    /** True for a syntactically valid namespaced school id that is not the generic non-school. */
    public static boolean isWellFormed(@Nullable String key) {
        return key != null && SchoolKeys.isNamespaced(key) && !SchoolKeys.GENERIC.equals(key);
    }

    /**
     * True when {@code key} names an Iron's school installed here that has a spell-power
     * attribute: the attribute is what scaling and progression act on, so a school without one
     * could not mean anything mechanically.
     */
    public static boolean isInstalled(@Nullable String key) {
        return isWellFormed(key) && IronsCompat.isLoaded()
            && com.otectus.arsnspells.compat.IronsSchoolAttributes.power(key) != null;
    }

    /**
     * Whether this server honours explicit schools. Read defensively: a config that is not
     * loaded yet, or one Forge is reloading, answers with the shipped default.
     */
    public static boolean overridesAllowed() {
        try {
            Boolean value = AnsConfig.ALLOW_SPELL_SCHOOL_OVERRIDE == null
                ? null : AnsConfig.ALLOW_SPELL_SCHOOL_OVERRIDE.get();
            return value == null || value;
        } catch (IllegalStateException | NullPointerException notLoaded) {
            return true;
        }
    }

    /** Judge a request against the live config and the installed schools. */
    public static Verdict check(@Nullable String requested) {
        return check(requested, overridesAllowed(), SpellSchoolChoice::isInstalled);
    }

    /**
     * The decision itself, over plain inputs so it can be unit-tested without a registry. A
     * malformed value is reported as malformed even when overrides are off, so a bad request is
     * never mistaken for a policy refusal.
     */
    public static Verdict check(@Nullable String requested, boolean overridesAllowed,
                                Predicate<String> installed) {
        String key = normalize(requested);
        if (key.isEmpty()) {
            return Verdict.AUTOMATIC;
        }
        if (!isWellFormed(key)) {
            return Verdict.MALFORMED;
        }
        if (!overridesAllowed) {
            return Verdict.DISABLED;
        }
        return installed.test(key) ? Verdict.ACCEPTED : Verdict.NOT_INSTALLED;
    }

    /**
     * The explicit school a cast of an entry storing {@code stored} must use, or {@code null}
     * for Automatic. A stored value that is malformed, not installed or disallowed is ignored,
     * never repaired: the item keeps it.
     */
    @Nullable
    public static String effective(@Nullable String stored) {
        return effective(stored, overridesAllowed(), SpellSchoolChoice::isInstalled);
    }

    /** {@link #effective(String)} over plain inputs, for unit tests. */
    @Nullable
    public static String effective(@Nullable String stored, boolean overridesAllowed,
                                   Predicate<String> installed) {
        return check(stored, overridesAllowed, installed) == Verdict.ACCEPTED ? normalize(stored) : null;
    }

    /**
     * Every school a player may choose here, built-ins first in their usual order and then any
     * addon school registered with Iron's, by id. Empty without Iron's.
     */
    public static List<String> supportedSchools() {
        if (!IronsCompat.isLoaded()) {
            return List.of();
        }
        List<String> schools = new ArrayList<>();
        for (SpellSchoolId builtin : SpellSchoolId.values()) {
            String key = SchoolKeys.normalize(builtin.id());
            if (!builtin.isGeneric() && isInstalled(key)) {
                schools.add(key);
            }
        }
        List<String> registered = new ArrayList<>(
            com.otectus.arsnspells.compat.irons_spells.SchoolIndex.allSchools());
        registered.sort(String::compareTo);
        for (String raw : registered) {
            String key = normalize(raw);
            if (!schools.contains(key) && isInstalled(key)) {
                schools.add(key);
            }
        }
        return List.copyOf(schools);
    }

    /** Carry {@code school} on {@code context}; a null or empty school leaves the cast Automatic. */
    public static void stamp(@Nullable SpellContext context, @Nullable String school) {
        if (context != null && context.tag != null && school != null && !school.isEmpty()) {
            context.tag.putString(CONTEXT_KEY, school);
        }
    }

    /**
     * The explicit school a cast carries, or {@code null} when it is Automatic. Native Ars casts
     * and Iron's casts never carry one, because only an ANS cross-cast stamps it.
     */
    @Nullable
    public static String fromContext(@Nullable SpellContext context) {
        if (context == null || context.tag == null || !context.tag.contains(CONTEXT_KEY, Tag.TAG_STRING)) {
            return null;
        }
        String key = context.tag.getString(CONTEXT_KEY);
        return isWellFormed(key) ? key : null;
    }
}
