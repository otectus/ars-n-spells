package com.otectus.arsnspells.casting;

import net.minecraft.world.entity.player.Player;

/**
 * STUB — central pre-cast resource validator. Touched AN's
 * {@code SpellResolver} + {@code AbstractSpellPart} + Sanctified Legacy
 * compat. Pending Phase 11 — until then casts proceed without the
 * pre-cast LP / Aura / mana availability check (the underlying mods'
 * own checks still apply).
 */
public class CastingAuthority {
    private CastingAuthority() {}

    public static boolean canCast(Player player) { return true; }
    public static boolean canCastIronsSpell(Player player) { return true; }
}
