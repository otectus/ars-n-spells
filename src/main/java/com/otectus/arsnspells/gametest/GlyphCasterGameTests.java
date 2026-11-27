package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.runtime.GlyphAuthorization;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Audit ANS-002: imported glyphs run for real players only. NeoForge's {@code FakePlayer} (what other
 * mods' automation uses) and Ars's {@code ANFakePlayer} are refused before any imported effect or
 * payment, and a session cannot be carried over to a fake caster. The positive fixtures of the other
 * imported-glyph tests are real players ({@link TestPlayers}). Same names as the Forge tests.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class GlyphCasterGameTests {
    private GlyphCasterGameTests() {}

    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_caster_loaderFakePlayerIsRefusedBeforeEffectAndPayment(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer fake = FakePlayerFactory.get(helper.getLevel(), profile("caster_loader_fake"));
        helper.assertTrue(GlyphSessions.isAutomation(fake), "NeoForge's FakePlayer is automation");
        assertRefused(helper, fake, "NeoForge FakePlayer");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_caster_arsFakePlayerIsRefusedBeforeEffectAndPayment(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer fake = com.hollingsworth.arsnouveau.api.ANFakePlayer.getPlayer(helper.getLevel());
        helper.assertTrue(GlyphSessions.isAutomation(fake), "Ars's ANFakePlayer is automation");
        assertRefused(helper, fake, "ANFakePlayer");
        helper.succeed();
    }

    /** The control: the same cast from a real player heals and pays, so the refusals above are the guard, not the fixture. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime")
    public static void ironsLoaded_caster_realPlayerStillCastsAndPays(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, "caster_real", GameType.SURVIVAL);
        helper.assertTrue(!GlyphSessions.isAutomation(player), "the test fixture is a real player");
        ImportedGlyphGameTests.fund(player);
        player.setHealth(6.0f);
        double before = ImportedGlyphGameTests.pools(player);
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("heal", 1)));
        helper.assertTrue(player.getHealth() > 6.0f, "a real player's imported Heal heals, health " + player.getHealth());
        helper.assertTrue(ImportedGlyphGameTests.pools(player) < before, "and is paid for");
        helper.succeed();
    }

    /** A session belongs to the admitted real player; a fake player with the same UUID is refused at every later stage. */
    @GameTest(template = "platform", batch = "ans_glyph_runtime", timeoutTicks = 200)
    public static void ironsLoaded_caster_sessionCannotTransferToAFakeCaster(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = ImportedGlyphGameTests.caster(helper, "caster_transfer", GameType.SURVIVAL);
        ImportedGlyphGameTests.fund(player);
        player.setHealth(6.0f);
        var heal = ImportedGlyphGameTests.glyph("heal", 1);
        ImportedGlyphGameTests.cast(player, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, EffectDelay.INSTANCE, heal));
        var sessions = GlyphSessions.forPlayer(player.getUUID());
        helper.assertTrue(!sessions.isEmpty(), "the delayed cast holds an open session");
        var session = sessions.get(0);
        helper.assertTrue(GlyphAuthorization.casterRefusal(player, session, helper.getLevel(), heal) == null, "the owner may continue it");
        ServerPlayer impostor = FakePlayerFactory.get(helper.getLevel(), player.getGameProfile());
        helper.assertTrue(impostor != player && impostor.getUUID().equals(player.getUUID()), "fixture: a fake player sharing the owner's UUID");
        helper.assertTrue(GlyphAuthorization.casterRefusal(impostor, session, helper.getLevel(), heal) != null,
            "a fake player sharing the owner's UUID must not continue the session");
        GlyphSessions.closeAllFor(player.getUUID(), "test end");
        helper.succeed();
    }

    private static void assertRefused(GameTestHelper helper, ServerPlayer fake, String kind) {
        fake.moveTo(helper.absoluteVec(ImportedGlyphGameTests.STAND));
        fake.setGameMode(GameType.SURVIVAL);
        ImportedGlyphGameTests.fund(fake);
        ImportedGlyphGameTests.clearCooldowns(fake);
        fake.setHealth(6.0f);
        double before = ImportedGlyphGameTests.pools(fake);
        ImportedGlyphGameTests.cast(fake, ImportedGlyphGameTests.spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph("heal", 1)));
        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        helper.assertTrue(fake.getHealth() == 6.0f, kind + ": the imported Heal must not run, health " + fake.getHealth());
        helper.assertTrue(ImportedGlyphGameTests.pools(fake) == before, kind + ": nothing was paid");
        helper.assertTrue(GlyphSessions.forPlayer(fake.getUUID()).isEmpty(), kind + ": no session was opened");
        helper.assertTrue(!IronsProxyCastDriver.onNativeCooldown(fake, "irons_spellbooks:heal"), kind + ": no Iron's cooldown was started");
    }

    private static GameProfile profile(String scenario) {
        return new GameProfile(UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), scenario);
    }
}
