package com.otectus.arsnspells.equipment;

import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

/**
 * Cross-feeds Ars Nouveau gear mana bonuses into Iron's Spellbooks mana
 * attributes so that, in the shared-pool modes where Iron's owns the active
 * pool (ISS_PRIMARY / HYBRID), Ars mage armour / mana-boost gear still raises
 * the unified max mana and regen.
 *
 * <h2>Ars Nouveau 5.x migration</h2>
 * The Forge 1.20.1 implementation scanned each equipped {@code ItemStack}'s
 * {@code getAttributeModifiers(slot)} multimap (removed in 1.21) plus an
 * {@code IManaEquipment} fallback (removed in Ars 5.x). Ars 5.x applies all
 * gear / perk / curio mana bonuses as modifiers on the player's
 * {@link PerkAttributes#MAX_MANA} / {@link PerkAttributes#MANA_REGEN_BONUS}
 * attributes, so the aggregate bonus is read directly off the player — this is
 * both simpler and strictly more correct (it captures perk- and curio-applied
 * bonuses the per-item scan missed, which the 1.8.2 changelog called out).
 *
 * <h2>Mode handling</h2>
 * <ul>
 *   <li><b>ISS_PRIMARY / HYBRID</b> — Iron's owns the pool; push the Ars gear
 *       bonus into Iron's {@code MAX_MANA} / {@code MANA_REGEN} attributes.</li>
 *   <li><b>ARS_PRIMARY</b> — Ars owns the pool and counts its own gear natively;
 *       Iron's gear already flows the other way via {@code ArsManaCalcHandler}.
 *       Writing Iron's {@code MAX_MANA} here would feed back through that handler
 *       and runaway, so we only clear.</li>
 *   <li><b>SEPARATE / DISABLED</b> — pools are independent; clear.</li>
 * </ul>
 *
 * <p>Iron's-only: every write targets Iron's attributes, so the whole class is
 * inert (and never links Iron's classes) when Iron's is absent.
 */
public final class EquipmentIntegration {

    private EquipmentIntegration() {}

    /** ResourceLocation-keyed modifier ids (1.21 replaced UUID-keyed modifiers). */
    private static final ResourceLocation ARS_TO_IRON_MAX_MANA_ID =
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "ars_gear_max_mana");
    private static final ResourceLocation ARS_TO_IRON_REGEN_ID =
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "ars_gear_mana_regen");

    /**
     * Recompute and apply the Ars→Iron's gear-bonus modifiers for the player's
     * current loadout and the active mana mode. Server-side; safe to call
     * repeatedly (modifiers are removed and re-added idempotently).
     */
    public static void recomputeFor(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return; // every write targets Iron's attributes
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (!BridgeManager.isUnificationEnabled() || mode == null || !AnsConfig.respectArmorBonuses.get()) {
            clearAll(player);
            return;
        }
        if (mode.isIssPrimary() || mode.isHybrid()) {
            applyArsBonusesToIrons(player, AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());
        } else {
            // ARS_PRIMARY: handled the other direction by ArsManaCalcHandler.
            // SEPARATE / DISABLED: pools independent. Either way, clear.
            clearAll(player);
        }
    }

    /** Remove any Ars-derived modifiers from Iron's attributes. */
    public static void clearAll(Player player) {
        if (player == null || !IronsCompat.isLoaded()) {
            return;
        }
        removeAttributeModifier(player, AttributeRegistry.MAX_MANA, ARS_TO_IRON_MAX_MANA_ID);
        removeAttributeModifier(player, AttributeRegistry.MANA_REGEN, ARS_TO_IRON_REGEN_ID);
    }

    private static void applyArsBonusesToIrons(Player player, double conversionRate) {
        // Apply max mana first so the regen conversion sees the post-bonus pool size.
        double maxManaBonus = arsAttribute(player, PerkAttributes.MAX_MANA) * conversionRate;
        applyAttributeModifier(player, AttributeRegistry.MAX_MANA, ARS_TO_IRON_MAX_MANA_ID, maxManaBonus);

        // Ars regen is absolute mana/sec; Iron's MANA_REGEN is a percentage-of-pool
        // multiplier — go through the bridge to avoid the unit-mismatch bug.
        double absRegenPerSec = arsAttribute(player, PerkAttributes.MANA_REGEN_BONUS) * conversionRate;
        double regenAttr = ManaRegenBridge.convertArsToIrons(absRegenPerSec, player);
        applyAttributeModifier(player, AttributeRegistry.MANA_REGEN, ARS_TO_IRON_REGEN_ID, regenAttr);
    }

    /** Read an aggregate Ars perk-attribute value (gear/perk/curio bonus), 0 on any failure. */
    private static double arsAttribute(Player player, Holder<Attribute> attribute) {
        try {
            return player.getAttributeValue(attribute);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static void applyAttributeModifier(Player player, Holder<Attribute> attribute,
                                               ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (amount == 0.0) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        // Only churn the attribute map when the value actually changed — recomputeFor
        // runs once per second (EquipmentHandler tick) to catch dynamic perk/potion
        // sources, and a no-op remove/add every tick would force needless recomputes.
        if (existing != null && existing.amount() == amount) {
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeAttributeModifier(Player player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        // Only remove when present — clearAll() runs once per second in the non-shared
        // modes, and an unconditional remove would dirty the attribute every tick.
        if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }
}
