package com.otectus.arsnspells.util;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
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
 */
public final class SchoolMappings {

    private static volatile SchoolMappings current = new SchoolMappings(
        builtinGlyphSchools(), Collections.emptyMap());

    private final Map<String, SpellSchoolId> glyphSchools;
    private final Map<String, SpellSchoolId> arsSchools;

    private SchoolMappings(Map<String, SpellSchoolId> glyphSchools,
                           Map<String, SpellSchoolId> arsSchools) {
        this.glyphSchools = Collections.unmodifiableMap(glyphSchools);
        this.arsSchools = Collections.unmodifiableMap(arsSchools);
    }

    /** The active snapshot. */
    public static SchoolMappings get() {
        return current;
    }

    /**
     * Replace the active mappings with the built-ins plus {@code overlay} entries.
     *
     * @param glyphOverlay full glyph registry id (e.g. {@code ars_elemental:glyph_water_grave})
     *                     → school id
     * @param arsSchoolOverlay Ars school id (e.g. {@code water}) → school id
     */
    public static void applyOverlay(Map<String, SpellSchoolId> glyphOverlay,
                                    Map<String, SpellSchoolId> arsSchoolOverlay) {
        Map<String, SpellSchoolId> glyphs = new HashMap<>(builtinGlyphSchools());
        if (glyphOverlay != null) {
            glyphOverlay.forEach((k, v) -> {
                if (k != null && v != null) {
                    glyphs.put(k.toLowerCase(Locale.ROOT), v);
                }
            });
        }
        Map<String, SpellSchoolId> ars = new HashMap<>();
        if (arsSchoolOverlay != null) {
            arsSchoolOverlay.forEach((k, v) -> {
                if (k != null && v != null) {
                    ars.put(k.toLowerCase(Locale.ROOT), v);
                }
            });
        }
        current = new SchoolMappings(glyphs, ars);
    }

    /** Reset to shipped defaults. Used on server shutdown and by tests. */
    public static void reset() {
        current = new SchoolMappings(builtinGlyphSchools(), Collections.emptyMap());
    }

    /** Explicit school for a full glyph registry id, or null if unmapped. */
    @Nullable
    public SpellSchoolId glyphSchool(String registryId) {
        return registryId == null ? null : glyphSchools.get(registryId.toLowerCase(Locale.ROOT));
    }

    /** Override for an Ars school id, or null to use the built-in translation. */
    @Nullable
    public SpellSchoolId arsSchool(String arsSchoolId) {
        return arsSchoolId == null ? null : arsSchools.get(arsSchoolId.toLowerCase(Locale.ROOT));
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
    private static Map<String, SpellSchoolId> builtinGlyphSchools() {
        Map<String, SpellSchoolId> m = new HashMap<>();
        m.put("ars_nouveau:glyph_firework", SpellSchoolId.GENERIC);
        // Declares no Ars school but is unambiguously Iron's-side lightning/ender/eldritch.
        m.put("ars_nouveau:glyph_lightning", SpellSchoolId.LIGHTNING);
        m.put("ars_nouveau:glyph_blink", SpellSchoolId.ENDER);
        m.put("ars_nouveau:glyph_ender_inventory", SpellSchoolId.ENDER);
        m.put("ars_nouveau:glyph_wither", SpellSchoolId.ELDRITCH);
        m.put("ars_nouveau:glyph_hex", SpellSchoolId.ELDRITCH);
        m.put("ars_nouveau:glyph_fangs", SpellSchoolId.EVOCATION);
        m.put("ars_nouveau:glyph_summon_undead", SpellSchoolId.EVOCATION);
        m.put("ars_nouveau:glyph_heal", SpellSchoolId.HOLY);
        m.put("ars_nouveau:glyph_light", SpellSchoolId.HOLY);

        // Ars Elemental 0.6.8.0. Everything else it ships resolves correctly from its own
        // declared schools, including glyph_conjure_terrain, which declares conjuration and
        // earth and lands on NATURE through the declaration-order tie-break.
        // Life Link declares conjuration (it summons the link) but its payload is a drain.
        m.put("ars_elemental:glyph_life_link", SpellSchoolId.BLOOD);
        // The propagators chain a spell onward; they carry no payload of their own.
        m.put("ars_elemental:glyph_propagator_arc", SpellSchoolId.GENERIC);
        m.put("ars_elemental:glyph_propagator_homing", SpellSchoolId.GENERIC);

        // Ars Zero 2.0.2. Its ids carry no glyph_ prefix. effect_geometrize (earth),
        // push_effect (air), conjure_voxel_effect / zero_gravity_effect / effect_beam
        // (manipulation) and effect_conjure_blight (necromancy) all resolve from metadata.
        // effect_windshear is a lang-only id in 2.0.2: Ars Zero registers no such glyph, and
        // the class it references is Ars Nouveau's own EffectWindshear (ELEMENTAL_AIR).
        // A ward, like Ars's abjuration line, but it declares manipulation and would read ENDER.
        m.put("ars_zero:conjure_arcane_shield_effect", SpellSchoolId.HOLY);
        // Multi-phase control flow. These all declare manipulation, which would earn ender
        // affinity for glyphs that only decide when and where the rest of the spell runs.
        m.put("ars_zero:anchor_effect", SpellSchoolId.GENERIC);
        m.put("ars_zero:select_effect", SpellSchoolId.GENERIC);
        m.put("ars_zero:sustain_effect", SpellSchoolId.GENERIC);
        m.put("ars_zero:discard_effect", SpellSchoolId.GENERIC);
        m.put("ars_zero:effect_convergence", SpellSchoolId.GENERIC);
        m.put("ars_zero:enlarge_effect", SpellSchoolId.GENERIC);
        return m;
    }
}
