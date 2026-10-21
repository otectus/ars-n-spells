package com.otectus.arsnspells.glyph.behavior;

import com.otectus.arsnspells.glyph.GlyphTestFixtures;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reloadable mapping layer: validation, override precedence, deny-wins policy and fail-closed behaviour. */
class GlyphMappingsTest {

    private static VariantBehavior variant(GlyphBehaviorSnapshot snapshot, String spell, int level) {
        return snapshot.variant(new ImportedGlyphKey(net.minecraft.resources.ResourceLocation.parse(spell), level));
    }

    @Test
    void defaultsFollowRarityTiers() {
        GlyphBehaviorSnapshot snapshot = GlyphTestFixtures.snapshot();
        VariantBehavior common = variant(snapshot, "irons_spellbooks:firebolt", 1);
        VariantBehavior rare = variant(snapshot, "irons_spellbooks:firebolt", 3);
        VariantBehavior epic = variant(snapshot, "irons_spellbooks:heal", 2);
        assertEquals(1, common.tier());
        assertEquals(2, rare.tier());
        assertEquals(3, epic.tier());
        assertEquals(new VariantBehavior(Dormancy.AVAILABLE, 1, 1, 500, 100, VariantBehavior.DEFAULT_CATALYST), common);
        assertEquals(new VariantBehavior(Dormancy.AVAILABLE, 2, 2, 1500, 140, VariantBehavior.DEFAULT_CATALYST), rare);
        assertEquals(new VariantBehavior(Dormancy.AVAILABLE, 3, 4, 3000, 200, VariantBehavior.DEFAULT_CATALYST), epic);
    }

    @Test
    void levelOverridesBeatDefaultsWhichBeatBuiltIns() {
        GlyphMappings.Result mappings = GlyphTestFixtures.mappings("""
            {"schema_version": 1, "spell": "irons_spellbooks:firebolt", "defaults": {"source_cost": 900},
             "levels": {"3": {"ars_tier": 3, "duration_ticks": 60, "catalyst": {"tag": "c:gems/diamond", "count": 5}}}}""");
        assertTrue(mappings.valid(), mappings.errors().toString());
        GlyphBehaviorSnapshot snapshot = GlyphTestFixtures.snapshot(mappings, GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of());
        assertEquals(new VariantBehavior(Dormancy.AVAILABLE, 1, 1, 900, 100, VariantBehavior.DEFAULT_CATALYST),
            variant(snapshot, "irons_spellbooks:firebolt", 1));
        assertEquals(new VariantBehavior(Dormancy.AVAILABLE, 3, 5, 900, 60, "c:gems/diamond"),
            variant(snapshot, "irons_spellbooks:firebolt", 3));
        assertTrue(snapshot.mappingWinners().get(GlyphTestFixtures.FIREBOLT).contains("pack_0"), "the winning file and pack are reported");
    }

    @Test
    void invalidFilesAreRejectedWholeAndFailClosed() {
        GlyphMappings.Result mappings = GlyphTestFixtures.mappings(
            "{\"schema_version\": 1, \"spell\": \"irons_spellbooks:firebolt\", \"levels\": {\"4\": {\"ars_tier\": 1}}}",
            "{\"schema_version\": 1, \"spell\": \"irons_spellbooks:heal\", \"levels\": {\"1\": {\"source_cost\": -5}}}",
            "{\"schema_version\": 1, \"spell\": \"irons_spellbooks:heal\", \"adapter\": \"ars_n_spells:summon\"}",
            "{\"schema_version\": 1, \"spell\": \"irons_spellbooks:thunderstorm\"}",
            "{\"schema_version\": 7, \"spell\": \"irons_spellbooks:firebolt\"}");
        assertFalse(mappings.valid());
        assertEquals(5, mappings.errors().size(), mappings.errors().toString());
        assertTrue(mappings.failedClosed().containsAll(Set.of(GlyphTestFixtures.FIREBOLT, GlyphTestFixtures.HEAL)));
        GlyphBehaviorSnapshot snapshot = GlyphTestFixtures.snapshot(mappings, GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of());
        assertEquals(Dormancy.DISABLED_BY_PACK, variant(snapshot, "irons_spellbooks:firebolt", 1).dormancy(),
            "a spell named by a rejected file fails closed at first load");
    }

    @Test
    void aSecondFileForTheSameSpellIsIgnoredAndReported() {
        GlyphMappings.Result mappings = GlyphTestFixtures.mappings(
            "{\"schema_version\": 1, \"spell\": \"irons_spellbooks:heal\", \"defaults\": {\"source_cost\": 700}}",
            "{\"schema_version\": 1, \"spell\": \"irons_spellbooks:heal\", \"defaults\": {\"source_cost\": 100}}");
        GlyphBehaviorSnapshot snapshot = GlyphTestFixtures.snapshot(mappings, GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of());
        assertEquals(700, variant(snapshot, "irons_spellbooks:heal", 1).pressSource(), "the lowest resource id wins");
        assertEquals(1, mappings.errors().size());
    }

    @Test
    void anExplicitDenyWinsAndAllowListsRestrict() {
        GlyphMappings.Result deny = GlyphTestFixtures.mappings(
            "{\"schema_version\": 1, \"type\": \"policy\", \"allow_spells\": [\"irons_spellbooks:heal\"], \"deny_spells\": [\"irons_spellbooks:heal\"]}");
        GlyphBehaviorSnapshot denied = GlyphTestFixtures.snapshot(deny, GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of());
        assertEquals(Dormancy.DISABLED_BY_PACK, variant(denied, "irons_spellbooks:heal", 1).dormancy(), "deny beats allow");
        GlyphMappings.Result allow = GlyphTestFixtures.mappings(
            "{\"schema_version\": 1, \"type\": \"policy\", \"allow_schools\": [\"irons_spellbooks:holy\"]}");
        GlyphBehaviorSnapshot allowed = GlyphTestFixtures.snapshot(allow, GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of());
        assertEquals(Dormancy.AVAILABLE, variant(allowed, "irons_spellbooks:heal", 1).dormancy());
        assertEquals(Dormancy.DISABLED_BY_PACK, variant(allowed, "irons_spellbooks:firebolt", 1).dormancy(), "outside the allow list");
    }

    @Test
    void aPackCannotEnableWhatIronsOrTheServerDisables() {
        GlyphMappings.Result enable = GlyphTestFixtures.mappings("{\"schema_version\": 1, \"spell\": \"irons_spellbooks:heal\", \"enabled\": true}");
        GlyphBehaviorSnapshot ironsOff = GlyphTestFixtures.snapshot(enable, GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of(GlyphTestFixtures.HEAL));
        assertEquals(Dormancy.SPELL_DISABLED_BY_IRONS, variant(ironsOff, "irons_spellbooks:heal", 1).dormancy());
        var off = new GlyphBehaviorSnapshot.Settings(false, true, "per_spell", 4, 16, 8, 4000);
        assertEquals(Dormancy.FEATURE_DISABLED, variant(GlyphTestFixtures.snapshot(enable, off, Set.of()), "irons_spellbooks:heal", 1).dormancy());
    }

    /** Iron's answers before its spell config is built: everything enabled, maximum level 1, Common. */
    private static GlyphBehaviorSnapshot.LiveIrons unbuiltIrons(boolean configReady) {
        return new GlyphBehaviorSnapshot.LiveIrons() {
            @Override
            public boolean present() {
                return true;
            }

            @Override
            public GlyphBehaviorSnapshot.LiveSpell spell(net.minecraft.resources.ResourceLocation spellId) {
                return new GlyphBehaviorSnapshot.LiveSpell(true, true, 1, java.util.List.of("COMMON"));
            }

            @Override
            public boolean configReady() {
                return configReady;
            }
        };
    }

    @Test
    void anUnbuiltIronsConfigUsesCatalogDefaultsAndMarksTheSnapshotPending() {
        GlyphBehaviorSnapshot pending = GlyphBehaviorSnapshot.build(1, GlyphTestFixtures.catalog(), GlyphMappings.Result.EMPTY,
            GlyphBehaviorSnapshot.Settings.DEFAULTS, unbuiltIrons(false));
        assertTrue(pending.ironsConfigPending());
        assertEquals(GlyphTestFixtures.snapshot().variant(new ImportedGlyphKey(GlyphTestFixtures.FIREBOLT, 3)),
            variant(pending, "irons_spellbooks:firebolt", 3), "level III keeps its catalogued availability and tier");
        assertEquals(3, variant(pending, "irons_spellbooks:heal", 2).tier(), "the tier follows the catalogued rarity");

        GlyphBehaviorSnapshot ready = GlyphBehaviorSnapshot.build(2, GlyphTestFixtures.catalog(), GlyphMappings.Result.EMPTY,
            GlyphBehaviorSnapshot.Settings.DEFAULTS, unbuiltIrons(true));
        assertFalse(ready.ironsConfigPending());
        assertEquals(Dormancy.LEVEL_ABOVE_SERVER_MAX, variant(ready, "irons_spellbooks:firebolt", 3).dormancy(),
            "once Iron's config is built its values apply");

        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            pending.write(buffer, GlyphTestFixtures.catalog());
            assertFalse(GlyphBehaviorSnapshot.read(buffer, GlyphTestFixtures.catalog()).ironsConfigPending(),
                "a client copy is never pending");
        } finally {
            buffer.release();
        }
    }

    @Test
    void theSnapshotRoundTripsItsWireFormatWithTheServerDigest() {
        GlyphBehaviorSnapshot server = GlyphTestFixtures.snapshot();
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            server.write(buffer, GlyphTestFixtures.catalog());
            GlyphBehaviorSnapshot client = GlyphBehaviorSnapshot.read(buffer, GlyphTestFixtures.catalog());
            assertEquals(server.digest(), client.digest(), "the client keeps the server's digest");
            for (ImportedGlyphKey key : GlyphTestFixtures.catalog().keys()) {
                assertEquals(server.variant(key), client.variant(key), key.toString());
            }
        } finally {
            buffer.release();
        }
    }
}
