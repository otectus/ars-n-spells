package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.glyph.runtime.GlyphInvocationScope;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.function.Consumer;

/**
 * Iron's-typed helpers for the 3.4.1 review GameTests (CAST-01, CAST-03 to CAST-05). Kept out of the
 * holder classes so the Iron's-absent profile never resolves an Iron's type from a test method.
 *
 * <p>The fault injector stands in for a native spell or addon that throws: an exception from a listener
 * reaches the code that posted the event, exactly as one thrown by the spell's own code would.
 */
final class CastReviewIronsSupport {
    private CastReviewIronsSupport() {}

    /** Where an injected failure is thrown. */
    enum Fault {
        /** Iron's final cast event: before the payment boundary. */
        CAST_EVENT,
        /** An entity added by an imported invocation's native code: after the effect began. */
        SPAWN,
        /**
         * The same point, failing with a {@link LinkageError} (3.4.1 review CAST-04): what an addon built
         * against another Iron's build throws (Wind's Spellbooks 1.0.1's Tornado: NoSuchFieldError).
         */
        SPAWN_LINKAGE,
        /**
         * A {@link LinkageError} when a projectile owned by the player joins the world, from any code path:
         * a charge's release runs outside the invocation scope {@link #SPAWN} keys on.
         */
        OWNED_PROJECTILE_LINKAGE
    }

    /** Throw at the {@code occurrence}-th (1-based) {@code fault} point for {@code player}'s {@code spellId}. */
    static AutoCloseable arm(ServerPlayer player, Fault fault, String spellId, int occurrence) {
        int[] seen = {0};
        if (fault == Fault.CAST_EVENT) {
            Consumer<SpellOnCastEvent> listener = event -> {
                if (event.getEntity() == player && spellId.equals(event.getSpellId()) && ++seen[0] == occurrence)
                    throw new IllegalStateException("ANS GameTest fault at the cast event of " + spellId);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, SpellOnCastEvent.class, listener);
            return () -> NeoForge.EVENT_BUS.unregister(listener);
        }
        if (fault == Fault.OWNED_PROJECTILE_LINKAGE) {
            Consumer<EntityJoinLevelEvent> owned = event -> {
                if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                        && projectile.getOwner() == player && ++seen[0] == occurrence)
                    throw new NoSuchFieldError("ANS GameTest linkage fault: " + spellId + " released " + event.getEntity().getType());
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, owned);
            return () -> NeoForge.EVENT_BUS.unregister(owned);
        }
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (!event.getLevel().isClientSide() && event.getEntity() != player && GlyphInvocationScope.active() && ++seen[0] == occurrence) {
                if (fault == Fault.SPAWN_LINKAGE)
                    throw new NoSuchFieldError("ANS GameTest linkage fault after " + spellId + " spawned " + event.getEntity().getType());
                throw new IllegalStateException("ANS GameTest fault after " + spellId + " spawned " + event.getEntity().getType());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, listener);
        return () -> NeoForge.EVENT_BUS.unregister(listener);
    }

    static void disarm(AutoCloseable fault) {
        try {
            fault.close();
        } catch (Exception ignored) {
            // Unregistering a listener cannot fail meaningfully.
        }
    }

    /** Iron's regenerates in proportion to {@code MANA_REGEN}: zero it, returning the previous base value. */
    static double disableIronsRegen(ServerPlayer player) {
        AttributeInstance regen = player.getAttribute(AttributeRegistry.MANA_REGEN);
        if (regen == null) return Double.NaN;
        double before = regen.getBaseValue();
        regen.setBaseValue(0.0);
        return before;
    }

    static void restoreIronsRegen(ServerPlayer player, double base) {
        AttributeInstance regen = player.getAttribute(AttributeRegistry.MANA_REGEN);
        if (regen != null && Double.isFinite(base)) regen.setBaseValue(base);
    }

    static int projectilesOwnedBy(ServerPlayer player, AABB box) {
        return player.level().getEntitiesOfClass(AbstractMagicProjectile.class, box, p -> p.getOwner() == player).size();
    }

    static int areasOwnedBy(ServerPlayer player, AABB box) {
        return player.level().getEntitiesOfClass(AoeEntity.class, box, a -> a.getOwner() == player).size();
    }
}
