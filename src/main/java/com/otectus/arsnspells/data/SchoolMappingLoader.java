package com.otectus.arsnspells.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.util.SchoolKeys;
import java.util.*;
import java.util.function.Predicate;

/** Pure, deterministic datapack merge. Higher priority then lexically later file ID wins. */
public final class SchoolMappingLoader {
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_MEMBERSHIPS = 16;
    /** Per-section UTF-16 budget includes provenance; keeps the full UTF-8 S2C frame below 1 MiB. */
    public static final int MAX_SECTION_CHARACTERS = 32768;
    public record Result(Map<String, List<String>> glyphs, Map<String, List<String>> arsSchools,
                         Map<String, String> provenance, List<String> warnings) {}
    private record File(String id, int priority, JsonObject root, int schema) {}
    private SchoolMappingLoader() {}

    public static Result merge(Map<String, JsonElement> files, Predicate<String> modLoaded) {
        List<String> warnings = new ArrayList<>();
        List<File> ordered = new ArrayList<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                if (entry.getKey().length() > 256) throw new IllegalArgumentException("file id too long");
                JsonObject root = entry.getValue().getAsJsonObject();
                int schema = root.has("schema_version") ? root.get("schema_version").getAsInt() : 1;
                if (schema < 1 || schema > 2) throw new IllegalArgumentException("unsupported schema " + schema);
                int priority = root.has("priority") ? root.get("priority").getAsInt() : 0;
                if (root.has("requires_mods")) {
                    for (JsonElement mod : root.getAsJsonArray("requires_mods")) {
                        if (!modLoaded.test(mod.getAsString())) return;
                    }
                }
                ordered.add(new File(entry.getKey(), priority, root, schema));
            } catch (RuntimeException invalid) {
                warnings.add(entry.getKey() + ": " + invalid.getMessage());
            }
        });
        ordered.sort(Comparator.comparingInt(File::priority).thenComparing(File::id));
        Map<String, List<String>> glyphs = new TreeMap<>();
        Map<String, List<String>> ars = new TreeMap<>();
        Map<String, String> provenance = new TreeMap<>();
        for (File file : ordered) {
            read(file, "glyphs", glyphs, provenance, warnings);
            read(file, "ars_schools", ars, provenance, warnings);
        }
        return new Result(Map.copyOf(glyphs), Map.copyOf(ars), Map.copyOf(provenance), List.copyOf(warnings));
    }

    private static void read(File file, String section, Map<String, List<String>> sink,
                             Map<String, String> sources, List<String> warnings) {
        if (!file.root.has(section)) return;
        if (!file.root.get(section).isJsonObject()) {
            warnings.add(file.id + "/" + section + ": expected object");
            return;
        }
        file.root.getAsJsonObject(section).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String context = file.id + "/" + section + "/" + key;
            try {
                if (key.length() > 256 || (section.equals("glyphs") && !SchoolKeys.isNamespaced(key)))
                    throw new IllegalArgumentException("invalid registry key");
                JsonElement value = entry.getValue();
                boolean payload = true;
                if (value.isJsonObject()) {
                    if (file.schema != 2) throw new IllegalArgumentException("object syntax requires schema_version 2");
                    JsonObject object = value.getAsJsonObject();
                    if (object.has("roles")) {
                        payload = false;
                        for (JsonElement role : object.getAsJsonArray("roles")) {
                            String name = role.getAsString();
                            if (!Set.of("payload", "filter", "augment", "cast_method", "control").contains(name))
                                throw new IllegalArgumentException("unknown role " + name);
                            payload |= name.equals("payload");
                        }
                    }
                    value = object.get("schools");
                }
                List<String> schools = new ArrayList<>();
                if (value != null && value.isJsonArray()) {
                    if (file.schema != 2) throw new IllegalArgumentException("array syntax requires schema_version 2");
                    for (JsonElement school : value.getAsJsonArray()) schools.add(school(school));
                } else schools.add(school(value));
                if (schools.isEmpty() || schools.size() > MAX_MEMBERSHIPS)
                    throw new IllegalArgumentException("school count must be 1.." + MAX_MEMBERSHIPS);
                if (sink.size() >= MAX_ENTRIES && !sink.containsKey(key))
                    throw new IllegalArgumentException("mapping budget exceeded");
                String provenanceKey = section + "/" + key;
                List<String> accepted = payload ? schools.stream().distinct().toList() : List.of(SchoolKeys.GENERIC);
                String source = file.id + "@" + file.priority;
                int used = sink.entrySet().stream().mapToInt(old -> weight(section, old.getKey(), old.getValue(),
                    sources.getOrDefault(section + "/" + old.getKey(), ""))).sum();
                int replaced = sink.containsKey(key) ? weight(section, key, sink.get(key), sources.get(provenanceKey)) : 0;
                if (used - replaced + weight(section, key, accepted, source) > MAX_SECTION_CHARACTERS)
                    throw new IllegalArgumentException("snapshot character budget exceeded");
                String previous = sources.put(provenanceKey, source);
                if (previous != null) warnings.add(context + " overrides " + previous);
                sink.put(key, accepted);
            } catch (RuntimeException invalid) {
                warnings.add(context + ": " + invalid.getMessage());
            }
        });
    }

    private static int weight(String section, String key, List<String> values, String source) {
        return section.length() + 1 + key.length() * 2 + source.length()
            + values.stream().mapToInt(String::length).sum() + 24;
    }

    private static String school(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("school must be a string");
        String key = SchoolKeys.normalize(value.getAsString());
        if (!SchoolKeys.isNamespaced(key)) throw new IllegalArgumentException("unknown short school " + key);
        return key;
    }
}
