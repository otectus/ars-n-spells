package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.setup.config.ServerConfig;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Ars's per-glyph mana bonus, with imported glyph levels counted once per Iron's spell.
 *
 * <p>Ars counts every known glyph toward its maximum-mana and regeneration bonus
 * ({@code glyphmax}, {@code glyph_regen_bonus} per glyph). Every level of an imported spell is a
 * separate glyph, so learning Firebolt I to X would count as ten glyphs. Under the default
 * {@code per_spell} policy, all levels of one Iron's spell count as one: the surplus levels are
 * taken out of the bonus here, at Ars's own mana calculation, on every calculation and from the
 * player's synced knowledge alone. Nothing stored changes, no glyph is forgotten, ordinary Ars
 * glyphs are unaffected, and the same knowledge always gives the same maximum, through
 * equipment changes, relogs and every mana mode (which all read this maximum).
 *
 * <p>Ars raises its counted bonus to the number of known glyphs when a spell book is used, so
 * the adjustment only removes what that count actually includes.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class ImportedGlyphKnowledge {
    private ImportedGlyphKnowledge() {}

    /**
     * How many of the counted glyph-bonus units are surplus imported levels.
     *
     * @param knownGlyphs      every glyph the player knows
     * @param countedBonus     the glyph count Ars's bonus currently includes
     */
    public static int surplus(Collection<AbstractSpellPart> knownGlyphs, int countedBonus) {
        if (!AnsConfig.ironGlyphBonusPerSpell()) return 0;
        int imported = 0;
        Set<ResourceLocation> families = new HashSet<>();
        for (AbstractSpellPart part : knownGlyphs) {
            if (part instanceof ImportedIronEffect effect) {
                imported++;
                families.add(effect.definition().spell());
            }
        }
        return surplus(knownGlyphs.size(), imported, families.size(), countedBonus);
    }

    /**
     * Pure form: the surplus imported levels the counted bonus can include. Never more than the
     * real surplus ({@code imported - families}); a bonus raised past the known count by another
     * mod or a command is left alone, because nothing says it counted imported glyphs.
     */
    public static int surplus(int knownTotal, int knownImported, int importedFamilies, int countedBonus) {
        int excess = Math.max(0, knownImported - importedFamilies);
        if (excess == 0) return 0;
        int normalizedCount = knownTotal - excess;
        return Math.min(excess, Math.max(0, countedBonus - normalizedCount));
    }

    private static int surplus(Player player) {
        IManaCap mana = CapabilityRegistry.getMana(player).orElse(null);
        IPlayerCap knowledge = CapabilityRegistry.getPlayerDataCap(player).orElse(null);
        if (mana == null || knowledge == null) return 0;
        return surplus(knowledge.getKnownGlyphs(), mana.getGlyphBonus());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMaxMana(MaxManaCalcEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int surplus = surplus(player);
        if (surplus > 0) event.setMax(Math.max(0, event.getMax() - surplus * ServerConfig.GLYPH_MAX_BONUS.get()));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRegen(ManaRegenCalcEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int surplus = surplus(player);
        if (surplus > 0) event.setRegen(Math.max(0, event.getRegen() - surplus * ServerConfig.GLYPH_REGEN_BONUS.get()));
    }
}
