package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.cooldown.CooldownCategory;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.CooldownData;
import com.otectus.arsnspells.data.ModCapabilityProvider;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Audit ANS-006: {@code PlayerEvent.Clone} fires on death and on the End-to-Overworld return.
 * Category cooldowns survive the non-death clone and reset on death; affinity is copied in both.
 * The clone handler runs as Forge runs it, on two real players (the original and its replacement).
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CloneCapabilityGameTests {
    private static final String FIRE = "irons_spellbooks:fire";

    private CloneCapabilityGameTests() {}

    @GameTest(template = "platform")
    public static void clone_endReturnKeepsCooldownsAndDeathResetsThem(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        long end = now + 600;

        ServerPlayer leaving = CrossCastGameTests.scenarioPlayer(helper, "clone_end_old");
        ServerPlayer returned = CrossCastGameTests.scenarioPlayer(helper, "clone_end_new");
        seed(leaving, end);
        ModCapabilityProvider.onPlayerClone(new PlayerEvent.Clone(returned, leaving, false));
        long kept = cooldowns(returned).getCooldownEnd(CooldownCategory.OFFENSIVE);
        helper.assertTrue(kept == end, "an End return keeps the category cooldown end " + end + ", got " + kept);
        helper.assertTrue(kept - helper.getLevel().getGameTime() == end - now,
            "with the same remaining time under the current game-time clock");
        helper.assertTrue(affinity(returned) == 3, "affinity is copied on an End return, got " + affinity(returned));

        ServerPlayer dying = CrossCastGameTests.scenarioPlayer(helper, "clone_death_old");
        ServerPlayer respawned = CrossCastGameTests.scenarioPlayer(helper, "clone_death_new");
        seed(dying, end);
        ModCapabilityProvider.onPlayerClone(new PlayerEvent.Clone(respawned, dying, true));
        for (CooldownCategory category : CooldownCategory.values())
            helper.assertTrue(cooldowns(respawned).getCooldownEnd(category) == 0, "death starts with no " + category + " cooldown");
        helper.assertTrue(affinity(respawned) == 3, "affinity is copied on death too, got " + affinity(respawned));

        CooldownData legacy = new CooldownData();
        legacy.load(new CompoundTag());
        helper.assertTrue(legacy.getCooldownEnd(CooldownCategory.OFFENSIVE) == 0, "a save without BridgeCooldowns loads as no cooldowns");
        helper.succeed();
    }

    private static void seed(ServerPlayer player, long end) {
        cooldowns(player).setCooldownEnd(CooldownCategory.OFFENSIVE, end);
        AffinityData data = player.getCapability(AffinityData.AFFINITY_DATA).orElseThrow(IllegalStateException::new);
        data.addLevel(FIRE, 3 - data.getLevel(FIRE));
    }

    private static CooldownData cooldowns(ServerPlayer player) {
        return player.getCapability(CooldownData.COOLDOWN_CAP).orElseThrow(IllegalStateException::new);
    }

    private static int affinity(ServerPlayer player) {
        return player.getCapability(AffinityData.AFFINITY_DATA).map(data -> data.getLevel(FIRE)).orElse(-1);
    }
}
