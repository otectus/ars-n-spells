package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.Optional;

/**
 * Iron's Spells 'n Spellbooks descriptor pointing to a registered spell by id
 * + level. Iron's owns its own spell registry; the descriptor never stores
 * the resolved object, only the lookup key.
 *
 * <p>All Iron-loaded checks are guarded so this class is safe to load even
 * when Iron's is absent.
 */
public record IronsRegistrySpellDescriptor(ResourceLocation spellId, int level, String castSource)
    implements SpellDescriptor {

    @Override
    public CrossCastValidator.ValidationResult validate(int unusedLevel, Player player) {
        if (spellId == null) {
            return CrossCastValidator.ValidationResult.failure("arsnspells.crosscast.invalid.iron_id");
        }
        if (level < 1) {
            return CrossCastValidator.ValidationResult.failure("arsnspells.crosscast.invalid.iron_level");
        }
        if (ModList.get().isLoaded("irons_spellbooks")) {
            if (!ironResolvable(spellId)) {
                return CrossCastValidator.ValidationResult.failure("arsnspells.crosscast.invalid.iron_unresolved");
            }
        }
        return CrossCastValidator.ValidationResult.success(CrossSpellType.IRONS_SPELLBOOKS, spellId);
    }

    @Override
    public void serialize(CompoundTag out) {
        out.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.IRONS_SPELLBOOKS.name());
        if (spellId != null) {
            out.putString(CrossCastNbt.TAG_SPELL_ID, spellId.toString());
        }
        out.putInt(CrossCastNbt.TAG_SPELL_LEVEL, level);
        if (castSource != null && !castSource.isEmpty()) {
            out.putString(CrossCastNbt.TAG_CAST_SOURCE, castSource);
        }
    }

    @Override
    public Component displayName() {
        if (spellId != null) {
            return Component.literal(spellId.toString() + " L" + level);
        }
        return Component.literal("Iron's Spell L" + level);
    }

    @Override
    public Object resolve() {
        if (!ModList.get().isLoaded("irons_spellbooks") || spellId == null) {
            return null;
        }
        return io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);
    }

    @Override
    public CrossSpellType systemType() {
        return CrossSpellType.IRONS_SPELLBOOKS;
    }

    private static boolean ironResolvable(ResourceLocation id) {
        return io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(id) != null;
    }

    static Optional<SpellDescriptor> tryParse(CompoundTag entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.getString(CrossCastNbt.TAG_SPELL_ID));
        if (id == null) return Optional.empty();
        int level = Math.max(0, entry.getInt(CrossCastNbt.TAG_SPELL_LEVEL));
        String source = entry.getString(CrossCastNbt.TAG_CAST_SOURCE);
        return Optional.of(new IronsRegistrySpellDescriptor(id, level, source));
    }
}
