package com.otectus.arsnspells.commands;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Optional-mod-isolated native spell lookup. No cast initiation, event posting or payment. */
final class IronsHeldSpellInspection {
    private IronsHeldSpellInspection() {}
    static HeldSpellInspection.Result read(ServerPlayer player, ResourceLocation id, int level, String selection, boolean cross) {
        var spell = id == null ? null : SpellRegistry.getSpell(id);
        if (spell == null || spell == SpellRegistry.none() || level < 1 || level > spell.getMaxLevel()
            || spell.getSpellId().startsWith("ars_n_spells:ars_cross_"))
            return HeldSpellInspection.Result.failure("ars_n_spells.diagnostics.inspect_invalid");
        int effectiveLevel = spell.getLevelFor(level, player);
        return new HeldSpellInspection.Result(selection, id.toString(), spell.getSchoolType().getId().toString(),
            com.otectus.arsnspells.contract.ResourceUnit.IRONS_MANA, spell.getManaCost(effectiveLevel), cross, "");
    }
}
