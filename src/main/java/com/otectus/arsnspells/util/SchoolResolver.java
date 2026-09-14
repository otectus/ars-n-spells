package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellSchool;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 *
 * <h2>One school or all of them</h2>
 * {@link #resolve} answers "which single school is this glyph?", which is what affinity,
 * progression, cooldowns and the UI need — one glyph, one credited track. {@link #resolveAll}
 * answers "which schools does this glyph belong to at all?", which is what damage scaling needs:
 * a dual-element or compound-element addon glyph really does carry several, and collapsing it to
 * one silently discarded the rest. The two share a single resolution chain, so they can never
 * disagree: {@code resolve} is the deterministic winner among {@code resolveAll}'s answers.
 */
public final class SchoolResolver {

    private SchoolResolver() {}
    private static boolean isNonPayloadPart(AbstractSpellPart part) {
        return part instanceof com.hollingsworth.arsnouveau.api.spell.AbstractAugment
            || part instanceof com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod
            || part instanceof com.hollingsworth.arsnouveau.api.spell.AbstractFilter;
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
        // "elemental" intentionally absent — see the javadoc above. It is NOT expanded to its
        // four child elements even now that resolveAll can return several schools: under the
        // MAX aggregation policy that would let any generic-elemental glyph claim whichever of
        // the caster's fire/ice/lightning/nature powers is highest, which is a balance change
        // rather than a compatibility fix. A pack that wants that can opt in per glyph or per
        // school through SchoolMappings.
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
        if (part != null && isNonPayloadPart(part)) return SpellSchoolId.GENERIC;
        return resolve(registryIdOf(part), declaredArsSchoolsOf(part));
    }

    /**
     * The resolution chain itself, expressed over plain data.
     *
     * <p>Defined as the deterministic winner among {@link #resolveAll(String, List)}: the school
     * earliest in {@link SpellSchoolId} declaration order, or {@link SpellSchoolId#GENERIC} when
     * that set is empty. Declaration order of the glyph's own schools is deliberately *not* the
     * tie-break here — two addons declaring the same pair in opposite orders must still credit
     * the same affinity track.
     *
     * @param registryId   full glyph registry id, e.g. {@code ars_nouveau:glyph_ignite}
     * @param arsSchoolIds the Ars school ids the glyph declares, possibly empty
     */
    public static SpellSchoolId resolve(@Nullable String registryId,
                                        @Nullable List<String> arsSchoolIds) {
        SpellSchoolId best = SpellSchoolId.GENERIC;
        for (SpellSchoolId candidate : resolveAll(registryId, arsSchoolIds)) {
            if (candidate.ordinal() < best.ordinal()) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Resolve <em>every</em> canonical school a glyph belongs to.
     *
     * <p>Same three sources in the same order as {@link #resolve}, and the same source wins
     * outright — the difference is only that every school that source names is translated,
     * rather than just the winning one. Ars addons declare dual elements (Ars Elemental) and
     * compound elements, and damage scaling has to see the whole set to apply the configured
     * multi-school policy to it.
     *
     * <p>The returned set is ordered by declaration and never contains
     * {@link SpellSchoolId#GENERIC}: "generic" is the absence of a school, so it is expressed as
     * an empty set. Empty is therefore a normal, meaningful result.
     */
    public static Set<SpellSchoolId> resolveAll(@Nullable AbstractSpellPart part) {
        if (part != null && isNonPayloadPart(part)) return Collections.emptySet();
        return resolveAll(registryIdOf(part), declaredArsSchoolsOf(part));
    }

    /**
     * The multi-school resolution chain over plain data, so it is unit-testable without an Ars
     * runtime. See {@link #resolveAll(AbstractSpellPart)} for the contract.
     */
    public static Set<SpellSchoolId> resolveAll(@Nullable String registryId,
                                                @Nullable List<String> arsSchoolIds) {
        if (registryId == null || registryId.isEmpty()) {
            return Collections.emptySet();
        }

        int nameStart = registryId.indexOf(':');
        if (isNonPayloadPath(nameStart < 0 ? registryId : registryId.substring(nameStart + 1))) return Collections.emptySet();

        // An explicit mapping is the pack author's deliberate correction and is authoritative:
        // it stops resolution even when it names nothing but "generic", which is how a pack says
        // "this glyph has no school" (glyph_firework).
        List<SpellSchoolId> mapped = SchoolMappings.get().glyphSchoolsAll(registryId);
        if (mapped != null) {
            return toSchoolSet(mapped);
        }

        Set<SpellSchoolId> fromMetadata = fromArsSchools(arsSchoolIds);
        if (!fromMetadata.isEmpty()) {
            return fromMetadata;
        }

        int colon = registryId.indexOf(':');
        String path = colon >= 0 ? registryId.substring(colon + 1) : registryId;
        return toSchoolSet(List.of(heuristic(path)));
    }

    /** Namespaced ordered memberships; cosmetic metadata is never consulted. */
    public static List<String> resolveKeys(@Nullable AbstractSpellPart part) {
        if (part == null || part.getRegistryName() == null || isNonPayloadPart(part))
            return List.of(SchoolKeys.GENERIC);
        List<String> declared = new ArrayList<>();
        if (part.spellSchools != null) for (SpellSchool school : part.spellSchools) {
            if (school != null && school.getId() != null) declared.add(school.getId());
        }
        return resolveKeys(part.getRegistryName().toString(), declared);
    }

    public static List<String> resolveKeys(@Nullable String registryId, @Nullable List<String> declared) {
        if (registryId == null || registryId.isEmpty()) return List.of(SchoolKeys.GENERIC);
        int colon = registryId.indexOf(':');
        String path = colon < 0 ? registryId : registryId.substring(colon + 1);
        if (isNonPayloadPath(path)) return List.of(SchoolKeys.GENERIC);
        SchoolMappings snapshot = SchoolMappings.get();
        List<String> mapped = snapshot.glyphSchoolKeys(registryId);
        if (mapped != null) return mapped;
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        if (declared != null) for (String raw : declared) {
            if (raw == null) continue;
            List<String> overlay = snapshot.arsSchoolKeys(raw);
            if (overlay != null) result.addAll(overlay);
            else {
                SpellSchoolId builtin = ARS_SCHOOL_TO_CANONICAL.get(raw.toLowerCase(Locale.ROOT));
                if (builtin != null) result.add(SchoolKeys.normalize(builtin.id()));
                // Untranslated Ars schools are a different vocabulary, not Iron's school IDs.
            }
        }
        return result.isEmpty() ? List.of(SchoolKeys.normalize(heuristic(path).id())) : List.copyOf(result);
    }

    /** Full registry id of a glyph, or null when it has none. */
    @Nullable
    private static String registryIdOf(@Nullable AbstractSpellPart part) {
        if (part == null || part.getRegistryName() == null) {
            return null;
        }
        return part.getRegistryName().toString();
    }

    /**
     * The Ars school ids a glyph declares, in declaration order. Extracted here so the whole
     * resolution chain can be exercised through the plain-data overloads.
     */
    private static List<String> declaredArsSchoolsOf(@Nullable AbstractSpellPart part) {
        List<String> arsSchoolIds = new ArrayList<>();
        if (part == null) {
            return arsSchoolIds;
        }
        List<SpellSchool> declared = part.spellSchools;
        if (declared != null) {
            for (SpellSchool school : declared) {
                if (school != null && school.getId() != null) {
                    arsSchoolIds.add(school.getId());
                }
            }
        }
        return arsSchoolIds;
    }

    /**
     * Translate every declared Ars school, keeping declaration order and dropping anything that
     * does not name a canonical school (an unrecognised id, or the parent {@code elemental}).
     */
    private static Set<SpellSchoolId> fromArsSchools(@Nullable List<String> arsSchoolIds) {
        if (arsSchoolIds == null || arsSchoolIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<SpellSchoolId> resolved = new LinkedHashSet<>();
        for (String rawId : arsSchoolIds) {
            if (rawId == null) {
                continue;
            }
            String arsId = rawId.toLowerCase(Locale.ROOT);
            List<SpellSchoolId> candidates = SchoolMappings.get().arsSchoolsAll(arsId);
            if (candidates == null) {
                SpellSchoolId builtin = ARS_SCHOOL_TO_CANONICAL.get(arsId);
                candidates = builtin == null ? List.of() : List.of(builtin);
            }
            for (SpellSchoolId candidate : candidates) {
                if (candidate != null && !candidate.isGeneric()) {
                    resolved.add(candidate);
                }
            }
        }
        return resolved;
    }

    /** Ordered, GENERIC-free view of a resolved school list. */
    private static Set<SpellSchoolId> toSchoolSet(List<SpellSchoolId> schools) {
        Set<SpellSchoolId> out = new LinkedHashSet<>();
        for (SpellSchoolId school : schools) {
            if (school != null && !school.isGeneric()) {
                out.add(school);
            }
        }
        return out;
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
