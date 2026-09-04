package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.items.Glyph;
import com.otectus.arsnspells.registry.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects Ars spell payloads that can no longer be reconstituted faithfully, because a glyph's
 * mod has been removed since the spell was exported.
 *
 * <p><b>Why this is necessary, and how Ars 5.x made it worse.</b> On 1.20.1,
 * {@code Spell.fromTag} skipped a glyph whose id no longer resolved, so a spell exported as
 * {@code Projectile -> Ignite -> WaterGrave} came back as {@code Projectile -> Ignite} once
 * Ars Elemental was removed: shorter, still "valid", still castable, and no longer what the
 * player built — after charging them for it.
 *
 * <p>Ars 5.x does not shorten the recipe. {@code AbstractSpellPart.CODEC} is
 * {@code ResourceLocation.CODEC.xmap(GlyphRegistry::getSpellPartOrDefault, ...)}, and
 * {@code getSpellPartOrDefault} returns {@code EffectBreak.INSTANCE} for an unknown id. The
 * recipe therefore keeps its length and the missing glyph is silently <em>substituted with
 * Break</em> — so the same removed addon now turns a bound fire spell into one that breaks
 * blocks. Same class of failure, louder consequences, and equally invisible after decoding:
 * the decoded {@link com.hollingsworth.arsnouveau.api.spell.Spell} carries no trace that a
 * substitution happened.
 *
 * <p>Checking the serialized ids against the registry <em>before</em> decoding is the only way
 * to tell "the player built a spell with Break in it" from "the player built a spell whose
 * third glyph is gone".
 */
public final class ArsSpellIntegrity {

    private ArsSpellIntegrity() {}

    /**
     * Glyph ids present in {@code arsTag}'s serialized recipe that no longer resolve to a
     * registered glyph, in recipe order. Empty when the payload is intact.
     *
     * <p>Mirrors {@code Spell.CODEC}'s own {@code recipe} field — a list of glyph
     * {@link ResourceLocation}s written as strings — so the two cannot disagree about which
     * parts a payload claims to contain.
     */
    public static List<String> missingGlyphIds(@Nullable CompoundTag arsTag) {
        List<String> missing = new ArrayList<>();
        if (arsTag == null || arsTag.isEmpty()) {
            return missing;
        }
        if (!arsTag.contains("recipe", Tag.TAG_LIST)) {
            return missing;
        }
        ListTag recipe = arsTag.getList("recipe", Tag.TAG_STRING);
        for (int i = 0; i < recipe.size(); i++) {
            String raw = recipe.getString(i);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            // tryParse rather than a throwing parse: a malformed id is itself a missing glyph,
            // not an exception to propagate into a cast or a tooltip.
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null || GlyphRegistry.getSpellpartMap().get(id) == null) {
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
     * Glyph ids in {@code arsTag}'s serialized recipe whose glyph item is tagged
     * {@link ModTags#CROSS_CAST_BLACKLIST}, in recipe order. Empty when none are.
     *
     * <p>The complement of {@link #missingGlyphIds}: that one catches glyphs that are gone, this
     * one catches glyphs that are present but must not leave their own caster. Ars Zero's
     * multi-phase glyphs are the shipped case - they read a phase context that only a Spell Staff
     * provides, so cast from an Iron's scroll they do nothing, or not what the player built.
     * Unregistered ids are skipped here; they are {@code missingGlyphIds}' job.
     */
    public static List<String> blacklistedGlyphIds(@Nullable CompoundTag arsTag) {
        List<String> blacklisted = new ArrayList<>();
        if (arsTag == null || arsTag.isEmpty() || !arsTag.contains("recipe", Tag.TAG_LIST)) {
            return blacklisted;
        }
        ListTag recipe = arsTag.getList("recipe", Tag.TAG_STRING);
        for (int i = 0; i < recipe.size(); i++) {
            String raw = recipe.getString(i);
            ResourceLocation id = raw == null || raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
            AbstractSpellPart part = id == null ? null : GlyphRegistry.getSpellpartMap().get(id);
            if (part != null && isBlacklisted(part)) {
                blacklisted.add(raw);
            }
        }
        return blacklisted;
    }

    /**
     * Registry ids of the glyphs in a live {@code spell} whose glyph item is tagged
     * {@link ModTags#CROSS_CAST_BLACKLIST}. The export-time twin of
     * {@link #blacklistedGlyphIds(CompoundTag)}, for the Spell Loom and the transcription
     * ritual, which hold a decoded {@link Spell} rather than its serialized form.
     */
    public static List<String> blacklistedGlyphIds(@Nullable Spell spell) {
        List<String> blacklisted = new ArrayList<>();
        if (spell == null) {
            return blacklisted;
        }
        for (AbstractSpellPart part : spell.unsafeList()) {
            if (part != null && isBlacklisted(part)) {
                ResourceLocation id = part.getRegistryName();
                blacklisted.add(id == null ? part.getClass().getSimpleName() : id.toString());
            }
        }
        return blacklisted;
    }

    /**
     * Whether {@code part}'s glyph item carries {@link ModTags#CROSS_CAST_BLACKLIST}. Keyed on
     * the item rather than the spell-part id so the tag is an ordinary item tag a pack can edit,
     * and so the check needs no naming convention between the two registries.
     */
    public static boolean isBlacklisted(AbstractSpellPart part) {
        Glyph glyph = part.getGlyph();
        return glyph != null && BuiltInRegistries.ITEM.wrapAsHolder(glyph).is(ModTags.CROSS_CAST_BLACKLIST);
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
