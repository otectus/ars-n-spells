package com.otectus.arsnspells.glyph.catalog;

import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One Iron's spell as the startup catalog records it: the identity facts every level variant's
 * registration needs, with no live Iron's object behind it.
 *
 * <p>Registry identity comes only from here, never from the loaded Iron's registry, so a world
 * keeps resolving its imported glyphs while Iron's is absent or the spell is disabled.
 *
 * @param defaultTier  the Ars tier each level maps to from Iron's default rarity
 *                     (Common I; Uncommon and Rare II; Epic and Legendary III)
 * @param target       what the adapter resolves against
 * @param finalEffectOnly whether the effect must be the last one in an Ars recipe (it moves the
 *                     caster to another dimension)
 */
public record CatalogEntry(ResourceLocation spellId, String name, ResourceLocation school, String castType,
                           int minLevel, int maxLevel, List<String> rarity, List<Integer> defaultTier,
                           boolean requiresLearning, boolean enabledByDefault, ResourceLocation adapter,
                           TargetKind target, boolean finalEffectOnly, String status, String sourceCatalog) {

    /** What an imported effect resolves against. */
    public enum TargetKind {
        /** Only the caster: the spell must be cast with Self. */
        SELF,
        /** A living entity other than the caster. */
        ENTITY,
        /** A living entity, or the caster with Self. */
        ENTITY_OR_SELF,
        /** A block. */
        BLOCK,
        /** Any resolved point: a block, an entity's position, or the caster with Self. */
        POSITION,
        /** Anything: the caster launches natively with Self, other hits receive a direct impact. */
        ANY;

        public static TargetKind parse(String raw) {
            return switch (raw) {
                case "self" -> SELF;
                case "entity" -> ENTITY;
                case "entity_or_self" -> ENTITY_OR_SELF;
                case "block" -> BLOCK;
                case "position" -> POSITION;
                case "any" -> ANY;
                default -> throw new IllegalArgumentException("unknown target kind " + raw);
            };
        }

        public boolean allowsSelf() {
            return this == SELF || this == ENTITY_OR_SELF || this == POSITION || this == ANY;
        }
    }

    public CatalogEntry {
        rarity = List.copyOf(rarity);
        defaultTier = List.copyOf(defaultTier);
    }

    public boolean supported() {
        return "supported".equals(status);
    }

    public boolean charged() {
        return "LONG".equals(castType);
    }

    public boolean channelled() {
        return "CONTINUOUS".equals(castType);
    }

    public int defaultTierFor(int level) {
        int index = level - minLevel;
        return index >= 0 && index < defaultTier.size() ? defaultTier.get(index) : 3;
    }

    public String rarityFor(int level) {
        int index = level - minLevel;
        return index >= 0 && index < rarity.size() ? rarity.get(index) : "LEGENDARY";
    }

    public List<ImportedGlyphKey> keys() {
        List<ImportedGlyphKey> keys = new ArrayList<>(maxLevel - minLevel + 1);
        for (int level = minLevel; level <= maxLevel; level++) {
            keys.add(new ImportedGlyphKey(spellId, level));
        }
        return keys;
    }
}
