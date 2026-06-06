package com.otectus.arsnspells.compat.curios;

import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.compat.ModPresence;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.events.CurioDiscountHandler;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iron's-side counterpart to {@link CurioDiscountHandler}: applies the same
 * {@code #ars_n_spells:curio_spell_discount} tagged-curio mana discount to Iron's
 * spell casts via {@link SpellOnCastEvent#setManaCost(int)}. Before 2.5.0 the
 * discount existed only on the Ars side.
 *
 * <p><b>Iron's-only.</b> This class imports Iron's event types, so it is never
 * auto-registered; {@code ArsNSpells} registers it only inside its
 * {@code isLoaded("irons_spellbooks")} block, keeping Iron's classes off the
 * always-loaded path (the dedicated-server-safety rule).
 *
 * <p>Runs at {@link EventPriority#LOW} so it composes multiplicatively on top of
 * {@link com.otectus.arsnspells.spell.CrossCastIronsHandler} (which runs at
 * NORMAL): the discount applies to whatever the cost ended up being after any
 * cross-cast / conversion adjustment. The combined discount is clamped by
 * {@link AnsConfig#MAX_TOTAL_CURIO_DISCOUNT}.
 *
 * @since 2.5.0
 */
public final class IronsCurioDiscountHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsCurioDiscountHandler.class);

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onIronsSpellCast(SpellOnCastEvent event) {
        if (!AnsConfig.ENABLE_CURIO_DISCOUNTS.get()) {
            return;
        }
        if (!ModPresence.isLoaded(CompatIds.CURIOS)) {
            return;
        }
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        int matching = CuriosAccess.countTagged(player, CurioDiscountHandler.CURIO_SPELL_DISCOUNT_TAG);
        if (matching <= 0) {
            return;
        }
        double perCurio = AnsConfig.VIRTUE_RING_DISCOUNT.get();
        if (perCurio <= 0.0) {
            return;
        }
        double factor = Math.pow(Math.max(0.0, 1.0 - perCurio), matching);
        // Clamp combined discount so stacked curios can't exceed the configured cap.
        factor = Math.max(factor, 1.0 - AnsConfig.MAX_TOTAL_CURIO_DISCOUNT.get());
        int original = event.getManaCost();
        int discounted = (int) Math.max(original > 0 ? 1 : 0, Math.round(original * factor));
        event.setManaCost(discounted);

        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CurioDiscount][Irons] {} matching curios -> {} (was {})",
                matching, discounted, original);
        }
    }
}
