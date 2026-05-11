package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.SpellScalingUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wires {@link SpellScalingUtil} into the Ars Nouveau spell-damage path.
 *
 * <p>{@code SpellScalingUtil} reads Iron's Spellbooks {@code SPELL_POWER} and
 * elemental attributes; before 1.9.0 it had no callers from gameplay code, so
 * the README's "Ars spell potency scales with Iron's spell power" claim was
 * untrue. This handler is the missing connector:
 *
 * <ol>
 *   <li>On Ars {@code SpellCastEvent}, compute the scaling multiplier with
 *       {@link SpellScalingUtil#getMultiplierForCaster} and stage it for the
 *       casting player with a short tick window.</li>
 *   <li>On {@link LivingHurtEvent} from a magic-flavored damage source whose
 *       attacker is the marked player, multiply the damage amount by the
 *       staged multiplier.</li>
 * </ol>
 *
 * <p>The tick window covers spell projectile travel and delayed AOE ticks.
 * The damage-source filter avoids scaling unrelated melee or environmental
 * damage from the same player during the window.
 *
 * <p>Iron's-only: must only be registered when Iron's is loaded
 * (see {@link com.otectus.arsnspells.ArsNSpells}).
 */
public class ArsSpellScalingHandler {

    private static final int WINDOW_TICKS = 60; // 3 seconds at 20 TPS
    private static final Map<UUID, ScalingEntry> ACTIVE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onArsSpellCast(SpellCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            float multiplier = SpellScalingUtil.getMultiplierForCaster(player, event.spell);
            // Only stage when scaling actually changes the outcome.
            if (multiplier > 1.001f || multiplier < 0.999f) {
                ACTIVE.put(player.getUUID(), new ScalingEntry(multiplier, player.tickCount + WINDOW_TICKS));
            }
        } catch (Throwable t) {
            // SpellScalingUtil reads Iron's attributes; if anything goes wrong
            // we silently skip scaling rather than blocking the cast.
            ACTIVE.remove(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent event) {
        DamageSource src = event.getSource();
        if (src == null) {
            return;
        }
        Entity attacker = src.getEntity();
        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }
        ScalingEntry entry = ACTIVE.get(player.getUUID());
        if (entry == null) {
            return;
        }
        if (player.tickCount > entry.expiryTick) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        if (!isSpellDamage(src.getMsgId())) {
            return;
        }
        float scaled = (float) Math.min(
            event.getAmount() * entry.multiplier,
            event.getAmount() * AnsConfig.SPELL_POWER_CAP.get());
        event.setAmount(scaled);
    }

    /**
     * Heuristic for "this is probably an Ars spell hit." Ars uses vanilla
     * {@code magic} / {@code indirectMagic} for many effects; specific Ars
     * damage types include the {@code ars_nouveau:} namespace prefix.
     * Filtering keeps the scaling away from melee or environmental damage
     * the same player happens to deal during the window.
     */
    private static boolean isSpellDamage(String type) {
        if (type == null) return false;
        return type.contains("magic")
            || type.contains("ars_nouveau")
            || type.equals("onFire")
            || type.equals("inFire");
    }

    private static final class ScalingEntry {
        final float multiplier;
        final int expiryTick;

        ScalingEntry(float multiplier, int expiryTick) {
            this.multiplier = multiplier;
            this.expiryTick = expiryTick;
        }
    }
}
