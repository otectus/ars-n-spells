package com.otectus.arsnspells.glyph.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict reading of one imported-glyph mapping object, with the location of every problem.
 *
 * <p>Every number is checked for type, finiteness, integrality and bounds <em>before</em> it is
 * narrowed to an {@code int}, so {@code 1.5}, {@code "3"}, {@code 1e10} or {@code -1} are each
 * reported as what they are instead of being truncated into a plausible value. Unknown fields are
 * errors: a misspelled restriction must not be silently ignored. Keys starting with {@code _} are
 * comments and are skipped.
 */
final class MappingJson {
    /** Thrown for one invalid field; the message already names the field. */
    static final class Invalid extends IllegalArgumentException {
        Invalid(String message) {
            super(message);
        }
    }

    private final JsonObject object;
    private final String path;

    private MappingJson(JsonObject object, String path) {
        this.object = object;
        this.path = path;
    }

    /** {@code element} as an object at {@code path}, with only {@code allowed} keys (plus comments). */
    static MappingJson object(JsonElement element, String path, Set<String> allowed) {
        if (element == null || !element.isJsonObject()) throw new Invalid(path + ": expected an object, got " + describe(element));
        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_")) continue;
            if (!allowed.contains(key)) throw new Invalid(at(path, key) + ": unknown field (allowed: " + String.join(", ", new java.util.TreeSet<>(allowed)) + ")");
        }
        return new MappingJson(object, path);
    }

    /** An object whose keys are data (level numbers, tier numbers), each checked by the caller. */
    static MappingJson anyKeys(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) throw new Invalid(path + ": expected an object, got " + describe(element));
        return new MappingJson(element.getAsJsonObject(), path);
    }

    /** A string field of a raw object, or {@code fallback} when absent. */
    static String optionalString(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        return new MappingJson(object, "").string(key);
    }

    boolean has(String key) {
        return object.has(key);
    }

    String path() {
        return path;
    }

    JsonObject raw() {
        return object;
    }

    String where(String key) {
        return at(path, key);
    }

    /** A whole number in {@code [min, max]}. */
    int whole(String key, int min, int max) {
        return whole(object.get(key), where(key), min, max);
    }

    static int whole(JsonElement element, String where, int min, int max) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new Invalid(where + ": expected a whole number in " + min + ".." + max + ", got " + describe(element));
        BigDecimal value;
        try {
            value = new BigDecimal(element.getAsJsonPrimitive().getAsString());
        } catch (NumberFormatException notANumber) {
            throw new Invalid(where + ": not a finite number: " + element);
        }
        if (value.signum() != 0 && value.stripTrailingZeros().scale() > 0)
            throw new Invalid(where + ": expected a whole number, got " + element);
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0)
            throw new Invalid(where + ": " + element + " is outside " + min + ".." + max);
        return value.intValueExact();
    }

    boolean bool(String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
            throw new Invalid(where(key) + ": expected true or false, got " + describe(element));
        return element.getAsBoolean();
    }

    String string(String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw new Invalid(where(key) + ": expected a string, got " + describe(element));
        return element.getAsString();
    }

    ResourceLocation id(String key) {
        String raw = string(key);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new Invalid(where(key) + ": not a valid id: \"" + raw + "\"");
        return id;
    }

    List<ResourceLocation> ids(String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) throw new Invalid(where(key) + ": expected a list of ids, got " + describe(element));
        JsonArray array = element.getAsJsonArray();
        List<ResourceLocation> ids = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            String where = where(key) + "[" + i + "]";
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new Invalid(where + ": expected an id, got " + describe(item));
            ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
            if (id == null) throw new Invalid(where + ": not a valid id: \"" + item.getAsString() + "\"");
            ids.add(id);
        }
        return ids;
    }

    MappingJson child(String key, Set<String> allowed) {
        return object(object.get(key), where(key), allowed);
    }

    /** A canonical positive decimal map key ("3", never "03", "+3" or "3.0"). */
    static int levelKey(String key, String where, int min, int max) {
        if (key.isEmpty() || key.length() > 2 || !key.chars().allMatch(Character::isDigit) || (key.length() > 1 && key.charAt(0) == '0'))
            throw new Invalid(where + ": level key \"" + key + "\" is not a level number");
        int level = Integer.parseInt(key);
        if (level < min || level > max) throw new Invalid(where + ": level " + level + " is outside " + min + ".." + max);
        return level;
    }

    static String at(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    static String describe(JsonElement element) {
        if (element == null || element.isJsonNull()) return "nothing";
        if (element.isJsonObject()) return "an object";
        if (element.isJsonArray()) return "a list";
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isString()) return "the string " + primitive;
        return primitive.toString();
    }
}
