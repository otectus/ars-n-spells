package com.otectus.arsnspells.block;

import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The immutable terms of one Glyph Press operation, fixed when a player presses Inscribe Glyph.
 *
 * @param operation   unique id of this operation (diagnostics; never reused)
 * @param owner       the player who started it, whose prerequisites were checked
 * @param fingerprint SHA-256 of the locked input slots at start; completion requires the same inputs
 * @param digest      the effective glyph behaviour digest the terms were read under
 */
public record GlyphPressJob(UUID operation, UUID owner, ImportedGlyphKey key, int tier, int gems, int source, int ticks,
                            String catalyst, String fingerprint, String digest) {

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("operation", operation);
        tag.putUUID("owner", owner);
        tag.putString("spell", key.ironSpellId().toString());
        tag.putInt("level", key.baseLevel());
        tag.putInt("tier", tier);
        tag.putInt("gems", gems);
        tag.putInt("source", source);
        tag.putInt("ticks", ticks);
        tag.putString("catalyst", catalyst);
        tag.putString("fingerprint", fingerprint);
        tag.putString("digest", digest);
        return tag;
    }

    /** The saved job, or null when the tag is incomplete or names no valid glyph identity. */
    @Nullable
    static GlyphPressJob load(CompoundTag tag) {
        try {
            if (!tag.hasUUID("operation") || !tag.hasUUID("owner")) return null;
            ResourceLocation spell = ResourceLocation.tryParse(tag.getString("spell"));
            int level = tag.getInt("level");
            if (spell == null || level < 1 || level > ImportedGlyphKey.MAX_LEVEL) return null;
            int gems = tag.getInt("gems"), source = tag.getInt("source"), ticks = tag.getInt("ticks");
            if (gems < 0 || source < 0 || ticks < 1) return null;
            return new GlyphPressJob(tag.getUUID("operation"), tag.getUUID("owner"), new ImportedGlyphKey(spell, level),
                tag.getInt("tier"), gems, source, ticks, tag.getString("catalyst"), tag.getString("fingerprint"), tag.getString("digest"));
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
