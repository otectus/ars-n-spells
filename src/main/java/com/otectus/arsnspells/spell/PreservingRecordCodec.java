package com.otectus.arsnspells.spell;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Retain unrecognized extension fields when a known component record is decoded and saved. */
final class PreservingRecordCodec {
    private PreservingRecordCodec() {}
    static <T> Codec<T> wrap(Codec<T> known, Set<String> keys, Function<T, CompoundTag> archive,
                             BiFunction<T, CompoundTag, T> attach) {
        return CompoundTag.CODEC.flatXmap(raw -> known.parse(NbtOps.INSTANCE, raw).map(value -> {
            CompoundTag extras = raw.copy();
            keys.forEach(extras::remove);
            return attach.apply(value, extras);
        }), value -> known.encodeStart(NbtOps.INSTANCE, value).map(encoded -> {
            CompoundTag preserved = archive.apply(value).copy();
            preserved.merge((CompoundTag) encoded);
            return preserved;
        }));
    }
}
