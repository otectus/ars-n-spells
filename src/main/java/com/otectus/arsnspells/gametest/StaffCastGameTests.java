package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Casting a bound Ars spell by right-clicking an Iron's casting implement (a staff), next to the
 * hotkey path that already worked.
 *
 * <p>The 3.3.5 report (Minecraft 1.21.1, Iron's 3.16.3): an Ars spell bound into an equipped Iron's
 * spellbook casts with the hotkey but a staff right-click fails with "Cross-cast failed: the book
 * carrying this spell could not be found", because Iron's hands the staff itself to
 * {@code attemptInitiateCast} as the casting item. Every scenario drives Iron's own right-click
 * entry point and its cast ticker; the observable is a Self+Heal raising a hurt caster's health,
 * so a cast that resolved the wrong book or no book is a health that did not move.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class StaffCastGameTests {

    @GameTest(template = "platform")
    public static void ironsLoaded_staffRightClick_castsBoundArsSpellFromEquippedBook(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = bound(helper, heal(), "Staff Heal");
        ServerPlayer player = player(helper, "staff_equipped");
        int index = slotOf(helper, book);
        helper.assertTrue(IronsProxyCastDriver.castViaStaffRightClick(player, book, index, IronsProxyCastDriver.staff()),
            "Iron's must start the staff cast of the selected wheel slot");
        helper.assertTrue(player.getHealth() > 10.0f, "a staff right-click must cast the Ars spell bound into the "
            + "equipped book; health unchanged means the proxy could not find the book (the staff is the casting "
            + "item, not the carrier)");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_staffRightClick_survivalCastPaysFromTheSharedPools(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = bound(helper, heal(), "Staff Heal Paid");
        ServerPlayer player = player(helper, "staff_survival");
        player.setGameMode(GameType.SURVIVAL);
        fundBothPools(player);
        double before = pools(player);
        int index = slotOf(helper, book);
        helper.assertTrue(IronsProxyCastDriver.castViaStaffRightClick(player, book, index, IronsProxyCastDriver.staff()),
            "Iron's must start the survival staff cast");
        helper.assertTrue(player.getHealth() > 10.0f, "a funded survival staff cast must resolve the bound Ars spell");
        helper.assertTrue(pools(player) < before, "the delegated Ars cast must still be paid from the mana pools");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_hotkeyCast_stillResolvesWhileAStaffIsHeld(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = bound(helper, heal(), "Hotkey Heal");
        ServerPlayer player = player(helper, "staff_hotkey");
        player.setItemInHand(InteractionHand.MAIN_HAND, IronsProxyCastDriver.staff());
        int index = slotOf(helper, book);
        helper.assertTrue(IronsProxyCastDriver.hotkeyCast(player, book, index), "Iron's must start the hotkey cast");
        helper.assertTrue(player.getHealth() > 10.0f, "the hotkey path must keep resolving the equipped book with a staff held");
        helper.succeed();
    }

    /** Two bound books sharing pool 1: the staff casts the equipped one and never the other. */
    @GameTest(template = "platform")
    public static void ironsLoaded_staffRightClick_withTwoBoundBooks_castsOnlyTheEquippedOne(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack healBook = bound(helper, heal(), "Equipped Heal");
        ItemStack inertBook = bound(helper, inert(), "Offhand Inert");
        ServerPlayer player = player(helper, "staff_two_books_a");
        player.setItemInHand(InteractionHand.OFF_HAND, inertBook);
        helper.assertTrue(IronsProxyCastDriver.castViaStaffRightClick(player, healBook, slotOf(helper, healBook), IronsProxyCastDriver.staff()),
            "Iron's must start the staff cast with the heal book equipped");
        helper.assertTrue(player.getHealth() > 10.0f, "with two bound books sharing pool 1, the staff must cast the equipped book's spell");

        ServerPlayer other = player(helper, "staff_two_books_b");
        other.setItemInHand(InteractionHand.OFF_HAND, healBook);
        helper.assertTrue(IronsProxyCastDriver.castViaStaffRightClick(other, inertBook, slotOf(helper, inertBook), IronsProxyCastDriver.staff()),
            "Iron's must start the staff cast with the inert book equipped");
        helper.assertTrue(other.getHealth() == 10.0f, "the staff must cast the equipped inert book, never the offhand book that shares the pool id");
        helper.succeed();
    }

    /** An equipped book whose sidecar entry is gone keeps its wheel slot: the cast is refused, not redirected. */
    @GameTest(template = "platform")
    public static void ironsLoaded_staffRightClick_staleEquippedBinding_isRefusedNotRedirected(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack stale = bound(helper, heal(), "Stale");
        // Removing the last entry through the public path also removes the wheel slot; the
        // payload-only clear leaves the native proxy slot behind, which is the stale state.
        CrossModSpellComponents.clearPayloadOnly(stale);
        helper.assertTrue(!CrossModSpellComponents.has(stale), "the sidecar payload must be gone");
        int index = slotOf(helper, stale);
        ItemStack other = bound(helper, heal(), "Other Heal");
        ServerPlayer player = player(helper, "staff_stale");
        player.setItemInHand(InteractionHand.OFF_HAND, other);
        IronsProxyCastDriver.castViaStaffRightClick(player, stale, index, IronsProxyCastDriver.staff());
        helper.assertTrue(player.getHealth() == 10.0f, "a stale equipped binding must be refused, not redirected to another carried book");
        helper.succeed();
    }

    /** A bound book that is only held is not on Iron's wheel, so the staff starts nothing. */
    @GameTest(template = "platform")
    public static void ironsLoaded_staffRightClick_heldButUnequippedBook_startsNoCast(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = bound(helper, heal(), "Held Only");
        int index = slotOf(helper, book);
        ServerPlayer player = player(helper, "staff_unequipped");
        player.setItemInHand(InteractionHand.OFF_HAND, book);
        boolean started = IronsProxyCastDriver.castViaStaffRightClick(player, ItemStack.EMPTY, index, IronsProxyCastDriver.staff());
        helper.assertTrue(!started, "Iron's must not start a cast from a book that is only held");
        helper.assertTrue(player.getHealth() == 10.0f, "no cast may resolve from a book that is only held");
        helper.succeed();
    }

    /** A native Iron's spell cast from the staff is untouched: effect, payment and cooldown as before. */
    @GameTest(template = "platform")
    public static void ironsLoaded_staffRightClick_nativeIronsSpell_isUnaffected(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ItemStack book = IronsProxyCastDriver.nativeBook("irons_spellbooks:heal");
        ServerPlayer player = player(helper, "staff_native");
        player.setGameMode(GameType.SURVIVAL);
        fundBothPools(player);
        double before = pools(player);
        helper.assertTrue(IronsProxyCastDriver.castViaStaffRightClick(player, book, 0, IronsProxyCastDriver.staff()),
            "Iron's must start the native staff cast");
        helper.assertTrue(player.getHealth() > 10.0f, "a native Iron's heal cast from the staff must still heal");
        helper.assertTrue(pools(player) < before, "a native Iron's cast from the staff must still be paid");
        helper.assertTrue(IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:heal"),
            "a native Iron's cast from the staff must still start its cooldown");
        helper.succeed();
    }

    private static Spell heal() {
        return new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
    }

    /** Self+Break resolves and does nothing to the caster: a cast of it is a health that stays put. */
    private static Spell inert() {
        return new Spell(MethodSelf.INSTANCE, EffectBreak.INSTANCE);
    }

    private static ItemStack bound(GameTestHelper helper, Spell spell, String name) {
        ItemStack book = IronsTableDriver.freshBook();
        helper.assertTrue(!book.isEmpty(), "no Iron's spell book is registered despite Iron's being loaded");
        IronsBookBindingUtil.AppendResult result = IronsBookBindingUtil.appendArsSpellToBook(
            book, CrossCastingHandler.encodeArsSpell(spell), name, "water", "heart", -1);
        helper.assertTrue(result.wasAdded(), "binding " + name + " must ADD, got " + result);
        helper.assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(book, 1).isPresent(), name + " must land in proxy pool 1");
        return book;
    }

    private static int slotOf(GameTestHelper helper, ItemStack book) {
        int index = IronsProxyCastDriver.proxySlotIndex(book, 1);
        helper.assertTrue(index >= 0, "the bound proxy must occupy a wheel slot in the book's Iron's container");
        return index;
    }

    /** A hurt, empty-handed caster private to {@code scenario}, with every cast-gating property reset. */
    private static ServerPlayer player(GameTestHelper helper, String scenario) {
        String name = scenario.length() > 16 ? scenario.substring(0, 16) : scenario;
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), name));
        player.moveTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setHealth(10.0f);
        player.setGameMode(GameType.CREATIVE);
        IronsProxyCastDriver.setIronsMana(player, 10000.0f);
        IronsProxyCastDriver.equipSpellbook(player, ItemStack.EMPTY);
        IronsProxyCastDriver.resetCastingState(player);
        return player;
    }

    private static void fundBothPools(ServerPlayer player) {
        com.otectus.arsnspells.bridge.NativeManaAccess.with(player, com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA, () -> {
            var mana = com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player);
            mana.setMaxMana(10000);
            mana.setMana(10000);
            return null;
        });
        IronsProxyCastDriver.setIronsMana(player, 10000.0f);
    }

    /** Both native pools together, so the assertion holds in every mana mode. */
    private static double pools(ServerPlayer player) {
        double ars = com.otectus.arsnspells.bridge.NativeManaAccess.with(player, com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA,
            () -> com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player).getCurrentMana());
        return ars + IronsProxyCastDriver.ironsMana(player);
    }
}
