package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.casting.AttemptLedgerService;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.CrossModSpellList;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * A cross-cast that does not happen must cost nothing, and must say so (audit V03).
 *
 * <p>The defect: {@code castArsSpell} called {@code SpellResolver.onCast}, threw the return value
 * away, and returned {@code true} unconditionally. In the pinned Ars 5.13.1.1400 that value is the
 * cast's success - {@code onCast} returns {@code CastResolveType.wasSuccess}, and returns
 * {@code false} outright when its own {@code canCast()} fails or when {@code postEvent()} comes
 * back cancelled. So a spell another mod cancelled was reported as cast, the advancement was
 * granted, and any resources already committed to it stayed committed.
 *
 * <p>This runs the real pipeline - a real {@link Spell}, a real {@code SpellResolver}, the real
 * mixins - with a test-only listener that cancels {@link SpellCastEvent}, which is exactly how a
 * downstream mod refuses a cast. Only a booted server can prove this: the mixins have to be
 * applied and the events actually posted.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CrossCastSettlementGameTests {

    private static final GameProfile FAKE_PROFILE =
        new GameProfile(UUID.fromString("0a000000-0000-0000-0000-000000000003"), "ans_settle_test");

    private CrossCastSettlementGameTests() {}

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), FAKE_PROFILE);
        MagicData data = MagicData.getPlayerMagicData(player);
        data.setServerPlayer(player);
        data.setMana(500.0f);
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap != null) {
            cap.setMana(cap.getMaxMana());
        }
        return player;
    }

    /** A carrier holding one genuine Ars spell, the shape the Spell Loom hands out. */
    private static ItemStack arsCarrier() {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.BOOK);
        CrossCastingHandler.addCrossModSpell(stack, new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        return stack;
    }

    private static float arsPool(ServerPlayer player) {
        ManaCap cap = CapabilityRegistry.getMana(player);
        return cap == null ? 0.0f : (float) cap.getCurrentMana();
    }

    private static float ironsPool(ServerPlayer player) {
        return MagicData.getPlayerMagicData(player).getMana();
    }

    /**
     * The audit scenario. A downstream cancellation must leave both pools exactly as it found
     * them, <em>and</em> the cross-cast must report failure.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_downstreamCancel_touchesNoPoolAndReturnsFalse(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        ItemStack carrier = arsCarrier();
        CrossModSpellList list = CrossModSpellComponents.get(carrier);
        if (list.isEmpty()) {
            helper.fail("test setup failed: the carrier holds no cross-cast entry");
            return;
        }
        CrossModSpell entry = list.spells().get(0);

        float arsBefore = arsPool(player);
        float ironsBefore = ironsPool(player);
        int openBefore = AttemptLedgerService.openFor(player).size();

        // HIGHEST so the refusal lands before anything else can react to the cast.
        Consumer<SpellCastEvent> refuse = event -> event.setCanceled(true);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, SpellCastEvent.class, refuse);
        boolean reported;
        try {
            reported = CrossCastingHandler.castArsSpell(player, carrier, entry, UUID.randomUUID());
        } finally {
            NeoForge.EVENT_BUS.unregister(refuse);
        }

        if (reported) {
            helper.fail("a cancelled cast must report failure. SpellResolver.onCast returns false "
                + "when postEvent() comes back cancelled; discarding that value and returning "
                + "true is audit V03");
            return;
        }
        if (Math.abs(arsPool(player) - arsBefore) > 0.01f) {
            helper.fail("a cancelled cast must not move the Ars pool: was " + arsBefore
                + ", now " + arsPool(player));
            return;
        }
        if (Math.abs(ironsPool(player) - ironsBefore) > 0.01f) {
            helper.fail("a cancelled cast must not move the Iron's pool: was " + ironsBefore
                + ", now " + ironsPool(player) + ". A reservation taken before the cast has to be "
                + "released by the finally, exactly once");
            return;
        }
        if (AttemptLedgerService.openFor(player).size() != openBefore) {
            helper.fail("a cancelled cast must leave no attempt open - that is the leak the TTL "
                + "sweep exists to catch, and it should never have to");
            return;
        }
        helper.succeed();
    }

    /**
     * The same cast, uncancelled, must actually settle: no attempt left open either way. Without
     * this the test above passes just as well for a pipeline that never runs at all.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_uncancelledCast_leavesNoAttemptOpen(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        ItemStack carrier = arsCarrier();
        CrossModSpellList list = CrossModSpellComponents.get(carrier);
        if (list.isEmpty()) {
            helper.fail("test setup failed: the carrier holds no cross-cast entry");
            return;
        }

        CrossCastingHandler.castArsSpell(
            player, carrier, list.spells().get(0), UUID.randomUUID());

        if (!AttemptLedgerService.openFor(player).isEmpty()) {
            helper.fail("every exit path settles in a finally, so no attempt may survive the "
                + "call: " + AttemptLedgerService.openFor(player).size() + " left open");
            return;
        }
        helper.succeed();
    }
    // ------------------------------------------------------------------
    //  Audit V02: the carrier's kind decides the billing, not its data
    // ------------------------------------------------------------------

    /**
     * A reusable carrier and a consumable scroll must bill differently, and each the way its own
     * kind implies - pool delta, cooldown, stack count, and whether a recast spell may be cast at
     * all.
     *
     * <p>The old code read the {@code CastSource} out of the item's own cross-cast data and fell
     * back to {@code SCROLL}, which Iron's neither charges mana for nor cooldowns. So a spellbook
     * carrying an edited (or simply default) descriptor cast for free, forever. Held against the
     * real Iron's runtime, since the whole finding is about what Iron's does with the value.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_carrierKindDecidesBilling_notTheSerializedSource(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);

        ItemStack reusable = IronsCarrierSupport.spellBookWithNativeSpell();
        if (reusable.isEmpty()) {
            helper.fail("test setup failed: no Iron's spellbook fixture available");
            return;
        }
        CrossCastingHandler.addCrossModSpell(
            reusable, new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));

        net.minecraft.nbt.CompoundTag payload =
            CrossCastingHandler.encodeArsSpell(new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE));
        ItemStack scroll = IronsCarrierSupport.scrollCarrier(payload);
        if (scroll.isEmpty()) {
            helper.fail("test setup failed: no Iron's scroll fixture available");
            return;
        }

        String failure = IronsCarrierPolicySupport.checkNativeSourceSemantics();
        if (failure == null) {
            failure = IronsCarrierPolicySupport.checkCarrierMapping(reusable, scroll);
        }
        if (failure == null) {
            failure = IronsCarrierPolicySupport.checkBillingDifference(player, reusable, scroll);
        }
        if (failure == null) {
            failure = IronsCarrierPolicySupport.checkRecastSpellIsBookOnly(player, reusable, scroll);
        }
        if (failure != null) {
            helper.fail(failure);
            return;
        }
        helper.succeed();
    }
}
