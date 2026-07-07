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
     * Blasphemy curios granting school discounts. School matching is by item
     * path suffix {@code <school>_blasphemy} (any namespace), so pack-added
     * entries school-match by following that naming convention.
     */
    public static final TagKey<Item> BLASPHEMY_CURIOS =
        ItemTags.create(new ResourceLocation(ArsNSpells.MODID, "blasphemy_curios"));

    /** Blocks that count as Source Jars for the regen synergy scan. */
    public static final TagKey<Block> SOURCE_JARS =
        BlockTags.create(new ResourceLocation(ArsNSpells.MODID, "source_jars"));

    private ModTags() {}
}
