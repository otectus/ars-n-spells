package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectFreeze;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectIgnite;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.combat.CombatDebugState;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.SpellAnalysis;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The cross-mod combat bridge against the real event objects, posted on the real bus.
 *
 * <p>The reported bug was "my Iron's school armour does nothing to my Ars spells", and the
 * arithmetic core ({@code SpellScalingUtil.combine}, the aggregation policy) was already right
 * when it was tested in isolation - the JUnit suite passed throughout. What nothing covered was
 * whether the bridge is reached at all with a real {@code SpellDamageEvent.Pre}, and whether the
 * school the event's {@code SpellContext} resolves to is the school whose attribute gets read.
 * Every test here therefore constructs the genuine event and posts it on
 * {@link NeoForge#EVENT_BUS}, so a handler that is never invoked fails the test instead of
 * passing it.
 *
 * <p>Both halves of the bridge are registered only behind the Iron's-loaded gate, so every test
 * self-skips when Iron's Spellbooks is absent - the default {@code runGameTestServer} profile.
 * Run the ones that mean anything with:
 *
 * <pre>
 *   ./gradlew runGameTestServer -PwithIronsRuntimeGameTests
 * </pre>
 *
 * <p>Spell power is injected with transient {@link AttributeModifier}s using
 * {@link AttributeModifier.Operation#ADD_MULTIPLIED_BASE}, the operation Iron's own armour
 * uses, so the values under test are the values real gear produces. One test deliberately does
 * it the slow way instead and equips an actual Pyromancer set, because "the formula works on
 * numbers I injected" is not the claim the bug report disputes.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CombatBridgeGameTests {

    private static final GameProfile FAKE_PROFILE =
        new GameProfile(UUID.fromString("0a000000-0000-0000-0000-00000000c0b7"),
                        "ans_combat_test");

    /** Modifier ids for the injected attributes, one per attribute so cleanup is exact. */
    private static final ResourceLocation GLOBAL_POWER_ID = testId("gametest_global_power");
    private static final ResourceLocation FIRE_POWER_ID = testId("gametest_fire_power");
    private static final ResourceLocation ICE_POWER_ID = testId("gametest_ice_power");
    private static final ResourceLocation ARS_BONUS_ID = testId("gametest_ars_damage_bonus");

    /** Iron's armour is +5% generic per piece; four pieces is the 1.20 every test below uses. */
    private static final double GLOBAL_POWER_BONUS = 0.20;
    /** Iron's school armour is +10% school per piece; four pieces is 1.40. */
    private static final double FIRE_POWER_BONUS = 0.40;
    /** A deliberately different school bonus, so a cross-contaminated read is visible. */
    private static final double ICE_POWER_BONUS = 0.10;

    /** {@code global + (school - 1.0)}, the additive rule the bridge implements. */
    private static final float FIRE_MULTIPLIER = 1.60f;
    private static final float ICE_MULTIPLIER = 1.30f;
    private static final float GENERIC_MULTIPLIER = 1.20f;

    private static final float BASE_DAMAGE = 10.0f;
    private static final float ARS_DAMAGE_BONUS = 4.0f;
    private static final float EPSILON = 0.01f;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
    };

    /** The four Pyromancer pieces, in the slot order of {@link #ARMOR_SLOTS}. */
    private static final String[] PYROMANCER_PIECES = {
        "pyromancer_helmet", "pyromancer_chestplate", "pyromancer_leggings", "pyromancer_boots",
    };

    private CombatBridgeGameTests() {}

    private static ResourceLocation testId(String path) {
        return ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, path);
    }

    // ---- Fixture ----

    /**
     * The shared fake player, stripped of everything a previous test in this class applied.
     *
     * <p>{@code FakePlayerFactory} caches by profile, so every test here gets the same instance:
     * without the reset an earlier test's spell power silently becomes the next one's baseline.
     */
    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), FAKE_PROFILE);
        cleanUp(player);
        return player;
    }

    /** Undo every attribute, item and flag a test in this class can set. */
    private static void cleanUp(ServerPlayer player) {
        removeModifier(player, AttributeRegistry.SPELL_POWER, GLOBAL_POWER_ID);
        removeModifier(player, AttributeRegistry.FIRE_SPELL_POWER, FIRE_POWER_ID);
        removeModifier(player, AttributeRegistry.ICE_SPELL_POWER, ICE_POWER_ID);
        removeModifier(player, PerkAttributes.SPELL_DAMAGE_BONUS, ARS_BONUS_ID);
        unequipArmor(player);
        CombatDebugState.clear(player.getUUID());
        AnsConfig.DEBUG_MODE.set(false);
    }

    private static void addModifier(ServerPlayer player, Holder<Attribute> attribute,
                                    ResourceLocation id, double amount,
                                    AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void removeModifier(ServerPlayer player, Holder<Attribute> attribute,
                                       ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }

    /** Iron's four-piece generic spell power, as a transient modifier. */
    private static void giveGlobalPower(ServerPlayer player) {
        addModifier(player, AttributeRegistry.SPELL_POWER, GLOBAL_POWER_ID, GLOBAL_POWER_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void giveFirePower(ServerPlayer player) {
        addModifier(player, AttributeRegistry.FIRE_SPELL_POWER, FIRE_POWER_ID, FIRE_POWER_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void giveIcePower(ServerPlayer player) {
        addModifier(player, AttributeRegistry.ICE_SPELL_POWER, ICE_POWER_ID, ICE_POWER_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    /**
     * Equip a real item and apply its attribute modifiers by hand.
     *
     * <p>{@code LivingEntity.detectEquipmentUpdates} is private and only runs from the entity
     * tick, which a {@code FakePlayer} does not have; this reproduces exactly what it does with
     * the item's own declared modifiers, so the values under test still come from the item
     * rather than from the test.
     */
    private static void equipWithAttributes(ServerPlayer player, EquipmentSlot slot,
                                            ItemStack stack) {
        player.setItemSlot(slot, stack);
        stack.forEachModifier(slot, (attribute, modifier) -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(modifier.id());
                instance.addTransientModifier(modifier);
            }
        });
    }

    private static void unequipArmor(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty()) {
                worn.forEachModifier(slot, (attribute, modifier) -> {
                    AttributeInstance instance = player.getAttribute(attribute);
                    if (instance != null) {
                        instance.removeModifier(modifier.id());
                    }
                });
            }
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    // ---- Event fixtures ----

    private static Spell arsSpell(AbstractSpellPart effect) {
        return new Spell(MethodProjectile.INSTANCE, effect);
    }

    /**
     * Post a real Ars {@code SpellDamageEvent.Pre} for {@code spell} and return the damage the
     * bus left behind.
     */
    private static float postArsSpellDamage(ServerPlayer player, Spell spell) {
        SpellContext context = SpellContext.fromEntity(spell, player, ItemStack.EMPTY);
        SpellDamageEvent.Pre event = new SpellDamageEvent.Pre(
            player.damageSources().magic(), player, player, BASE_DAMAGE, context);
        NeoForge.EVENT_BUS.post(event);
        return event.damage;
    }

    /** Post a plain incoming-damage event and return the amount the bus left behind. */
    private static float postGenericDamage(ServerPlayer player, DamageSource source,
                                           float amount) {
        LivingIncomingDamageEvent event =
            new LivingIncomingDamageEvent(player, new DamageContainer(source, amount));
        NeoForge.EVENT_BUS.post(event);
        return event.getAmount();
    }

    // ---- Setup guards ----

    /**
     * Fail loudly when a glyph does not resolve to the school the test is about. Without this a
     * mis-resolving glyph would look like a scaling bug, which is the confusion these tests
     * exist to end.
     */
    private static boolean schoolIs(GameTestHelper helper, Spell spell, String expected) {
        var schools = SpellAnalysis.analyze(spell).schools();
        if (!schools.contains(expected)) {
            helper.fail("test setup failed: the fixture spell must resolve to the " + expected
                + " school for this test to mean anything, resolved " + schools);
            return false;
        }
        return true;
    }

    private static boolean attributeIs(GameTestHelper helper, ServerPlayer player,
                                       Holder<Attribute> attribute, String label,
                                       double expected) {
        double actual = player.getAttributeValue(attribute);
        if (Math.abs(actual - expected) > EPSILON) {
            helper.fail("test setup failed: " + label + " must be " + expected + ", got " + actual);
            return false;
        }
        return true;
    }

    private static boolean damageIs(GameTestHelper helper, float actual, float expected,
                                    String what) {
        if (Math.abs(actual - expected) > EPSILON) {
            helper.fail(what + ": expected " + expected + ", got " + actual);
            return false;
        }
        return true;
    }

    // ---- The Iron's -> Ars direction ----

    /** A caster with no spell power at all must deal exactly the damage Ars computed. */
    @GameTest(template = "platform")
    public static void ironsLoaded_arsSpellWithNoSpellPower_isUnchanged(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            // Also the control for affinity and resonance: both are enabled by default and both
            // are multiplicative, so a non-neutral one would show up here first.
            float damage = postArsSpellDamage(player, arsSpell(EffectIgnite.INSTANCE));
            if (!damageIs(helper, damage, BASE_DAMAGE, "an unpowered caster's spell")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    /** Generic spell power with no matching school scales by the generic factor alone. */
    @GameTest(template = "platform")
    public static void ironsLoaded_genericSpellPower_scalesAnArsSpell(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            giveGlobalPower(player);
            if (!attributeIs(helper, player, AttributeRegistry.SPELL_POWER, "spell_power",
                             GENERIC_MULTIPLIER)) {
                return;
            }
            float damage = postArsSpellDamage(player, arsSpell(EffectIgnite.INSTANCE));
            if (!damageIs(helper, damage, BASE_DAMAGE * GENERIC_MULTIPLIER,
                          "generic spell power alone")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    /**
     * The reported bug, positive half: fire armour must scale an Ars fire spell, additively on
     * top of the generic power rather than multiplied by it.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_matchingSchoolPower_addsToTheGenericFactor(
            GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            Spell fire = arsSpell(EffectIgnite.INSTANCE);
            if (!schoolIs(helper, fire, "fire")) {
                return;
            }
            giveGlobalPower(player);
            giveFirePower(player);
            if (!attributeIs(helper, player, AttributeRegistry.FIRE_SPELL_POWER,
                             "fire_spell_power", 1.40)) {
                return;
            }
            float damage = postArsSpellDamage(player, fire);
            if (!damageIs(helper, damage, BASE_DAMAGE * FIRE_MULTIPLIER,
                          "global 1.20 + (fire 1.40 - 1.0)")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    /**
     * The reported bug, negative half: the same fire armour must do nothing extra for a spell of
     * another school. Without this, a bridge that ignored the school entirely would pass the
     * positive test.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_nonMatchingSchoolPower_isNotApplied(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            Spell ice = arsSpell(EffectFreeze.INSTANCE);
            if (!schoolIs(helper, ice, "ice")) {
                return;
            }
            giveGlobalPower(player);
            giveFirePower(player);

            float damage = postArsSpellDamage(player, ice);
            if (Math.abs(damage - BASE_DAMAGE * FIRE_MULTIPLIER) <= EPSILON) {
                helper.fail("an Ars ice spell picked up the caster's fire_spell_power: a school "
                    + "bonus that applies to every school is not a school bonus");
                return;
            }
            if (!damageIs(helper, damage, BASE_DAMAGE * GENERIC_MULTIPLIER,
                          "a non-matching school must leave only the generic factor")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    /**
     * The same rule against real gear rather than injected numbers: a four-piece Pyromancer set
     * off the item registry. Its per-piece grant is {@code +0.05 spell_power} and
     * {@code +0.10 fire_spell_power}, both {@code ADD_MULTIPLIED_BASE} over a base of 1.0, so
     * the set is worth 1.20 and 1.40 - the values the injected tests above use.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_realPyromancerSet_scalesAnArsFireSpell(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    IronsCompat.MODID, PYROMANCER_PIECES[i]);
                var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) {
                    helper.fail("Iron's is loaded but " + id + " is not registered; the real-gear "
                        + "test cannot run and must not be treated as passing");
                    return;
                }
                equipWithAttributes(player, ARMOR_SLOTS[i], new ItemStack(item));
            }

            // Assert the gear before the damage: if Iron's rebalances the set, this says so
            // instead of blaming the bridge.
            if (!attributeIs(helper, player, AttributeRegistry.SPELL_POWER,
                             "spell_power from four Pyromancer pieces", GENERIC_MULTIPLIER)
                || !attributeIs(helper, player, AttributeRegistry.FIRE_SPELL_POWER,
                                "fire_spell_power from four Pyromancer pieces", 1.40)) {
                return;
            }

            Spell fire = arsSpell(EffectIgnite.INSTANCE);
            if (!schoolIs(helper, fire, "fire")) {
                return;
            }
            float damage = postArsSpellDamage(player, fire);
            if (!damageIs(helper, damage, BASE_DAMAGE * FIRE_MULTIPLIER,
                          "a real Pyromancer set on an Ars fire spell")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    /**
     * Scaling must not depend on a recent cast. The handler this bridge replaced staged its
     * multiplier on {@code SpellCastEvent} and expired it after 60 ticks, so a delayed or
     * lingering effect scaled by nothing at all; no cast event is posted here.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_scalingNeedsNoPrecedingCastEvent(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            giveGlobalPower(player);
            giveFirePower(player);
            float damage = postArsSpellDamage(player, arsSpell(EffectIgnite.INSTANCE));
            if (!damageIs(helper, damage, BASE_DAMAGE * FIRE_MULTIPLIER,
                          "damage with no cast event ever posted")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    /**
     * Interleaved spells of different schools must each read their own attribute. The staged
     * design this replaced kept one entry per caster, so the second spell inherited the first
     * spell's multiplier.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_interleavedSchools_doNotContaminateEachOther(
            GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            Spell fire = arsSpell(EffectIgnite.INSTANCE);
            Spell ice = arsSpell(EffectFreeze.INSTANCE);
            if (!schoolIs(helper, fire, "fire") || !schoolIs(helper, ice, "ice")) {
                return;
            }
            giveGlobalPower(player);
            giveFirePower(player);
            giveIcePower(player);

            float firstFire = postArsSpellDamage(player, fire);
            float firstIce = postArsSpellDamage(player, ice);
            float secondFire = postArsSpellDamage(player, fire);
            float secondIce = postArsSpellDamage(player, ice);

            if (!damageIs(helper, firstFire, BASE_DAMAGE * FIRE_MULTIPLIER, "the first fire spell")
                || !damageIs(helper, firstIce, BASE_DAMAGE * ICE_MULTIPLIER, "the ice spell that "
                    + "followed a fire spell")
                || !damageIs(helper, secondFire, BASE_DAMAGE * FIRE_MULTIPLIER, "the fire spell "
                    + "that followed an ice spell")
                || !damageIs(helper, secondIce, BASE_DAMAGE * ICE_MULTIPLIER,
                             "the second ice spell")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    /**
     * The cross-cast invariant: an Ars spell must scale identically however it was cast, and
     * only the Ars half of the bridge may touch it. Two identical posts standing in for the
     * native and spellbook routes - both are ordinary Ars resolves by the time this event fires,
     * so a difference between them could only come from state the bridge should not be keeping.
     *
     * <p>The Iron's half not firing is asserted through {@link CombatDebugState}, which now
     * records a snapshot even for a zero bonus - so an absent Iron's snapshot means the handler
     * never ran, rather than "ran and found nothing to add".
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_repeatedArsCasts_scaleIdenticallyAndOnlyOnce(
            GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            AnsConfig.DEBUG_MODE.set(true);
            if (!AnsConfig.debugEnabled()) {
                helper.fail("test setup failed: could not enable debug mode, so the "
                    + "bridge-invocation assertions below would prove nothing");
                return;
            }
            giveGlobalPower(player);
            giveFirePower(player);
            Spell fire = arsSpell(EffectIgnite.INSTANCE);

            float nativeCast = postArsSpellDamage(player, fire);
            float bookCast = postArsSpellDamage(player, fire);

            if (!damageIs(helper, nativeCast, BASE_DAMAGE * FIRE_MULTIPLIER, "the first cast")
                || !damageIs(helper, bookCast, nativeCast, "the second, identical cast")) {
                return;
            }
            if (CombatDebugState.lastArs(player.getUUID()) == null) {
                helper.fail("the Ars bridge never recorded the hit, so it never ran - this test "
                    + "would have passed for the wrong reason");
                return;
            }
            if (CombatDebugState.lastIrons(player.getUUID()) != null) {
                helper.fail("the Iron's bridge also fired for an Ars spell hit; the flat perk "
                    + "bonus would be applied twice to one cast");
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    // ---- The Ars -> Iron's direction ----

    /** The Ars Spell Damage Bonus perk must reach an Iron's spell hit, as a flat add, once. */
    @GameTest(template = "platform")
    public static void ironsLoaded_arsDamageBonus_addsToAnIronsSpellExactlyOnce(
            GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            Object spell = IronsSpellDamageSupport.nativeSpell();
            if (spell == null) {
                helper.fail("no genuine Iron's spell is registered; this test cannot run");
                return;
            }
            addModifier(player, PerkAttributes.SPELL_DAMAGE_BONUS, ARS_BONUS_ID, ARS_DAMAGE_BONUS,
                        AttributeModifier.Operation.ADD_VALUE);
            if (!attributeIs(helper, player, PerkAttributes.SPELL_DAMAGE_BONUS,
                             "spell_damage_bonus", ARS_DAMAGE_BONUS)) {
                return;
            }

            float amount = IronsSpellDamageSupport.postSpellDamage(
                player, spell, BASE_DAMAGE);
            if (!damageIs(helper, amount, BASE_DAMAGE + ARS_DAMAGE_BONUS,
                          "the Ars perk on an Iron's spell hit")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    // ---- Negative controls: everything that is not a spell hit ----

    /** A sword swing is not a spell. The perk must not touch it. */
    @GameTest(template = "platform")
    public static void ironsLoaded_meleeDamage_isNeverTouched(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            giveGlobalPower(player);
            giveFirePower(player);
            addModifier(player, PerkAttributes.SPELL_DAMAGE_BONUS, ARS_BONUS_ID, ARS_DAMAGE_BONUS,
                        AttributeModifier.Operation.ADD_VALUE);

            float amount = postGenericDamage(player, player.damageSources().playerAttack(player),
                                             BASE_DAMAGE);
            if (!damageIs(helper, amount, BASE_DAMAGE, "a melee hit")) {
                return;
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }

    /**
     * Nor is standing in fire or hitting the ground. The handler this bridge replaced matched on
     * {@code DamageSource.getMsgId()}, which is exactly the test that could not tell these apart
     * from a spell.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_environmentalDamage_isNeverTouched(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        try {
            giveGlobalPower(player);
            giveFirePower(player);
            addModifier(player, PerkAttributes.SPELL_DAMAGE_BONUS, ARS_BONUS_ID, ARS_DAMAGE_BONUS,
                        AttributeModifier.Operation.ADD_VALUE);

            for (DamageSource source : List.of(player.damageSources().inFire(),
                                               player.damageSources().fall())) {
                float amount = postGenericDamage(player, source, BASE_DAMAGE);
                if (!damageIs(helper, amount, BASE_DAMAGE, source.getMsgId() + " damage")) {
                    return;
                }
            }
        } finally {
            cleanUp(player);
        }
        helper.succeed();
    }
}
