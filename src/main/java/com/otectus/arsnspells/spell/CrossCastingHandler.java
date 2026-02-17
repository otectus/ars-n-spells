package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.ISpellCaster;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles cross-mod spell casting - allows spells from one mod to be cast using items from another.
 * Examples:
 * - Cast Iron's Spellbooks spells from Ars Nouveau spellbooks
 * - Cast Ars Nouveau glyphs from Iron's Spellbooks items
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class CrossCastingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossCastingHandler.class);

    // NBT tags for cross-mod spell storage
    private static final String TAG_CROSS_MOD_SPELLS = "arsnspells:cross_spells";
    private static final String TAG_SPELL_ID = "spell_id";
    private static final String TAG_SPELL_LEVEL = "spell_level";
    private static final String TAG_SPELL_TYPE = "spell_type";
    private static final String TAG_SPELL_INDEX = "arsnspells:cross_spell_index";
    private static final String TAG_ARS_SPELL = "ars_spell";
    private static final String TAG_CAST_SOURCE = "cast_source";
    private static final String CROSS_CAST_SLOT = "arsnspells:cross_cast";

    /**
     * Handle right-click events to intercept spell casting
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        InteractionHand hand = event.getHand();

        // Only process main hand to avoid double-casting
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }

        // Check if cross-mod casting is enabled
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }

        // Handle cross-mod spell casting
        if (handleCrossCast(player, stack, hand)) {
            event.setCanceled(true);
        }
    }

    /**
     * Handle cross-mod spell casting
     */
    private static boolean handleCrossCast(Player player, ItemStack item, InteractionHand hand) {
        CompoundTag tag = item.getOrCreateTag();

        if (!tag.contains(TAG_CROSS_MOD_SPELLS)) {
            return false; // No cross-mod spells inscribed
        }

        ListTag spellList = tag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);

        if (spellList.isEmpty()) {
            return false;
        }

        int index = getSelectedIndex(tag, spellList.size());

        if (player.isCrouching()) {
            int nextIndex = (index + 1) % spellList.size();
            setSelectedIndex(tag, nextIndex);
            if (!player.level().isClientSide()) {
                Component msg = Component.literal("Cross spell selected: " + (nextIndex + 1)
                    + "/" + spellList.size());
                player.displayClientMessage(msg, true);
            }
            return true;
        }

        CompoundTag spellData = spellList.getCompound(index);
        CrossSpellType type = parseSpellType(spellData);
        if (type == null) {
            return false;
        }

        if (player.level().isClientSide()) {
            return true;
        }

        switch (type) {
            case ARS_NOUVEAU:
                return castArsSpell(player, item, hand, spellData);
            case IRONS_SPELLBOOKS:
                return castIronsSpell(player, item, spellData);
            default:
                return false;
        }
    }

    private static CrossSpellType parseSpellType(CompoundTag spellData) {
        String typeName = spellData.getString(TAG_SPELL_TYPE);
        if (!typeName.isEmpty()) {
            try {
                return CrossSpellType.valueOf(typeName);
            } catch (IllegalArgumentException ignored) {
                // Fall back to namespace detection
            }
        }

        ResourceLocation spellId = ResourceLocation.tryParse(spellData.getString(TAG_SPELL_ID));
        if (spellId != null) {
            String namespace = spellId.getNamespace();
            if ("ars_nouveau".equals(namespace)) {
                return CrossSpellType.ARS_NOUVEAU;
            }
            if ("irons_spellbooks".equals(namespace)) {
                return CrossSpellType.IRONS_SPELLBOOKS;
            }
        }

        LOGGER.warn("Cross-mod spell missing or invalid type: {}", spellData);
        return null;
    }

    private static boolean castArsSpell(Player player, ItemStack item, InteractionHand hand, CompoundTag spellData) {
        CompoundTag arsSpellTag = spellData.getCompound(TAG_ARS_SPELL);
        if (arsSpellTag.isEmpty()) {
            LOGGER.warn("Cross-mod Ars spell missing ars_spell tag: {}", spellData);
            return false;
        }

        Spell spell = Spell.fromTag(arsSpellTag);
        ISpellCaster caster = new SpellCaster(item);

        boolean separate = BridgeManager.isUnificationEnabled()
            && BridgeManager.getCurrentMode() == ManaUnificationMode.SEPARATE;
        if (separate) {
            CrossCastContext.begin(player, CrossSpellType.ARS_NOUVEAU, player.level().getGameTime());
        }

        InteractionResultHolder<ItemStack> result = caster.castSpell(player.level(), player, hand, null, spell);
        boolean success = result.getResult().consumesAction();
        if (separate && !success) {
            CrossCastContext.clear(player);
        }
        return success;
    }

    private static boolean castIronsSpell(Player player, ItemStack item, CompoundTag spellData) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return false;
        }

        ResourceLocation spellId = ResourceLocation.tryParse(spellData.getString(TAG_SPELL_ID));
        if (spellId == null) {
            LOGGER.warn("Cross-mod Iron spell missing spell_id: {}", spellData);
            return false;
        }

        io.redspace.ironsspellbooks.api.spells.AbstractSpell spell =
            io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);

        if (spell == null) {
            LOGGER.warn("Cross-mod Iron spell not found: {}", spellId);
            return false;
        }

        int spellLevel = Math.max(1, spellData.getInt(TAG_SPELL_LEVEL));
        int castLevel = spell.getLevelFor(spellLevel, player);
        io.redspace.ironsspellbooks.api.spells.CastSource source =
            parseCastSource(spellData, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (BridgeManager.isUnificationEnabled() && mode == ManaUnificationMode.SEPARATE) {
            float totalCost = spell.getManaCost(castLevel);
            float arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get().floatValue();
            float issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get().floatValue();
            float arsCost = (float) (totalCost * arsPercent * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get());
            float issCost = totalCost * issPercent;

            if (!player.isCreative() && arsCost > 0.0f) {
                float arsMana = BridgeManager.getBridge().getMana(player);
                if (arsMana < arsCost) {
                    logDebug("Insufficient Ars mana for cross-cast: need {}, have {}", arsCost, arsMana);
                    return false;
                }
            }

            CrossCastContext.begin(player, CrossSpellType.IRONS_SPELLBOOKS,
                player.level().getGameTime(), arsCost, issCost, spell.getSpellId());

            boolean success = CrossCastContext.withManaCheckOverride(player, issPercent,
                () -> spell.attemptInitiateCast(item, castLevel, player.level(), player, source, true, CROSS_CAST_SLOT));
            if (!success) {
                CrossCastContext.clear(player);
            }
            return success;
        }

        return spell.attemptInitiateCast(item, castLevel, player.level(), player, source, true, CROSS_CAST_SLOT);
    }

    private static io.redspace.ironsspellbooks.api.spells.CastSource parseCastSource(
        CompoundTag spellData,
        io.redspace.ironsspellbooks.api.spells.CastSource fallback) {

        String sourceName = spellData.getString(TAG_CAST_SOURCE);
        if (sourceName.isEmpty()) {
            return fallback;
        }
        try {
            return io.redspace.ironsspellbooks.api.spells.CastSource.valueOf(sourceName);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    @SubscribeEvent
    public static void onArsSpellCost(SpellCostCalcEvent event) {
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode != ManaUnificationMode.SEPARATE) {
            return;
        }

        LivingEntity caster = event.context != null ? event.context.getUnwrappedCaster() : null;
        if (!(caster instanceof Player player)) {
            return;
        }

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry == null || entry.type != CrossSpellType.ARS_NOUVEAU) {
            return;
        }

        int totalCost = Math.max(0, event.currentCost);
        float arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get().floatValue();
        float issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get().floatValue();
        float arsCost = totalCost * arsPercent;
        float issCost = (float) (totalCost * issPercent * AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());

        entry.arsCost = arsCost;
        entry.issCost = issCost;
        entry.costsReady = true;

        if (!player.isCreative() && issCost > 0.0f) {
            IManaBridge issBridge = BridgeManager.getSecondaryBridge();
            float issMana = issBridge != null ? issBridge.getMana(player) : 0.0f;
            if (issMana < issCost) {
                entry.blocked = true;
                event.currentCost = Integer.MAX_VALUE;
                CrossCastContext.clear(player);
                logDebug("Insufficient Iron mana for cross-cast: need {}, have {}", issCost, issMana);
                return;
            }
        }

        event.currentCost = Math.max(0, Math.round(arsCost));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player == null || event.player.level().isClientSide()) {
            return;
        }
        CrossCastContext.cleanupExpired(event.player, event.player.level().getGameTime());
    }

    /**
     * Add a cross-mod spell to an item
     */
    public static void addCrossModSpell(ItemStack stack, ResourceLocation spellId, int spellLevel,
        CrossSpellType type) {
        addCrossModSpell(stack, spellId, spellLevel, type, null);
    }

    /**
     * Add a cross-mod Ars spell to an item using serialized spell NBT
     */
    public static void addCrossModSpell(ItemStack stack, Spell spell) {
        if (spell == null) {
            return;
        }
        addCrossModSpell(stack, new ResourceLocation("ars_nouveau", "spell"), 1,
            CrossSpellType.ARS_NOUVEAU, spell.serialize());
    }

    /**
     * Add a cross-mod spell to an item with optional Ars spell tag
     */
    public static void addCrossModSpell(ItemStack stack, ResourceLocation spellId, int spellLevel,
        CrossSpellType type, CompoundTag arsSpellTag) {

        CompoundTag tag = stack.getOrCreateTag();

        ListTag spellList;
        if (tag.contains(TAG_CROSS_MOD_SPELLS)) {
            spellList = tag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        } else {
            spellList = new ListTag();
        }

        CompoundTag spellData = new CompoundTag();
        if (spellId != null) {
            spellData.putString(TAG_SPELL_ID, spellId.toString());
        }
        spellData.putInt(TAG_SPELL_LEVEL, spellLevel);
        if (type != null) {
            spellData.putString(TAG_SPELL_TYPE, type.name());
        }
        if (arsSpellTag != null) {
            spellData.put(TAG_ARS_SPELL, arsSpellTag);
        }

        spellList.add(spellData);
        tag.put(TAG_CROSS_MOD_SPELLS, spellList);
    }

    /**
     * Remove all cross-mod spells from an item
     */
    public static void clearCrossModSpells(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(TAG_CROSS_MOD_SPELLS);
    }

    /**
     * Get all cross-mod spells on an item
     */
    public static ListTag getCrossModSpells(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        if (tag.contains(TAG_CROSS_MOD_SPELLS)) {
            return tag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        }

        return new ListTag();
    }

    private static int getSelectedIndex(CompoundTag tag, int size) {
        if (size <= 0) {
            return 0;
        }
        int index = tag.getInt(TAG_SPELL_INDEX);
        if (index < 0 || index >= size) {
            index = 0;
            tag.putInt(TAG_SPELL_INDEX, index);
        }
        return index;
    }

    private static void setSelectedIndex(CompoundTag tag, int index) {
        if (index < 0) {
            index = 0;
        }
        tag.putInt(TAG_SPELL_INDEX, index);
    }

    /**
     * Log debug message if debug mode is enabled
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CrossCasting] [DEBUG] " + message, args);
        }
    }
}
