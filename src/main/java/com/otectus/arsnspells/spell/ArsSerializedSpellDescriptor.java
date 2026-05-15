package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Ars Nouveau spell descriptor wrapping a serialized Spell NBT blob. Ars
 * spells are recipe-like (a glyph sequence) so the descriptor carries the
 * full serialized form rather than a registry pointer.
 */
public record ArsSerializedSpellDescriptor(ResourceLocation spellId, CompoundTag arsSpellTag)
    implements SpellDescriptor {

    @Override
    public CrossCastValidator.ValidationResult validate(int level, Player player) {
        if (arsSpellTag == null || arsSpellTag.isEmpty()) {
            return CrossCastValidator.ValidationResult.failure("arsnspells.crosscast.invalid.ars_spell_empty");
        }
        return CrossCastValidator.ValidationResult.success(CrossSpellType.ARS_NOUVEAU, spellId);
    }

    @Override
    public void serialize(CompoundTag out) {
        out.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.ARS_NOUVEAU.name());
        if (spellId != null) {
            out.putString(CrossCastNbt.TAG_SPELL_ID, spellId.toString());
        }
        out.put(CrossCastNbt.TAG_ARS_SPELL, arsSpellTag);
    }

    @Override
    public Component displayName() {
        if (spellId != null) {
            return Component.literal(spellId.toString());
        }
        return Component.literal("Ars Spell");
    }

    @Override
    public Object resolve() {
        return Spell.fromTag(arsSpellTag);
    }

    @Override
    public CrossSpellType systemType() {
        return CrossSpellType.ARS_NOUVEAU;
    }

    static Optional<SpellDescriptor> tryParse(CompoundTag entry) {
        if (!entry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag arsTag = entry.getCompound(CrossCastNbt.TAG_ARS_SPELL);
        if (arsTag.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.getString(CrossCastNbt.TAG_SPELL_ID));
        return Optional.of(new ArsSerializedSpellDescriptor(id, arsTag.copy()));
    }
}
