package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * The identity of one imported glyph: an Iron's spell and the base level of the scroll it came
 * from. Both are part of the registered Ars spell-part id, because Ars serializes spell parts by
 * id alone: a level kept anywhere else would be lost the moment the glyph is learned or put in a
 * spell.
 *
 * <h2>Frozen id encoding (3.4.0, schema 1)</h2>
 * <pre>
 *   spell part: ars_n_spells:iron/&lt;spell namespace&gt;/&lt;spell path&gt;/level_&lt;level&gt;
 *   glyph item: ars_n_spells:glyph_iron/&lt;spell namespace&gt;/&lt;spell path&gt;/level_&lt;level&gt;
 *   e.g. irons_spellbooks:firebolt at level 3 -&gt; part ars_n_spells:iron/irons_spellbooks/firebolt/level_3,
 *        item ars_n_spells:glyph_iron/irons_spellbooks/firebolt/level_3
 * </pre>
 * The encoding is injective: a namespace cannot contain {@code /}, so the first segment after the
 * prefix is always the namespace, the last segment is always {@code level_<n>}, and everything
 * between is the spell path, which may itself contain {@code /}. Two different keys can therefore
 * never produce the same id, which {@link #decode} round-trips and the unit tests pin. Never change
 * this encoding: saved spellbooks and known-glyph lists store the part ids, inventories the item ids.
 */
public record ImportedGlyphKey(ResourceLocation ironSpellId, int baseLevel) implements Comparable<ImportedGlyphKey> {

    public static final String NAMESPACE = "ars_n_spells";
    /** Spell-part id prefix. */
    public static final String PREFIX = "iron/";
    /** Glyph item id prefix. */
    public static final String ITEM_PREFIX = "glyph_iron/";
    public static final String LEVEL_SEGMENT = "level_";
    /** Iron's own configs cap levels well below this; anything above is malformed, not a glyph. */
    public static final int MAX_LEVEL = 99;

    public ImportedGlyphKey {
        Objects.requireNonNull(ironSpellId, "ironSpellId");
        if (baseLevel < 1 || baseLevel > MAX_LEVEL) {
            throw new IllegalArgumentException("Imported glyph level out of range: " + baseLevel);
        }
    }

    /** The registered Ars spell-part id: what spell recipes and known-glyph lists store. */
    public ResourceLocation partId() {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, PREFIX + suffix());
    }

    /** The glyph item id (Ars registers the glyph item under the key of its glyph-item map). */
    public ResourceLocation itemId() {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, ITEM_PREFIX + suffix());
    }

    private String suffix() {
        return ironSpellId.getNamespace() + "/" + ironSpellId.getPath() + "/" + LEVEL_SEGMENT + baseLevel;
    }

    /** Decode a part id produced by {@link #partId()}; empty for anything else. */
    public static Optional<ImportedGlyphKey> decode(ResourceLocation partId) {
        if (partId == null || !NAMESPACE.equals(partId.getNamespace()) || !partId.getPath().startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = partId.getPath().substring(PREFIX.length());
        int firstSlash = rest.indexOf('/');
        int lastSlash = rest.lastIndexOf('/');
        if (firstSlash <= 0 || lastSlash <= firstSlash + 1 || lastSlash == rest.length() - 1) {
            return Optional.empty();
        }
        String namespace = rest.substring(0, firstSlash);
        String path = rest.substring(firstSlash + 1, lastSlash);
        String levelSegment = rest.substring(lastSlash + 1);
        if (!levelSegment.startsWith(LEVEL_SEGMENT)) {
            return Optional.empty();
        }
        String digits = levelSegment.substring(LEVEL_SEGMENT.length());
        if (digits.isEmpty() || digits.length() > 2 || !digits.chars().allMatch(Character::isDigit)
                || (digits.length() > 1 && digits.charAt(0) == '0')) {
            return Optional.empty();
        }
        int level = Integer.parseInt(digits);
        if (level < 1 || level > MAX_LEVEL) {
            return Optional.empty();
        }
        ResourceLocation spell = ResourceLocation.tryBuild(namespace, path);
        return spell == null ? Optional.empty() : Optional.of(new ImportedGlyphKey(spell, level));
    }

    /** Whether {@code partId} is in the imported-glyph id space at all, valid or not. */
    public static boolean isImportedNamespace(ResourceLocation partId) {
        return partId != null && NAMESPACE.equals(partId.getNamespace()) && partId.getPath().startsWith(PREFIX);
    }

    @Override
    public int compareTo(ImportedGlyphKey other) {
        int bySpell = ironSpellId.toString().compareTo(other.ironSpellId.toString());
        return bySpell != 0 ? bySpell : Integer.compare(baseLevel, other.baseLevel);
    }

    @Override
    public String toString() {
        return ironSpellId + "@" + baseLevel;
    }
}
