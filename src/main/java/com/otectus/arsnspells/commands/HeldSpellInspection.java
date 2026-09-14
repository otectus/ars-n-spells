package com.otectus.arsnspells.commands;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.rituals.InscriptionInputs;
import com.otectus.arsnspells.spell.CrossSpellType;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

/** Pure source reads for the diagnostic mana quote; never dispatches a cast/cost event. */
public final class HeldSpellInspection {
    public record Result(String selection, String spellId, String school, ResourceUnit origin,
                         double baseCost, boolean crossCast, String failureKey) {
        static Result failure(String key) { return new Result("", "", "", ResourceUnit.ARS_MANA, 0, false, key); }
        public boolean valid() { return failureKey.isEmpty(); }
    }
    private HeldSpellInspection() {}
    public static Result inspect(ServerPlayer player, ItemStack original) {
        if (original.isEmpty()) return Result.failure("ars_n_spells.diagnostics.inspect_empty");
        try {
            if (com.otectus.arsnspells.spell.CrossCastNbt.schemaVersion(original.getTag()) > com.otectus.arsnspells.spell.CrossCastNbt.SCHEMA_VERSION)
                return Result.failure("arsnspells.crosscast.invalid.future_schema");
            if (com.otectus.arsnspells.spell.CrossCastNbt.hasCrossModSpells(original.getTag())) {
                var list = original.getTag().getList(com.otectus.arsnspells.spell.CrossCastNbt.TAG_CROSS_MOD_SPELLS, net.minecraft.nbt.Tag.TAG_COMPOUND);
                int index = original.getTag().getInt(com.otectus.arsnspells.spell.CrossCastNbt.TAG_SPELL_INDEX);
                if (index < 0 || index >= list.size()) index = 0;
                var entry = list.getCompound(index);
                var validation = com.otectus.arsnspells.spell.CrossCastValidator.validate(player, entry, index, list.size());
                if (!validation.ok()) return Result.failure(validation.reasonKey());
                if (validation.resolvedType() == CrossSpellType.ARS_NOUVEAU)
                    return ars(Spell.fromTag(entry.getCompound(com.otectus.arsnspells.spell.CrossCastNbt.TAG_ARS_SPELL).copy()), "ans_entry_" + (index + 1), true);
                return iron(player, ResourceLocation.tryParse(entry.getString(com.otectus.arsnspells.spell.CrossCastNbt.TAG_SPELL_ID)),
                    entry.getInt(com.otectus.arsnspells.spell.CrossCastNbt.TAG_SPELL_LEVEL), "ans_entry_" + (index + 1), true);
            }
            var nativeSource = InscriptionInputs.readSource(original.copy());
            if (nativeSource == null) return Result.failure("ars_n_spells.diagnostics.inspect_empty");
            if (nativeSource.arsSpell != null) return ars(nativeSource.arsSpell, "native_ars", false);
            return iron(player, nativeSource.spellId, nativeSource.spellLevel, "native_slot_one", false);
        } catch (RuntimeException invalid) {
            return Result.failure("ars_n_spells.diagnostics.inspect_invalid");
        }
    }
    private static Result ars(Spell spell, String selection, boolean cross) {
        if (spell == null || spell.recipe == null || spell.recipe.isEmpty()) return Result.failure("ars_n_spells.diagnostics.inspect_empty");
        return new Result(selection, "ars_nouveau:spell", SpellAnalysis.analyze(spell).schoolKey(), ResourceUnit.ARS_MANA, spell.getCost(), cross, "");
    }
    private static Result iron(ServerPlayer player, ResourceLocation id, int level, String selection, boolean cross) {
        if (!IronsCompat.isLoaded()) return Result.failure("ars_n_spells.spell_loom.error.irons_missing");
        return IronsHeldSpellInspection.read(player, id, level, selection, cross);
    }
}
