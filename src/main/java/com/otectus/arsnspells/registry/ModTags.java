package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Audit F-1/F-2: datapack-extensible tags replacing hardcoded registry-name
 * sets, following the pattern of {@code ars_n_spells:irons_spell_books} (the
 * F1 fix). Shipped defaults live under
 * {@code data/ars_n_spells/tags/} with {@code required: false} entries so the
 * tags load cleanly when the optional mods are absent; pack makers extend or
 * {@code replace} them to add custom rings/blasphemies/jars without a code
 * change.
 */
public final class ModTags {

    /** Rings that trigger the Cursed Ring LP-cost path (Covenant / Enigmatic Legacy by default). */
    public static final TagKey<Item> CURSED_RINGS =
        ItemTags.create(new ResourceLocation(ArsNSpells.MODID, "cursed_rings"));

    /** Rings that trigger the Virtue Ring aura-cost path. */
    public static final TagKey<Item> VIRTUE_RINGS =
        ItemTags.create(new ResourceLocation(ArsNSpells.MODID, "virtue_rings"));

    /**
     * Blasphemy curios granting generic discounts. Matching-school membership is explicit
     * in ars_n_spells:blasphemy/<school>; item names never imply a school.
     */
    public static final TagKey<Item> BLASPHEMY_CURIOS =
        ItemTags.create(new ResourceLocation(ArsNSpells.MODID, "blasphemy_curios"));

    /**
     * Items that count as Iron's spell books for binding.
     *
     * <p>The shipped file lists every Iron's tier. It is additive on top of the
     * {@code ISpellbook} interface check, which is the primary test — the tag exists so a pack
     * can declare a spellbook-like item from a third mod that does not implement Iron's
     * interface, not because the tag has to enumerate Iron's own books to work.
     */
    public static final TagKey<Item> IRONS_SPELL_BOOKS =
        ItemTags.create(new ResourceLocation(ArsNSpells.MODID, "irons_spell_books"));

    /**
     * Glyph items whose spell part must not be exported onto an Iron's scroll or cast through
     * the cross-cast pipeline.
     *
     * <p>Exists for addon glyphs that only work inside their own caster's context. The shipped
     * file lists Ars Zero's multi-phase glyphs (temporal context, sustain, anchor, select,
     * discard): outside a Spell Staff they have no phase context to act on, so a scroll carrying
     * them would cast a spell the player never built. Entries are {@code required: false}, so
     * the tag loads cleanly when the addon is absent; packs extend or {@code replace} it.
     */
    public static final TagKey<Item> CROSS_CAST_BLACKLIST =
        ItemTags.create(new ResourceLocation(ArsNSpells.MODID, "cross_cast_blacklist"));

    /** Blocks that count as Source Jars for the regen synergy scan. */
    public static final TagKey<Block> SOURCE_JARS =
        BlockTags.create(new ResourceLocation(ArsNSpells.MODID, "source_jars"));

    private ModTags() {}
}
