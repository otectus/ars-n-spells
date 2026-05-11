package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a configurable mana discount to Ars spell casts when the player
 * is wearing any curio whose stack carries the
 * {@code #ars_n_spells:curio_spell_discount} item tag.
 *
 * <p>Replaces the Sanctified Legacy "Blasphemy curio" feature footprint
 * deleted during the NeoForge 1.21.1 port (Branch B, no upstream 5.x build
 * of Sanctified Legacy / Covenant of the Seven). Pack authors populate
 * the tag with whichever curio items they want to grant a spell discount;
 * the per-curio discount fraction is shared globally via
 * {@link AnsConfig#VIRTUE_RING_DISCOUNT} (kept for config continuity —
 * a future release may rename the key).
 *
 * <p>Curios is optional. The handler is annotation-registered but guards
 * every Curios API call behind {@link ModList#isLoaded(String)} so an
 * Ars+Iron's-without-Curios install is safe.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class CurioDiscountHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurioDiscountHandler.class);

    /** Item tag — any worn-curio stack matching this tag grants the discount. */
    public static final TagKey<net.minecraft.world.item.Item> CURIO_SPELL_DISCOUNT_TAG = ItemTags.create(
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
        if (!ModList.get().isLoaded("curios")) {
            return;
        }
        LivingEntity caster = event.context.getUnwrappedCaster();
        if (!(caster instanceof Player player)) {
            return;
        }

        int matching = countDiscountCurios(player);
        if (matching <= 0) {
            return;
        }

        double perCurio = AnsConfig.VIRTUE_RING_DISCOUNT.get();
        if (perCurio <= 0.0) {
            return;
        }
        // Multiplicative stack: each tagged curio multiplies cost by (1 - perCurio),
        // clamped to a 1-mana floor so spells never round to free unless 0-cost already.
        double factor = Math.pow(Math.max(0.0, 1.0 - perCurio), matching);
        int original = event.currentCost;
        int discounted = (int) Math.max(original > 0 ? 1 : 0, Math.round(original * factor));
        event.currentCost = discounted;

        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CurioDiscount] {} matching curios -> {}% cost -> {} (was {})",
                matching, (int) (factor * 100), discounted, original);
        }
    }

    /**
     * Count the player's worn curios whose ItemStack carries the
     * {@link #CURIO_SPELL_DISCOUNT_TAG} item tag. Returns 0 if Curios is
     * absent or any inventory access throws.
     */
    private static int countDiscountCurios(Player player) {
        try {
            return CuriosAccess.countTagged(player, CURIO_SPELL_DISCOUNT_TAG);
        } catch (Throwable t) {
            // Defensive: if Curios's API changed underfoot, swallow rather than crash casting.
            return 0;
        }
    }

    /**
     * Reflection-based Curios slot iteration. Curios is not pinned on the
     * compile classpath (the user's instance ships the runtime jar, but
     * `gradle.properties` does not include a CurseMaven file id for Curios
     * — a follow-up can add one and switch this to direct API calls).
     * Until then we reach Curios reflectively so the handler still
     * functions at runtime when Curios is present. Methods are cached
     * after first lookup to keep the SpellCostCalcEvent hot path cheap.
     */
    private static final class CuriosAccess {
        private static volatile boolean initAttempted = false;
        private static volatile Class<?> curiosApi;
        private static volatile java.lang.reflect.Method mGetInventory;
        private static volatile java.lang.reflect.Method mGetCurios;
        private static volatile java.lang.reflect.Method mGetStacks;
        private static volatile java.lang.reflect.Method mGetSlots;
        private static volatile java.lang.reflect.Method mGetStackInSlot;

        static int countTagged(Player player, TagKey<net.minecraft.world.item.Item> tag) {
            if (!resolve()) return 0;
            try {
                Object optional = mGetInventory.invoke(null, player);
                if (!(optional instanceof java.util.Optional<?> opt) || opt.isEmpty()) {
                    return 0;
                }
                Object handler = opt.get();
                if (mGetCurios == null) mGetCurios = handler.getClass().getMethod("getCurios");
                Object slots = mGetCurios.invoke(handler);
                if (!(slots instanceof java.util.Map<?, ?> slotMap)) return 0;
                int count = 0;
                for (Object entry : slotMap.values()) {
                    if (mGetStacks == null) mGetStacks = entry.getClass().getMethod("getStacks");
                    Object inv = mGetStacks.invoke(entry);
                    if (mGetSlots == null) mGetSlots = inv.getClass().getMethod("getSlots");
                    int slotCount = (int) mGetSlots.invoke(inv);
                    if (mGetStackInSlot == null) mGetStackInSlot =
                        inv.getClass().getMethod("getStackInSlot", int.class);
                    for (int i = 0; i < slotCount; i++) {
                        Object obj = mGetStackInSlot.invoke(inv, i);
                        if (obj instanceof ItemStack stack && !stack.isEmpty() && stack.is(tag)) {
                            count++;
                        }
                    }
                }
                return count;
            } catch (Throwable t) {
                return 0;
            }
        }

        private static boolean resolve() {
            if (curiosApi != null) return true;
            if (initAttempted) return false;
            initAttempted = true;
            try {
                curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi", false,
                    CurioDiscountHandler.class.getClassLoader());
                mGetInventory = curiosApi.getMethod("getCuriosInventory",
                    net.minecraft.world.entity.LivingEntity.class);
                return true;
            } catch (Throwable t) {
                curiosApi = null;
                return false;
            }
        }
    }
}
