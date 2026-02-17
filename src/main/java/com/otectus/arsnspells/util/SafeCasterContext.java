package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import net.minecraft.world.entity.player.Player;
import java.util.Optional;

/**
 * Thread-safe caster context with automatic cleanup.
 * Replaces the old CasterContext to prevent ThreadLocal memory leaks.
 */
public class SafeCasterContext implements AutoCloseable {
    private static final ThreadLocal<Player> CASTER = new ThreadLocal<>();
    private static final ThreadLocal<Spell> ACTIVE_SPELL = new ThreadLocal<>();

    private SafeCasterContext(Player player, Spell spell) {
        CASTER.set(player);
        ACTIVE_SPELL.set(spell);
    }

    /**
     * Create a new caster context. Must be used with try-with-resources.
     * 
     * Example:
     * <pre>
     * try (SafeCasterContext ctx = SafeCasterContext.create(player, spell)) {
     *     // Use context
     * }
     * </pre>
     */
    public static SafeCasterContext create(Player player, Spell spell) {
        return new SafeCasterContext(player, spell);
    }

    public static Optional<Player> getPlayer() {
        return Optional.ofNullable(CASTER.get());
    }

    public static Optional<Spell> getSpell() {
        return Optional.ofNullable(ACTIVE_SPELL.get());
    }

    @Override
    public void close() {
        CASTER.remove();
        ACTIVE_SPELL.remove();
    }

    /**
     * Manual cleanup method for cases where try-with-resources cannot be used.
     * Prefer using try-with-resources with create() instead.
     */
    public static void clear() {
        CASTER.remove();
        ACTIVE_SPELL.remove();
    }
}
