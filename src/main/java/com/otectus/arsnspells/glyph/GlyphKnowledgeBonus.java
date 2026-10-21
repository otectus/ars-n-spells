package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The glyph-knowledge mana bonus policy for imported glyphs.
 *
 * <p>Ars raises a player's glyph bonus to the number of glyphs they know, and both maximum mana
 * and regeneration grow with it. Every level of an Iron's spell is its own glyph, so learning
 * Firebolt I to X would otherwise be worth ten glyphs. Under the default {@code per_spell} policy
 * the variants of one Iron's spell count once: the excess (known variants minus distinct spells)
 * is subtracted from the count Ars reads, at the moment it reads it. Nothing stored is changed;
 * every variant stays learned. {@code native_per_variant} counts every variant, as Ars would.
 */
public final class GlyphKnowledgeBonus {

    private GlyphKnowledgeBonus() {
    }

    public static int effective(Player player, int nativeBonus) {
        if (player == null || nativeBonus <= 0 || !perSpellPolicy()) return nativeBonus;
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player);
        if (cap == null) return nativeBonus;
        return Math.max(0, nativeBonus - excessVariants(cap.getKnownGlyphs()));
    }

    /** Known imported variants beyond the first of each Iron's spell. */
    public static int excessVariants(Collection<AbstractSpellPart> known) {
        java.util.List<ImportedGlyphKey> keys = new java.util.ArrayList<>();
        for (AbstractSpellPart part : known) {
            if (part instanceof ImportedIronEffect imported) keys.add(imported.key());
        }
        return excessVariantKeys(keys);
    }

    /** {@link #excessVariants} over glyph identities: every key beyond the first of its Iron's spell. */
    public static int excessVariantKeys(Collection<ImportedGlyphKey> known) {
        Set<ImportedGlyphKey> distinct = new HashSet<>(known);
        Set<ResourceLocation> spells = new HashSet<>();
        for (ImportedGlyphKey key : distinct) spells.add(key.ironSpellId());
        return distinct.size() - spells.size();
    }

    static boolean perSpellPolicy() {
        try {
            return !"native_per_variant".equals(AnsConfig.IRON_GLYPH_MANA_BONUS_POLICY.get());
        } catch (IllegalStateException configNotLoaded) {
            return true;
        }
    }
}
