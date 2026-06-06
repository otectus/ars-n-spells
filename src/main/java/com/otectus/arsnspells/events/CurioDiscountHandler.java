package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.compat.ModPresence;
import com.otectus.arsnspells.compat.curios.CuriosAccess;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a configurable mana discount to Ars spell casts when the player is
 * wearing any curio whose stack carries the
 * {@code #ars_n_spells:curio_spell_discount} item tag.
 *
 * <p>Replaces the Sanctified Legacy "Blasphemy curio" feature footprint deleted
 * during the NeoForge 1.21.1 port (Branch B, no upstream 5.x build of Sanctified
 * Legacy / Covenant of the Seven). Pack authors populate the tag with whichever
 * curio items they want to grant a spell discount; the per-curio discount
 * fraction is shared globally via {@link AnsConfig#VIRTUE_RING_DISCOUNT} (kept
 * for config continuity — a future release may rename the key), and the combined
 * discount is clamped by {@link AnsConfig#MAX_TOTAL_CURIO_DISCOUNT}.
 *
 * <p><b>2.5.0:</b> the slot scan is now the shared {@link CuriosAccess} util (so
 * the Iron's-side {@code IronsCurioDiscountHandler} can reuse it), the combined
 * discount is clamped, and the tag ships a sensible default entry.
 *
 * <p>Curios is optional. The handler is annotation-registered but guards every
 * Curios access behind {@link ModPresence#isLoaded(String)} so an
 * Ars+Iron's-without-Curios install is safe.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class CurioDiscountHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurioDiscountHandler.class);

    /** Item tag — any worn-curio stack matching this tag grants the discount. */
    public static final TagKey<Item> CURIO_SPELL_DISCOUNT_TAG = ItemTags.create(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "curio_spell_discount"));

    private CurioDiscountHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSpellCostCalc(SpellCostCalcEvent event) {
        if (event.context == null) {
            return;
        }
        if (!AnsConfig.ENABLE_CURIO_DISCOUNTS.get()) {
            return;
        }
        if (!ModPresence.isLoaded(CompatIds.CURIOS)) {
            return;
        }
        LivingEntity caster = event.context.getUnwrappedCaster();
        if (!(caster instanceof Player player)) {
            return;
        }

        int matching = CuriosAccess.countTagged(player, CURIO_SPELL_DISCOUNT_TAG);
        if (matching <= 0) {
            return;
        }

        double perCurio = AnsConfig.VIRTUE_RING_DISCOUNT.get();
        if (perCurio <= 0.0) {
            return;
        }
        // Multiplicative stack: each tagged curio multiplies cost by (1 - perCurio),
        // then clamped so the combined discount never exceeds the configured cap,
        // and floored at 1 mana so spells never round to free unless 0-cost already.
        double factor = Math.pow(Math.max(0.0, 1.0 - perCurio), matching);
        factor = Math.max(factor, 1.0 - AnsConfig.MAX_TOTAL_CURIO_DISCOUNT.get());
        int original = event.currentCost;
        int discounted = (int) Math.max(original > 0 ? 1 : 0, Math.round(original * factor));
        event.currentCost = discounted;

        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CurioDiscount] {} matching curios -> {}% cost -> {} (was {})",
                matching, (int) (factor * 100), discounted, original);
        }
    }
}
