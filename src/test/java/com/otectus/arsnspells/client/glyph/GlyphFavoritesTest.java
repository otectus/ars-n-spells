package com.otectus.arsnspells.client.glyph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GlyphFavoritesTest {
    @TempDir Path directory;
    private Path file() { return directory.resolve("config/ars_n_spells/glyph_browser.json"); }
    private void seed(String data) throws Exception { Files.createDirectories(file().getParent()); Files.writeString(file(), data); }
    @Test void persistsOnlyIdsAndRetainsUnknownSpells() throws Exception {
        var prefs = new GlyphFavorites(file());
        assertFalse(Files.exists(file()));
        assertNull(prefs.toggle("other:missing/spell"));
        assertEquals(Set.of("other:missing/spell"), new GlyphFavorites(file()).ids());
        assertNull(prefs.toggle("other:missing/spell"));
        assertEquals(Set.of(), new GlyphFavorites(file()).ids());
        seed("{\"favorite_spells\":[\"a:b\",\"a:b\"],\"schema\":1}");
        assertEquals(Set.of("a:b"), new GlyphFavorites(file()).ids());
    }
    @Test void futureSchemaIsReadOnlyIncludingDifferentPayloadShapes() throws Exception {
        String future = "{\"favorite_spells\":{},\"schema\":2}";
        seed(future);
        assertEquals("favorites_future", new GlyphFavorites(file()).toggle("mod:id"));
        assertEquals(future, Files.readString(file()));
    }
    @Test void malformedAndOversizedAreBackedUpBeforeRepair() throws Exception {
        for (String raw : List.of("{", "x".repeat(GlyphFavorites.MAX_BYTES + 1), "{\"schema\":1,\"favorite_spells\":[\"BAD ID\"]}")) {
            seed(raw);
            assertTrue(new GlyphFavorites(file()).ids().isEmpty());
            try (var files = Files.list(file().getParent())) {
                boolean found = false;
                for (var backup : files.filter(p -> p.toString().endsWith(".bak")).toList()) found |= Files.readString(backup).equals(raw);
                assertTrue(found);
            }
            assertTrue(Files.readString(file()).contains("\"schema\":1"));
        }
    }
    @Test void refusesNewEntryAtCapacityWithoutDiscardingExistingData() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < GlyphFavorites.MAX_IDS; i++) ids.add("\"mod:s" + i + "\"");
        String original = "{\"schema\":1,\"favorite_spells\":[" + String.join(",", ids) + "]}";
        seed(original);
        var prefs = new GlyphFavorites(file());
        assertEquals("favorites_limit", prefs.toggle("mod:another"));
        assertEquals(original, Files.readString(file()));
        assertNull(prefs.toggle("mod:s1"));
        assertNull(prefs.toggle("mod:another"));
        assertEquals(GlyphFavorites.MAX_IDS, new GlyphFavorites(file()).ids().size());
    }
}
