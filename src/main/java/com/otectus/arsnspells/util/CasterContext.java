package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import net.minecraft.world.entity.player.Player;
import java.util.Optional;

public class CasterContext {
    private static final ThreadLocal<Player> CASTER = new ThreadLocal<>();
    private static final ThreadLocal<Spell> ACTIVE_SPELL = new ThreadLocal<>();

    public static void set(Player player, Spell spell) {
        CASTER.set(player);
        ACTIVE_SPELL.set(spell);
    }

    public static Optional<Player> getPlayer() { return Optional.ofNullable(CASTER.get()); }
    public static Optional<Spell> getSpell() { return Optional.ofNullable(ACTIVE_SPELL.get()); }

    public static void clear() {
        CASTER.remove();
        ACTIVE_SPELL.remove();
    }
}