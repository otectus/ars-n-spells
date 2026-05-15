package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Typed adapter between Ars Nouveau and Iron's Spells 'n Spellbooks spell
 * models. The bridge does not own a spell registry; instead, descriptors wrap
 * the upstream serialized form (Ars) or registry pointer (Iron's) and present
 * a uniform validate/serialize/displayName/resolve surface.
 *
 * <p>Sealed so the validator and cast handlers can switch exhaustively over
 * the two upstream sources at compile time.
 *
 * <p>Introduced in 2.0.0 as the foundation for Phase 3 work; full migration
 * of call sites onto descriptors (instead of raw {@link CompoundTag} maps) is
 * tracked for 2.1.0. For 2.0.0, descriptors are constructible from existing
 * NBT via {@link #parse(CompoundTag)} and round-trippable via
 * {@link #serialize(CompoundTag)}.
 */
public sealed interface SpellDescriptor
    permits ArsSerializedSpellDescriptor, IronsRegistrySpellDescriptor {

    /**
     * Validate this descriptor against the player and environment. Mirrors
     * {@link CrossCastValidator} but operates on the typed descriptor.
     */
    CrossCastValidator.ValidationResult validate(int level, Player player);

    /**
     * Write the descriptor's data into the supplied tag, using the same keys
     * {@link CrossCastNbt} writes today. The on-disk shape is unchanged so
     * pre-2.0.0 inscribed items round-trip cleanly.
     */
    void serialize(CompoundTag out);

    /**
     * Human-readable display name. Used by future tooltip/HUD code.
     */
    Component displayName();

    /**
     * Resolve the descriptor to its upstream spell object. For Ars this is a
     * {@code com.hollingsworth.arsnouveau.api.spell.Spell}; for Iron's a
     * {@code io.redspace.ironsspellbooks.api.spells.AbstractSpell}. Callers
     * cast based on the descriptor's runtime type.
     */
    Object resolve();

    /**
     * Which upstream system this descriptor targets.
     */
    CrossSpellType systemType();

    /**
     * The spell's logical id. May be a placeholder for Ars descriptors
     * (which carry a serialized recipe rather than a registry entry).
     */
    ResourceLocation spellId();

    /**
     * Parse a {@link CompoundTag} entry produced by {@link CrossCastNbt} into
     * a typed descriptor. Returns {@link Optional#empty()} for malformed or
     * unresolvable entries; callers should still run
     * {@link CrossCastValidator} for translated error reasons.
     */
    static Optional<SpellDescriptor> parse(CompoundTag entry) {
        if (entry == null || entry.isEmpty()) return Optional.empty();
        CrossSpellType type = CrossCastValidator.resolveType(entry);
        if (type == null) return Optional.empty();
        switch (type) {
            case ARS_NOUVEAU:
                return ArsSerializedSpellDescriptor.tryParse(entry);
            case IRONS_SPELLBOOKS:
                return IronsRegistrySpellDescriptor.tryParse(entry);
            default:
                return Optional.empty();
        }
    }
}
