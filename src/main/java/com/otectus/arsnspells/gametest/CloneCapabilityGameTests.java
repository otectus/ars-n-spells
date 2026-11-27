package com.otectus.arsnspells.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.cooldown.CooldownCategory;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.data.CooldownData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Audit ANS-006 (same name as the Forge test): {@code PlayerEvent.Clone} fires on death and on the
 * End-to-Overworld return. Category cooldowns survive the non-death clone and reset on death, and
 * affinity is copied in both. On NeoForge the attachment system does the copy: every attachment on a
 * non-death clone, only {@code copyOnDeath} ones on death (the cooldown attachment is not). The event
 * is posted on the real bus, so the copy runs exactly as it does in game.
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

        ServerPlayer leaving = player(helper, "clone_end_old");
        ServerPlayer returned = player(helper, "clone_end_new");
        seed(leaving, end);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(returned, leaving, false));
        long kept = cooldowns(returned).getLastCast(CooldownCategory.OFFENSIVE);
        helper.assertTrue(kept == end, "an End return keeps the category cooldown end " + end + ", got " + kept);
        helper.assertTrue(affinity(returned) == 3, "affinity is copied on an End return, got " + affinity(returned));

        ServerPlayer dying = player(helper, "clone_death_old");
        ServerPlayer respawned = player(helper, "clone_death_new");
        seed(dying, end);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, dying, true));
        for (CooldownCategory category : CooldownCategory.values())
            helper.assertTrue(cooldowns(respawned).getLastCast(category) == 0, "death starts with no " + category + " cooldown");
        helper.assertTrue(affinity(respawned) == 3, "affinity is copied on death too, got " + affinity(respawned));
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String scenario) {
        return TestPlayers.get(helper.getLevel(), new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), scenario));
    }

    private static void seed(ServerPlayer player, long end) {
        cooldowns(player).setLastCast(CooldownCategory.OFFENSIVE, end);
        var data = player.getData(AttachmentTypes.AFFINITY.get());
        data.addLevel(FIRE, 3 - data.getLevel(FIRE));
    }

    private static CooldownData cooldowns(ServerPlayer player) {
        return player.getData(AttachmentTypes.COOLDOWN.get());
    }

    private static int affinity(ServerPlayer player) {
        return player.getData(AttachmentTypes.AFFINITY.get()).getLevel(FIRE);
    }
}
