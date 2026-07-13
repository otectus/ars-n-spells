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
 *
 * 3.0.0 native-proxy fields (additive; entries written before 3.0.x lack them
 * and decode with defaults, falling back to the sidecar right-click cast path):
 * {@code proxyPoolId} is the pool id k (1..N) of the registered ars_cross_k
 * proxy that drives this entry in Iron's native spell wheel, or
 * {@link CrossModSpellComponents#NO_PROXY_POOL_ID}; {@code customName} is the
 * player-authored wheel display name; {@code nature}/{@code iconSymbol} pick
 * the wheel icon (whitelisted keys — see
 * {@link CrossModSpellComponents#NATURE_KEYS} /
 * {@link CrossModSpellComponents#ICON_SYMBOLS}).
 */
public record CrossModSpell(
    ResourceLocation spellId,
    int level,
    String typeName,
    Optional<CompoundTag> arsSpellTag,
    Optional<String> castSource,
    int proxyPoolId,
    Optional<String> customName,
    Optional<String> nature,
    Optional<String> iconSymbol
) {
    /** Legacy shape (pre-3.0.x): no proxy metadata. Kept so existing callers compile untouched. */
    public CrossModSpell(ResourceLocation spellId,
                         int level,
                         String typeName,
                         Optional<CompoundTag> arsSpellTag,
                         Optional<String> castSource) {
        this(spellId, level, typeName, arsSpellTag, castSource,
            CrossModSpellComponents.NO_PROXY_POOL_ID,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static final Codec<CrossModSpell> CODEC = RecordCodecBuilder.create(i -> i.group(
        ResourceLocation.CODEC.fieldOf("spell_id").forGetter(CrossModSpell::spellId),
        Codec.INT.fieldOf("spell_level").forGetter(CrossModSpell::level),
        Codec.STRING.fieldOf("spell_type").forGetter(CrossModSpell::typeName),
        CompoundTag.CODEC.optionalFieldOf("ars_spell").forGetter(CrossModSpell::arsSpellTag),
        Codec.STRING.optionalFieldOf("cast_source").forGetter(CrossModSpell::castSource),
        Codec.INT.optionalFieldOf("proxy_pool_id", CrossModSpellComponents.NO_PROXY_POOL_ID)
            .forGetter(CrossModSpell::proxyPoolId),
        Codec.STRING.optionalFieldOf("custom_name").forGetter(CrossModSpell::customName),
        Codec.STRING.optionalFieldOf("nature").forGetter(CrossModSpell::nature),
        Codec.STRING.optionalFieldOf("icon_symbol").forGetter(CrossModSpell::iconSymbol)
    ).apply(i, CrossModSpell::new));

    // Hand-written: StreamCodec.composite maxes out at 6 fields on 1.21.1.
    public static final StreamCodec<RegistryFriendlyByteBuf, CrossModSpell> STREAM_CODEC =
        StreamCodec.of(
            (buf, value) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, value.spellId());
                ByteBufCodecs.VAR_INT.encode(buf, value.level());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.typeName());
                ByteBufCodecs.optional(ByteBufCodecs.COMPOUND_TAG).encode(buf, value.arsSpellTag());
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, value.castSource());
                ByteBufCodecs.VAR_INT.encode(buf, value.proxyPoolId());
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, value.customName());
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, value.nature());
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, value.iconSymbol());
            },
            buf -> new CrossModSpell(
                ResourceLocation.STREAM_CODEC.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.optional(ByteBufCodecs.COMPOUND_TAG).decode(buf),
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf),
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf),
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf)
            )
        );

    /** True when this entry owns a native-wheel proxy slot. */
    public boolean hasProxyPool() {
        return proxyPoolId != CrossModSpellComponents.NO_PROXY_POOL_ID;
    }
}
