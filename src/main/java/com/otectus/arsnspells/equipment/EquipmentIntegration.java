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
 * <h2>Gear bonus</h2>
 * The Ars gear bonus is the contribution of the player's equipped items and (when
 * {@code read_curio_attribute_modifiers} is on) worn curios to Ars's
 * {@link PerkAttributes#MAX_MANA} / {@link PerkAttributes#MANA_REGEN_BONUS}, measured with
 * {@link AttributeContribution#equipmentDelta}. Item attribute modifiers include the Ars perk
 * threads, which NeoForge's item-attribute hook adds to the stack's modifiers. Potion
 * modifiers are not gear; {@link PotionContributions} mirrors Ars potions separately, as the
 * Forge 1.20.1 build does. The Forge build also reads Ars 4's {@code IManaEquipment}
 * fallback, an interface Ars 5 removed.
 *
 * <h2>Mode handling (identical to the Forge 1.20.1 build)</h2>
 * <ul>
 *   <li><b>ISS_PRIMARY</b> — Iron's owns the pool; push the Ars gear bonus, times
 *       {@code conversion_rate_ars_to_iron}, into Iron's {@code MAX_MANA} /
 *       {@code MANA_REGEN}.</li>
 *   <li><b>HYBRID</b> — Iron's {@code MAX_MANA} is raised to cover Ars's real max
 *       (the shared-pool ceiling), and the Ars gear regen is mirrored. With
 *       {@code respect_armor_bonuses} off only the regen is dropped: the ceiling is a
 *       correctness invariant, not a bonus.</li>
 *   <li><b>ARS_PRIMARY</b> — Ars owns the pool; Iron's {@code MAX_MANA} mirrors Ars's real
 *       max (the same ceiling {@code ArsManaCalcHandler} writes after each max-mana
 *       calculation). Iron's gear flows the other way via {@code ArsManaCalcHandler}; the
 *       ceiling cannot feed back into it because that handler excludes ANS's own
 *       modifiers.</li>
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
     * Recompute and apply the Ars→Iron's modifiers for the player's current loadout and the
     * active mana mode. Server-side; safe to call repeatedly (a modifier is only rewritten
     * when its value changes). Mirrors {@code EquipmentHandler.updatePlayerMaxMana} in the
     * Forge 1.20.1 build.
     */
    public static void recomputeFor(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return; // every write targets Iron's attributes
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (!BridgeManager.isUnificationEnabled() || mode == null) {
            clearAll(player);
            return;
        }
        if (!AnsConfig.respectArmorBonuses.get() && !mode.isHybrid()) {
            clearAll(player);
            return;
        }
        if (mode.isArsPrimary() || mode.isHybrid()) {
            syncIronsMaxToArs(player, arsRealMaxMana(player));
            if (mode.isHybrid()) {
                if (AnsConfig.respectArmorBonuses.get()) {
                    applyArsRegenBonusToIrons(player, AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());
                } else {
                    clearArsRegenBonusFromIrons(player);
                }
            }
        } else if (mode.isIssPrimary()) {
            applyArsBonusesToIrons(player, AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());
        } else {
            clearAll(player);
        }
    }

    /**
     * The Ars max-mana and regen bonus contributed by equipped items and curios, excluding
     * ANS's own modifiers and, when {@code respect_enchantments} is off, enchantment modifiers.
     * {@code double[]{maxMana, manaRegen}}.
     */
    public static double[] arsGearBonus(Player player) {
        if (player == null) {
            return new double[] {0.0, 0.0};
        }
        try {
            EquipmentSnapshot snapshot = equipment(player);
            java.util.Set<ResourceLocation> excluded = new java.util.HashSet<>(ownedModifierIds());
            if (!AnsConfig.respectEnchantments.get()) excluded.addAll(snapshot.enchantmentIds);
            return new double[] {
                gearContribution(player, PerkAttributes.MAX_MANA, snapshot, excluded),
                gearContribution(player, PerkAttributes.MANA_REGEN_BONUS, snapshot, excluded)};
        } catch (Throwable t) {
            LOGGER.debug("Could not read Ars gear bonuses for {}", player.getName().getString(), t);
            return new double[] {0.0, 0.0};
        }
    }

    private static double gearContribution(Player player, Holder<Attribute> attribute,
                                           EquipmentSnapshot snapshot, java.util.Set<ResourceLocation> excluded) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return 0.0;
        }
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
        return AttributeContribution.equipmentDelta(instance,
            snapshot.modifiers.getOrDefault(key, java.util.Map.of()).values(), excluded);
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

    /**
     * Remove Ars-derived mana bonuses from Iron's attributes, under their current and legacy
     * identities.
     *
     * <p>V14: no Iron's gate. {@link com.otectus.arsnspells.bridge.AnsFeatureCleanup} resolves
     * attributes from the registry, so an absent Iron's makes this a no-op instead of a crash,
     * and a modifier stranded by a feature that is gone can still be taken off.
     */
    public static void clearAll(Player player) {
        com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeKeys(player,
            com.otectus.arsnspells.contract.AnsModifierIds.ARS_GEAR_MAX_MANA,
            com.otectus.arsnspells.contract.AnsModifierIds.ARS_GEAR_MANA_REGEN);
    }

    /** Drop only the Ars-derived regen modifier, leaving the shared-pool ceiling in place. */
    public static void clearArsRegenBonusFromIrons(Player player) {
        com.otectus.arsnspells.bridge.AnsFeatureCleanup.removeKeys(player,
            com.otectus.arsnspells.contract.AnsModifierIds.ARS_GEAR_MANA_REGEN);
    }

    private static final java.util.Set<ResourceLocation> OWNED_MODIFIER_IDS =
        java.util.Set.copyOf(com.otectus.arsnspells.bridge.AnsModifierIdentities.allIdentities());

    public static java.util.Set<ResourceLocation> ownedModifierIds() {
        return OWNED_MODIFIER_IDS;
    }

    private static final class EquipmentSnapshot {
        final java.util.Map<ResourceLocation, java.util.Map<ResourceLocation, AttributeModifier>> modifiers = new java.util.HashMap<>();
        final java.util.Set<ResourceLocation> enchantmentIds = new java.util.HashSet<>();
        final java.util.Set<ResourceLocation> curioIds = new java.util.HashSet<>();
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
                            top.theillusivec4.curios.api.CuriosApi.getSlotId(context), stack).forEach((attribute, modifier) -> {
                                result.curioIds.add(modifier.id());
                                if (AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.get()) result.add(attribute, modifier);
                            });
                    }
                }));
        }
        return result;
    }

    /**
     * ISS_PRIMARY: Iron's owns the pool, so Ars gear adds to it. The max-mana bonus is the
     * Ars gear bonus times the conversion rate; the ceiling of the other shared modes does not
     * apply, because Ars's base pool and book tier are not gear.
     */
    public static void applyArsBonusesToIrons(Player player, double conversionRate) {
        if (player == null || player.level().isClientSide() || !IronsCompat.isLoaded()) {
            return;
        }
        double maxManaBonus = arsGearBonus(player)[0] * conversionRate;
        // Apply max mana first so the regen conversion sees the post-bonus pool size.
        // Otherwise EQUAL_EFFECT would underestimate the regen attribute delta needed.
        applyAttributeModifier(player, AttributeRegistry.MAX_MANA, ARS_TO_IRON_MAX_MANA_ID, maxManaBonus);
        applyArsRegenBonusToIrons(player, conversionRate);
    }

    /**
     * Mirror the Ars gear regen onto Iron's {@code MANA_REGEN}. Ars regen is absolute
     * mana/sec; Iron's is a percentage-of-pool multiplier, so the bridge converts units.
     */
    public static void applyArsRegenBonusToIrons(Player player, double conversionRate) {
        if (player == null || player.level().isClientSide() || !IronsCompat.isLoaded()) {
            return;
        }
        double absRegenPerSec = arsGearBonus(player)[1] * conversionRate;
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
            // Ars's own max, never reduced by ANS's enchantment or curio toggles: those decide
            // what ANS transfers between the pools, and a ceiling below the real max would
            // delete mana on the next write (same rule as the Forge 1.20.1 build).
            return (float) Math.max(0, com.hollingsworth.arsnouveau.api.util.ManaUtil.calcMaxMana(player).getRealMax());
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
        double needed = SharedPoolCeiling.modifierAmount(nativeMaximum, arsMax, amplification);
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
