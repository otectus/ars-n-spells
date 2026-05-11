package com.otectus.arsnspells.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One inscribed spell entry on a cross-cast item. The {@code arsSpellTag}
 * blob carries an Ars Nouveau {@link com.hollingsworth.arsnouveau.api.spell.Spell}
 * serialized via AN's own format (CompoundTag in 1.20.1; in 5.x this may
 * become a Codec-driven shape — we keep the field as a generic
 * {@link CompoundTag} until we know which AN 5.x shape to narrow to).
 *
 * The {@code castSource} string is the {@code CastSource} enum name for
 * Iron's-typed entries (e.g. {@code "SPELLBOOK"}), used by Iron's
 * {@code attemptInitiateCast} to determine cast semantics. Optional for
 * Ars-typed entries.
 */
public record CrossModSpell(
    ResourceLocation spellId,
    int level,
    String typeName,
    Optional<CompoundTag> arsSpellTag,
    Optional<String> castSource
) {
    public static final Codec<CrossModSpell> CODEC = RecordCodecBuilder.create(i -> i.group(
        ResourceLocation.CODEC.fieldOf("spell_id").forGetter(CrossModSpell::spellId),
        Codec.INT.fieldOf("spell_level").forGetter(CrossModSpell::level),
        Codec.STRING.fieldOf("spell_type").forGetter(CrossModSpell::typeName),
        CompoundTag.CODEC.optionalFieldOf("ars_spell").forGetter(CrossModSpell::arsSpellTag),
        Codec.STRING.optionalFieldOf("cast_source").forGetter(CrossModSpell::castSource)
    ).apply(i, CrossModSpell::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrossModSpell> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, CrossModSpell::spellId,
            ByteBufCodecs.VAR_INT,         CrossModSpell::level,
            ByteBufCodecs.STRING_UTF8,     CrossModSpell::typeName,
            ByteBufCodecs.optional(ByteBufCodecs.COMPOUND_TAG), CrossModSpell::arsSpellTag,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),  CrossModSpell::castSource,
            CrossModSpell::new
        );
}
