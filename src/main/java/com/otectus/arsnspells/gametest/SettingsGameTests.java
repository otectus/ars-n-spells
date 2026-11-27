package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.PaymentTimeline;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.AnsSettings;
import com.otectus.arsnspells.config.AnsSettingsService;
import com.otectus.arsnspells.config.ConfigAuthority;
import com.otectus.arsnspells.glyph.runtime.GlyphOutcome;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * 3.4.1 review CONFIG-01: world settings change through one server-side service. A console or
 * operator apply reaches the routing, the players and the file; an invalid request changes nothing; a
 * player who is neither an operator nor the world's owner is refused; and a mode change while an
 * imported charge is in flight keeps the price it was admitted at, neither repricing nor losing it.
 * The owner check itself is a single-player client matter (the GameTest server has no owner) and is
 * unit-tested by its rule.
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
        if (modeBefore != null) AnsSettingsService.apply(null, Map.of(AnsSettings.MODE, modeBefore));
    }

    private static AnsSettingsService.Result applyMode(String mode) {
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
        ServerPlayer player = IronsInscriptionTableGameTests.scenarioPlayer(helper, "settings_guest");
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

    /**
     * A mode change while an imported Slow charges: the charge completes at the price it was admitted
     * at. Nothing is debited after the change, nothing is refunded and repriced, and the session closes.
     */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 200)
    public static void ironsLoaded_settings_modeChangeMidChargeKeepsTheAdmittedPrice(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        applyMode("iss_primary");
        ServerPlayer player = CastReviewGameTests.caster(helper, "settings_charge");
        Pig pig = CastReviewGameTests.sturdyPig(helper, new Vec3(3.5, 2.0, 2.5));
        ImportedGlyphGameTests.aim(player, pig.getBoundingBox().getCenter());
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodTouch.INSTANCE, ImportedGlyphGameTests.glyph("slow", 1)));
        helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 1, "the imported Slow charges");
        List<PaymentTimeline.Entry> admitted = glyphDebits(player);
        helper.assertTrue(admitted.size() == 1, "the charge was admitted with one reservation: " + PaymentTimeline.entries(player.getUUID()));
        AnsSettingsService.Result changed = applyMode("ars_primary");
        helper.assertTrue(changed.applied() && changed.changed(), "the mode changes mid-charge: " + changed);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(GlyphSessions.continuationsFor(player.getUUID()) == 0, "the charge ends"))
            .thenExecuteAfter(3, () -> {
                List<PaymentTimeline.Entry> timeline = PaymentTimeline.entries(player.getUUID());
                helper.assertTrue(ImportedGlyphGameTests.hasEffect(pig, "irons_spellbooks:slowed"), "the charge was not lost: " + timeline);
                helper.assertTrue(CastReviewGameTests.outcome(player, "irons_spellbooks:slow", GlyphOutcome.EFFECT_APPLIED, ""),
                    "it completed: " + GlyphSessions.outcomes(player.getUUID()));
                helper.assertTrue(glyphDebits(player).equals(admitted), "no debit after the mode change, so no repricing: " + timeline);
                helper.assertTrue(timeline.stream().noneMatch(e -> e.kind() == PaymentTimeline.Kind.REFUND && e.label().startsWith("glyph:")),
                    "nothing was refunded: " + timeline);
                helper.assertTrue(timeline.stream().filter(e -> e.kind() == PaymentTimeline.Kind.COMMIT && e.label().startsWith("glyph:")).count() == 1,
                    "the admitted reservation is committed once: " + timeline);
                helper.assertTrue(GlyphSessions.forPlayer(player.getUUID()).isEmpty(), "the session closes");
            })
            .thenSucceed();
    }

    private static List<PaymentTimeline.Entry> glyphDebits(ServerPlayer player) {
        return PaymentTimeline.entries(player.getUUID()).stream()
            .filter(e -> e.kind() == PaymentTimeline.Kind.DEBIT && e.label().startsWith("glyph:")).toList();
    }
}
