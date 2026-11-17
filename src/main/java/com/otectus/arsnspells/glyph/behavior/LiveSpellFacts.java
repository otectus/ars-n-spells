package com.otectus.arsnspells.glyph.behavior;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What Iron's effective configuration says about one catalogued spell right now: whether it is
 * registered and enabled, its live maximum level, its school and whether that school must be
 * learned, and its rarity at each <em>registered</em> glyph level.
 *
 * <p>Read through Iron's own getters ({@code getMaxLevel}, {@code isEnabled}, {@code getSchoolType},
 * {@code requiresLearning}, {@code getRarity}), which apply Iron's precedence of per-spell files,
 * datapack overrides, the global config and configuration events, so ANS never re-implements it.
 * The maximum is kept as a scalar; rarities are only queried for levels the catalog registered,
 * never for every level up to an arbitrarily large configured maximum.
 *
 * @param minLevel the spell's live minimum level (Iron's {@code getMinLevel}, normally 1); a registered
 *                 glyph below it is dormant (3.4.1 review CATALOG-02)
 * @param rarities rarity names for levels {@code 1..rarities.size()} (the registered envelope)
 */
public record LiveSpellFacts(boolean registered, boolean enabled, int minLevel, int maxLevel, ResourceLocation school,
                             boolean requiresLearning, List<String> rarities) {
    public LiveSpellFacts {
        rarities = List.copyOf(rarities);
    }

    public static LiveSpellFacts missing(ResourceLocation catalogSchool) {
        return new LiveSpellFacts(false, false, 1, 0, catalogSchool, false, List.of());
    }

    @Nullable
    public String rarity(int level) {
        int index = level - 1;
        return index >= 0 && index < rarities.size() ? rarities.get(index) : null;
    }
}
