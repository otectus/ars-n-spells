package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.spell.CrossCastNbt;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * The Iron's-typed half of {@link CombatBridgeGameTests}, held at arm's length.
 *
 * <p>The GameTest scanner calls {@code Class.forName} on every {@code @GameTestHolder}
 * class before any test runs, which links it - and linking verifies method bodies, so an Iron's
 * type that has to be proved assignable to something (here, {@code SpellDamageEvent} to
 * {@code Event} at the {@code EVENT_BUS.post} call) is loaded eagerly. On the default
 * Iron's-less {@code runGameTestServer} profile that aborts the whole server before the
 * {@code IronsCompat.isLoaded()} self-skips get a chance to run.
 *
 * <p>Moving those bodies into a plain class the scanner never touches restores the usual lazy
 * behaviour: nothing here is loaded until a gated test calls it.
 *
 * <p>Iron's-only, and test-only.
 */
final class IronsSpellDamageSupport {

    private IronsSpellDamageSupport() {}

    /**
     * A genuine Iron's spell, or {@code null} if none is registered. Never one of ANS's
     * {@code ars_cross_*} proxies: the Iron's bridge is required to skip those, which would make
     * a bonus test pass for the wrong reason.
     *
     * <p>Returned as {@link Object} so the caller can hold it without naming an Iron's type.
     */
    static Object nativeSpell() {
        for (AbstractSpell spell : SpellRegistry.REGISTRY.get().getValues()) {
            if (spell == SpellRegistry.none() || !spell.isEnabled()
                || CrossCastNbt.isArsCrossProxyId(spell.getSpellId())) {
                continue;
            }
            return spell;
        }
        return null;
    }

    /**
     * Post a real Iron's {@link SpellDamageEvent} for {@code spell} and return the amount the
     * bus left behind.
     *
     * @param spell an {@code AbstractSpell} from {@link #nativeSpell()}
     */
    static float postSpellDamage(ServerPlayer player, Object spell, float amount) {
        SpellDamageSource source = SpellDamageSource.source(player, (AbstractSpell) spell);
        SpellDamageEvent event = new SpellDamageEvent(player, amount, source);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getAmount();
    }
}
