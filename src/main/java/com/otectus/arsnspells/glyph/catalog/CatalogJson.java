package com.otectus.arsnspells.glyph.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Set;

/**
 * Strict reading of imported-glyph catalog JSON (3.4.1 review CATALOG-02), shared in behaviour with
 * the Forge build and with {@code tools/glyph_catalog_pack.py}'s validator.
 *
 * <p>A catalog decides registry identity, so nothing in it is coerced: a number must be a JSON
 * number with no fractional part inside its bounds before it is narrowed (Gson's {@code getAsInt}
 * would truncate {@code 2.5}, parse {@code "3"} and wrap an overflowing value), a flag must be a
 * JSON boolean, a list must be a list, every field must be one the schema names, and the text
 * itself must be strict JSON (no comments, {@code NaN}, unquoted names or trailing data). Every
 * failure names the field, so a rejected source says exactly what to fix.
 */
public final class CatalogJson {
    private static final Gson GSON = new Gson();

    private CatalogJson() {}

    /** Parse strict JSON whose root must be an object. */
    public static JsonObject parseObject(String json) {
        JsonElement root;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            root = GSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("trailing data after the JSON document");
        } catch (IOException | RuntimeException malformed) {
            throw new IllegalArgumentException("not valid JSON: " + malformed.getMessage());
        }
        if (root == null || !root.isJsonObject()) throw new IllegalArgumentException("the document is not a JSON object");
        return root.getAsJsonObject();
    }

    /** Refuse any field of {@code object} that {@code allowed} does not name. */
    public static void onlyFields(JsonObject object, Set<String> allowed, String where) {
        for (String field : object.keySet())
            if (!allowed.contains(field)) throw new IllegalArgumentException(where + "unknown field '" + field + "'");
    }

    public static JsonObject object(JsonObject parent, String field, String where) {
        JsonElement value = parent.get(field);
        if (value == null) throw missing(where, field);
        if (!value.isJsonObject()) throw wrong(where, field, "an object", value);
        return value.getAsJsonObject();
    }

    @Nullable
    public static JsonArray optionalArray(JsonObject parent, String field, String where, int maxSize) {
        JsonElement value = parent.get(field);
        if (value == null) return null;
        if (!value.isJsonArray()) throw wrong(where, field, "a list", value);
        JsonArray array = value.getAsJsonArray();
        if (array.size() > maxSize) throw new IllegalArgumentException(where + "'" + field + "' lists " + array.size() + " entries (at most " + maxSize + ")");
        return array;
    }

    public static JsonArray array(JsonObject parent, String field, String where, int maxSize) {
        JsonArray array = optionalArray(parent, field, where, maxSize);
        if (array == null) throw missing(where, field);
        return array;
    }

    public static String string(JsonObject parent, String field, String where, int maxLength) {
        String value = optionalString(parent, field, where, maxLength);
        if (value == null) throw missing(where, field);
        return value;
    }

    @Nullable
    public static String optionalString(JsonObject parent, String field, String where, int maxLength) {
        JsonElement value = parent.get(field);
        if (value == null) return null;
        return stringValue(value, where + "'" + field + "'", maxLength);
    }

    /** One string element (of a list, say), checked the same way as a string field. */
    public static String stringValue(JsonElement value, String what, int maxLength) {
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString())
            throw new IllegalArgumentException(what + " must be a string (found " + describe(value) + ")");
        String text = primitive.getAsString();
        if (text.isEmpty() || text.length() > maxLength)
            throw new IllegalArgumentException(what + " must be 1-" + maxLength + " characters long");
        return text;
    }

    public static ResourceLocation id(JsonObject parent, String field, String where) {
        return idValue(string(parent, field, where, 256), where + "'" + field + "'");
    }

    public static ResourceLocation idValue(String text, String what) {
        ResourceLocation id = text.indexOf(':') > 0 ? ResourceLocation.tryParse(text) : null;
        if (id == null) throw new IllegalArgumentException(what + " must be a namespaced id such as addon:spell (found \"" + text + "\")");
        return id;
    }

    public static boolean bool(JsonObject parent, String field, String where, boolean fallback) {
        JsonElement value = parent.get(field);
        if (value == null) return fallback;
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isBoolean()) throw wrong(where, field, "true or false", value);
        return primitive.getAsBoolean();
    }

    /** A required integer field within [{@code min}, {@code max}]. */
    public static int integer(JsonObject parent, String field, String where, int min, int max) {
        JsonElement value = parent.get(field);
        if (value == null) throw missing(where, field);
        return integerValue(value, where + "'" + field + "'", min, max);
    }

    public static int integer(JsonObject parent, String field, String where, int min, int max, int fallback) {
        JsonElement value = parent.get(field);
        return value == null ? fallback : integerValue(value, where + "'" + field + "'", min, max);
    }

    /** A JSON number with no fractional part inside the bounds, checked before it is narrowed. */
    public static int integerValue(JsonElement value, String what, int min, int max) {
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber())
            throw new IllegalArgumentException(what + " must be a whole number from " + min + " to " + max + " (found " + describe(value) + ")");
        BigDecimal number;
        try {
            number = new BigDecimal(primitive.getAsString());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(what + " must be a whole number from " + min + " to " + max + " (found " + primitive + ")");
        }
        if (number.signum() != 0 && number.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException(what + " must be a whole number from " + min + " to " + max + " (found " + primitive + ")");
        if (number.compareTo(BigDecimal.valueOf(min)) < 0 || number.compareTo(BigDecimal.valueOf(max)) > 0)
            throw new IllegalArgumentException(what + " must be a whole number from " + min + " to " + max + " (found " + primitive + ")");
        return number.intValueExact();
    }

    private static IllegalArgumentException missing(String where, String field) {
        return new IllegalArgumentException(where + "missing required field '" + field + "'");
    }

    private static IllegalArgumentException wrong(String where, String field, String expected, JsonElement found) {
        return new IllegalArgumentException(where + "'" + field + "' must be " + expected + " (found " + describe(found) + ")");
    }

    private static String describe(JsonElement value) {
        if (value == null || value.isJsonNull()) return "null";
        if (value.isJsonObject()) return "an object";
        if (value.isJsonArray()) return "a list";
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) return "the string \"" + abbreviate(primitive.getAsString()) + "\"";
        return primitive.toString();
    }

    private static String abbreviate(String text) {
        return text.length() <= 40 ? text : text.substring(0, 37) + "...";
    }
}
