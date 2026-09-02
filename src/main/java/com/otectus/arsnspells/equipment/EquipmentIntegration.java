package com.otectus.arsnspells.equipment;

import com.google.common.collect.Multimap;
import com.hollingsworth.arsnouveau.api.mana.IManaEquipment;
import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.SharedPoolCeiling;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
        // Skip the remove/add churn when the ceiling is unchanged; MAX_MANA is syncable and each dirty broadcasts an attribute packet.
        if (existing != null) {
            double ownMax = instance.getBaseValue();
            boolean additiveOnly = true;
            for (AttributeModifier m : instance.getModifiers()) {
                if (m.getOperation() != AttributeModifier.Operation.ADDITION) { additiveOnly = false; break; }
                if (!m.getId().equals(ARS_TO_IRON_MAX_MANA_ID)) ownMax += m.getAmount();
            }
            if (additiveOnly
                    && SharedPoolCeiling.modifierAmount(instance.getAttribute().sanitizeValue(ownMax), arsMax)
                       == existing.getAmount()) {
                return; // ceiling already correct; do not dirty the attribute
            }
        }
        // Drop our own modifier first so getValue() reports Iron's own max, whatever
        // operations other mods' modifiers use. Nothing writes mana in between, so the
        // momentarily lower ceiling cannot clamp anything.
        if (existing != null) {
            instance.removeModifier(ARS_TO_IRON_MAX_MANA_ID);
        }
        double ironsOwnMax = instance.getValue();
        double needed = SharedPoolCeiling.modifierAmount(ironsOwnMax, arsMax);
        if (needed != 0.0) {
            instance.addTransientModifier(new AttributeModifier(
                ARS_TO_IRON_MAX_MANA_ID, "Ars Max Mana Sync", needed,
                AttributeModifier.Operation.ADDITION));
        }
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
     */
    public static void clearArsBonusesFromIrons(Player player) {
        if (player == null) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return;
        }
        removeAttributeModifier(player, AttributeRegistry.MAX_MANA.get(), ARS_TO_IRON_MAX_MANA_ID);
        removeAttributeModifier(player, AttributeRegistry.MANA_REGEN.get(), ARS_TO_IRON_REGEN_ID);
    }

    /**
     * Drop only the Ars-derived regen modifier, leaving the shared-pool ceiling in place.
     * Used when {@code respect_armor_bonuses} is off in HYBRID: the gear-derived regen is a
     * bonus the player opted out of, but the ceiling is a correctness invariant.
     */
    public static void clearArsRegenBonusFromIrons(Player player) {
        if (player == null || !IronsCompat.isLoaded()) {
            return;
        }
        removeAttributeModifier(player, AttributeRegistry.MANA_REGEN.get(), ARS_TO_IRON_REGEN_ID);
    }
    
    private static CachedEquipmentData calculateBonuses(Player player) {
        if (!BridgeManager.isUnificationEnabled()) {
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

        boolean ironsLoaded = IronsCompat.isLoaded();

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

        // Curios (if present): read IManaEquipment + Ars enchantments, and — when enabled —
        // attribute modifiers off each curio slot. The attribute pass is what lets
        // Apotheosis/Apothic-Curios affixes & sockets (and any other curio mana gear) reach the
        // cross-mod bridge, mirroring the armor/weapon path in calculateItemBonuses.
        try {
            boolean readCurioAttributes = AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.get();
            List<SlotResult> wornCurios = CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findCurios(stack -> !stack.isEmpty()))
                .orElse(Collections.emptyList());
            for (SlotResult result : wornCurios) {
                ItemBonuses itemBonuses = calculateCurioBonuses(result, ironsLoaded, readCurioAttributes);
                arsMaxBonus += itemBonuses.arsBonus.maxMana;
                arsRegenBonus += itemBonuses.arsBonus.manaRegen;
                ironMaxBonus += itemBonuses.ironBonus.maxMana;
                ironRegenBonus += itemBonuses.ironBonus.manaRegen;
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
        logDebug("Calculated curio discounts for {}: virtue={}, blasphemy={}, total={}%",
            player.getName().getString(), curioDiscounts.hasVirtueRing,
            curioDiscounts.hasBlasphemy, String.format("%.1f", curioDiscounts.totalDiscount * 100));

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

    private static ItemBonuses calculateCurioBonuses(SlotResult result, boolean ironsLoaded, boolean readAttributes) {
        ItemStack item = result.stack();
        double arsMax = 0.0;
        double arsRegen = 0.0;
        double ironMax = 0.0;
        double ironRegen = 0.0;

        // Attribute modifiers on the curio slot (Apotheosis/Apothic-Curios affixes & sockets,
        // and any other mod's curio mana attributes). CuriosApi aggregates the item's own
        // declared curio modifiers with anything injected for the slot. ADDITION-only via
        // sumModifiers, matching calculateItemBonuses so Ars-vs-Iron's attribution is identical.
        if (readAttributes) {
            try {
                SlotContext ctx = result.slotContext();
                UUID slotUuid = CuriosApi.getSlotUuid(ctx);
                Multimap<Attribute, AttributeModifier> modifiers =
                    CuriosApi.getAttributeModifiers(ctx, slotUuid, item);
                arsMax += sumModifiers(modifiers, PerkAttributes.MAX_MANA.get());
                arsRegen += sumModifiers(modifiers, PerkAttributes.MANA_REGEN_BONUS.get());
                if (ironsLoaded) {
                    ironMax += sumModifiers(modifiers, AttributeRegistry.MAX_MANA.get());
                    ironRegen += sumModifiers(modifiers, AttributeRegistry.MANA_REGEN.get());
                }
            } catch (Exception e) {
                LOGGER.debug("Failed reading curio attribute modifiers for {}", item, e);
            }
        }

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
    
    private static final ResourceLocation ARS_MANA_REGEN_ENCHANT_ID =
        new ResourceLocation("ars_nouveau", "mana_regen");
    private static final ResourceLocation ARS_MANA_BOOST_ENCHANT_ID =
        new ResourceLocation("ars_nouveau", "mana_boost");

    /**
     * Get mana bonus from Ars Nouveau enchantments equipped on an item.
     *
     * <p>Returns Ars-side units: {@code maxMana} in absolute mana, {@code manaRegen}
     * in absolute mana/sec. The cross-system bridge converts these to Iron's
     * percentage-of-pool units when applied via {@link #applyArsBonusesToIrons}.
     *
     * <p>This path is load-bearing in {@code ISS_PRIMARY} / {@code HYBRID} mode where
     * {@code MixinManaCapability.addMana} suppresses Ars's native regen tick — the
     * enchantment's intended effect would otherwise be lost on the Iron's pool.
     * In {@code ARS_PRIMARY} mode Ars handles its own enchantments natively, so the
     * value populated here is unused (the Ars-primary regen handler reads only the
     * Iron's-side bonus container).
     *
     * <p>Detection is anchored to specific Ars enchantment IDs to avoid false
     * positives from the previous broad string match (which granted +50 max per
     * level to any enchantment whose description contained "mana" or "source",
     * including unrelated ones like {@code mana_steal} or {@code source_friendly}).
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
                ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
                if (id == null) {
                    continue;
                }

                if (ARS_MANA_REGEN_ENCHANT_ID.equals(id)) {
                    regenBonus += level;
                } else if (ARS_MANA_BOOST_ENCHANT_ID.equals(id)) {
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
