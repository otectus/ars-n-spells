package com.otectus.arsnspells.glyph.behavior;

import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The three level quantities of 3.4.1 B2 as pure decisions: the registered envelope (catalog), the
 * effective allowed level (Iron's live maximum) and the cast level. A ten-level spell with effective
 * maximums 1, 5, 10 and 15; a one-level spell raised to 5; a per-spell exception; and a maximum
 * beyond the envelope.
 */
class GlyphLiveDecisionTest {
    static final ResourceLocation BOLT = new ResourceLocation("irons_spellbooks:firebolt");
    static final ResourceLocation ROOT = new ResourceLocation("irons_spellbooks:root");

    static ImportedGlyphCatalog catalog() {
        return ImportedGlyphCatalog.parse("{\"schema_version\":2,\"catalog\":\"t\",\"spells\":["
            + spell("irons_spellbooks:firebolt", "irons_spellbooks:fire", 10, 10) + ","
            + spell("irons_spellbooks:root", "irons_spellbooks:nature", 1, 10) + "]}");
    }

    static String spell(String id, String school, int defaultMax, int registered) {
        StringBuilder rarity = new StringBuilder(), tier = new StringBuilder();
        for (int i = 0; i < defaultMax; i++) {
            rarity.append(i == 0 ? "" : ",").append(i + 1 == defaultMax ? "\"LEGENDARY\"" : "\"COMMON\"");
            tier.append(i == 0 ? "" : ",").append(i + 1 == defaultMax ? 3 : 1);
        }
        return "{\"spell\":\"" + id + "\",\"name\":\"X\",\"school\":\"" + school + "\",\"cast_type\":\"INSTANT\","
            + "\"levels\":{\"min\":1,\"default_max\":" + defaultMax + ",\"registered_max\":" + registered + "},"
            + "\"rarity\":[" + rarity + "],\"tier\":[" + tier + "],\"requires_learning\":false,\"enabled_by_default\":true,"
            + "\"adapter\":\"ars_n_spells:aimed\",\"adapter_version\":1}";
    }

    static GlyphLiveSnapshot live(ImportedGlyphCatalog catalog, Function<ResourceLocation, LiveSpellFacts> facts) {
        return GlyphLiveSnapshot.of(catalog, GlyphLiveSnapshot.Readiness.READY, facts);
    }

    static LiveSpellFacts facts(ResourceLocation school, int max) {
        List<String> rarities = new ArrayList<>();
        for (int level = 1; level <= 10; level++) rarities.add(level >= max ? "LEGENDARY" : "COMMON");
        return new LiveSpellFacts(true, true, 1, max, school, false, rarities);
    }

    static GlyphAvailability.Status status(ImportedGlyphDefinition definition, GlyphLiveSnapshot live) {
        return GlyphAvailability.decide(definition, true, true, live, GlyphMappingSnapshot.defaults());
    }

    @Test
    void tenLevelSpellAtEffectiveMaximumsOneFiveTenFifteen() {
        ImportedGlyphCatalog catalog = catalog();
        for (int max : new int[] {1, 5, 10, 15}) {
            GlyphLiveSnapshot live = live(catalog, spell -> facts(new ResourceLocation("irons_spellbooks:fire"), max));
            for (ImportedGlyphDefinition level : catalog.levelsOf(BOLT)) {
                GlyphAvailability.Status status = status(level, live);
                assertEquals(level.level() <= max, status.available(), "level " + level.level() + " at maximum " + max);
                if (!status.available()) {
                    assertEquals(GlyphAvailability.Reason.LEVEL_UNSUPPORTED, status.reason());
                    assertEquals(max, status.liveMax(), "the explanation names the live maximum");
                }
            }
        }
        // Beyond the envelope there is no identity: level 11 is not registered, so the Press refuses it
        // with the registered range (LEVEL_NOT_CATALOGUED) instead of downcasting.
        assertEquals(10, catalog.registeredMax(BOLT));
        assertTrue(catalog.byKey(new com.otectus.arsnspells.glyph.ImportedGlyphKey(BOLT, 11)).isEmpty());
    }

    @Test
    void oneLevelSpellRaisedWithinItsEnvelopeEnablesExistingIds() {
        ImportedGlyphCatalog catalog = catalog();
        var root = catalog.levelsOf(ROOT);
        assertEquals(10, root.size(), "a one-level spell reserves levels 2-10");
        assertTrue(root.get(4).reserved());
        GlyphLiveSnapshot defaults = live(catalog, spell -> facts(new ResourceLocation("irons_spellbooks:nature"), 1));
        assertFalse(status(root.get(1), defaults).available(), "level 2 is dormant at the default maximum");
        GlyphLiveSnapshot raised = live(catalog, spell -> spell.equals(ROOT) ? facts(new ResourceLocation("irons_spellbooks:nature"), 5)
            : facts(new ResourceLocation("irons_spellbooks:fire"), 10));
        for (ImportedGlyphDefinition level : root)
            assertEquals(level.level() <= 5, status(level, raised).available(), "level " + level.level() + " after raising to 5");
        assertEquals(root.get(4).glyphId(), new com.otectus.arsnspells.glyph.ImportedGlyphKey(ROOT, 5).glyphId(), "same id, nothing renamed");
    }

    @Test
    void perSpellExceptionToAGlobalSettingIsPerSpell() {
        ImportedGlyphCatalog catalog = catalog();
        // A global maximum of 3, with root's own file raising it to 6 (Iron's precedence, read through its getters).
        GlyphLiveSnapshot live = live(catalog, spell -> spell.equals(ROOT) ? facts(new ResourceLocation("irons_spellbooks:nature"), 6)
            : facts(new ResourceLocation("irons_spellbooks:fire"), 3));
        assertTrue(status(catalog.levelsOf(ROOT).get(5), live).available());
        assertFalse(status(catalog.levelsOf(BOLT).get(5), live).available());
        assertTrue(status(catalog.levelsOf(BOLT).get(2), live).available());
    }

    @Test
    void liveSchoolDrivesPolicyNotTheCataloguedOne() {
        ImportedGlyphCatalog catalog = catalog();
        GlyphMappingSnapshot denyEnder = GlyphMappingSnapshot.build(List.of(new GlyphMappingSnapshot.Source(new ResourceLocation("t:p"), "p", 1,
            com.google.gson.JsonParser.parseString("{\"schema_version\":2,\"type\":\"ars_n_spells:policy\",\"deny_schools\":[\"irons_spellbooks:ender\"]}"))),
            catalog, 1);
        GlyphLiveSnapshot moved = live(catalog, spell -> facts(new ResourceLocation("irons_spellbooks:ender"), 10));
        ImportedGlyphDefinition bolt = catalog.levelsOf(BOLT).get(0);
        assertTrue(denyEnder.allows(bolt), "by its catalogued school (fire) it would be allowed");
        assertEquals(GlyphAvailability.Reason.DISABLED_BY_DATAPACK,
            GlyphAvailability.decide(bolt, true, true, moved, denyEnder).reason(), "its live school is denied");
        // A custom addon namespace with the same school path is a different school.
        GlyphLiveSnapshot addonSchool = live(catalog, spell -> facts(new ResourceLocation("addon:ender"), 10));
        assertTrue(GlyphAvailability.decide(bolt, true, true, addonSchool, denyEnder).available());
    }

    @Test
    void pendingAndUnsyncedAreNotUnavailable() {
        ImportedGlyphCatalog catalog = catalog();
        GlyphLiveSnapshot pending = GlyphLiveSnapshot.build(catalog, new GlyphLiveSnapshot.LiveIrons() {
            @Override public boolean present() { return true; }
            @Override public LiveSpellFacts facts(ResourceLocation spell, int registeredMax) { return new LiveSpellFacts(true, true, 1, 1, spell, false, List.of()); }
        }, false);
        assertEquals(GlyphLiveSnapshot.Readiness.PENDING, pending.readiness());
        // Iron's fallbacks (maximum 1) are not used while pending: the catalog defaults stand in.
        assertEquals(10, pending.facts(BOLT).maxLevel());
        GlyphAvailability.Status provisional = status(catalog.levelsOf(BOLT).get(4), pending);
        assertTrue(provisional.available() && provisional.provisional(), "available on defaults, but provisional");
        GlyphLiveSnapshot unsynced = GlyphLiveSnapshot.defaults(catalog, GlyphLiveSnapshot.Readiness.NOT_SYNCED);
        assertEquals(GlyphAvailability.Reason.NOT_SYNCED, status(catalog.levelsOf(BOLT).get(0), unsynced).reason());
    }

    @Test
    void liveSnapshotRoundTripsAndRefusesAnotherCatalog() {
        ImportedGlyphCatalog catalog = catalog();
        GlyphLiveSnapshot live = live(catalog, spell -> facts(new ResourceLocation("irons_spellbooks:fire"), 7));
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        live.write(buffer);
        GlyphLiveSnapshot read = GlyphLiveSnapshot.read(buffer, catalog);
        assertEquals(live.digest(), read.digest());
        assertEquals(7, read.facts(BOLT).maxLevel());
        assertEquals(live.facts(BOLT), read.facts(BOLT));
        FriendlyByteBuf again = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        live.write(again);
        ImportedGlyphCatalog other = ImportedGlyphCatalog.parse("{\"schema_version\":2,\"catalog\":\"o\",\"spells\":["
            + spell("irons_spellbooks:firebolt", "irons_spellbooks:fire", 10, 12) + "]}");
        assertThrows(IllegalArgumentException.class, () -> GlyphLiveSnapshot.read(again, other), "positions only apply to their own catalog");
    }

    @Test
    void anExtremeConfiguredMaximumAllocatesNothingProportional() {
        ImportedGlyphCatalog catalog = catalog();
        int[] queried = {0};
        GlyphLiveSnapshot huge = GlyphLiveSnapshot.build(catalog, new GlyphLiveSnapshot.LiveIrons() {
            @Override public boolean present() { return true; }
            @Override public LiveSpellFacts facts(ResourceLocation spell, int registeredMax) {
                queried[0] = Math.max(queried[0], registeredMax);
                List<String> rarities = new ArrayList<>();
                for (int level = 1; level <= registeredMax; level++) rarities.add("COMMON");
                return new LiveSpellFacts(true, true, 1, Integer.MAX_VALUE, spell, false, rarities);
            }
        }, true);
        assertEquals(10, queried[0], "rarity is asked for registered levels only");
        assertEquals(Integer.MAX_VALUE, huge.facts(BOLT).maxLevel(), "the maximum is kept as a scalar");
        assertEquals(10, huge.facts(BOLT).rarities().size());
    }
}
