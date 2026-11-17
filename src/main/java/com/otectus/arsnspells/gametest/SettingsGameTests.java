package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.AnsSettings;
import com.otectus.arsnspells.config.AnsSettingsService;
import com.otectus.arsnspells.config.ConfigAuthority;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.Map;

/**
 * 3.4.1 review CONFIG-01: world settings change through one server-side service. A console or
 * operator apply reaches the routing, the players and the file; an invalid request changes nothing; a
 * player who is neither an operator nor the world's owner is refused; and a mode change while an
 * imported charge is in flight neither debits twice nor loses the hold. The owner check itself is a
 * single-player client matter (the GameTest server has no owner) and is unit-tested by its rule.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SettingsGameTests {
    private static final String BATCH = "ans_review_settings";
    private static volatile String modeBefore;

    private SettingsGameTests() {}

    @BeforeBatch(batch = BATCH)
    public static void before(ServerLevel level) {
        modeBefore = AnsConfig.MANA_UNIFICATION_MODE.get();
    }

    @AfterBatch(batch = BATCH)
    public static void after(ServerLevel level) {
        if (modeBefore != null) applyMode(modeBefore);
    }

    /**
     * Apply {@code mode} as the console, through the service. The GameTest harness re-applies values
     * earlier tests asked for whenever Forge reloads the file, which the service's own save triggers;
     * declaring the mode as wanted first keeps that guard from reverting the service's write.
     */
    private static AnsSettingsService.Result applyMode(String mode) {
        TestConfig.want(AnsConfig.MANA_UNIFICATION_MODE, mode);
        return AnsSettingsService.apply(null, Map.of(AnsSettings.MODE, mode));
    }

    @GameTest(template = "platform", batch = BATCH)
    public static void settings_consoleApplyReachesRoutingPlayersAndTheFile(GameTestHelper helper) {
        String target = "iss_primary".equals(AnsConfig.MANA_UNIFICATION_MODE.get()) ? "separate" : "iss_primary";
        AnsSettingsService.Result outcome = applyMode(target);
        helper.assertTrue(outcome.applied() && outcome.changed(), "the console may change the mode: " + outcome);
        helper.assertTrue(target.equals(AnsConfig.MANA_UNIFICATION_MODE.get()), "the value is in force");
        if (IronsCompat.isLoaded())
            helper.assertTrue(target.equals(BridgeManager.getCurrentMode().getConfigName()), "the routing follows at once");
        helper.assertTrue("ars_n_spells.settings.result.applied".equals(outcome.resultKey()), "and the file was saved: " + outcome);
        try {
            String file = Files.readString(AnsConfig.loadedPath());
            helper.assertTrue(file.contains("mana_unification_mode = \"" + target + "\""), "the saved file holds the new mode");
        } catch (java.io.IOException | NullPointerException unreadable) {
            helper.fail("the saved config file cannot be read: " + unreadable);
        }
        AnsSettingsService.Result again = applyMode(target);
        helper.assertTrue(again.applied() && !again.changed(), "applying the values in force changes nothing: " + again);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = BATCH)
    public static void settings_anInvalidRequestChangesNothing(GameTestHelper helper) {
        Map<String, String> before = AnsSettings.current();
        AnsSettingsService.Result bad = AnsSettingsService.apply(null, Map.of("debug_mode", "true", AnsSettings.MODE, "bogus"));
        helper.assertTrue(!bad.applied() && bad.resultKey().endsWith("invalid_value"), "an invalid value refuses the whole request: " + bad);
        AnsSettingsService.Result unknown = AnsSettingsService.apply(null, Map.of("enable_lp_system", "true"));
        helper.assertTrue(!unknown.applied() && unknown.resultKey().endsWith("unknown_key"), "an unknown key is refused: " + unknown);
        helper.assertTrue(before.equals(AnsSettings.current()), "nothing changed, not even the valid half: " + AnsSettings.current());
        helper.succeed();
    }

    @GameTest(template = "platform", batch = BATCH)
    public static void settings_aPlayerWhoIsNeitherOperatorNorOwnerIsRefused(GameTestHelper helper) {
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "settings_guest");
        helper.assertTrue(!ConfigAuthority.mayChange(player), "a non-op player on a server without an owner may not change settings");
        Map<String, String> before = AnsSettings.current();
        String other = "iss_primary".equals(before.get(AnsSettings.MODE)) ? "separate" : "iss_primary";
        AnsSettingsService.Result outcome = AnsSettingsService.apply(player, Map.of(AnsSettings.MODE, other));
        helper.assertTrue(!outcome.applied() && outcome.resultKey().endsWith("not_allowed"), "refused: " + outcome);
        helper.assertTrue(before.equals(AnsSettings.current()), "nothing changed");
        var snapshot = AnsSettingsService.snapshotFor(player, null);
        helper.assertTrue(!snapshot.canEdit(), "the screen is told it is read-only for this player");
        helper.succeed();
    }

    /** A mode change while an imported Fireball charges: its release is refused and refunded, never billed twice. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void ironsLoaded_settings_modeChangeMidChargeNeitherDoubleBillsNorLosesTheHold(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = CastPolicyGameTests.caster(helper, "settings_charge");
        player.setGameMode(GameType.SURVIVAL);
        TouchRegressionGameTests.lookAtSky(player);
        applyMode("iss_primary");
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("irons_spellbooks:fireball", 1)));
        helper.assertTrue(IronsGlyphTestSupport.casting(player), "the imported Fireball charges");
        applyMode("ars_primary");
        IronsProxyCastDriver.tickUntilCastEnds(player);
        helper.startSequence()
            .thenExecuteAfter(3, () -> {
                long effects = PaymentTimeline.entries(player.getUUID()).stream()
                    .filter(e -> e.kind() == PaymentTimeline.Kind.NATIVE_EFFECT).count();
                helper.assertTrue(effects == 0, "the release priced under the old mode is refused, not repriced: "
                    + PaymentTimeline.entries(player.getUUID()));
                CastPolicyGameTests.assertNetImportedDebit(helper, player, 0.0);
                helper.assertTrue(CastLifecycleGameTests.baseline(player), "nothing of the cast remains");
            })
            .thenSucceed();
    }
}
