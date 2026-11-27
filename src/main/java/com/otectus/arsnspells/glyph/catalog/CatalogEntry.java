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
 * @param maxLevel     the spell's default maximum level in the catalogued Iron's build
 * @param registeredMax the highest base level that has a registered identity (3.4.1: at least 10);
 *                     levels above {@code maxLevel} are reserved and dormant until Iron's live
 *                     maximum reaches them
 * @param defaultTier  the Ars tier each default level maps to from Iron's default rarity
 *                     (Common I; Uncommon and Rare II; Epic and Legendary III)
 * @param target       what the adapter resolves against
 * @param finalEffectOnly whether the effect must be the last one in an Ars recipe (it moves the
 *                     caster to another dimension)
 * @param status       supported, unsupported or retired; every status keeps its identities
 *                     registered, only a supported one may execute
 * @param adapterVersion the version of the adapter's execution contract it was catalogued for; 0 marks
 *                     a placeholder that never had one (only unsupported or retired), and a version
 *                     this build does not implement ({@link #ADAPTER_VERSION}) is registered but dormant
 * @param icon         the spell's icon texture ({@code ns:textures/....png}) when the catalog names one,
 *                     for the atlas source and the runtime model fallback; null otherwise
 * @param catalog      the {@code catalog} name of the manifest that declared it (its lineage)
 * @param version      that manifest's {@code catalog_version} (or its source name), for a world's provenance record
 * @param required     whether that manifest was ANS's own required catalog
 */
public record CatalogEntry(ResourceLocation spellId, String name, ResourceLocation school, String castType,
                           int minLevel, int maxLevel, int registeredMax, List<String> rarity, List<Integer> defaultTier,
                           boolean requiresLearning, boolean enabledByDefault, ResourceLocation adapter,
                           TargetKind target, boolean finalEffectOnly, String status, int adapterVersion,
                           @org.jetbrains.annotations.Nullable ResourceLocation icon, String catalog, String version,
                           boolean required, String sourceCatalog) {

    /** The adapter execution-contract version this build implements (3.4.1 review CATALOG-02). */
    public static final int ADAPTER_VERSION = 1;

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
        if (registeredMax < maxLevel) throw new IllegalArgumentException("registered maximum " + registeredMax + " below the default maximum " + maxLevel);
        if (adapterVersion < 0 || adapterVersion == 0 && "supported".equals(status))
            throw new IllegalArgumentException("adapter version " + adapterVersion + " with status " + status);
    }

    /** Whether the catalog marks the spell supported. Identities of every status are registered. */
    public boolean supported() {
        return "supported".equals(status);
    }

    /** Supported, with an adapter contract this build implements: the only identities that may execute. */
    public boolean executable() {
        return supported() && adapterVersion == ADAPTER_VERSION;
    }

    /** Adapter version 0: identities only, never an executable contract. */
    public boolean placeholder() {
        return adapterVersion == 0;
    }

    /** The execution contract two declarations must share to merge without a version transition. */
    boolean sameContract(CatalogEntry other) {
        return adapter.equals(other.adapter) && adapterVersion == other.adapterVersion && target == other.target
            && finalEffectOnly == other.finalEffectOnly;
    }

    public boolean retired() {
        return "retired".equals(status);
    }

    /** A level above the default maximum, registered ahead of a server raising Iron's maximum. */
    public boolean reserved(int level) {
        return level > maxLevel;
    }

    /** The same declaration with its registered envelope set to {@code max} and its provenance to {@code source}. */
    CatalogEntry withEnvelope(int max, String source) {
        return new CatalogEntry(spellId, name, school, castType, minLevel, maxLevel, max, rarity, defaultTier, requiresLearning,
            enabledByDefault, adapter, target, finalEffectOnly, status, adapterVersion, icon, catalog, version, required, source);
    }

    /** Every source that contributed this declaration, in load order (3.4.1 review SAVE-01 provenance). */
    public List<String> sources() {
        return List.of(sourceCatalog.split(" \\+ "));
    }

    /**
     * This declaration with its registered levels widened to cover {@code other}'s too: identities
     * only ever grow. Levels added below this declaration's first level take {@code other}'s per-level
     * data; everything else (behaviour, default maximum) stays this declaration's. The same rule as
     * the Forge build's {@code Declared.unitedWith}.
     */
    CatalogEntry unitedWith(CatalogEntry other, String source) {
        int registered = Math.max(registeredMax, other.registeredMax);
        if (other.minLevel >= minLevel) return withEnvelope(registered, source);
        List<String> lowRarity = new ArrayList<>(rarity.size() + minLevel - other.minLevel);
        List<Integer> lowTiers = new ArrayList<>(lowRarity.size());
        for (int level = other.minLevel; level < minLevel; level++) {
            lowRarity.add(other.rarityFor(level));
            lowTiers.add(other.defaultTierFor(level));
        }
        lowRarity.addAll(rarity);
        lowTiers.addAll(defaultTier);
        return new CatalogEntry(spellId, name, school, castType, other.minLevel, maxLevel, registered, lowRarity, lowTiers, requiresLearning,
            enabledByDefault, adapter, target, finalEffectOnly, status, adapterVersion, icon, catalog, version, required, source);
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

    /** Iron's default rarity; a reserved level is Legendary, as Iron's rates any level at or above its maximum. */
    public String rarityFor(int level) {
        int index = level - minLevel;
        return index >= 0 && index < rarity.size() ? rarity.get(index) : "LEGENDARY";
    }

    /** Every registered identity of the spell: default and reserved levels alike. */
    public List<ImportedGlyphKey> keys() {
        List<ImportedGlyphKey> keys = new ArrayList<>(registeredMax - minLevel + 1);
        for (int level = minLevel; level <= registeredMax; level++) {
            keys.add(new ImportedGlyphKey(spellId, level));
        }
        return keys;
    }
}
