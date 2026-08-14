package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects Ars spell payloads that can no longer be reconstituted faithfully, because a glyph's
 * mod has been removed since the spell was exported.
 *
 * <p><b>Why this is necessary.</b> {@code Spell.fromTag} resolves each serialized glyph id
 * through the glyph registry and, when the lookup misses, simply skips it:
 *
 * <pre>{@code
 * AbstractSpellPart part = GlyphRegistry.getSpellpartMap().get(registryName);
 * if (part == null) continue;              // <- silently dropped
 * }</pre>
 *
 * and {@code Spell.isValid()} is only {@code !isEmpty()}. So a spell exported as
 * {@code Projectile -> Ignite -> WaterGrave} on a pack with Ars Elemental deserializes, after
 * that addon is removed, to {@code Projectile -> Ignite}: a shorter recipe that is still
 * "valid", still castable, and does something different from what the player built — after
 * charging them for it. A recipe reduced to just its cast method degrades to a no-op that
 * still costs mana.
 *
 * <p>Comparing the serialized part count against what actually resolved is the only way to tell
 * "the player built a two-glyph spell" from "the player built a three-glyph spell and one is
 * gone", because the deserialized {@code Spell} no longer carries any trace of the missing part.
 */
public final class ArsSpellIntegrity {

    private ArsSpellIntegrity() {}

    /**
     * Glyph ids present in {@code arsTag}'s serialized recipe that no longer resolve to a
     * registered glyph, in recipe order. Empty when the payload is intact.
     *
     * <p>Mirrors {@code Spell.fromTag}'s own read of the {@code recipe} sub-tag, so the two
     * cannot disagree about which parts a payload claims to contain.
     */
    public static List<String> missingGlyphIds(@Nullable CompoundTag arsTag) {
        List<String> missing = new ArrayList<>();
        if (arsTag == null || arsTag.isEmpty()) {
            return missing;
        }
        CompoundTag recipeTag = arsTag.getCompound("recipe");
        int size = recipeTag.getInt("size");
        for (int i = 0; i < size; i++) {
            String raw = recipeTag.getString("part" + i);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            ResourceLocation id;
            try {
                id = new ResourceLocation(raw);
            } catch (Exception e) {
                missing.add(raw);
                continue;
            }
            if (GlyphRegistry.getSpellpartMap().get(id) == null) {
                missing.add(raw);
            }
        }
        return missing;
    }

    /** True when every serialized glyph still resolves. */
    public static boolean isIntact(@Nullable CompoundTag arsTag) {
        return missingGlyphIds(arsTag).isEmpty();
    }

    /**
     * A short, human-readable summary of the missing glyphs for a player-facing message —
     * at most three ids so a badly broken payload does not produce an unreadable wall of text.
     */
    public static String describeMissing(List<String> missing) {
        if (missing == null || missing.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(3, missing.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(missing.get(i));
        }
        if (missing.size() > shown) {
            sb.append(" (+").append(missing.size() - shown).append(" more)");
        }
        return sb.toString();
    }
}
