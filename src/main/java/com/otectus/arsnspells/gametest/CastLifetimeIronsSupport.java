package com.otectus.arsnspells.gametest;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.AABB;

/**
 * Iron's-typed helpers for {@link CastLifetimeGameTests}. Kept out of the holder class: Forge resolves
 * every method a {@code @GameTestHolder} declares, so an Iron's type there would break the whole class
 * in the Iron's-absent profile.
 */
final class CastLifetimeIronsSupport {
    private CastLifetimeIronsSupport() {}

    /** Iron's regenerates {@code max * MANA_REGEN * 0.01 * multiplier} per regen tick: zero attribute, zero regen. */
    static double disableIronsRegen(ServerPlayer player) {
        AttributeInstance regen = player.getAttribute(AttributeRegistry.MANA_REGEN.get());
        if (regen == null) return Double.NaN;
        double before = regen.getBaseValue();
        regen.setBaseValue(0.0);
        return before;
    }

    static void restoreIronsRegen(ServerPlayer player, double base) {
        AttributeInstance regen = player.getAttribute(AttributeRegistry.MANA_REGEN.get());
        if (regen != null && Double.isFinite(base)) regen.setBaseValue(base);
    }

    /** Native magic projectiles (a Magma Bomb's Fire Bomb, Fire Breath's flames) the player owns in {@code box}. */
    static int projectilesOwnedBy(ServerPlayer player, AABB box) {
        return player.level().getEntitiesOfClass(AbstractMagicProjectile.class, box, p -> p.getOwner() == player).size();
    }

    /** Native lingering areas (a Magma Bomb's Fire Field, Poison Splash's cloud) the player owns in {@code box}. */
    /**
     * 3.4.1 review CAST-04: throw a {@link LinkageError} the next time an imported invocation's native
     * code adds an entity other than {@code player}, as an addon built against another Iron's build does
     * (Wind's Spellbooks 1.0.1's Tornado: NoSuchFieldError). Close the result to disarm.
     */
    static AutoCloseable armSpawnLinkage(ServerPlayer player) {
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> listener = event -> {
            if (!event.getLevel().isClientSide() && event.getEntity() != player
                    && com.otectus.arsnspells.compat.irons.glyph.IronTargetingScope.current() != null)
                throw new NoSuchFieldError("ANS GameTest linkage fault: " + event.getEntity().getType());
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false,
            net.minecraftforge.event.entity.EntityJoinLevelEvent.class, listener);
        return () -> net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
    }

    static int areasOwnedBy(ServerPlayer player, AABB box) {
        return player.level().getEntitiesOfClass(AoeEntity.class, box, a -> a.getOwner() == player).size();
    }

    static float health(LivingEntity entity) {
        return entity.getHealth();
    }
}
