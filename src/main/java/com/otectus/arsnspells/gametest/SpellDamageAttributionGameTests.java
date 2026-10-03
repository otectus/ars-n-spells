package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.util.SpellScalingUtil;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Audit V08 - a spell hit is scaled by <em>its own</em> caster's school power.
 *
 * <p>The regression this pins: the scaling handler kept one entry per player UUID with a
 * 60-tick window and decided "this is spell damage" by substring-matching
 * {@code DamageSource.getMsgId()}. Attribution was therefore player-wide and time-based, and a
 * delayed projectile fired by one player that landed while another player's window was open
 * could be scaled by the wrong caster's school entirely. The handler now subscribes to Ars's
 * own {@code SpellDamageEvent.Pre}, which carries the caster and the spell, so the two casters
 * below cannot bleed into each other however their hits interleave.
 *
 * <p>Iron's-only: the school powers being asserted on are Iron's attributes. Runs under
 * {@code -PwithIronsRuntimeGameTests}; self-skips otherwise.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SpellDamageAttributionGameTests {

    private SpellDamageAttributionGameTests() {}

    /** Test-only school-power boost identity, never left on the player. */
    private static final UUID TEST_POWER_ID =
        UUID.fromString("0a000000-0000-0000-0000-0000000d0008");

    private static final float BASE_DAMAGE = 10.0f;

    private static ServerPlayer caster(GameTestHelper helper, String scenario) {
        GameProfile profile = new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)),
            scenario.length() > 16 ? scenario.substring(0, 16) : scenario);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), profile);
        clearBoost(player, AttributeRegistry.FIRE_SPELL_POWER.get());
        clearBoost(player, AttributeRegistry.ICE_SPELL_POWER.get());
        return player;
    }

    private static void boost(ServerPlayer player, Attribute attribute, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addTransientModifier(new AttributeModifier(
                TEST_POWER_ID, "ANS attribution test", amount, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void clearBoost(ServerPlayer player, Attribute attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(TEST_POWER_ID) != null) {
            instance.removeModifier(TEST_POWER_ID);
        }
    }

    private static Spell glyphSpell(String path) {
        AbstractSpellPart part = GlyphRegistry.getSpellpartMap()
            .get(new ResourceLocation("ars_nouveau", path));
        return part == null ? null : new Spell(part);
    }

    /**
     * Post an Ars spell-damage event for {@code caster} and report the damage the handler left
     * behind. {@code SpellDamageEvent.Pre} is cancelable on Ars 4.12.7, but the handler under
     * test never cancels, so the only thing that changes here is the amount.
     */
    private static float resolveDamage(GameTestHelper helper, ServerPlayer caster, Spell spell) {
        Pig target = helper.spawn(EntityType.PIG, 1, 2, 1);
        SpellContext context = SpellContext.fromEntity(spell, caster, ItemStack.EMPTY);
        SpellDamageEvent.Pre event = new SpellDamageEvent.Pre(
            caster.damageSources().magic(), caster, target, BASE_DAMAGE, context);
        MinecraftForge.EVENT_BUS.post(event);
        target.discard();
        return event.damage;
    }

    /**
     * Two casters, two schools, hits resolved in the order that used to mis-attribute: the fire
     * caster's projectile lands <em>after</em> the ice caster has already cast.
     */
    @GameTest(template = "platform", batch = "ans_damage_attribution")
    public static void ironsLoaded_delayedHitIsScaledByItsOwnCasterSchool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        Spell fire = glyphSpell("glyph_ignite");
        Spell ice = glyphSpell("glyph_freeze");
        if (fire == null || ice == null) {
            helper.fail("Ars ignite/freeze glyphs are missing; cannot classify two schools");
            return;
        }

        ServerPlayer fireCaster = caster(helper, "attrib_fire");
        ServerPlayer iceCaster = caster(helper, "attrib_ice");
        boost(fireCaster, AttributeRegistry.FIRE_SPELL_POWER.get(), 1.0);
        boost(iceCaster, AttributeRegistry.ICE_SPELL_POWER.get(), 2.0);

        try {
            float fireMultiplier = SpellScalingUtil.getMultiplierForCaster(fireCaster, fire).multiplier();
            float iceMultiplier = SpellScalingUtil.getMultiplierForCaster(iceCaster, ice).multiplier();
            if (Math.abs(fireMultiplier - iceMultiplier) < 0.1f) {
                helper.fail("the two casters must scale differently for this test to mean anything; "
                    + "got fire=" + fireMultiplier + " ice=" + iceMultiplier);
                return;
            }

            // The ice caster casts first and opens what used to be a 60-tick attribution window.
            float iceHit = resolveDamage(helper, iceCaster, ice);
            // The fire caster's delayed projectile lands inside that window.
            float fireHit = resolveDamage(helper, fireCaster, fire);

            if (!close(iceHit, BASE_DAMAGE * iceMultiplier)) {
                helper.fail("ice hit: expected " + (BASE_DAMAGE * iceMultiplier) + " but got " + iceHit);
                return;
            }
            if (!close(fireHit, BASE_DAMAGE * fireMultiplier)) {
                helper.fail("fire hit: expected " + (BASE_DAMAGE * fireMultiplier) + " but got " + fireHit);
                return;
            }
            if (close(fireHit, BASE_DAMAGE * iceMultiplier)) {
                helper.fail("the fire caster hit was scaled by the ice caster school");
                return;
            }
            helper.succeed();
        } finally {
            clearBoost(fireCaster, AttributeRegistry.FIRE_SPELL_POWER.get());
            clearBoost(iceCaster, AttributeRegistry.ICE_SPELL_POWER.get());
        }
    }

    /**
     * The other half of V08: incidental damage is no longer scaled just because the caster
     * recently cast. There is no window left to be inside, so a hit carrying no classifiable
     * spell passes through untouched.
     */
    @GameTest(template = "platform", batch = "ans_damage_attribution")
    public static void ironsLoaded_unclassifiedDamageIsNeverScaled(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = caster(helper, "attrib_melee");
        boost(player, AttributeRegistry.FIRE_SPELL_POWER.get(), 4.0);
        try {
            float dealt = resolveDamage(helper, player, new Spell());
            if (!close(dealt, BASE_DAMAGE)) {
                helper.fail("unclassified hit: expected " + BASE_DAMAGE + " but got " + dealt);
                return;
            }
            helper.succeed();
        } finally {
            clearBoost(player, AttributeRegistry.FIRE_SPELL_POWER.get());
        }
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) <= 0.01f;
    }
}
