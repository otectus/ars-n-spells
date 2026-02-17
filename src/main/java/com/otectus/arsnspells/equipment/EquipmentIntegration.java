package com.otectus.arsnspells.equipment;

import com.google.common.collect.Multimap;
import com.hollingsworth.arsnouveau.api.mana.IManaEquipment;
import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.hollingsworth.arsnouveau.api.util.CuriosUtil;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles equipment integration between Ars Nouveau and Iron's Spellbooks.
 * Provides unified mana bonuses, cross-mod enchantment support, and armor compatibility.
 */
public class EquipmentIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentIntegration.class);
    
    // Cache for expensive calculations
    private static final Map<UUID, CachedEquipmentData> equipmentCache = new HashMap<>();
    private static final long CACHE_DURATION_MS = 1000; // 1 second cache

    private static final UUID ARS_TO_IRON_MAX_MANA_ID = UUID.fromString("d3e1f1d1-6b39-4ec7-9a4a-7e6d706a8b9b");
    private static final UUID ARS_TO_IRON_REGEN_ID = UUID.fromString("0c2c7e6a-44e8-4cc6-9b5d-5a43a0e5f23b");

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
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }

        ManaBonus arsBonus = getArsManaBonuses(player);
        double maxMana = arsBonus.maxMana * conversionRate;
        double regen = arsBonus.manaRegen * conversionRate;

        applyAttributeModifier(player, AttributeRegistry.MAX_MANA.get(), ARS_TO_IRON_MAX_MANA_ID,
            "Ars Gear Max Mana", maxMana);
        applyAttributeModifier(player, AttributeRegistry.MANA_REGEN.get(), ARS_TO_IRON_REGEN_ID,
            "Ars Gear Mana Regen", regen);
    }

    /**
     * Remove Ars-derived mana bonuses from Iron's attributes.
     */
    public static void clearArsBonusesFromIrons(Player player) {
        if (player == null) {
            return;
        }
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        removeAttributeModifier(player, AttributeRegistry.MAX_MANA.get(), ARS_TO_IRON_MAX_MANA_ID);
        removeAttributeModifier(player, AttributeRegistry.MANA_REGEN.get(), ARS_TO_IRON_REGEN_ID);
    }
    
    private static CachedEquipmentData calculateBonuses(Player player) {
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return CachedEquipmentData.EMPTY;
        }

        CachedEquipmentData cached = equipmentCache.get(player.getUUID());
        long currentTime = System.currentTimeMillis();
        if (cached != null && (currentTime - cached.timestamp) < CACHE_DURATION_MS) {
            return cached;
        }

        double arsMaxBonus = 0.0;
        double arsRegenBonus = 0.0;
        double ironMaxBonus = 0.0;
        double ironRegenBonus = 0.0;

        boolean ironsLoaded = ModList.get().isLoaded("irons_spellbooks");

        for (EquipmentSlot slot : EQUIPPED_SLOTS) {
            ItemStack item = player.getItemBySlot(slot);
            if (item.isEmpty()) {
                continue;
            }

            ItemBonuses itemBonuses = calculateItemBonuses(item, slot, ironsLoaded);
            arsMaxBonus += itemBonuses.arsBonus.maxMana;
            arsRegenBonus += itemBonuses.arsBonus.manaRegen;
            ironMaxBonus += itemBonuses.ironBonus.maxMana;
            ironRegenBonus += itemBonuses.ironBonus.manaRegen;
        }

        // Curios (if present) - only use enchantment + IManaEquipment fallbacks
        try {
            IItemHandlerModifiable curios = CuriosUtil.getAllWornItems(player).orElse(null);
            if (curios != null) {
                for (int i = 0; i < curios.getSlots(); i++) {
                    ItemStack item = curios.getStackInSlot(i);
                    if (item.isEmpty()) {
                        continue;
                    }
                    ItemBonuses itemBonuses = calculateCurioBonuses(item);
                    arsMaxBonus += itemBonuses.arsBonus.maxMana;
                    arsRegenBonus += itemBonuses.arsBonus.manaRegen;
                    ironMaxBonus += itemBonuses.ironBonus.maxMana;
                    ironRegenBonus += itemBonuses.ironBonus.manaRegen;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Curios integration unavailable: {}", e.getMessage());
        }

        // Calculate curio discounts
        CurioDiscountData curioDiscounts = calculateCurioDiscountsInternal(player);

        CachedEquipmentData computed = new CachedEquipmentData(
            new ManaBonus(arsMaxBonus, arsRegenBonus),
            new ManaBonus(ironMaxBonus, ironRegenBonus),
            curioDiscounts,
            currentTime
        );
        equipmentCache.put(player.getUUID(), computed);

        logDebug("Calculated Ars bonuses for {}: max={}, regen={}",
            player.getName().getString(), arsMaxBonus, arsRegenBonus);
        logDebug("Calculated Iron bonuses for {}: max={}, regen={}",
            player.getName().getString(), ironMaxBonus, ironRegenBonus);
        logDebug("Calculated curio discounts for {}: virtue={}, blasphemy={}, total={:.1f}%",
            player.getName().getString(), curioDiscounts.hasVirtueRing, 
            curioDiscounts.hasBlasphemy, curioDiscounts.totalDiscount * 100);

        return computed;
    }

    private static ItemBonuses calculateItemBonuses(ItemStack item, EquipmentSlot slot, boolean ironsLoaded) {
        double arsMax = 0.0;
        double arsRegen = 0.0;
        double ironMax = 0.0;
        double ironRegen = 0.0;

        try {
            Multimap<Attribute, AttributeModifier> modifiers = item.getAttributeModifiers(slot);
            arsMax += sumModifiers(modifiers, PerkAttributes.MAX_MANA.get());
            arsRegen += sumModifiers(modifiers, PerkAttributes.MANA_REGEN_BONUS.get());
            if (ironsLoaded) {
                ironMax += sumModifiers(modifiers, AttributeRegistry.MAX_MANA.get());
                ironRegen += sumModifiers(modifiers, AttributeRegistry.MANA_REGEN.get());
            }
        } catch (Exception e) {
            LOGGER.debug("Failed reading attribute modifiers for {}", item, e);
        }

        // Ars equipment API fallbacks (some items only implement IManaEquipment)
        if (item.getItem() instanceof IManaEquipment manaEquipment) {
            arsMax += manaEquipment.getMaxManaBoost(item);
            arsRegen += manaEquipment.getManaRegenBonus(item);
        }

        // Ars enchantment heuristics (applied to Ars side)
        ManaBonus enchantBonus = getEnchantmentManaBonus(item);
        arsMax += enchantBonus.maxMana;
        arsRegen += enchantBonus.manaRegen;

        // Generic name-based fallback for mage gear (applied to both sides)
        if (item.getItem() instanceof ArmorItem && arsMax == 0.0 && ironMax == 0.0) {
            double fallback = getGenericArmorBonus(item);
            if (fallback != 0.0) {
                arsMax += fallback;
                ironMax += fallback;
            }
        }

        return new ItemBonuses(new ManaBonus(arsMax, arsRegen), new ManaBonus(ironMax, ironRegen));
    }

    private static ItemBonuses calculateCurioBonuses(ItemStack item) {
        double arsMax = 0.0;
        double arsRegen = 0.0;
        double ironMax = 0.0;
        double ironRegen = 0.0;

        if (item.getItem() instanceof IManaEquipment manaEquipment) {
            arsMax += manaEquipment.getMaxManaBoost(item);
            arsRegen += manaEquipment.getManaRegenBonus(item);
        }

        ManaBonus enchantBonus = getEnchantmentManaBonus(item);
        arsMax += enchantBonus.maxMana;
        arsRegen += enchantBonus.manaRegen;

        return new ItemBonuses(new ManaBonus(arsMax, arsRegen), new ManaBonus(ironMax, ironRegen));
    }

    private static double sumModifiers(Multimap<Attribute, AttributeModifier> modifiers, Attribute attribute) {
        if (modifiers == null || attribute == null) {
            return 0.0;
        }
        double total = 0.0;
        for (AttributeModifier modifier : modifiers.get(attribute)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                total += modifier.getAmount();
            }
        }
        return total;
    }

    /**
     * Get generic armor bonus (for any mage armor).
     */
    private static double getGenericArmorBonus(ItemStack armor) {
        try {
            String itemName = armor.getItem().toString().toLowerCase();
            if (itemName.contains("mage") || itemName.contains("wizard") ||
                itemName.contains("sorcerer") || itemName.contains("archmage")) {
                return 25.0;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get generic armor bonus", e);
        }
        return 0.0;
    }
    
    /**
     * Get mana bonus from enchantments
     */
    private static ManaBonus getEnchantmentManaBonus(ItemStack armor) {
        if (!AnsConfig.respectEnchantments.get()) {
            return ManaBonus.ZERO;
        }
        
        double maxBonus = 0.0;
        double regenBonus = 0.0;
        
        try {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(armor);
            
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                Enchantment enchantment = entry.getKey();
                int level = entry.getValue();
                
                // Check for mana-related enchantments
                String enchantName = enchantment.getDescriptionId().toLowerCase();
                
                if (enchantName.contains("mana_regen") || enchantName.contains("mana_regeneration")) {
                    regenBonus += level;
                } else if (enchantName.contains("mana") || enchantName.contains("source")) {
                    maxBonus += level * 50;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get enchantment mana bonus", e);
        }
        
        return new ManaBonus(maxBonus, regenBonus);
    }
    
    /**
     * Calculate total spell power bonus from equipment
     */
    public static double calculateTotalSpellPowerBonus(Player player) {
        double totalBonus = 0.0;
        
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            
            ItemStack armorPiece = player.getItemBySlot(slot);
            
            if (armorPiece.isEmpty()) {
                continue;
            }
            
            // Simplified spell power calculation (fallback for Ars mage armor)
            String itemName = armorPiece.getItem().toString().toLowerCase();
            if (itemName.contains("arcanist") || itemName.contains("mage")) {
                totalBonus += 0.05; // 5% per piece
            }
        }
        
        return totalBonus;
    }
    
    /**
     * Check if a player has any mage armor equipped
     */
    public static boolean hasMageArmorEquipped(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            
            ItemStack armorPiece = player.getItemBySlot(slot);
            
            if (armorPiece.isEmpty()) {
                continue;
            }
            
            String itemName = armorPiece.getItem().toString().toLowerCase();
            if (itemName.contains("mage") || itemName.contains("wizard")) {
                return true;
            }
        }
        
        return false;
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

    /**
     * Get curio discount data for a player (cached).
     * 
     * @param player The player
     * @return Curio discount data
     */
    public static CurioDiscountData getCurioDiscounts(Player player) {
        if (!AnsConfig.ENABLE_CURIO_DISCOUNTS.get()) {
            return CurioDiscountData.NONE;
        }
        
        CachedEquipmentData cached = equipmentCache.get(player.getUUID());
        long currentTime = System.currentTimeMillis();
        
        if (cached != null && (currentTime - cached.timestamp) < CACHE_DURATION_MS) {
            return cached.curioDiscounts;
        }
        
        // Cache miss - will be recalculated on next equipment scan
        return calculateCurioDiscounts(player);
    }
    
    /**
     * Calculate curio discount data for a player.
     * 
     * @param player The player
     * @return Curio discount data
     */
    private static CurioDiscountData calculateCurioDiscounts(Player player) {
        // This will be populated by SanctifiedLegacyCompat
        // For now, return empty data - actual calculation happens in CurioDiscountHandler
        return CurioDiscountData.NONE;
    }
    
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
        
        // Import SanctifiedLegacyCompat at the top of the file
        // Check if Sanctified Legacy is available
        if (!ModList.get().isLoaded("covenant_of_the_seven")) {
            return CurioDiscountData.NONE;
        }
        
        try {
            // Use reflection-safe approach
            Class<?> compatClass = Class.forName("com.otectus.arsnspells.compat.SanctifiedLegacyCompat");
            java.lang.reflect.Method hasVirtueMethod = compatClass.getMethod("hasVirtueRing", Player.class);
            java.lang.reflect.Method hasBlasphemyMethod = compatClass.getMethod("hasAnyBlasphemy", Player.class);
            
            boolean hasVirtue = (boolean) hasVirtueMethod.invoke(null, player);
            boolean hasBlasphemy = (boolean) hasBlasphemyMethod.invoke(null, player);
            
            // Calculate base discount (will be refined per-spell in CurioDiscountHandler)
            double baseDiscount = 0.0;
            if (hasVirtue) {
                baseDiscount = AnsConfig.VIRTUE_RING_DISCOUNT.get();
            }
            if (hasBlasphemy && (AnsConfig.ALLOW_DISCOUNT_STACKING.get() || !hasVirtue)) {
                baseDiscount += AnsConfig.BLASPHEMY_DISCOUNT.get();
            }
            
            return new CurioDiscountData(hasVirtue, hasBlasphemy, baseDiscount);
        } catch (Exception e) {
            logDebug("Failed to calculate curio discounts: {}", e.getMessage());
            return CurioDiscountData.NONE;
        }
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

    private static void removeAttributeModifier(Player player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(id);
        }
    }
}
