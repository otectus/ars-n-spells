package com.otectus.arsnspells.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.util.SchoolMappings;
import com.otectus.arsnspells.util.SpellSchoolId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads glyph → school overrides from datapacks, so supporting a new Ars addon is a data change
 * instead of another branch in a Java substring heuristic.
 *
 * <p>Reads every JSON under {@code data/<namespace>/ans_glyph_schools/}. Each file may contain
 * either or both of:
 *
 * <pre>{@code
 * {
 *   "glyphs": {
 *     "ars_elemental:glyph_water_grave": "ice",
 *     "toomanyglyphs:glyph_gravity":     "nature",
 *     "ars_elemental:glyph_conjure_water": ["ice", "nature"]
 *   },
 *   "ars_schools": {
 *     "water": "ice"
 *   }
 * }
 * }</pre>
 *
 * <p>{@code glyphs} keys are full glyph registry ids and override that one glyph.
 * {@code ars_schools} keys are Ars Nouveau school ids ({@code fire}, {@code water}, {@code air},
 * {@code earth}, {@code abjuration}, {@code conjuration}, {@code manipulation}) and re-aim an
 * entire school at once. Values are ANS canonical school ids: {@code fire ice lightning nature
 * holy ender blood evocation eldritch generic}. A value may be a single id or an array of them:
 * a glyph really can be several schools at once (dual and compound elements). All entries scale
 * damage under the configured multi-school policy; which one is primary (and so credits affinity)
 * stays {@code SchoolResolver}'s deterministic choice, not the array order.
 *
 * <p>Unparseable entries are skipped with a warning rather than failing the reload — one typo in
 * one pack must not take the server's data load down. Entries always layer on top of the shipped
 * defaults, so a pack can correct ANS without having to restate everything it got right.
 */
public class GlyphSchoolReloadListener extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphSchoolReloadListener.class);
    private static final Gson GSON = new Gson();

    /** Datapack folder scanned for override files. */
    public static final String FOLDER = "ans_glyph_schools";

    public GlyphSchoolReloadListener() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, List<SpellSchoolId>> glyphs = new HashMap<>();
        Map<String, List<SpellSchoolId>> arsSchools = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            if (!file.getValue().isJsonObject()) {
                LOGGER.warn("Skipping glyph-school file {}: expected a JSON object", file.getKey());
                continue;
            }
            JsonObject root = file.getValue().getAsJsonObject();
            readSection(file.getKey(), root, "glyphs", glyphs);
            readSection(file.getKey(), root, "ars_schools", arsSchools);
        }

        SchoolMappings.applyMultiOverlay(glyphs, arsSchools);
        if (!glyphs.isEmpty() || !arsSchools.isEmpty()) {
            LOGGER.info("Loaded {} glyph and {} Ars-school overrides from datapacks",
                glyphs.size(), arsSchools.size());
        }
    }

    private static void readSection(ResourceLocation source, JsonObject root, String section,
                                    Map<String, List<SpellSchoolId>> sink) {
        if (!root.has(section) || !root.get(section).isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(section).entrySet()) {
            String key = entry.getKey();
            List<SpellSchoolId> schools = parseSchools(entry.getValue(), section + "." + key,
                String.valueOf(source));
            if (!schools.isEmpty()) {
                sink.put(key.toLowerCase(Locale.ROOT), schools);
            }
        }
    }

    /**
     * Parse one mapping value: either a single school id string or an array of them.
     *
     * <p>The array form exists because a glyph can genuinely be more than one school — addon
     * dual and compound elements — and a pack had no way to say so. A single string keeps
     * behaving exactly as before, so existing files are unaffected.
     *
     * <p>Bad entries are skipped with a warning rather than failing the reload; within an array
     * only the offending element is dropped, so one typo does not discard the whole mapping.
     * Package-visible and free of Minecraft types so the parse can be unit-tested.
     *
     * @param label where the value came from, for the warning text only
     * @param source the file the value came from, for the warning text only
     * @return the parsed schools in declaration order, empty when nothing usable was found
     */
    static List<SpellSchoolId> parseSchools(@Nullable JsonElement value, String label,
                                            String source) {
        List<SpellSchoolId> schools = new ArrayList<>();
        if (value == null) {
            return schools;
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (JsonElement element : array) {
                addSchool(element, label, source, schools);
            }
            return schools;
        }
        addSchool(value, label, source, schools);
        return schools;
    }

    private static void addSchool(@Nullable JsonElement element, String label, String source,
                                  List<SpellSchoolId> sink) {
        if (element == null || !element.isJsonPrimitive()) {
            LOGGER.warn("Skipping {} in {}: value must be a school id string or an array of them",
                label, source);
            return;
        }
        String raw = element.getAsString();
        SpellSchoolId school = SpellSchoolId.fromId(raw);
        // fromId maps anything unknown to GENERIC, so distinguish "deliberately generic"
        // from "typo" and warn about the latter — a silent typo would look like it worked.
        if (school.isGeneric() && !"generic".equals(raw.toLowerCase(Locale.ROOT))) {
            LOGGER.warn("Skipping {} -> '{}' in {}: not a known school id. Valid ids: {}",
                label, raw, source, validIds());
            return;
        }
        if (!sink.contains(school)) {
            sink.add(school);
        }
    }

    private static String validIds() {
        StringBuilder sb = new StringBuilder();
        for (SpellSchoolId id : SpellSchoolId.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(id.id());
        }
        return sb.toString();
    }
}
