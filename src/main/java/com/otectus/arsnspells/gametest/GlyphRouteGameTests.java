package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.AlternativePayment;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.GlyphSessions;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 3.4.1 review PAYMENT-01, in the pinned Covenant of the Seven profile (Covenant 2.2.6-hotfix, Blood
 * Magic, Enigmatic Legacy, Nature's Aura): an imported glyph pays with the resource a native cast of
 * the same spell takes from the same caster, and admission never demands a resource that route does
 * not spend. Every raw LP movement is reconciled with the timeline's alternative legs; the imported
 * legs are told apart by their marker.
 *
 * <p>All tests share one batch: the ring toggles, the failure policy and the mana mode are globals.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class GlyphRouteGameTests {
    private static final String COVENANT = "covenant_of_the_seven";
    private static final String BATCH = "ans_review_route";
    private static final String HEAL = "irons_spellbooks:heal";
    private static final String FIREBALL = "irons_spellbooks:fireball";

    private GlyphRouteGameTests() {}

    /** Cursed Ring, LP but no Iron's mana: the imported Heal runs and pays LP, as a native Heal does, and no mana. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_cursedRingImportedGlyphPaysLpLikeANativeCast(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_cursed", "enigmaticlegacy:cursed_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        float arsBefore = BridgeManager.getNativeArsBridge().getMana(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
        helper.assertTrue(player.getHealth() > 10.0f, "the imported Heal runs with no Iron's mana: admission did not demand it");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no Iron's mana was spent");
        helper.assertTrue(BridgeManager.getNativeArsBridge().getMana(player) == arsBefore, "no Ars mana was spent: the ring pays the Ars leg in LP");
        double imported = importedLp(player);
        int rawSpent = lpBefore - SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.assertTrue(imported > 0, "the imported leg paid LP: " + PaymentTimeline.entries(player.getUUID()));
        helper.assertTrue(Math.abs(rawSpent - allLp(player)) < 1.0e-6, "every LP movement is a recorded leg: raw " + rawSpent
            + ", recorded " + allLp(player) + "\n" + PaymentTimeline.entries(player.getUUID()));
        // The native cast of the same spell, same caster, same ring.
        player.setHealth(10.0f);
        IronsGlyphTestSupport.clearCooldowns(player);
        int nativeBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, IronsProxyCastDriver.nativeBook(HEAL), 0), "native Heal starts");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        int nativeSpent = nativeBefore - SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.assertTrue(player.getHealth() > 10.0f, "the native Heal runs");
        helper.assertTrue(Math.abs(nativeSpent - imported) < 1.0e-6,
            "the imported leg pays exactly the native LP: imported " + imported + ", native " + nativeSpent);
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
        helper.succeed();
    }

    /** Enough LP for the Ars leg, not for the imported one: refused before anything is taken. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_cursedRingInsufficientLpRefusesAndTakesNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_short", "enigmaticlegacy:cursed_ring", 10000);
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        Spell spell = new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 8));
        // Measure both legs of this exact cast once, with plenty of LP.
        ImportedGlyphGameTests.cast(helper, player, spell);
        double imported = importedLp(player), ars = allLp(player) - imported;
        helper.assertTrue(imported > ars + 1, "fixture: the imported leg costs more LP than the Ars leg (" + imported + " vs " + ars + ")");
        IronsGlyphTestSupport.clearCooldowns(player);
        PaymentTimeline.clear(player.getUUID());
        player.setHealth(10.0f);
        setLp(player, (int) Math.round(ars + imported) - 1);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        float ironsBefore = IronsProxyCastDriver.ironsMana(player);
        ImportedGlyphGameTests.cast(helper, player, spell);
        helper.assertTrue(player.getHealth() == 10.0f, "the Heal does not run");
        helper.assertTrue(SanctifiedLegacyCompat.getBloodMagicLP(player) == lpBefore, "no LP is taken by a refused cast");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == ironsBefore, "and no mana: the route is LP, refused, not silently switched");
        var records = GlyphSessions.records(player.getUUID());
        helper.assertTrue(!records.isEmpty() && records.get(records.size() - 1).outcome().contains("message.ars_n_spells.cast.insufficient"),
            "the caster is told LP is short: " + records);
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.succeed();
    }

    /** The native fallback policy: with too little LP for the imported leg it pays routed mana instead, as a native cast falls back. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_nativeFallbackPaysManaWhenLpIsShort(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_fallback", "enigmaticlegacy:cursed_ring", 10000);
        // Iron's clamps its pool to the max-mana attribute (100 by default); Heal VIII needs more.
        IronsGlyphTestSupport.fundIronsPool(player, 5000.0f);
        Spell spell = new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 8));
        ImportedGlyphGameTests.cast(helper, player, spell);
        double ars = allLp(player) - importedLp(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        PaymentTimeline.clear(player.getUUID());
        player.setHealth(10.0f);
        TestConfig.set(AnsConfig.PAYMENT_OPEN_FAILURE_POLICY, "native_fallback");
        AnsConfig.refreshResolvedPolicies();
        setLp(player, (int) Math.round(ars));
        float ironsBefore = IronsProxyCastDriver.ironsMana(player);
        ImportedGlyphGameTests.cast(helper, player, spell);
        TestConfig.set(AnsConfig.PAYMENT_OPEN_FAILURE_POLICY, "refuse");
        AnsConfig.refreshResolvedPolicies();
        helper.assertTrue(player.getHealth() > 10.0f, "the Heal runs on the fallback: " + GlyphSessions.records(player.getUUID())
            + "\n" + PaymentTimeline.entries(player.getUUID()));
        helper.assertTrue(importedLp(player) == 0, "the imported leg took no LP");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) < ironsBefore, "it paid routed mana instead");
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.succeed();
    }

    /** Virtue Ring with the aura system on: Covenant takes ambient aura, ANS takes no mana, and admission demands none. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_virtueRingImportedGlyphPaysCovenantAuraNotMana(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_virtue", "covenant_of_the_seven:virtue_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        int auraBefore = SanctifiedLegacyCompat.getCovenantAura(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
        helper.assertTrue(player.getHealth() > 10.0f, "the imported Heal runs with no Iron's mana");
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no Iron's mana was spent");
        helper.assertTrue(SanctifiedLegacyCompat.getCovenantAura(player) < auraBefore, "Covenant took aura for it");
        helper.assertTrue(SanctifiedLegacyCompat.getBloodMagicLP(player) == lpBefore, "and no LP");
        helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
        helper.succeed();
    }

    /** A charged glyph under the Cursed Ring: the admission leg is handed back and Iron's own pipeline takes the LP once. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void covenantLoaded_route_cursedRingChargedGlyphPaysLpOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, COVENANT)) return;
        ServerPlayer player = ringed(helper, "route_charged", "enigmaticlegacy:cursed_ring", 10000);
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        TouchRegressionGameTests.lookAtSky(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(FIREBALL, 1)));
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "the imported Fireball charges with no Iron's mana");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        int spent = lpBefore - SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.assertTrue(Math.abs(spent - allLp(player)) < 1.0e-6, "every LP movement is a recorded leg: raw " + spent
            + ", recorded " + allLp(player) + "\n" + PaymentTimeline.entries(player.getUUID()));
        long releases = PaymentTimeline.entries(player.getUUID()).stream().filter(e -> e.kind() == PaymentTimeline.Kind.REFUND).count();
        helper.assertTrue(releases >= 1, "the admission leg was handed back before Iron's pipeline priced the release\n"
            + PaymentTimeline.entries(player.getUUID()));
        helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no Iron's mana was spent");
        helper.assertTrue(noOpenLegs(player), "no LP leg of this caster is left open: " + AlternativePayment.allOpen());
        helper.succeed();
    }

    // ---- Fixtures --------------------------------------------------------------------------------

    private static volatile boolean lpBefore, auraBefore, deathBefore;
    private static volatile String sourceBefore, policyBefore, modeBefore;

    @BeforeBatch(batch = BATCH)
    public static void before(ServerLevel level) {
        lpBefore = AnsConfig.ENABLE_LP_SYSTEM.get();
        auraBefore = AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM.get();
        deathBefore = AnsConfig.DEATH_ON_INSUFFICIENT_LP.get();
        sourceBefore = AnsConfig.LP_SOURCE_MODE.get();
        policyBefore = AnsConfig.PAYMENT_OPEN_FAILURE_POLICY.get();
        modeBefore = AnsConfig.MANA_UNIFICATION_MODE.get();
        TestConfig.set(AnsConfig.ENABLE_LP_SYSTEM, true);
        TestConfig.set(AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM, true);
        TestConfig.set(AnsConfig.DEATH_ON_INSUFFICIENT_LP, false);
        TestConfig.set(AnsConfig.LP_SOURCE_MODE, "BLOOD_MAGIC_ONLY");
        TestConfig.set(AnsConfig.PAYMENT_OPEN_FAILURE_POLICY, "refuse");
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, "iss_primary");
        AnsConfig.refreshResolvedPolicies();
        BridgeManager.refreshMode();
    }

    @AfterBatch(batch = BATCH)
    public static void after(ServerLevel level) {
        if (sourceBefore == null) return;
        TestConfig.set(AnsConfig.ENABLE_LP_SYSTEM, lpBefore);
        TestConfig.set(AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM, auraBefore);
        TestConfig.set(AnsConfig.DEATH_ON_INSUFFICIENT_LP, deathBefore);
        TestConfig.set(AnsConfig.LP_SOURCE_MODE, sourceBefore);
        TestConfig.set(AnsConfig.PAYMENT_OPEN_FAILURE_POLICY, policyBefore);
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, modeBefore);
        AnsConfig.refreshResolvedPolicies();
        BridgeManager.refreshMode();
    }

    private static ServerPlayer ringed(GameTestHelper helper, String name, String ring, int lp) {
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, name);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10.0f);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var slot = curios.getCurios().get("ring");
        if (slot == null) throw new IllegalStateException("Pinned Covenant ring slot missing");
        slot.getStacks().setStackInSlot(0, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(ring))));
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        helper.assertTrue(ring.endsWith("virtue_ring") ? SanctifiedLegacyCompat.isWearingVirtueRing(player)
            : SanctifiedLegacyCompat.isWearingCursedRing(player), "the ring is worn");
        setLp(player, lp);
        PaymentTimeline.enableForTesting(true);
        PaymentTimeline.clear(player.getUUID());
        return player;
    }

    private static boolean noOpenLegs(ServerPlayer player) {
        return AlternativePayment.allOpen().stream().noneMatch(leg -> leg.playerId().equals(player.getUUID()));
    }

    private static void setLp(ServerPlayer player, int lp) {
        try {
            Object network = Class.forName("wayoftime.bloodmagic.util.helper.NetworkHelper").getMethod("getSoulNetwork", java.util.UUID.class)
                .invoke(null, player.getUUID());
            network.getClass().getMethod("setCurrentEssence", int.class).invoke(network, lp);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Net LP moved by the imported glyphs' legs (their markers), from the timeline. */
    private static double importedLp(ServerPlayer player) {
        List<PaymentTimeline.Entry> entries = PaymentTimeline.entries(player.getUUID());
        Set<String> imported = new HashSet<>();
        for (PaymentTimeline.Entry e : entries) if (e.label().startsWith("glyph-lp:")) imported.add("alt:" + e.label().substring(9));
        return netLp(entries, imported);
    }

    /** Net LP moved by every alternative leg, from the timeline. */
    private static double allLp(ServerPlayer player) {
        return netLp(PaymentTimeline.entries(player.getUUID()), null);
    }

    private static double netLp(List<PaymentTimeline.Entry> entries, Set<String> only) {
        double net = 0;
        for (PaymentTimeline.Entry e : entries) {
            if (!e.label().startsWith("alt:") || (only != null && !only.contains(e.label()))) continue;
            if (!e.detail().contains("moved=" + ResourceUnit.LP + ":")) continue;
            double moved = Double.parseDouble(e.detail().substring(e.detail().indexOf("moved=LP:") + 9).trim());
            if (e.kind() == PaymentTimeline.Kind.DEBIT) net += moved;
            else if (e.kind() == PaymentTimeline.Kind.REFUND) net -= moved;
        }
        return net;
    }
}
