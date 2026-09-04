package com.otectus.arsnspells.util;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Overridable glyph → school and Ars-school → school mappings, so supporting a new addon is a
 * data change rather than another branch in a substring heuristic.
 *
 * <p>Ships a built-in default set and accepts an overlay loaded from datapacks by
 * {@link com.otectus.arsnspells.data.GlyphSchoolReloadListener}. Overlay entries win, and the
 * built-ins are never mutated, so a pack that supplies a bad file degrades to shipped
 * behaviour rather than to nothing.
 *
 * <p>The instance is swapped atomically on reload; readers take a snapshot via {@link #get()}
 * and never observe a half-applied map.
 *
 * <p>A mapping value is a <em>list</em> of schools, because a glyph may legitimately be more than
 * one (dual and compound elements). The single-school accessors return the first entry, so a pack
 * that supplies one school per key behaves exactly as it did before lists existed.
 */
public final class SchoolMappings {

    private static volatile SchoolMappings current = new SchoolMappings(
        builtinGlyphSchools(), Collections.emptyMap());

    private final Map<String, List<SpellSchoolId>> glyphSchools;
    private final Map<String, List<SpellSchoolId>> arsSchools;

    private SchoolMappings(Map<String, List<SpellSchoolId>> glyphSchools,
                           Map<String, List<SpellSchoolId>> arsSchools) {
        this.glyphSchools = Collections.unmodifiableMap(glyphSchools);
        this.arsSchools = Collections.unmodifiableMap(arsSchools);
    }

    /** The active snapshot. */
    public static SchoolMappings get() {
        return current;
    }

    /**
     * Replace the active mappings with the built-ins plus single-school {@code overlay} entries.
     *
     * <p>The one-school-per-key form, kept because most overrides are exactly that and because it
     * is what callers written before multi-school support use.
     *
     * @param glyphOverlay full glyph registry id (e.g. {@code ars_elemental:glyph_water_grave})
     *                     → school id
     * @param arsSchoolOverlay Ars school id (e.g. {@code water}) → school id
     */
    public static void applyOverlay(Map<String, SpellSchoolId> glyphOverlay,
                                    Map<String, SpellSchoolId> arsSchoolOverlay) {
        applyMultiOverlay(toLists(glyphOverlay), toLists(arsSchoolOverlay));
    }

    /**
     * Replace the active mappings with the built-ins plus multi-school {@code overlay} entries.
     *
     * <p>The order of each value list is preserved: it is the order the pack author wrote, and it
     * decides which of a glyph's schools is the primary one.
     *
     * @param glyphOverlay full glyph registry id → the schools that glyph counts as
     * @param arsSchoolOverlay Ars school id → the schools it translates to
     */
    public static void applyMultiOverlay(Map<String, List<SpellSchoolId>> glyphOverlay,
                                         Map<String, List<SpellSchoolId>> arsSchoolOverlay) {
        Map<String, List<SpellSchoolId>> glyphs = new HashMap<>(builtinGlyphSchools());
        copyInto(glyphOverlay, glyphs);
        Map<String, List<SpellSchoolId>> ars = new HashMap<>();
        copyInto(arsSchoolOverlay, ars);
        current = new SchoolMappings(glyphs, ars);
    }

    /** Widen a one-school-per-key overlay to the list form the mappings store. */
    private static Map<String, List<SpellSchoolId>> toLists(
            @Nullable Map<String, SpellSchoolId> single) {
        if (single == null) {
            return Collections.emptyMap();
        }
        Map<String, List<SpellSchoolId>> out = new LinkedHashMap<>();
        single.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k, List.of(v));
            }
        });
        return out;
    }

    /** Lower-case the keys, drop nulls and duplicates, and freeze each value list. */
    private static void copyInto(@Nullable Map<String, List<SpellSchoolId>> overlay,
                                 Map<String, List<SpellSchoolId>> sink) {
        if (overlay == null) {
            return;
        }
        overlay.forEach((k, v) -> {
            if (k == null || v == null || v.isEmpty()) {
                return;
            }
            List<SpellSchoolId> schools = new ArrayList<>();
            for (SpellSchoolId school : v) {
                if (school != null && !schools.contains(school)) {
                    schools.add(school);
                }
            }
            if (!schools.isEmpty()) {
                sink.put(k.toLowerCase(Locale.ROOT), Collections.unmodifiableList(schools));
            }
        });
    }

    /** Reset to shipped defaults. Used on server shutdown and by tests. */
    public static void reset() {
        current = new SchoolMappings(builtinGlyphSchools(), Collections.emptyMap());
    }

    /** Explicit school for a full glyph registry id, or null if unmapped. */
    @Nullable
    public SpellSchoolId glyphSchool(String registryId) {
        return first(glyphSchoolsAll(registryId));
    }

    /**
     * Every explicit school for a full glyph registry id, in declaration order, or null if the
     * glyph is unmapped.
     *
     * <p>Null and "mapped to generic" are different answers: null means no override exists and
     * resolution should continue, while an override naming {@code generic} is a deliberate
     * statement that the glyph has no school.
     */
    @Nullable
    public List<SpellSchoolId> glyphSchoolsAll(String registryId) {
        return registryId == null ? null : glyphSchools.get(registryId.toLowerCase(Locale.ROOT));
    }

    /** Override for an Ars school id, or null to use the built-in translation. */
    @Nullable
    public SpellSchoolId arsSchool(String arsSchoolId) {
        return first(arsSchoolsAll(arsSchoolId));
    }

    /** Every override for an Ars school id, in declaration order, or null if not overridden. */
    @Nullable
    public List<SpellSchoolId> arsSchoolsAll(String arsSchoolId) {
        return arsSchoolId == null ? null : arsSchools.get(arsSchoolId.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private static SpellSchoolId first(@Nullable List<SpellSchoolId> schools) {
        return schools == null || schools.isEmpty() ? null : schools.get(0);
    }

    public int glyphMappingCount() {
        return glyphSchools.size();
    }

    /**
     * Shipped corrections, kept deliberately short.
     *
     * <p>Only glyphs whose declared Ars school is absent or actively misleading belong here.
     * Everything else resolves from {@code AbstractSpellPart.spellSchools}, which vanilla Ars
     * and Ars Elemental both populate, so this map does not need to grow with content.
     *
     * <p>{@code glyph_firework} is the standing example: it declares no school and its path
     * contains "fire", which the old heuristic matched — a decorative glyph classified as fire.
     */
    private static Map<String, List<SpellSchoolId>> builtinGlyphSchools() {
        Map<String, List<SpellSchoolId>> m = new HashMap<>();
        m.put("ars_nouveau:glyph_firework", List.of(SpellSchoolId.GENERIC));
        // Declares no Ars school but is unambiguously Iron's-side lightning/ender/eldritch.
        m.put("ars_nouveau:glyph_lightning", List.of(SpellSchoolId.LIGHTNING));
        m.put("ars_nouveau:glyph_blink", List.of(SpellSchoolId.ENDER));
        m.put("ars_nouveau:glyph_ender_inventory", List.of(SpellSchoolId.ENDER));
        m.put("ars_nouveau:glyph_wither", List.of(SpellSchoolId.ELDRITCH));
        m.put("ars_nouveau:glyph_hex", List.of(SpellSchoolId.ELDRITCH));
        m.put("ars_nouveau:glyph_fangs", List.of(SpellSchoolId.EVOCATION));
        m.put("ars_nouveau:glyph_summon_undead", List.of(SpellSchoolId.EVOCATION));
        m.put("ars_nouveau:glyph_heal", List.of(SpellSchoolId.HOLY));
        m.put("ars_nouveau:glyph_light", List.of(SpellSchoolId.HOLY));
        return m;
    }
}
