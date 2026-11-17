package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One imported glyph as frozen in the startup catalog: identity plus the defaults it was
 * generated with. Immutable, and free of any Iron's type, so it exists whether or not Iron's
 * is installed.
 *
 * <p>Identity is only {@link #key()}. Everything else is a default or a description: the live
 * maximum, school, rarity and learning requirement are read from Iron's at run time
 * ({@code GlyphBehavior}), and {@link #status()} decides whether the identity may execute.
 *
 * @param key              the frozen (spell, base level) identity
 * @param spellName        the spell's English name, the fallback when no translation is loaded
 * @param school           the Iron's school id the spell had when catalogued
 * @param castType         Iron's cast type name: INSTANT, LONG or CONTINUOUS
 * @param minLevel         the spell's first registered base level: Iron's minimum when catalogued
 *                         (normally 1). Identities never start above a level a catalog once
 *                         registered, so a minimum that later rises leaves the lower levels
 *                         registered and dormant
 * @param maxLevel         the spell's default maximum level in the catalogued Iron's builds
 * @param registeredMax    the highest base level the catalog registers for the spell (its
 *                         envelope); levels above {@code maxLevel} are reserved and dormant until
 *                         Iron's live maximum reaches them
 * @param defaultRarity    Iron's default rarity for this level (Legendary for reserved levels)
 * @param defaultTier      the Ars glyph tier derived from that rarity (1, 2 or 3)
 * @param requiresLearning whether Iron's required the spell to be learned when catalogued
 * @param enabledByDefault whether Iron's ships the spell enabled
 * @param family           how the spell meets its Ars target
 * @param ironsVersions    the pinned Iron's builds that contain the spell
 * @param status           whether the identity may execute; a retired or unsupported identity
 *                         stays registered and dormant
 * @param adapterVersion   the version of {@code family}'s execution contract it was catalogued for;
 *                         0 marks a placeholder that never had one (only unsupported or retired),
 *                         and a version this build does not implement is registered but dormant
 * @param icon             the spell's icon texture ({@code ns:textures/....png}) when the catalog
 *                         names one, for the runtime model fallback; null otherwise
 * @param source           which catalog contributed the identity (provenance, diagnostics)
 */
public record ImportedGlyphDefinition(ImportedGlyphKey key, String spellName, ResourceLocation school, String castType,
                                      int minLevel, int maxLevel, int registeredMax, String defaultRarity, int defaultTier,
                                      boolean requiresLearning, boolean enabledByDefault, GlyphFamily family,
                                      List<String> ironsVersions, Status status, int adapterVersion,
                                      @org.jetbrains.annotations.Nullable ResourceLocation icon, String source) {
    /** Whether a catalogued identity may execute. Identities are registered whatever this says. */
    public enum Status {
        SUPPORTED, UNSUPPORTED, RETIRED;

        public static Status parse(String raw) {
            return switch (raw) {
                case "supported" -> SUPPORTED;
                case "unsupported" -> UNSUPPORTED;
                case "retired" -> RETIRED;
                default -> throw new IllegalArgumentException("unknown status " + raw + " (supported, unsupported or retired)");
            };
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public ImportedGlyphDefinition {
        if (minLevel < 1 || maxLevel < minLevel || registeredMax < maxLevel || registeredMax > ImportedGlyphKey.MAX_LEVEL)
            throw new IllegalArgumentException(key + ": registered levels " + minLevel + "-" + registeredMax + " must cover the default levels "
                + minLevel + "-" + maxLevel);
        if (key.level() < minLevel || key.level() > registeredMax)
            throw new IllegalArgumentException(key + " is outside the registered levels " + minLevel + "-" + registeredMax);
        if (defaultTier < 1 || defaultTier > 3) throw new IllegalArgumentException(key + " has tier " + defaultTier);
        if (adapterVersion < 0) throw new IllegalArgumentException(key + " has adapter version " + adapterVersion);
        if (adapterVersion == 0 && status == Status.SUPPORTED)
            throw new IllegalArgumentException(key + " is a placeholder (adapter version 0) and cannot be supported");
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

    public boolean supported() {
        return status == Status.SUPPORTED;
    }

    /** Supported, with an adapter contract this build implements: the only identities that may execute. */
    public boolean executable() {
        return supported() && GlyphFamily.supportsAdapterVersion(adapterVersion);
    }

    public boolean retired() {
        return status == Status.RETIRED;
    }

    /** A level above the default maximum: registered ahead of a server raising Iron's maximum. */
    public boolean reserved() {
        return key.level() > maxLevel;
    }

    /** Dampen lowers the cast level; it is only advertised where a lower registered level exists. */
    public boolean canDampen() {
        return key.level() > minLevel;
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
