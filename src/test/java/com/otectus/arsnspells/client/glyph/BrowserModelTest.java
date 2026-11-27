package com.otectus.arsnspells.client.glyph;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.config.DisplayMode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BrowserModelTest {
    private static BrowserModel.Variant variant(String spell, int level, String name, String suffix, int tier, String reason) {
        String id = "ars_n_spells:iron/" + spell.replace(':', '/') + "/level_" + level;
        return new BrowserModel.Variant(id, spell, level, name, name + " " + suffix, spell.split(":")[0], "irons:fire", tier, reason, false);
    }
    private static List<BrowserModel.Variant> known() {
        return List.of(variant("alpha:bolt", 8, "Firebolt", "VIII", 3, "Maximum level 5"),
            variant("beta:bolt", 2, "Firebolt", "II", 1, null), variant("alpha:bolt", 3, "Firebolt", "III", 3, null),
            variant("alpha:bolt", 1, "Firebolt", "I", 1, null));
    }
    private static JsonObject fixture(String name) throws Exception {
        try (var s = BrowserModelTest.class.getResourceAsStream("/ans_shared_fixtures/browser/" + name + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(java.util.Objects.requireNonNull(s), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @TestFactory List<DynamicTest> sharedCases() throws Exception {
        List<DynamicTest> result = new ArrayList<>();
        for (var entry : fixture("index").getAsJsonArray("cases")) {
            String name = entry.getAsString();
            result.add(DynamicTest.dynamicTest(name, () -> {
                var f = fixture(name);
                var known = known();
                var copy = List.copyOf(known);
                var model = new BrowserModel(known);
                var matched = model.rows(DisplayMode.parse(f.get("mode").getAsString()), f.get("pending").getAsBoolean(),
                    new BrowserModel.Filters(BrowserModel.View.valueOf(f.get("view").getAsString()), f.get("query").getAsString(),
                        f.get("source").getAsString(), f.get("school").getAsString(), f.get("fits").getAsBoolean()),
                    Set.of("alpha:bolt", "missing:addon"), 1);
                var ids = matched.stream().flatMap(row -> row.choices().stream()).map(v -> v.spellId() + "#" + v.level()).toList();
                assertEquals(f.getAsJsonArray("expected").asList().stream().map(e -> e.getAsString()).toList(), ids);
                assertEquals(copy, known);
                assertEquals("Maximum level 5", model.group("alpha:bolt").choices().get(2).reason());
                assertEquals(2, model.groups().size());
            }));
        }
        return result;
    }
    @Test void preservesNativeVisibilityAndRequiresFlatReadyImports() {
        for (var mode : DisplayMode.values()) {
            assertTrue(BrowserModel.inPalette(mode, false, false));
            assertFalse(BrowserModel.inPalette(mode, true, false));
            assertEquals(mode == DisplayMode.FLAT, BrowserModel.inPalette(mode, true, true));
        }
        assertEquals(DisplayMode.GROUPED, DisplayMode.parse("HIDDEN"));
        assertEquals(DisplayMode.GROUPED, DisplayMode.parse("invalid"));
    }
    @Test void numericOrderAndStableIdentityThroughTranslationAndKnowledgeReordering() {
        var variants = new ArrayList<>(known());
        variants.add(variant("a:tier", 11, "Stufe", "XI", 1, null));
        variants.add(variant("a:tier", 2, "Stufe", "II", 1, null));
        variants.add(variant("a:tier", 10, "Stufe", "X", 1, null));
        var model = new BrowserModel(variants);
        Collections.shuffle(variants, new Random(8));
        assertEquals(model.groups(), new BrowserModel(variants).groups());
        assertEquals(List.of(2,10,11), model.group("a:tier").choices().stream().map(BrowserModel.Variant::level).toList());
        var local = new BrowserModel(List.of(variant("a:tier", 2, "Level", "II", 1, null)));
        assertEquals(model.group("a:tier").spell().partId(), local.group("a:tier").spell().partId());
        assertEquals("Level", local.group("a:tier").spell().name());
        assertEquals(0, BrowserModel.clampPage(42, 0, 4));
        assertEquals(2, BrowserModel.clampPage(42, 9, 4));
    }
    @Test void emptyKnowledgeAndBothCatalogStressSizes() {
        assertTrue(new BrowserModel(List.of()).groups().isEmpty());
        for (int cap : List.of(4096,8192)) {
            List<BrowserModel.Variant> fixture = new ArrayList<>();
            for (int i = 0; i < cap; i++) fixture.add(variant("stress:spell_" + i / 8, i % 8 + 1, "Spell " + i / 8, Integer.toString(i % 8 + 1), 1, null));
            long start = System.nanoTime();
            var rows = new BrowserModel(fixture).rows(DisplayMode.GROUPED, false, BrowserModel.Filters.DEFAULT, Set.of(), 3);
            long micros = (System.nanoTime() - start) / 1000;
            assertEquals(cap / 8, rows.size());
            assertEquals(cap, rows.stream().mapToInt(r -> r.choices().size()).sum());
            System.out.printf("ANS_BROWSER_STRESS variants=%d spells=%d model_and_query_us=%d%n", cap, rows.size(), micros);
        }
    }
}
