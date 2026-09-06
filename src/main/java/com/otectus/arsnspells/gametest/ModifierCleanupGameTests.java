package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import com.otectus.arsnspells.events.ModeChangeMigration;
import com.otectus.arsnspells.events.ProgressionHandler;
import com.otectus.arsnspells.modifier.AnsFeatureCleanup;
import com.otectus.arsnspells.progression.ProgressionModifiers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Turning a feature off must take its attribute modifiers with it (audit V14).
 *
 * <p>Every cleanup path used to be guarded by the condition that had enabled the feature, so
 * the one case that most needed it - the feature being switched off - ran nothing, and the
 * bonus stayed on the player for the rest of the session (and, since the modifier is re-applied
 * from persistent data on every login, effectively forever). These drive the real attribute
 * maps against the real Iron's runtime, because the whole finding is about what is left behind
 * on them.
 *
 * <p>Iron's-gated: without Iron's the attributes ANS writes to do not exist, so there is
 * nothing for a leak to hide in.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ModifierCleanupGameTests {

    private static final GameProfile FAKE_PROFILE =
        new GameProfile(UUID.fromString("0a000000-0000-0000-0000-00000000c1ea"), "ans_cleanup_test");

    /** Enough glyph bonus that Ars's max clears Iron's default pool, so a ceiling is written. */
    private static final int GLYPH_BONUS = 40;

    private ModifierCleanupGameTests() {}

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        // FakePlayerFactory caches by profile, so every test in this class gets the same
        // instance; anything an earlier test left on it would be this test's baseline.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), FAKE_PROFILE);
        AnsFeatureCleanup.removeAll(player);
        setGlyphBonus(player, 0);
        return player;
    }

    private static void setGlyphBonus(ServerPlayer player, int bonus) {
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap != null) {
            cap.setGlyphBonus(bonus);
        }
    }

    /**
     * Gear bonus and progression bonus both applied, then the mode changes and the gear comes
     * off with the feature disabled. Nothing of ours may remain on any attribute.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_modeChangeThenGearRemoved_leavesNoAnsModifier(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        String previousMode = AnsConfig.MANA_UNIFICATION_MODE.get();
        boolean previousProgression = AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get();
        try {
            AnsConfig.MANA_UNIFICATION_MODE.set(ManaUnificationMode.HYBRID.getConfigName());
            BridgeManager.refreshMode();

            // Gear: a glyph bonus large enough that the shared-pool ceiling has to be written.
            setGlyphBonus(player, GLYPH_BONUS);
            EquipmentIntegration.recomputeFor(player);
            // Progression: real cast counts, so the bonus is the one a played character has.
            ProgressionData data = player.getData(AttachmentTypes.PROGRESSION.get());
            for (int i = 0; i < 50; i++) {
                data.incrementCastCount("fire");
            }
            ProgressionModifiers.apply(player, "fire", data.getBonusForSchool("fire"));

            int applied = AnsFeatureCleanup.ansModifierCount(player);
            if (applied < 2) {
                helper.fail("test setup failed: expected a gear modifier and a progression "
                    + "modifier on the player, found " + applied + " ANS modifiers");
                return;
            }

            // Now turn both off the way a server owner does: edit the config, switch the mode.
            AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.set(false);
            AnsConfig.MANA_UNIFICATION_MODE.set(ManaUnificationMode.SEPARATE.getConfigName());
            BridgeManager.refreshMode();
            ModeChangeMigration.migrate(player);

            setGlyphBonus(player, 0);
            EquipmentIntegration.recomputeFor(player);

            int remaining = AnsFeatureCleanup.ansModifierCount(player);
            if (remaining != 0) {
                helper.fail("disabling the features must remove every ANS modifier; "
                    + remaining + " still on the player's attributes");
                return;
            }
            if (data.getCastCount("fire") != 50) {
                helper.fail("progression data must survive a disable: expected 50 fire casts, "
                    + "found " + data.getCastCount("fire"));
                return;
            }
        } finally {
            AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.set(previousProgression);
            AnsConfig.MANA_UNIFICATION_MODE.set(previousMode);
            BridgeManager.refreshMode();
            AnsFeatureCleanup.removeAll(player);
            setGlyphBonus(player, 0);
        }
        helper.succeed();
    }

    /**
     * The replay path is a cleanup path too: running it with the feature disabled must remove
     * the bonus rather than return early and leave it applied.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_reapplyWithProgressionDisabled_removesTheBonus(
            GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        ServerPlayer player = preparedPlayer(helper);
        boolean previousProgression = AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get();
        try {
            ProgressionModifiers.apply(player, "fire", 0.2);
            if (AnsFeatureCleanup.ansModifierCount(player) != 1) {
                helper.fail("test setup failed: the progression bonus was not applied");
                return;
            }

            AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.set(false);
            ProgressionHandler.reapplyAll(player);

            int remaining = AnsFeatureCleanup.ansModifierCount(player);
            if (remaining != 0) {
                helper.fail("reapplyAll claims to be idempotent; with the feature off it must "
                    + "clear, not skip - " + remaining + " modifiers left");
            }
        } finally {
            AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.set(previousProgression);
            AnsFeatureCleanup.removeAll(player);
        }
        helper.succeed();
    }
}
