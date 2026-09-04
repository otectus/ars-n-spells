package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractFilter;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellSchool;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The single authority for "what school is this glyph?".
 *
 * <p>Every ANS subsystem that cares about school — affinity, progression, scaling, cooldowns,
 * LP cost, UI — resolves through here, so they cannot disagree. They previously did:
 * {@code SpellAnalysis} classified with one heuristic and {@code SpellScalingUtil} then threw
 * that answer away and re-derived the element with a *different* substring test, so the Firework
 * glyph counted as "generic" for affinity but matched {@code "fire"} for scaling.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li><b>Explicit glyph mapping</b> ({@link SchoolMappings}) — an exact registry-id override.
 *       Datapack-extensible, so addon glyphs are handled by data rather than by growing a Java
 *       substring list. This wins over everything, including the addon's own metadata, because
 *       it is the pack author's deliberate correction.</li>
 *   <li><b>Ars Nouveau's {@code AbstractSpellPart.spellSchools}</b> — real metadata, populated
 *       in the {@code AbstractSpellPart} constructor for vanilla glyphs and explicitly by
 *       Ars Elemental. This is the source the heuristic should always have used.</li>
 *   <li><b>Registry-path substring heuristic</b> — last resort for a glyph that declares no
 *       school and has no mapping. Kept deliberately small; new addon support belongs in the
 *       data file, not here.</li>
 * </ol>
 *
 * <h2>Determinism</h2>
 * A glyph may declare several schools (Ars Elemental does this). The winner is the one earliest
 * in {@link SpellSchoolId}'s declaration order — a total, stable ordering — rather than whichever
 * the underlying collection happened to yield first. The old scaling code iterated a
 * {@code HashMap}, so a path matching two elements resolved by hash order.
 */
public final class SchoolResolver {

    private SchoolResolver() {}

    /**
     * Ars Nouveau school id → ANS canonical school.
     *
     * <p>Ars's vocabulary is a different axis from Iron's, so these are translations, not
     * renames, and a few are judgment calls: Ars {@code water} covers Freeze and the Ars
     * Elemental water line, which sit on Iron's {@code ice}; {@code abjuration} (warding) maps
     * to {@code holy}; {@code conjuration} (summoning) to {@code evocation}; {@code manipulation}
     * (telekinesis, blink) to {@code ender}. {@code air} and {@code earth} have no Iron's
     * counterpart at all and map to {@code lightning} and {@code nature} as the nearest
     * available power. {@code elemental} is the parent of the four sub-schools and is
     * deliberately unmapped — a glyph carrying only the parent has not said which element it is.
     *
     * <p>{@code necromancy} is not an Ars Nouveau school; it is a custom {@code SpellSchool}
     * that Ars Elemental and Ars Zero each construct with the same id, so one entry serves both
     * (lookup is by {@code getId()}). It maps to {@code eldritch}, which is where the heuristic
     * already sends wither/dark/hex/void: {@code blood} would feed undead charms into the Blood
     * track, and {@code evocation} is summoning rather than death magic.
     *
     * <p>All of these are overridable per-glyph through {@link SchoolMappings}.
     */
    private static final Map<String, SpellSchoolId> ARS_SCHOOL_TO_CANONICAL;

    static {
        Map<String, SpellSchoolId> m = new HashMap<>();
        m.put("fire", SpellSchoolId.FIRE);
        m.put("water", SpellSchoolId.ICE);
        m.put("air", SpellSchoolId.LIGHTNING);
        m.put("earth", SpellSchoolId.NATURE);
        m.put("abjuration", SpellSchoolId.HOLY);
        m.put("conjuration", SpellSchoolId.EVOCATION);
        m.put("manipulation", SpellSchoolId.ENDER);
        m.put("necromancy", SpellSchoolId.ELDRITCH);
        // "elemental" intentionally absent — see the javadoc above.
        ARS_SCHOOL_TO_CANONICAL = Collections.unmodifiableMap(m);
    }

    /**
     * Resolve the canonical school of a single glyph. Never null; {@link SpellSchoolId#GENERIC}
     * when unknown.
     *
     * <p>A thin adapter over {@link #resolve(String, List)}: it only extracts the registry id
     * and the declared Ars school ids. Keeping the decision logic in the pure overload is what
     * lets the whole resolution chain be unit-tested without an Ars runtime.
     */
    public static SpellSchoolId resolve(@Nullable AbstractSpellPart part) {
        if (part == null || part.getRegistryName() == null) {
            return SpellSchoolId.GENERIC;
        }
        if (isNonPayloadPart(part)) {
            return SpellSchoolId.GENERIC;
        }
        List<SpellSchool> declared = part.spellSchools;
        List<String> arsSchoolIds = new ArrayList<>();
        if (declared != null) {
            for (SpellSchool school : declared) {
                if (school != null && school.getId() != null) {
                    arsSchoolIds.add(school.getId());
                }
            }
        }
        return resolve(part.getRegistryName().toString(), arsSchoolIds);
    }

    /**
     * The resolution chain itself, expressed over plain data.
     *
     * @param registryId   full glyph registry id, e.g. {@code ars_nouveau:glyph_ignite}
     * @param arsSchoolIds the Ars school ids the glyph declares, possibly empty
     */
    public static SpellSchoolId resolve(@Nullable String registryId,
                                        @Nullable List<String> arsSchoolIds) {
        if (registryId == null || registryId.isEmpty()) {
            return SpellSchoolId.GENERIC;
        }

        SpellSchoolId mapped = SchoolMappings.get().glyphSchool(registryId);
        if (mapped != null) {
            return mapped;
        }

        int nameStart = registryId.indexOf(':');
        if (isNonPayloadPath(nameStart >= 0 ? registryId.substring(nameStart + 1) : registryId)) {
            return SpellSchoolId.GENERIC;
        }

        SpellSchoolId fromMetadata = fromArsSchools(arsSchoolIds);
        if (!fromMetadata.isGeneric()) {
            return fromMetadata;
        }

        int colon = registryId.indexOf(':');
        String path = colon >= 0 ? registryId.substring(colon + 1) : registryId;
        return heuristic(path);
    }

    /**
     * Translate declared Ars schools, picking the canonical value earliest in
     * {@link SpellSchoolId} declaration order so multi-school glyphs resolve deterministically.
     */
    private static SpellSchoolId fromArsSchools(@Nullable List<String> arsSchoolIds) {
        if (arsSchoolIds == null || arsSchoolIds.isEmpty()) {
            return SpellSchoolId.GENERIC;
        }
        SpellSchoolId best = SpellSchoolId.GENERIC;
        for (String rawId : arsSchoolIds) {
            if (rawId == null) {
                continue;
            }
            String arsId = rawId.toLowerCase(Locale.ROOT);
            SpellSchoolId candidate = SchoolMappings.get().arsSchool(arsId);
            if (candidate == null) {
                candidate = ARS_SCHOOL_TO_CANONICAL.get(arsId);
            }
            if (candidate != null && !candidate.isGeneric() && candidate.ordinal() < best.ordinal()) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Whether the part only shapes a cast rather than delivering one.
     *
     * <p>Augments, cast methods and filters carry declared schools of their own — Ars
     * Elemental's {@code glyph_aquatic_filter} declares water, {@code glyph_fiery_filter} fire —
     * and those are statements about what the glyph <em>targets</em>, not about the damage the
     * spell deals. Letting them through made a fire spell filtered to aquatic mobs resolve ICE.
     * Ars Zero's forms and its eight augments fall out the same way. A spell's school comes from
     * its effects; everything else is {@link SpellSchoolId#GENERIC} and earns no affinity.
     */
    private static boolean isNonPayloadPart(AbstractSpellPart part) {
        return part instanceof AbstractAugment
            || part instanceof AbstractCastMethod
            || part instanceof AbstractFilter;
    }

    /**
     * The name-shaped equivalent of {@link #isNonPayloadPart}, so the pure overload and the
     * typed one give the same answer for the same glyph. Covers both id conventions in play:
     * Ars Nouveau and Ars Elemental prefix with {@code glyph_}, Ars Zero does not.
     */
    private static boolean isNonPayloadPath(String rawPath) {
        String path = rawPath.toLowerCase(Locale.ROOT);
        if (path.startsWith("glyph_")) {
            path = path.substring("glyph_".length());
        }
        return path.endsWith("_filter") || path.startsWith("augment_") || path.endsWith("_form");
    }

    /**
     * Last-resort keyword match on the registry path.
     *
     * <p>Only reached when a glyph declares no school and has no mapping — which for current
     * Ars Nouveau and Ars Elemental content means almost never. Do not grow this to support a
     * new addon; add entries to the glyph-school data file instead, which is the whole point of
     * {@link SchoolMappings}.
     */
    private static SpellSchoolId heuristic(String rawPath) {
        String path = rawPath.toLowerCase(Locale.ROOT);
        // "lightning" is tested before "light" so Lightning is never classified as holy, and
        // "firework" is excluded explicitly because it contains "fire".
        if (path.contains("lightning") || path.contains("shock") || path.contains("storm")) {
            return SpellSchoolId.LIGHTNING;
        }
        if (!path.contains("firework")
            && (path.contains("fire") || path.contains("ignite") || path.contains("flare")
                || path.contains("burn") || path.contains("plasma"))) {
            return SpellSchoolId.FIRE;
        }
        if (path.contains("ice") || path.contains("freeze") || path.contains("frost")
            || path.contains("cold") || path.contains("water") || path.contains("aqua")) {
            return SpellSchoolId.ICE;
        }
        if (path.contains("heal") || path.contains("holy") || path.contains("light")) {
            return SpellSchoolId.HOLY;
        }
        if (path.contains("ender") || path.contains("blink") || path.contains("warp")
            || path.contains("teleport") || path.contains("rift")) {
            return SpellSchoolId.ENDER;
        }
        if (path.contains("blood") || path.contains("drain") || path.contains("vampire")) {
            return SpellSchoolId.BLOOD;
        }
        if (path.contains("fang") || path.contains("evocation") || path.contains("summon")) {
            return SpellSchoolId.EVOCATION;
        }
        if (path.contains("grow") || path.contains("nature") || path.contains("plant")
            || path.contains("harvest") || path.contains("earth") || path.contains("crush")) {
            return SpellSchoolId.NATURE;
        }
        if (path.contains("wither") || path.contains("dark") || path.contains("hex")
            || path.contains("eldritch") || path.contains("void")) {
            return SpellSchoolId.ELDRITCH;
        }
        return SpellSchoolId.GENERIC;
    }
}
