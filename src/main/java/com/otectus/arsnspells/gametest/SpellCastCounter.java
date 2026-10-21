package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Counts Ars {@link SpellCastEvent}s fired for one caster while an action runs.
 *
 * <p>Ars posts that event once per resolver entry that passes validation and mana checks, before
 * the form runs, so the count is the number of casts the action actually started. It is how the
 * overlap scenarios prove that one click is one cast, independent of what the cast hit.
 */
final class SpellCastCounter {

    private final UUID caster;
    private int count;
    private int uncanceled;

    private SpellCastCounter(UUID caster) {
        this.caster = caster;
    }

    @SubscribeEvent
    public void onCast(SpellCastEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && caster.equals(entity.getUUID())) {
            count++;
            if (!event.isCanceled()) uncanceled++;
        }
    }

    /** Run {@code action} and return how many Ars casts it started for {@code casterId}. */
    static int count(UUID casterId, Runnable action) {
        return measure(casterId, () -> {
            action.run();
            return null;
        }).count;
    }

    static SpellCastCounter measure(UUID casterId, Supplier<?> action) {
        SpellCastCounter counter = new SpellCastCounter(casterId);
        NeoForge.EVENT_BUS.register(counter);
        try {
            action.get();
        } finally {
            NeoForge.EVENT_BUS.unregister(counter);
        }
        return counter;
    }

    int total() {
        return count;
    }

    int uncanceled() {
        return uncanceled;
    }
}
