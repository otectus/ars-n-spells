package com.otectus.arsnspells.equipment;

import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.bridge.SharedPoolCeiling;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentIntegration.class);


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

    /**
     * The gear-derived Iron's MAX_MANA bonus: everything modifiers contribute, minus ANS's own.
     *
     * <p>Used by {@code ArsManaCalcHandler} to fold Iron's gear into Ars's pool in ARS_PRIMARY.
     * Reading {@code getAttributeValue} raw would fold Iron's <em>entire</em> pool - its base
     * value included - on top of Ars's own, which is not a gear bonus at all.
     *
     * <p><b>Excluding our own modifier is load-bearing, not tidiness.</b> In ARS_PRIMARY the
     * LOWEST-priority sync writes ANS's modifier onto MAX_MANA after the calc completes. If
     * this counted that modifier, the next MaxManaCalcEvent would fold it back into Ars's max,
     * the sync would raise it again, and the pool would run away. That feedback loop is why
     * the 1.20.1 line read a per-item gear scan here instead.
     */
    public static double ironsGearMaxManaBonus(Player player) {
        return foreignModifierTotal(player, AttributeRegistry.MAX_MANA, ARS_TO_IRON_MAX_MANA_ID);
    }

    /** The gear-derived Iron's MANA_REGEN bonus. See {@link #ironsGearMaxManaBonus}. */
    public static double ironsGearRegenBonus(Player player) {
        return foreignModifierTotal(player, AttributeRegistry.MANA_REGEN, ARS_TO_IRON_REGEN_ID);
    }

    /**
     * {@code getValue() - getBaseValue()}, less the amount contributed by {@code ownId}.
     *
     * <p>Exact under the ADD_VALUE model every modifier in this class uses; a third-party
     * multiply modifier would skew it, which is the same approximation
     * {@link #syncIronsMaxToArs} already reasons in. Never negative.
     */
    private static double foreignModifierTotal(Player player, Holder<Attribute> attribute,
                                               ResourceLocation ownId) {
        if (player == null || !IronsCompat.isLoaded()) {
            return 0.0;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return 0.0;
        }
        EquipmentSnapshot snapshot = equipment(player);
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
        java.util.Set<ResourceLocation> excluded = new java.util.HashSet<>(ownedModifierIds());
        if (!AnsConfig.respectEnchantments.get()) excluded.addAll(snapshot.enchantmentIds);
        return AttributeContribution.equipmentDelta(instance,
            snapshot.modifiers.getOrDefault(key, java.util.Map.of()).values(), excluded);
    }

    /** Remove any Ars-derived modifiers from Iron's attributes. */
    public static void clearAll(Player player) {
        if (player == null || !IronsCompat.isLoaded()) {
            return;
        }
        removeAttributeModifier(player, AttributeRegistry.MAX_MANA, ARS_TO_IRON_MAX_MANA_ID);
        removeAttributeModifier(player, AttributeRegistry.MANA_REGEN, ARS_TO_IRON_REGEN_ID);
    }

    public static java.util.Set<ResourceLocation> ownedModifierIds() {
        var ids = new java.util.HashSet<ResourceLocation>();
        for (String key : com.otectus.arsnspells.contract.AnsModifierIds.allKeys()) {
            for (String candidate : com.otectus.arsnspells.contract.AnsModifierIds.currentAndLegacyKeysFor(key)) {
                ResourceLocation id = ResourceLocation.tryParse(candidate);
                if (id != null) ids.add(id);
            }
        }
        return java.util.Set.copyOf(ids);
    }

    private static final class EquipmentSnapshot {
        final java.util.Map<ResourceLocation, java.util.Map<ResourceLocation, AttributeModifier>> modifiers = new java.util.HashMap<>();
        final java.util.Set<ResourceLocation> enchantmentIds = new java.util.HashSet<>();
        void add(Holder<Attribute> attribute, AttributeModifier modifier) {
            if (ownedModifierIds().contains(modifier.id())) return;
            ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
            modifiers.computeIfAbsent(key, ignored -> new java.util.LinkedHashMap<>()).putIfAbsent(modifier.id(), modifier);
        }
    }

    /** Read actual item/Curios attribute APIs. Enchantment attributes have a distinct native iterator. */
    private static EquipmentSnapshot equipment(Player player) {
        EquipmentSnapshot result = new EquipmentSnapshot();
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            var stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            stack.getAttributeModifiers().forEach(slot, result::add);
            net.minecraft.world.item.enchantment.EnchantmentHelper.forEachModifier(stack, slot, (attribute, modifier) -> {
                result.enchantmentIds.add(modifier.id());
                if (AnsConfig.respectEnchantments.get()) result.add(attribute, modifier);
            });
        }
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(inventory ->
                inventory.getCurios().forEach((name, handler) -> {
                    var stacks = handler.getStacks();
                    for (int index = 0; index < stacks.getSlots(); index++) {
                        var stack = stacks.getStackInSlot(index);
                        if (stack.isEmpty()) continue;
                        var context = new top.theillusivec4.curios.api.SlotContext(name, player, index, false, true);
                        top.theillusivec4.curios.api.CuriosApi.getAttributeModifiers(context,
                            top.theillusivec4.curios.api.CuriosApi.getSlotId(context), stack).forEach(result::add);
                    }
                }));
        }
        return result;
    }

    private static void applyArsBonusesToIrons(Player player, double conversionRate) {
        // 3.2.0: the ceiling is Ars's REAL max, not the gear-derived slice of it.
        //
        // This used to push `PerkAttributes.MAX_MANA * rate` across - only the bonus from
        // gear and perks, not the base pool or the book tier. In HYBRID that left Iron's
        // max_mana far below the pool the player actually had, and because
        // MagicData.setMana clamps EVERY write down to that attribute, the surplus was not
        // capped, it was deleted on the next write of any kind. One spell emptied the pool.
        syncIronsMaxToArs(player, arsRealMaxMana(player));

        // Ars regen is absolute mana/sec; Iron's MANA_REGEN is a percentage-of-pool
        // multiplier — go through the bridge to avoid the unit-mismatch bug. Regen keeps
        // using the gear-derived value: Ars's real max already includes its gear bonus, so
        // the ceiling above must not be double-counted here.
        double absRegenPerSec = arsAttribute(player, PerkAttributes.MANA_REGEN_BONUS) * conversionRate;
        double regenAttr = ManaRegenBridge.convertArsToIrons(absRegenPerSec, player);
        applyAttributeModifier(player, AttributeRegistry.MANA_REGEN, ARS_TO_IRON_REGEN_ID, regenAttr);
    }

    /**
     * Ars's own computed max mana: config base + glyph bonus + book tier + Ars perk
     * attributes, less any reserve.
     *
     * <p>Read from Ars's {@code ManaUtil.calcMaxMana} rather than from the mana capability,
     * which ANS's own {@code MixinManaCapability} intercepts — going through the capability
     * would make the ceiling sync depend on the value it is supposed to produce.
     */
    public static float arsRealMaxMana(Player player) {
        if (player == null) {
            return 0.0f;
        }
        try {
            double maximum = com.hollingsworth.arsnouveau.api.util.ManaUtil.calcMaxMana(player).getRealMax();
            if (!AnsConfig.respectEnchantments.get()) {
                var instance = player.getAttribute(PerkAttributes.MAX_MANA);
                if (instance != null) maximum -= instance.getValue() - arsAttribute(player, PerkAttributes.MAX_MANA);
            }
            return (float) Math.max(0, maximum);
        } catch (Throwable t) {
            LOGGER.debug("Could not read Ars max mana for {}", player.getName().getString(), t);
            return 0.0f;
        }
    }

    /**
     * Sync Iron's MAX_MANA attribute so it covers Ars's actual max mana.
     *
     * <p>Used by every shared-pool mode. The shortfall is measured against Iron's max
     * <em>with the ANS modifier removed</em>, so the result is exactly
     * {@code max(Ars max, Iron's own max)}: nothing Iron's contributes is voided, and the
     * pool is not inflated past what either system intends.
     *
     * <p>This is not cosmetic. Iron's {@code MagicData.setMana} clamps <em>every</em> write
     * down to this attribute, so a ceiling below the current pool does not limit mana — it
     * deletes it, on the next write, whatever that write happens to be.
     */
    public static void syncIronsMaxToArs(Player player, float arsMax) {
        if (player == null || player.level().isClientSide() || !IronsCompat.isLoaded()) {
            return;
        }
        AttributeInstance instance = player.getAttribute(AttributeRegistry.MAX_MANA);
        if (instance == null) {
            return;
        }
        var nativeModifiers = instance.getModifiers().stream()
            .filter(modifier -> !ownedModifierIds().contains(modifier.id())).toList();
        double nativeMaximum = instance.getAttribute().value().sanitizeValue(
            AttributeContribution.evaluate(instance.getBaseValue(), nativeModifiers));
        double amplification = AttributeContribution.additiveAmplification(nativeModifiers);
        double needed = amplification > 0 ? Math.max(0, arsMax - nativeMaximum) / amplification : 0;
        applyAttributeModifier(player, AttributeRegistry.MAX_MANA, ARS_TO_IRON_MAX_MANA_ID, needed);
    }
    /**
     * Tolerance for the ceiling-is-already-correct check. The modifier amount is a double
     * difference, so {@code ironsOwnMax + (arsMax - ironsOwnMax)} need not reproduce
     * {@code arsMax} bit-for-bit. Mana values run from tens to thousands, so a 1e-4 window is
     * far below anything that matters and far above the representation error.
     */
    private static final double CEILING_EPSILON = 1.0e-4;

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
        if (player == null || player.level().isClientSide() || !IronsCompat.isLoaded()) {
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
        AttributeInstance instance = player.getAttribute(AttributeRegistry.MAX_MANA);
        if (instance == null || instance.getValue() >= arsMax) {
            return;
        }
        syncIronsMaxToArs(player, arsMax);
    }

    /** Read an aggregate Ars perk-attribute value (gear/perk/curio bonus), 0 on any failure. */
    private static double arsAttribute(Player player, Holder<Attribute> attribute) {
        try {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) return 0;
            var excluded = new java.util.HashSet<>(ownedModifierIds());
            if (!AnsConfig.respectEnchantments.get()) excluded.addAll(equipment(player).enchantmentIds);
            var modifiers = instance.getModifiers().stream().filter(modifier -> !excluded.contains(modifier.id())).toList();
            return attribute.value().sanitizeValue(AttributeContribution.evaluate(instance.getBaseValue(), modifiers));
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
