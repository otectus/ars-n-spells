package com.otectus.arsnspells.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.bridge.AnsFeatureCleanup;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import com.otectus.arsnspells.progression.ProgressionAttributes;
import com.otectus.arsnspells.util.ManaUtil;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Audit V14 - turning a feature off must take its bonus off the player.
 *
 * <p>The regression these pin: every cleanup path was guarded by the same condition that had
 * enabled the feature. {@code clearArsBonusesFromIrons} returned early when Iron's was not
 * loaded, the potion redirect returned before its own removal when the mode was not
 * ISS_PRIMARY, and a mode change ran no cleanup at all. Enable the gear and progression
 * bonuses, take the source away, and the transient attribute modifiers stayed on the Iron's
 * attribute maps with no remaining code path able to remove them.
 *
 * <p>The reconcile sequence that {@code /ans mode set} and a config reload now run starts with
 * {@link AnsFeatureCleanup#removeAll}, which consults no config, no mode and no mod list. What
 * is asserted below is that property, against the real attribute maps.
 *
 * <p><b>Why no live mode flip here.</b> Writing {@code mana_unification_mode} from a GameTest
 * batch marks the server config spec dirty for the whole run, and that leaked into the
 * unrelated {@code ans_rituals} batch and failed it. The mode-change entry points are covered
 * by the unit suite and by their wiring; what needs a live attribute map is the cleanup, and
 * that is what runs here.
 *
 * <p>Iron's-only: the attributes the modifiers sit on are Iron's. Runs under
 * {@code -PwithIronsRuntimeGameTests}; self-skips otherwise.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ModeChangeCleanupGameTests {

    private ModeChangeCleanupGameTests() {}

    /** Enough glyph bonus that the ceiling sync has a real shortfall to cover. */
    private static final int GLYPH_BONUS = 400;

    private static ServerPlayer preparedPlayer(GameTestHelper helper, String scenario) {
        GameProfile profile = new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)),
            scenario.length() > 16 ? scenario.substring(0, 16) : scenario);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), profile);
        AnsFeatureCleanup.removeAll(player);
        ManaUtil.getNativeMana(player).ifPresent(cap -> {
            cap.setGlyphBonus(0);
            cap.setBookTier(0);
        });
        MagicData.getPlayerMagicData(player).setServerPlayer(player);
        MagicData.getPlayerMagicData(player).setMana(0.0f);
        return player;
    }

    /**
     * Gear ceiling plus progression bonus applied, then the ungated cleanup, then the gear
     * comes off. Nothing ANS owns may remain on the native attribute maps.
     */
    @GameTest(template = "platform", batch = "ans_mode_cleanup")
    public static void ironsLoaded_cleanupLeavesNoAnsModifiersOnTheNativeMaps(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "cleanup_modes");
        try {
            // 1. Two different features contribute, under two different identities.
            ManaUtil.getNativeMana(player).ifPresent(cap -> cap.setGlyphBonus(GLYPH_BONUS));
            EquipmentIntegration.syncIronsMaxToArs(
                player, EquipmentIntegration.arsRealMaxMana(player));
            ProgressionAttributes.applyTransientBonus(player, "fire", 0.25);
            if (AnsFeatureCleanup.countRemaining(player) < 2) {
                helper.fail("expected the gear ceiling and the progression bonus to be applied, "
                    + "found " + AnsFeatureCleanup.countRemaining(player)
                    + "; the cleanup assertion below would be vacuous");
                return;
            }

            // 2. Step one of the reconcile sequence: strip everything, gated on nothing.
            AnsFeatureCleanup.removeAll(player);
            if (AnsFeatureCleanup.countRemaining(player) != 0) {
                helper.fail(AnsFeatureCleanup.countRemaining(player)
                    + " ANS attribute modifier(s) survived removeAll");
                return;
            }

            // 3. Re-apply, take the gear away, and use the narrow cleanup the equipment handler
            //    calls. It used to return early on its own gate and leave the modifier behind.
            EquipmentIntegration.syncIronsMaxToArs(
                player, EquipmentIntegration.arsRealMaxMana(player));
            ManaUtil.getNativeMana(player).ifPresent(cap -> cap.setGlyphBonus(0));
            EquipmentIntegration.clearArsBonusesFromIrons(player);
            EquipmentIntegration.clearArsRegenBonusFromIrons(player);
            if (AnsFeatureCleanup.countRemaining(player) != 0) {
                helper.fail("the gear cleanup left " + AnsFeatureCleanup.countRemaining(player)
                    + " ANS attribute modifier(s) behind after the gear was removed");
                return;
            }
            helper.succeed();
        } finally {
            AnsFeatureCleanup.removeAll(player);
            ManaUtil.getNativeMana(player).ifPresent(cap -> cap.setGlyphBonus(0));
        }
    }

    /**
     * Progression <em>data</em> is not cleanup's business. Only the transient modifier goes, so
     * re-enabling the feature restores the same bonus rather than starting the player at zero.
     */
    @GameTest(template = "platform", batch = "ans_mode_cleanup")
    public static void ironsLoaded_cleanupRemovesTheModifierButKeepsTheCastCounts(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper, "cleanup_counts");
        try {
            player.getCapability(ProgressionData.PROGRESSION_DATA)
                .ifPresent(data -> data.incrementCastCount("fire"));
            int before = player.getCapability(ProgressionData.PROGRESSION_DATA)
                .map(data -> data.getAllCastCounts().getOrDefault("fire", 0))
                .orElse(0);
            if (before <= 0) {
                helper.fail("could not record a cast count to preserve");
                return;
            }

            ProgressionAttributes.applyTransientBonus(player, "fire", 0.25);
            AnsFeatureCleanup.removeAll(player);

            int after = player.getCapability(ProgressionData.PROGRESSION_DATA)
                .map(data -> data.getAllCastCounts().getOrDefault("fire", 0))
                .orElse(0);
            if (after != before) {
                helper.fail("cleanup destroyed progression data: " + before + " -> " + after);
                return;
            }
            if (AnsFeatureCleanup.countRemaining(player) != 0) {
                helper.fail("the progression modifier survived removeAll");
                return;
            }
            helper.succeed();
        } finally {
            AnsFeatureCleanup.removeAll(player);
        }
    }
}
