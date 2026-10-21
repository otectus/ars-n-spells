package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.GlyphMappings;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A small catalog and live-Iron's stand-in for the pure imported-glyph tests. */
public final class GlyphTestFixtures {
    public static final ResourceLocation FIREBOLT = ResourceLocation.parse("irons_spellbooks:firebolt");
    public static final ResourceLocation HEAL = ResourceLocation.parse("irons_spellbooks:heal");
    public static final ResourceLocation SHROUD = ResourceLocation.parse("irons_spellbooks:abyssal_shroud");
    public static final ResourceLocation STORM = ResourceLocation.parse("irons_spellbooks:thunderstorm");

    private GlyphTestFixtures() {
    }

    public static String manifest() {
        return """
            {"schema_version": 1, "spells": [
              {"spell": "irons_spellbooks:firebolt", "name": "Firebolt", "school": "irons_spellbooks:fire", "cast_type": "INSTANT",
               "min_level": 1, "max_level": 3, "rarity": ["COMMON", "UNCOMMON", "RARE"], "default_tier": [1, 2, 2],
               "adapter": "ars_n_spells:projectile", "target": "any"},
              {"spell": "irons_spellbooks:heal", "name": "Heal", "school": "irons_spellbooks:holy", "cast_type": "INSTANT",
               "min_level": 1, "max_level": 2, "rarity": ["COMMON", "EPIC"], "default_tier": [1, 3],
               "adapter": "ars_n_spells:self", "target": "self"},
              {"spell": "irons_spellbooks:abyssal_shroud", "name": "Abyssal Shroud", "school": "irons_spellbooks:eldritch",
               "cast_type": "INSTANT", "min_level": 1, "max_level": 1, "rarity": ["LEGENDARY"], "default_tier": [3],
               "requires_learning": true, "adapter": "ars_n_spells:self", "target": "self"},
              {"spell": "irons_spellbooks:thunderstorm", "name": "Thunderstorm", "school": "irons_spellbooks:lightning",
               "cast_type": "INSTANT", "min_level": 1, "max_level": 1, "rarity": ["EPIC"], "default_tier": [3],
               "adapter": "ars_n_spells:self", "target": "self", "status": "unsupported"}
            ]}
            """;
    }

    public static ImportedGlyphCatalog catalog() {
        return ImportedGlyphCatalog.fromManifests(Map.of("test", manifest()));
    }

    /** Iron's present with every catalogued spell registered and enabled up to its catalogued level; {@code disabled} off. */
    public static GlyphBehaviorSnapshot.LiveIrons live(Set<ResourceLocation> disabled) {
        return new GlyphBehaviorSnapshot.LiveIrons() {
            @Override
            public boolean present() {
                return true;
            }

            @Override
            public GlyphBehaviorSnapshot.LiveSpell spell(ResourceLocation spellId) {
                var entry = catalog().entry(spellId);
                return entry == null ? null : new GlyphBehaviorSnapshot.LiveSpell(true, !disabled.contains(spellId), entry.maxLevel(), entry.rarity());
            }
        };
    }

    public static GlyphBehaviorSnapshot snapshot(GlyphMappings.Result mappings, GlyphBehaviorSnapshot.Settings settings,
                                                 Set<ResourceLocation> disabled) {
        return GlyphBehaviorSnapshot.build(1, catalog(), mappings, settings, live(disabled));
    }

    public static GlyphBehaviorSnapshot snapshot() {
        return snapshot(GlyphMappings.Result.EMPTY, GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of());
    }

    public static GlyphMappings.Result mappings(String... files) {
        List<GlyphMappings.Source> sources = new java.util.ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            sources.add(new GlyphMappings.Source(ResourceLocation.parse("test:ans_iron_glyphs/file_" + i + ".json"), "pack_" + i,
                com.google.gson.JsonParser.parseString(files[i])));
        }
        return GlyphMappings.parse(sources, catalog());
    }
}
