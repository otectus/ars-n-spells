package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Single source of truth for cross-cast inscription validity. Runs server-side
 * before {@link CrossCastingHandler} dispatches a cast, so a malformed / empty /
 * unresolvable inscription yields a translation key the player sees via
 * {@code displayClientMessage} instead of the cast silently doing nothing.
 *
 * <p>Adapted from the Forge 1.20.1 NBT-based validator to the NeoForge port's
 * data-component {@link CrossModSpell} model. The Ars decode and Iron's registry
 * lookup mirror the ones {@code CrossCastingHandler} already performs, so a
 * validation pass and the subsequent cast agree.
 *
 * <p>The Iron's resolution path is isolated in a private method reached only
 * after {@link IronsCompat#isLoaded()}, so this class never resolves Iron's
 * symbols on an Iron's-less server.
 */
public final class CrossCastValidator {

    public record ValidationResult(boolean ok, String reasonKey, CrossSpellType resolvedType) {
        public static ValidationResult success(CrossSpellType type) {
            return new ValidationResult(true, "", type);
        }

        public static ValidationResult failure(String reasonKey) {
            return new ValidationResult(false, reasonKey, null);
        }
    }

    private CrossCastValidator() {
    }

    public static ValidationResult validate(CrossModSpell entry, int index, int listSize) {
        return validate(entry, index, listSize, CrossCastValidator::defaultIronCheck);
    }

    /**
     * Test seam: callers in production use the three-arg overload; this lets unit
     * tests supply a synthetic Iron's-registry predicate without bootstrapping Iron's.
     */
    static ValidationResult validate(CrossModSpell entry, int index, int listSize,
                                     Predicate<ResourceLocation> ironChecker) {
        if (listSize <= 0 || index < 0 || index >= listSize) {
            return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.index");
        }
        if (entry == null) {
            return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.empty");
        }

        CrossSpellType type = resolveType(entry);
        if (type == null) {
            return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.type");
        }

        switch (type) {
            case ARS_NOUVEAU:
                Optional<CompoundTag> tag = entry.arsSpellTag();
                if (tag.isEmpty() || tag.get().isEmpty()) {
                    return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.ars_missing");
                }
                Spell spell = decodeArsSpell(tag.get());
                if (spell == null || spell.isEmpty()) {
                    return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.ars_empty");
                }
                return ValidationResult.success(CrossSpellType.ARS_NOUVEAU);

            case IRONS_SPELLBOOKS:
                if (entry.spellId() == null) {
                    return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.iron_id");
                }
                if (entry.level() < 1) {
                    return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.iron_level");
                }
                if (!ironChecker.test(entry.spellId())) {
                    return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.iron_unresolved");
                }
                return ValidationResult.success(CrossSpellType.IRONS_SPELLBOOKS);

            default:
                return ValidationResult.failure("message.ars_n_spells.crosscast.invalid.type");
        }
    }

    /** Resolve the type from the explicit {@code typeName}, else infer from the id namespace. */
    public static CrossSpellType resolveType(CrossModSpell entry) {
        if (entry == null) {
            return null;
        }
        String typeName = entry.typeName();
        if (typeName != null && !typeName.isEmpty()) {
            try {
                return CrossSpellType.valueOf(typeName);
            } catch (IllegalArgumentException ignored) {
                // Fall through to namespace inference.
            }
        }
        ResourceLocation id = entry.spellId();
        if (id != null) {
            if ("ars_nouveau".equals(id.getNamespace())) return CrossSpellType.ARS_NOUVEAU;
            if ("irons_spellbooks".equals(id.getNamespace())) return CrossSpellType.IRONS_SPELLBOOKS;
        }
        return null;
    }

    private static boolean defaultIronCheck(ResourceLocation spellId) {
        // Iron's-less servers reject Iron's-id inscriptions outright with a clear reason,
        // rather than letting the downstream cast path no-op with no diagnostic.
        if (!IronsCompat.isLoaded()) {
            return false;
        }
        return ironSpellResolvable(spellId);
    }

    /** Isolated so Iron's classes are never resolved unless Iron's is loaded. */
    private static boolean ironSpellResolvable(ResourceLocation spellId) {
        try {
            return io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get(spellId) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Spell decodeArsSpell(CompoundTag tag) {
        try {
            return Spell.CODEC.codec().parse(NbtOps.INSTANCE, tag).result().orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
