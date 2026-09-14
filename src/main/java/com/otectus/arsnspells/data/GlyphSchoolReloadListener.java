package com.otectus.arsnspells.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.util.SchoolMappings;
import com.otectus.arsnspells.util.SpellSchoolId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
 *     "toomanyglyphs:glyph_gravity":     "nature"
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
 * holy ender blood evocation eldritch generic}.
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
        Map<String, SpellSchoolId> glyphs = new HashMap<>();
        Map<String, SpellSchoolId> arsSchools = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            if (!file.getValue().isJsonObject()) {
                LOGGER.warn("Skipping glyph-school file {}: expected a JSON object", file.getKey());
                continue;
            }
            JsonObject root = file.getValue().getAsJsonObject();
            readSection(file.getKey(), root, "glyphs", glyphs);
            readSection(file.getKey(), root, "ars_schools", arsSchools);
        }

        SchoolMappings.applyOverlay(glyphs, arsSchools);
        if (!glyphs.isEmpty() || !arsSchools.isEmpty()) {
            LOGGER.info("Loaded {} glyph and {} Ars-school overrides from datapacks",
                glyphs.size(), arsSchools.size());
        }
    }

    private static void readSection(ResourceLocation source, JsonObject root, String section,
                                    Map<String, SpellSchoolId> sink) {
        if (!root.has(section) || !root.get(section).isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(section).entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive()) {
                LOGGER.warn("Skipping {}.{}.{} in {}: value must be a school id string",
                    section, key, "", source);
                continue;
            }
            String raw = value.getAsString();
            SpellSchoolId school = SpellSchoolId.fromId(raw);
            // fromId maps anything unknown to GENERIC, so distinguish "deliberately generic"
            // from "typo" and warn about the latter — a silent typo would look like it worked.
            if (school.isGeneric() && !"generic".equals(raw.toLowerCase(Locale.ROOT))) {
                LOGGER.warn("Skipping {} -> '{}' in {}: not a known school id. Valid ids: {}",
                    key, raw, source, validIds());
                continue;
            }
            sink.put(key.toLowerCase(Locale.ROOT), school);
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
