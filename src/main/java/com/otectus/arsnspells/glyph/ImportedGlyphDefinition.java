package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One imported glyph as frozen in the startup catalog: identity plus the defaults it was
 * generated with. Immutable, and free of any Iron's type, so it exists whether or not Iron's
 * is installed.
 *
 * @param key              the frozen (spell, base level) identity
 * @param spellName        the spell's English name, the fallback when no translation is loaded
 * @param school           the Iron's school id the spell belongs to
 * @param castType         Iron's cast type name: INSTANT, LONG or CONTINUOUS
 * @param maxLevel         the spell's highest level in the pinned builds
 * @param defaultRarity    Iron's default rarity for this level
 * @param defaultTier      the Ars glyph tier derived from that rarity (1, 2 or 3)
 * @param requiresLearning whether Iron's requires the spell to be learned before casting
 * @param enabledByDefault whether Iron's ships the spell enabled
 * @param family           how the spell meets its Ars target
 * @param ironsVersions    the pinned Iron's builds that contain the spell
 */
public record ImportedGlyphDefinition(ImportedGlyphKey key, String spellName, ResourceLocation school, String castType,
                                      int maxLevel, String defaultRarity, int defaultTier, boolean requiresLearning,
                                      boolean enabledByDefault, GlyphFamily family, List<String> ironsVersions) {
    public ImportedGlyphDefinition {
        if (key.level() > maxLevel) throw new IllegalArgumentException(key + " exceeds max level " + maxLevel);
        if (defaultTier < 1 || defaultTier > 3) throw new IllegalArgumentException(key + " has tier " + defaultTier);
        ironsVersions = List.copyOf(ironsVersions);
    }

    public ResourceLocation glyphId() {
        return key.glyphId();
    }

    public ResourceLocation spell() {
        return key.spell();
    }

    public int level() {
        return key.level();
    }

    public boolean instant() {
        return "INSTANT".equals(castType);
    }

    public boolean continuous() {
        return "CONTINUOUS".equals(castType);
    }

    /** Amplify raises the cast level; it is only advertised where a higher level exists. */
    public boolean canAmplify() {
        return key.level() < maxLevel;
    }

    /** Dampen lowers the cast level; it is only advertised where a lower level exists. */
    public boolean canDampen() {
        return key.level() > 1;
    }

    /** Roman numeral for the base level, as Iron's own scrolls show it. */
    public String levelNumeral() {
        return Numerals.roman(key.level());
    }

    public static final class Numerals {
        private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

        private Numerals() {}

        public static String roman(int level) {
            return level >= 1 && level < ROMAN.length ? ROMAN[level] : Integer.toString(level);
        }
    }
}
