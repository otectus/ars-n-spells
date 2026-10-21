package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * The frozen identity of an imported Iron's glyph: one Iron's spell at one base scroll level.
 *
 * <p>Ars serializes every spell part by its registry id alone, so the spell and the level must
 * both live in the id; a level stored only on the physical glyph item would be lost the moment
 * the glyph is learned or placed into a recipe. The encoding is
 * {@code ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>}. It is injective: the
 * namespace is the second segment (a namespace cannot contain {@code /}), the level is always
 * the last segment, and everything between is the spell path verbatim, so two different
 * (spell, level) pairs can never produce the same id and every valid id decodes to exactly the
 * pair that produced it. The glyph item is registered by Ars under the same id.
 *
 * <p>Never derived from a translated name, a school, a registry index or the {@code ars_cross_*}
 * proxy pool. Changing this encoding would orphan every learned glyph and every saved spell
 * that contains one, so it is covered by tests and must not change.
 */
public record ImportedGlyphKey(ResourceLocation spell, int level) implements Comparable<ImportedGlyphKey> {
    public static final String NAMESPACE = "ars_n_spells";
    public static final String PREFIX = "iron/";
    public static final String LEVEL_PREFIX = "level_";
    /** No Iron's spell in any known catalog exceeds this; a larger level is refused, never clamped. */
    public static final int MAX_LEVEL = 99;

    public ImportedGlyphKey {
        Objects.requireNonNull(spell, "spell");
        if (level < 1 || level > MAX_LEVEL) throw new IllegalArgumentException("Level out of range: " + level);
    }

    /** The registry id of the glyph (and of its item). */
    public ResourceLocation glyphId() {
        return new ResourceLocation(NAMESPACE, PREFIX + spell.getNamespace() + "/" + spell.getPath() + "/" + LEVEL_PREFIX + level);
    }

    /** Decode a glyph id, or empty when {@code id} is not an imported-glyph id. */
    public static Optional<ImportedGlyphKey> decode(ResourceLocation id) {
        if (id == null || !NAMESPACE.equals(id.getNamespace())) return Optional.empty();
        String path = id.getPath();
        if (!path.startsWith(PREFIX)) return Optional.empty();
        String rest = path.substring(PREFIX.length());
        int namespaceEnd = rest.indexOf('/');
        int levelStart = rest.lastIndexOf('/');
        if (namespaceEnd <= 0 || levelStart <= namespaceEnd + 1) return Optional.empty();
        String namespace = rest.substring(0, namespaceEnd);
        String spellPath = rest.substring(namespaceEnd + 1, levelStart);
        String levelSegment = rest.substring(levelStart + 1);
        if (!levelSegment.startsWith(LEVEL_PREFIX)) return Optional.empty();
        String digits = levelSegment.substring(LEVEL_PREFIX.length());
        // Canonical decimal only: "level_03" would be a second id for level 3.
        if (digits.isEmpty() || digits.length() > 2 || !digits.chars().allMatch(Character::isDigit)
            || (digits.length() > 1 && digits.charAt(0) == '0')) return Optional.empty();
        int level = Integer.parseInt(digits);
        if (level < 1 || level > MAX_LEVEL) return Optional.empty();
        ResourceLocation spell = ResourceLocation.tryBuild(namespace, spellPath);
        if (spell == null) return Optional.empty();
        ImportedGlyphKey key = new ImportedGlyphKey(spell, level);
        // Round-trip check: anything that does not re-encode to itself is not ours.
        return key.glyphId().equals(id) ? Optional.of(key) : Optional.empty();
    }

    public static boolean isImportedGlyphId(ResourceLocation id) {
        return decode(id).isPresent();
    }

    @Override
    public int compareTo(ImportedGlyphKey other) {
        int bySpell = spell.toString().compareTo(other.spell.toString());
        return bySpell != 0 ? bySpell : Integer.compare(level, other.level);
    }

    @Override
    public String toString() {
        return spell + "@" + level;
    }
}
