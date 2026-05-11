package com.otectus.arsnspells.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The full inscribed-spell payload on a cross-cast item: the list of
 * inscribed entries plus the currently-selected index for sneak-cycling.
 * Encoded as one DataComponent per item via {@link ModDataComponents#CROSS_SPELLS}.
 */
public record CrossModSpellList(List<CrossModSpell> spells, int selectedIndex) {
    public static final CrossModSpellList EMPTY = new CrossModSpellList(List.of(), 0);

    public static final Codec<CrossModSpellList> CODEC = RecordCodecBuilder.create(i -> i.group(
        CrossModSpell.CODEC.listOf().fieldOf("spells").forGetter(CrossModSpellList::spells),
        Codec.INT.optionalFieldOf("index", 0).forGetter(CrossModSpellList::selectedIndex)
    ).apply(i, CrossModSpellList::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrossModSpellList> STREAM_CODEC =
        StreamCodec.composite(
            CrossModSpell.STREAM_CODEC.apply(ByteBufCodecs.list()), CrossModSpellList::spells,
            ByteBufCodecs.VAR_INT,                                  CrossModSpellList::selectedIndex,
            CrossModSpellList::new
        );

    public boolean isEmpty() {
        return spells.isEmpty();
    }

    public int size() {
        return spells.size();
    }

    public int normalizedIndex() {
        if (spells.isEmpty()) return 0;
        if (selectedIndex < 0 || selectedIndex >= spells.size()) return 0;
        return selectedIndex;
    }
}
