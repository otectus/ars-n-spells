package com.otectus.arsnspells.equipment;

import com.google.common.collect.Multimap;
import com.hollingsworth.arsnouveau.api.mana.IManaEquipment;
import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.otectus.arsnspells.bridge.AnsFeatureCleanup;
import com.otectus.arsnspells.bridge.AnsModifierIdentities;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.SharedPoolCeiling;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.AnsModifierIds;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.item.ItemStack;


import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles equipment integration between Ars Nouveau and Iron's Spellbooks.
 * Provides unified mana bonuses, cross-mod enchantment support, and armor compatibility.
 */
public class EquipmentIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentIntegration.class);

    // ANS-HIGH-014: ConcurrentHashMap (was HashMap). Mutated from LivingEquipmentChangeEvent,
    // PlayerLoggedOutEvent, tick handler, and cost-calc spell handler — HashMap.put mid-resize
    // during another path's iteration produced CME on long-running servers. Matches the
    // ConcurrentHashMap pattern in SanctifiedLegacyCompat.java:72.
    private static final Map<UUID, CachedEquipmentData> equipmentCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 1000; // 1 second cache

    // V07/V14: read from the shared registry rather than re-declaring the literal. These are
    // the same two identities AnsFeatureCleanup walks, so a modifier applied here can never be
    // one the cleanup path has never heard of.
    private static final UUID ARS_TO_IRON_MAX_MANA_ID =
        AnsModifierIdentities.uuid(AnsModifierIds.ARS_GEAR_MAX_MANA);
    private static final UUID ARS_TO_IRON_REGEN_ID =
        AnsModifierIdentities.uuid(AnsModifierIds.ARS_GEAR_MANA_REGEN);


    private static final EquipmentSlot[] EQUIPPED_SLOTS = new EquipmentSlot[] {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND
    };
    
    /**
     * Calculate total unified mana bonus from all equipment (Ars-derived bonuses).
     */
    public static double calculateTotalManaBonus(Player player) {
        return getArsManaBonuses(player).maxMana;
    }

    /**
     * Calculate Ars-derived mana bonuses (from Ars gear/enchantments).
     */
    public static ManaBonus getArsManaBonuses(Player player) {
        return calculateBonuses(player).arsBonus;
    }

    /**
     * Calculate Iron-derived mana bonuses (from Iron's gear/attributes).
     */
    public static ManaBonus getIronManaBonuses(Player player) {
        return calculateBonuses(player).ironBonus;
    }

    /**
     * Apply Ars-derived mana bonuses to Iron's attributes.
     */
    public static void applyArsBonusesToIrons(Player player, double conversionRate) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return;
        }

        ManaBonus arsBonus = getArsManaBonuses(player);
        double maxManaBonus = arsBonus.maxMana * conversionRate;

        // Apply max mana first so the regen conversion sees the post-bonus pool size.
        // Otherwise EQUAL_EFFECT would underestimate the regen attribute delta needed.
        applyAttributeModifier(player, AttributeRegistry.MAX_MANA.get(), ARS_TO_IRON_MAX_MANA_ID,
            "Ars Gear Max Mana", maxManaBonus);

        applyArsRegenBonusToIrons(player, conversionRate);
    }

    /**
     * Apply only the regen half of {@link #applyArsBonusesToIrons}.
     *
     * <p>Split out for HYBRID, where the max-mana half is owned by
     * {@link #syncIronsMaxToArs}: Ars's real max already includes its gear bonuses, so
     * adding the gear bonus a second time under a different modifier would double-count it.
     */
    public static void applyArsRegenBonusToIrons(Player player, double conversionRate) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return;
        }
        ManaBonus arsBonus = getArsManaBonuses(player);
        // Ars regen is absolute mana/sec; Iron's MANA_REGEN is a percentage-of-pool
        // multiplier. Direct assignment is a unit-mismatch bug — go through the bridge.
        double absRegenPerSec = arsBonus.manaRegen * conversionRate;
        double regenAttr = ManaRegenBridge.convertArsToIrons(absRegenPerSec, player);
        applyAttributeModifier(player, AttributeRegistry.MANA_REGEN.get(), ARS_TO_IRON_REGEN_ID,
            "Ars Gear Mana Regen", regenAttr);
    }

    /**
     * Sync Iron's MAX_MANA attribute so it covers Ars's actual max mana.
     *
     * <p>Used by every shared-pool mode. The shortfall is measured against Iron's max
     * <em>with the ANS modifier removed</em>, so the result is exactly
     * {@code max(Ars max, Iron's own max)}: nothing Iron's contributes is voided, and the
     * pool is not inflated past what either system intends. (Measuring against the bare
     * base value, as this used to, re-added the shortfall on top of Iron's own gear.)
     *
     * <p>This is not cosmetic. Iron's {@code MagicData.setMana} clamps <em>every</em> write
     * down to this attribute, so a ceiling below the current pool does not limit mana — it
     * deletes it, on the next write, whatever that write happens to be.
     */
    public static void syncIronsMaxToArs(Player player, float arsMax) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return;
        }
        AttributeInstance instance = player.getAttribute(AttributeRegistry.MAX_MANA.get());
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(ARS_TO_IRON_MAX_MANA_ID);
        java.util.List<AttributeModifier> nativeModifiers = instance.getModifiers().stream()
            .filter(modifier -> !modifier.getId().equals(ARS_TO_IRON_MAX_MANA_ID)).toList();
        double ironsOwnMax = instance.getAttribute().sanitizeValue(
            AttributeContribution.evaluate(instance.getBaseValue(), nativeModifiers));
        double amplification = AttributeContribution.additiveAmplification(nativeModifiers);
        double needed = SharedPoolCeiling.modifierAmount(ironsOwnMax, arsMax, amplification);
        if (existing != null && existing.getAmount() == needed) return;
        if (existing != null) instance.removeModifier(ARS_TO_IRON_MAX_MANA_ID);
        if (needed != 0) instance.addTransientModifier(new AttributeModifier(
            ARS_TO_IRON_MAX_MANA_ID, "Ars Max Mana Sync", needed, AttributeModifier.Operation.ADDITION));
    }
    /**
     * Ars's own computed max mana: config base + glyph bonus + book tier + Ars perk
     * attributes, less any reserve.
     *
     * <p>Read straight from Ars's calculator rather than from {@code ManaCap.getMaxMana()},
     * which ANS's own {@code MixinManaCapability} intercepts — going through the capability
     * would make the ceiling sync depend on the value it is supposed to produce.
     */
    public static float arsRealMaxMana(Player player) {
        if (player == null) {
            return 0.0f;
        }
        try {
            return com.hollingsworth.arsnouveau.api.util.ManaUtil.calcMaxMana(player).getRealMax();
        } catch (Throwable t) {
            LOGGER.debug("Could not read Ars max mana for {}", player.getName().getString(), t);
            return 0.0f;
        }
    }

    /**
     * Re-apply the shared-pool ceiling if it has drifted below Ars's real max.
     *
     * <p>The ceiling is a <em>transient</em> attribute modifier, so it is lost whenever the
     * {@code ServerPlayer} is rebuilt and is otherwise only refreshed on equipment change,
     * login and respawn. This is the cheap last line of defence on the hot path: one
     * attribute read, and a re-apply only when the ceiling actually cannot hold the pool.
     * Calling it immediately before a deduction is what makes "a cast can never destroy
     * mana" true regardless of what else lagged.
     */
    public static void ensureSharedPoolCeiling(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        // ARS_PRIMARY already drives this attribute from its own MaxManaCalcEvent path;
        // ISS_PRIMARY makes Iron's the pool outright, so its ceiling is correct by
        // construction. HYBRID is the mode where the two maxima can disagree.
        if (mode == null || !mode.isHybrid()) {
            return;
        }
        float arsMax = arsRealMaxMana(player);
        if (arsMax <= 0.0f) {
            return;
        }
        AttributeInstance instance = player.getAttribute(AttributeRegistry.MAX_MANA.get());
        if (instance == null || instance.getValue() >= arsMax) {
            return;
        }
        syncIronsMaxToArs(player, arsMax);
    }

    /**
     * Remove Ars-derived mana bonuses from Iron's attributes.
     *
     * <p>V14: no gate. This used to return early when Iron's was not loaded, which is exactly
     * the state a modifier gets stranded in - the feature that applied it is gone, so the
     * feature can no longer take it off. {@link AnsFeatureCleanup} resolves the attributes by
     * id, so an absent Iron's makes this a no-op instead of a crash, and it also sweeps the
     * legacy identities older builds wrote these bonuses under.
     */
    public static void clearArsBonusesFromIrons(Player player) {
        AnsFeatureCleanup.removeKeys(player,
            AnsModifierIds.ARS_GEAR_MAX_MANA, AnsModifierIds.ARS_GEAR_MANA_REGEN);
    }

    /**
     * Drop only the Ars-derived regen modifier, leaving the shared-pool ceiling in place.
     * Used when {@code respect_armor_bonuses} is off in HYBRID: the gear-derived regen is a
     * bonus the player opted out of, but the ceiling is a correctness invariant.
     */
    public static void clearArsRegenBonusFromIrons(Player player) {
        // V14: ungated, for the same reason as clearArsBonusesFromIrons above.
        AnsFeatureCleanup.removeKeys(player, AnsModifierIds.ARS_GEAR_MANA_REGEN);
    }
    
    private static CachedEquipmentData calculateBonuses(Player player) {
        if (player == null || !BridgeManager.isUnificationEnabled()) return CachedEquipmentData.EMPTY;
        CachedEquipmentData cached = equipmentCache.get(player.getUUID());
        long now = System.currentTimeMillis();
        if (cached != null && now >= cached.timestamp && now - cached.timestamp < CACHE_DURATION_MS) return cached;

        // UUID deduplication matches the native attribute map, including accessories whose
        // modifiers reuse an armor identity. Forge ItemStack emits Ars perks and enchantments
        // through ItemAttributeModifierEvent, so adding manual enchant estimates doubles them.
        Map<Attribute, Map<UUID, AttributeModifier>> contributions = new java.util.HashMap<>();
        double fallbackMax = 0;
        double fallbackRegen = 0;
        for (EquipmentSlot slot : EQUIPPED_SLOTS) {
            ItemStack item = player.getItemBySlot(slot);
            if (item.isEmpty()) continue;
            Multimap<Attribute, AttributeModifier> modifiers = item.getAttributeModifiers(slot);
            collectModifiers(contributions, modifiers);
            if (slot.getType() == EquipmentSlot.Type.ARMOR && item.getItem() instanceof IManaEquipment equipment) {
                if (!modifiers.containsKey(PerkAttributes.MAX_MANA.get())) fallbackMax += equipment.getMaxManaBoost(item);
                if (!modifiers.containsKey(PerkAttributes.MANA_REGEN_BONUS.get())) fallbackRegen += equipment.getManaRegenBonus(item);
            }
        }
        try {
            List<SlotResult> worn = CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findCurios(stack -> !stack.isEmpty())).orElse(Collections.emptyList());
            for (SlotResult result : worn) {
                ItemStack item = result.stack();
                SlotContext context = result.slotContext();
                Multimap<Attribute, AttributeModifier> modifiers = CuriosApi.getAttributeModifiers(
                    context, CuriosApi.getSlotUuid(context), item);
                if (AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.get()) collectModifiers(contributions, modifiers);
                if (item.getItem() instanceof IManaEquipment equipment) {
                    if (!modifiers.containsKey(PerkAttributes.MAX_MANA.get())) fallbackMax += equipment.getMaxManaBoost(item);
                    if (!modifiers.containsKey(PerkAttributes.MANA_REGEN_BONUS.get())) fallbackRegen += equipment.getManaRegenBonus(item);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Curios equipment extraction unavailable", e);
        }
        java.util.Set<UUID> excluded = new java.util.HashSet<>();
        for (String key : AnsModifierIds.allKeys()) {
            for (String identity : AnsModifierIds.currentAndLegacyKeysFor(key)) {
                UUID id = AnsModifierIdentities.MAPPER.map(identity);
                if (id != null) excluded.add(id);
            }
        }
        if (!AnsConfig.respectEnchantments.get()) {
            for (EquipmentSlot slot : EQUIPPED_SLOTS) {
                UUID id = com.hollingsworth.arsnouveau.common.event.ArsEvents.getEnchantBoostBySlot(slot);
                excluded.add(id);
                for (Map<UUID, AttributeModifier> values : contributions.values()) values.remove(id);
            }
        }
        ManaBonus ars = new ManaBonus(fallbackMax + contribution(player, PerkAttributes.MAX_MANA.get(), contributions, excluded),
            fallbackRegen + contribution(player, PerkAttributes.MANA_REGEN_BONUS.get(), contributions, excluded));
        ManaBonus irons = IronsCompat.isLoaded() ? new ManaBonus(
            contribution(player, AttributeRegistry.MAX_MANA.get(), contributions, excluded),
            contribution(player, AttributeRegistry.MANA_REGEN.get(), contributions, excluded)) : ManaBonus.ZERO;
        CachedEquipmentData computed = new CachedEquipmentData(ars, irons, calculateCurioDiscountsInternal(player), now);
        equipmentCache.put(player.getUUID(), computed);
        return computed;
    }

    private static void collectModifiers(Map<Attribute, Map<UUID, AttributeModifier>> contributions,
                                         Multimap<Attribute, AttributeModifier> modifiers) {
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            if (Double.isFinite(entry.getValue().getAmount())) {
                contributions.computeIfAbsent(entry.getKey(), ignored -> new java.util.LinkedHashMap<>())
                    .put(entry.getValue().getId(), entry.getValue());
            }
        }
    }

    private static double contribution(Player player, Attribute attribute,
                                       Map<Attribute, Map<UUID, AttributeModifier>> contributions,
                                       java.util.Set<UUID> excluded) {
        Map<UUID, AttributeModifier> modifiers = contributions.getOrDefault(attribute, Collections.emptyMap());
        return AttributeContribution.equipmentDelta(player.getAttribute(attribute), modifiers.values(), excluded);
    }
    /**
     * Clear equipment cache for a player
     */
    public static void clearCache(Player player) {
        if (player != null) {
            equipmentCache.remove(player.getUUID());
        }
    }
    
    /**
     * Clear all equipment caches
     */
    public static void clearAllCaches() {
        equipmentCache.clear();
    }
    
    /**
     * Log debug message if debug mode is enabled
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[Equipment] [DEBUG] " + message, args);
        }
    }
    
    /**
     * Cached equipment data
     */
    private static class CachedEquipmentData {
        static final CachedEquipmentData EMPTY = new CachedEquipmentData(
            ManaBonus.ZERO, ManaBonus.ZERO, CurioDiscountData.NONE, 0L);

        final ManaBonus arsBonus;
        final ManaBonus ironBonus;
        final CurioDiscountData curioDiscounts;
        final long timestamp;
        
        CachedEquipmentData(ManaBonus arsBonus, ManaBonus ironBonus, CurioDiscountData curioDiscounts, long timestamp) {
            this.arsBonus = arsBonus;
            this.ironBonus = ironBonus;
            this.curioDiscounts = curioDiscounts;
            this.timestamp = timestamp;
        }
    }

    // getCurioDiscounts(Player) and its calculateCurioDiscounts(Player) helper were removed
    // here. Both were unreachable — nothing in the mod called either — and the pair was a trap
    // rather than dead weight: on a cache miss the public getter returned a stub NONE with the
    // comment "actual calculation happens in CurioDiscountHandler", so the first caller to
    // arrive would silently have been told a player has no curio discounts whenever the cache
    // had expired. calculateCurioDiscountsInternal, which genuinely computes the value and is
    // used by the equipment scan, is retained below.

    /**
     * Internal method to calculate curio discounts (called during equipment scan).
     * 
     * @param player The player
     * @return Curio discount data
     */
    private static CurioDiscountData calculateCurioDiscountsInternal(Player player) {
        if (!AnsConfig.ENABLE_CURIO_DISCOUNTS.get()) {
            return CurioDiscountData.NONE;
        }
        if (!ModList.get().isLoaded("covenant_of_the_seven")) {
            return CurioDiscountData.NONE;
        }

        // ANS-MED-021: direct call instead of Class.forName + Method.invoke. The previous
        // reflection setup silently degraded to "no discount" if a future refactor renamed
        // hasVirtueRing or hasAnyBlasphemy. Since SanctifiedLegacyCompat lives in the same
        // mod jar, there is no classloader-safety reason for the reflection.
        boolean hasVirtue = com.otectus.arsnspells.compat.SanctifiedLegacyCompat.hasVirtueRing(player);
        boolean hasBlasphemy = com.otectus.arsnspells.compat.SanctifiedLegacyCompat.hasAnyBlasphemy(player);
        double baseDiscount = hasBlasphemy ? AnsConfig.BLASPHEMY_DISCOUNT.get() : 0.0;
        return new CurioDiscountData(hasVirtue, hasBlasphemy, baseDiscount);
    }

    /**
     * Mana bonus container.
     */
    public static class ManaBonus {
        public static final ManaBonus ZERO = new ManaBonus(0.0, 0.0);

        public final double maxMana;
        public final double manaRegen;

        public ManaBonus(double maxMana, double manaRegen) {
            this.maxMana = maxMana;
            this.manaRegen = manaRegen;
        }
    }
    
    /**
     * Curio discount data container.
     */
    public static class CurioDiscountData {
        public static final CurioDiscountData NONE = new CurioDiscountData(false, false, 0.0);
        
        public final boolean hasVirtueRing;
        public final boolean hasBlasphemy;
        public final double totalDiscount;
        
        public CurioDiscountData(boolean hasVirtueRing, boolean hasBlasphemy, double totalDiscount) {
            this.hasVirtueRing = hasVirtueRing;
            this.hasBlasphemy = hasBlasphemy;
            this.totalDiscount = totalDiscount;
        }
    }

    private static class ItemBonuses {
        final ManaBonus arsBonus;
        final ManaBonus ironBonus;

        ItemBonuses(ManaBonus arsBonus, ManaBonus ironBonus) {
            this.arsBonus = arsBonus;
            this.ironBonus = ironBonus;
        }
    }

    private static void applyAttributeModifier(Player player, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
        }
    }

    // removeAttributeModifier was deleted here: every removal now goes through
    // AnsFeatureCleanup, which is the only path that also walks the legacy identities.
}
