package com.otectus.arsnspells.client.glyph;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/** Local spell IDs only. Commit the replacement file before changing the in-memory selection. */
public final class GlyphFavorites {
    static final int MAX_BYTES = 1024 * 1024, MAX_IDS = 8192;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Gson JSON = new Gson();
    private final Path file;
    private Set<String> favorites = Set.of();
    private String problem;

    public GlyphFavorites(Path file) {
        this.file = file;
        try {
            if (Files.notExists(file)) return;
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            try {
                if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("file exceeds 1 MiB");
                parse(new String(bytes, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException | IllegalStateException | IOException invalid) {
                Path backup = Files.createTempFile(file.getParent(), "glyph_browser.", ".bak");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                LoggerFactory.getLogger(GlyphFavorites.class).warn("Invalid glyph favorites: {}. Original saved to {}", invalid.getMessage(), backup);
                write(Set.of());
            }
        } catch (IOException failure) {
            problem = "favorites_io";
            LoggerFactory.getLogger(GlyphFavorites.class).warn("Could not access glyph favorites at {}", file, failure);
        }
    }

    private void parse(String raw) throws IOException {
        // Read the envelope first. A future version owns its entire payload, even if the format changes.
        int schema = -1;
        try (JsonReader reader = new JsonReader(new StringReader(raw))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("schema")) {
                    if (reader.peek() != JsonToken.NUMBER) throw new IllegalArgumentException("invalid schema");
                    schema = reader.nextInt();
                } else reader.skipValue();
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("extra JSON data");
        }
        if (schema > 1) { problem = "favorites_future"; return; }
        if (schema != 1) throw new IllegalArgumentException("missing schema 1");
        Set<String> spells = new LinkedHashSet<>();
        boolean found = false;
        try (JsonReader reader = new JsonReader(new StringReader(raw))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (!reader.nextName().equals("favorite_spells")) { reader.skipValue(); continue; }
                found = true;
                reader.beginArray();
                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.STRING) throw new IllegalArgumentException("invalid spell ID");
                    String id = reader.nextString();
                    if (!ID.matcher(id).matches()) throw new IllegalArgumentException("invalid spell ID");
                    spells.add(id);
                    if (spells.size() > MAX_IDS) throw new IllegalArgumentException("too many spell IDs");
                }
                reader.endArray();
            }
            reader.endObject();
        }
        if (!found) throw new IllegalArgumentException("missing favorite_spells");
        favorites = Set.copyOf(spells);
    }

    public Set<String> ids() { return favorites; }

    /** null on success, otherwise a localizable explanation. */
    public String toggle(String id) {
        if (problem != null) return problem;
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("invalid spell ID");
        Set<String> next = new TreeSet<>(favorites);
        if (!next.remove(id)) {
            if (next.size() >= MAX_IDS) return "favorites_limit";
            next.add(id);
        }
        try {
            if (!write(next)) return "favorites_limit";
            favorites = Set.copyOf(next);
            return null;
        } catch (IOException failure) {
            LoggerFactory.getLogger(GlyphFavorites.class).warn("Could not save glyph favorites to {}", file, failure);
            return "favorites_io";
        }
    }

    private boolean write(Set<String> ids) throws IOException {
        record Preferences(int schema, List<String> favorite_spells) {}
        byte[] data = (JSON.toJson(new Preferences(1, List.copyOf(ids))) + "\n").getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) return false;
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), ".glyph_browser-", ".tmp");
        try {
            Files.write(temp, data);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }
}
