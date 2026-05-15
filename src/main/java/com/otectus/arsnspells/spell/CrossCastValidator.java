package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

/**
 * Single source of truth for cross-cast payload validation.
 *
 * <p>Replaces the scattered checks that used to live in
 * {@code CrossCastingHandler.parseSpellType}, {@code castArsSpell}, and
 * {@code castIronsSpell}. All validation runs server-side at packet receipt,
 * before any upstream runtime is invoked. Failures yield a translation key
 * that the caller renders to the player via {@code displayClientMessage} and
 * a {@code descriptor_rejected} trace event.
 */
public final class CrossCastValidator {

    public record ValidationResult(boolean ok, String reasonKey,
        CrossSpellType resolvedType, ResourceLocation spellId) {

        public static ValidationResult success(CrossSpellType type, ResourceLocation spellId) {
            return new ValidationResult(true, "", type, spellId);
        }

        public static ValidationResult failure(String reasonKey) {
            return new ValidationResult(false, reasonKey, null, null);
        }
    }

    private CrossCastValidator() {
    }

    public static ValidationResult validate(Player player, CompoundTag spellEntry,
        int index, int listSize) {
        return validate(spellEntry, index, listSize, CrossCastValidator::defaultIronCheck);
    }

    /**
     * Test-friendly seam. Production callers should use the four-arg overload;
     * this exists so unit tests can supply a synthetic Iron's registry
     * predicate without bootstrapping {@link ModList}.
     */
    static ValidationResult validate(CompoundTag spellEntry, int index, int listSize,
        java.util.function.Predicate<ResourceLocation> ironChecker) {

        if (listSize <= 0 || index < 0 || index >= listSize) {
            return ValidationResult.failure("arsnspells.crosscast.invalid.index_out_of_range");
        }
        if (spellEntry == null || spellEntry.isEmpty()) {
            return ValidationResult.failure("arsnspells.crosscast.invalid.empty_payload");
        }

        CrossSpellType type = resolveType(spellEntry);
        if (type == null) {
            return ValidationResult.failure("arsnspells.crosscast.invalid.spell_type");
        }

        switch (type) {
            case ARS_NOUVEAU:
                if (!spellEntry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)) {
                    return ValidationResult.failure("arsnspells.crosscast.invalid.ars_spell_missing");
                }
                CompoundTag arsTag = spellEntry.getCompound(CrossCastNbt.TAG_ARS_SPELL);
                if (arsTag.isEmpty()) {
                    return ValidationResult.failure("arsnspells.crosscast.invalid.ars_spell_empty");
                }
                ResourceLocation arsId = ResourceLocation.tryParse(spellEntry.getString(CrossCastNbt.TAG_SPELL_ID));
                return ValidationResult.success(CrossSpellType.ARS_NOUVEAU, arsId);

            case IRONS_SPELLBOOKS:
                ResourceLocation ironId = ResourceLocation.tryParse(spellEntry.getString(CrossCastNbt.TAG_SPELL_ID));
                if (ironId == null) {
                    return ValidationResult.failure("arsnspells.crosscast.invalid.iron_id");
                }
                int level = spellEntry.getInt(CrossCastNbt.TAG_SPELL_LEVEL);
                if (level < 1) {
                    return ValidationResult.failure("arsnspells.crosscast.invalid.iron_level");
                }
                if (!ironChecker.test(ironId)) {
                    return ValidationResult.failure("arsnspells.crosscast.invalid.iron_unresolved");
                }
                return ValidationResult.success(CrossSpellType.IRONS_SPELLBOOKS, ironId);

            default:
                return ValidationResult.failure("arsnspells.crosscast.invalid.spell_type");
        }
    }

    private static boolean defaultIronCheck(ResourceLocation spellId) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return true;  // No Iron's installed; accept any ID — cast path will no-op.
        }
        return ironSpellResolvable(spellId);
    }

    /**
     * Reads {@code spell_type} (the explicit form) or falls back to namespace
     * inference on the {@code spell_id}. Public because the descriptor model
     * (Phase 3.1) uses the same resolution rule.
     */
    public static CrossSpellType resolveType(CompoundTag spellEntry) {
        if (spellEntry == null) return null;
        String typeName = spellEntry.getString(CrossCastNbt.TAG_SPELL_TYPE);
        if (!typeName.isEmpty()) {
            try {
                return CrossSpellType.valueOf(typeName);
            } catch (IllegalArgumentException ignored) {
                // Fall through to namespace detection
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(spellEntry.getString(CrossCastNbt.TAG_SPELL_ID));
        if (id != null) {
            String ns = id.getNamespace();
            if ("ars_nouveau".equals(ns)) return CrossSpellType.ARS_NOUVEAU;
            if ("irons_spellbooks".equals(ns)) return CrossSpellType.IRONS_SPELLBOOKS;
        }
        return null;
    }

    /**
     * Isolated to avoid loading Iron's classes when the mod isn't present.
     * Only called after a {@code ModList.isLoaded} check.
     */
    private static boolean ironSpellResolvable(ResourceLocation spellId) {
        return io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId) != null;
    }
}
