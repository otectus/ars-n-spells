package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.spell.CrossSpellType;
import net.minecraft.resources.ResourceLocation;

/**
 * Parsed spell payload from a ritual source item, tagged by the originating
 * mod. Only the fields relevant to the {@link CrossSpellType} are populated;
 * readers must branch on {@link #type} before accessing mod-specific state.
 */
public final class InscriptionSource {
    public final CrossSpellType type;
    /** Populated only when {@link #type} is {@code ARS_NOUVEAU}. */
    public final Spell arsSpell;
    /** Populated only when {@link #type} is {@code IRONS_SPELLBOOKS}. */
    public final ResourceLocation spellId;
    /** Populated only when {@link #type} is {@code IRONS_SPELLBOOKS}. */
    public final int spellLevel;

    private InscriptionSource(CrossSpellType type, Spell arsSpell,
                              ResourceLocation spellId, int spellLevel) {
        this.type = type;
        this.arsSpell = arsSpell;
        this.spellId = spellId;
        this.spellLevel = spellLevel;
    }

    public static InscriptionSource ars(Spell spell) {
        return new InscriptionSource(CrossSpellType.ARS_NOUVEAU, spell, null, 0);
    }

    public static InscriptionSource irons(ResourceLocation id, int level) {
        return new InscriptionSource(CrossSpellType.IRONS_SPELLBOOKS, null, id, level);
    }
}
