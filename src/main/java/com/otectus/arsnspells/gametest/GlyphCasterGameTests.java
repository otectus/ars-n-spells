package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.GlyphCasters;
import com.otectus.arsnspells.glyph.GlyphExecutionPolicy;
import com.otectus.arsnspells.glyph.GlyphSessions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Audit ANS-002: imported glyphs run for real players only. Forge's {@code FakePlayer} (what other
 * mods' automation uses) and Ars's {@code ANFakePlayer} are refused at root admission, before any
 * imported effect or payment, and a session cannot be carried over to a fake caster. The positive
 * fixtures of every other imported-glyph test are real players ({@link TestPlayers}).
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class GlyphCasterGameTests {
    private static final String HEAL = "irons_spellbooks:heal";

    private GlyphCasterGameTests() {}

    @GameTest(template = "platform")
    public static void ironsLoaded_caster_loaderFakePlayerIsRefusedBeforeEffectAndPayment(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer fake = FakePlayerFactory.get(helper.getLevel(), profile("caster_forge_fake"));
        helper.assertTrue(GlyphCasters.realPlayer(fake) == null, "a Forge FakePlayer is not a real player");
        assertRefused(helper, fake, "Forge FakePlayer");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_caster_arsFakePlayerIsRefusedBeforeEffectAndPayment(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer fake = com.hollingsworth.arsnouveau.api.ANFakePlayer.getPlayer(helper.getLevel());
        helper.assertTrue(GlyphCasters.realPlayer(fake) == null, "Ars's ANFakePlayer is not a real player");
        assertRefused(helper, fake, "ANFakePlayer");
        helper.succeed();
    }

    /** The control: the same cast from a real player heals and pays, so the refusals above are the guard, not the fixture. */
    @GameTest(template = "platform")
    public static void ironsLoaded_caster_realPlayerStillCastsAndPays(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "caster_real");
        helper.assertTrue(GlyphCasters.realPlayer(player) == player, "the test fixture is a real player");
        prepare(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
        helper.assertTrue(player.getHealth() > 10.0f, "a real player's imported Heal heals, health " + player.getHealth());
        var records = GlyphSessions.records(player.getUUID());
        helper.assertTrue(!records.isEmpty() && !"nothing".equals(records.get(records.size() - 1).paid()),
            "and its execution is paid for: " + records);
        helper.succeed();
    }

    /** A session belongs to the admitted real player; a fake player with the same UUID is refused at every later stage. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void ironsLoaded_caster_sessionCannotTransferToAFakeCaster(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = TouchRegressionGameTests.aimingPlayer(helper, "caster_transfer");
        prepare(player);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, EffectDelay.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
        var sessions = GlyphSessions.forPlayer(player.getUUID());
        helper.assertTrue(!sessions.isEmpty(), "the delayed cast holds an open session");
        var session = sessions.get(0);
        helper.assertTrue(GlyphExecutionPolicy.casterRefusal(player, session, helper.getLevel()) == null, "the owner may continue it");
        ServerPlayer impostor = FakePlayerFactory.get(helper.getLevel(), player.getGameProfile());
        helper.assertTrue(impostor != player && impostor.getUUID().equals(player.getUUID()), "fixture: a fake player sharing the owner's UUID");
        helper.assertTrue(GlyphExecutionPolicy.casterRefusal(impostor, session, helper.getLevel()) != null,
            "a fake player sharing the owner's UUID must not continue the session");
        helper.succeed();
    }

    private static void assertRefused(GameTestHelper helper, ServerPlayer fake, String kind) {
        fake.moveTo(helper.absoluteVec(new Vec3(0.5, 2.0, 0.5)));
        prepare(fake);
        float arsBefore = BridgeManager.getNativeArsBridge().getMana(fake);
        float ironsBefore = IronsProxyCastDriver.ironsMana(fake);
        ItemStack book = TouchRegressionGameTests.nativeBook(new Spell(MethodSelf.INSTANCE, ImportedGlyphGameTests.glyph(HEAL, 1)));
        fake.setItemInHand(InteractionHand.MAIN_HAND, book);
        fake.gameMode.useItem(fake, helper.getLevel(), book, InteractionHand.MAIN_HAND);
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(fake.getHealth() == 10.0f, kind + ": the imported Heal must not run, health " + fake.getHealth());
        helper.assertTrue(IronsProxyCastDriver.ironsMana(fake) == ironsBefore, kind + ": no imported payment was taken");
        helper.assertTrue(BridgeManager.getNativeArsBridge().getMana(fake) == arsBefore, kind + ": the refused cast spent no Ars mana either");
        helper.assertTrue(GlyphSessions.activeFor(fake.getUUID()) == 0, kind + ": no session was opened");
        helper.assertTrue(!IronsGlyphTestSupport.onCooldown(fake, HEAL), kind + ": no Iron's cooldown was started");
    }

    private static void prepare(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10.0f);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        IronsProxyCastDriver.setIronsMana(player, 100.0f);
    }

    private static GameProfile profile(String scenario) {
        return new GameProfile(UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), scenario);
    }
}
