package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.bridge.AnsFeatureCleanup;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import com.otectus.arsnspells.progression.ProgressionAttributes;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Audit V14 - turning a feature off must take its bonus off the player. The NeoForge
 * counterpart of the Forge 1.20.1 suite of the same name.
 *
 * <p>The regression these pin: every cleanup path was guarded by the same condition that had
 * enabled the feature, so a disabled feature left its transient attribute modifiers on the
 * Iron's attribute maps with no remaining code path able to remove them. The reconcile sequence
 * that {@code /ans mode set} and a config reload run starts with
 * {@link AnsFeatureCleanup#removeAll}, which is gated on nothing.
 *
 * <p>Iron's-only: the attributes being asserted on are Iron's. Runs under
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
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap != null) {
            cap.setGlyphBonus(0);
            cap.setBookTier(0);
        }
        MagicData.getPlayerMagicData(player).setServerPlayer(player);
        MagicData.getPlayerMagicData(player).setMana(0.0f);
        return player;
    }

    private static void glyphBonus(ServerPlayer player, int bonus) {
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap != null) {
            cap.setGlyphBonus(bonus);
        }
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
            glyphBonus(player, GLYPH_BONUS);
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
            glyphBonus(player, 0);
            EquipmentIntegration.clearAll(player);
            EquipmentIntegration.clearArsRegenBonusFromIrons(player);
            if (AnsFeatureCleanup.countRemaining(player) != 0) {
                helper.fail("the gear cleanup left " + AnsFeatureCleanup.countRemaining(player)
                    + " ANS attribute modifier(s) behind after the gear was removed");
                return;
            }
            helper.succeed();
        } finally {
            AnsFeatureCleanup.removeAll(player);
            glyphBonus(player, 0);
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
            ProgressionData data = player.getData(AttachmentTypes.PROGRESSION.get());
            data.incrementCastCount("fire");
            int before = data.getAllCastCounts().getOrDefault("irons_spellbooks:fire", 0);
            if (before <= 0) {
                helper.fail("could not record a cast count to preserve");
                return;
            }

            ProgressionAttributes.applyTransientBonus(player, "fire", 0.25);
            AnsFeatureCleanup.removeAll(player);

            int after = player.getData(AttachmentTypes.PROGRESSION.get())
                .getAllCastCounts().getOrDefault("irons_spellbooks:fire", 0);
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
