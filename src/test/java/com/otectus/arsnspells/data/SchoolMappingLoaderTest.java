package com.otectus.arsnspells.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SchoolMappingLoaderTest {
    private static JsonElement json(String text) { return JsonParser.parseString(text); }
    @AfterEach void reset() { SchoolMappings.reset(); }

    @Test void priorityAndFileOrderProduceStableDigestAndExplainWinner() {
        Map<String, JsonElement> files = new LinkedHashMap<>();
        files.put("pack:z", json("{\"glyphs\":{\"addon:payload\":\"ice\"}}"));
        files.put("pack:a", json("{\"priority\":10,\"glyphs\":{\"addon:payload\":\"fire\"}}"));
        String expected = null;
        for (int i = 0; i < 10; i++) {
            List<String> keys = new ArrayList<>(files.keySet());
            Collections.shuffle(keys, new Random(i));
            Map<String, JsonElement> reordered = new LinkedHashMap<>();
            keys.forEach(key -> reordered.put(key, files.get(key)));
            var result = SchoolMappingLoader.merge(reordered, ignored -> true);
            SchoolMappings.applyKeyOverlay(result.glyphs(), result.arsSchools(), result.provenance());
            assertEquals(List.of("irons_spellbooks:fire"), result.glyphs().get("addon:payload"));
            assertEquals("pack:a@10", result.provenance().get("glyphs/addon:payload"));
            assertEquals(1, result.warnings().size());
            if (expected == null) expected = SchoolMappings.get().digest();
            else assertEquals(expected, SchoolMappings.get().digest());
        }
    }

    @Test void malformedAndMissingModEntriesAreIsolatedAndCustomMembershipsStayNamespaced() {
        var result = SchoolMappingLoader.merge(Map.of(
            "pack:v2", json("""
                {"schema_version":2,"glyphs":{
                  "addon:payload":{"roles":["payload"],"schools":["alpha:fire","beta:fire"]},
                  "addon:control":{"roles":["filter"],"schools":["fire"]},
                  "addon:typo":"fier", "addon:bad":42}}
                """),
            "pack:absent", json("{\"requires_mods\":[\"absent\"],\"glyphs\":{\"addon:payload\":\"ice\"}}")),
            mod -> !mod.equals("absent"));
        assertEquals(2, result.warnings().size());
        SchoolMappings.applyKeyOverlay(result.glyphs(), result.arsSchools(), result.provenance());
        assertEquals(List.of("alpha:fire", "beta:fire"), SchoolResolver.resolveKeys("addon:payload", List.of()));
        assertEquals(SpellSchoolId.GENERIC, SchoolResolver.resolve("addon:payload", List.of()));
        assertEquals(List.of(SchoolKeys.GENERIC), SchoolResolver.resolveKeys("addon:control", List.of("fire")));
    }

    @Test void filtersCannotGainSchoolFromAnOverrideAndListsCannotBeMutatedAfterPublication() {
        List<String> schools = new ArrayList<>(List.of("fire", "ice"));
        SchoolMappings.applyKeyOverlay(Map.of("addon:fiery_filter", schools), Map.of(), Map.of());
        schools.clear();
        assertEquals(2, SchoolMappings.get().glyphSchoolKeys("addon:fiery_filter").size());
        assertEquals(List.of(SchoolKeys.GENERIC), SchoolResolver.resolveKeys("addon:fiery_filter", List.of("fire")));
        assertEquals(SpellSchoolId.GENERIC, SchoolResolver.resolve("addon:fiery_filter", List.of("fire")));
        SchoolMappings snapshot = SchoolMappings.get();
        assertThrows(IllegalArgumentException.class, () -> SchoolMappings.acceptSnapshot(
            Map.of(), Map.of(), Map.of(), snapshot.digest()));
        assertSame(snapshot, SchoolMappings.get());
    }

    @Test void onlyKnownShortKeysMigrateAndNamespacedCollisionsRemainDistinct() {
        assertEquals("irons_spellbooks:fire", SchoolKeys.normalize("FIRE"));
        assertEquals("custom:fire", SchoolKeys.normalize("custom:fire"));
        assertEquals("unknown", SchoolKeys.normalize("unknown"));
        assertEquals(SpellSchoolId.GENERIC, SchoolKeys.builtin("custom:fire"));
    }
    @Test void oversizedSnapshotRetainsValidPrefixAndReportsBudget() {
        com.google.gson.JsonObject glyphs = new com.google.gson.JsonObject();
        for (int i = 0; i < 1800; i++) glyphs.addProperty("example:payload_" + i, "custom:" + "a".repeat(200));
        com.google.gson.JsonObject document = new com.google.gson.JsonObject();
        document.add("glyphs", glyphs);
        var result = SchoolMappingLoader.merge(Map.of("pack:large", document), ignored -> true);
        assertFalse(result.glyphs().isEmpty());
        assertTrue(result.glyphs().size() < 1800);
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("character budget")));
        assertEquals(result.glyphs().size(), result.provenance().size());
    }

}
