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
 * F1 fix). Shipped defaults live under {@code data/ars_n_spells/tags/} with
 * {@code required: false} entries so the tags load cleanly when the optional
 * mods are absent; pack makers extend or {@code replace} them to add custom
 * rings/blasphemies/jars without a code change.
 *
 * <p><b>Covenant of the Seven on 1.21.1:</b> the 1.20.1 line also declared ring
 * and blasphemy tags for its LP/aura subsystem. Covenant has no 1.21.1 release,
 * so that subsystem and its tags were removed in the 3.2.1 parity pass rather
 * than carried as dead declarations.
 */
public final class ModTags {

    /**
     * Items that count as Iron's spell books for binding.
     *
     * <p>The shipped file lists every Iron's tier. It is additive on top of the
     * {@code ISpellbook} interface check, which is the primary test — the tag exists so a pack
     * can declare a spellbook-like item from a third mod that does not implement Iron's
     * interface, not because the tag has to enumerate Iron's own books to work.
     */
    public static final TagKey<Item> IRONS_SPELL_BOOKS =
        ItemTags.create(ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "irons_spell_books"));

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
        ItemTags.create(ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "cross_cast_blacklist"));

    /** Blocks that count as Source Jars for the regen synergy scan. */
    public static final TagKey<Block> SOURCE_JARS =
        BlockTags.create(ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "source_jars"));

    private ModTags() {}
}
