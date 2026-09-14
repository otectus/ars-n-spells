package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.network.CarrierFingerprint;
import com.otectus.arsnspells.network.CrossCastRequestPacket;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class NetworkAdmissionGameTests {
    @GameTest(template = "platform")
    public static void staleCarrierFingerprintDetectsItemAndPayloadChanges(GameTestHelper helper) {
        ItemStack book = carrier();
        String original = CarrierFingerprint.of(book);
        if (original.isEmpty() || !original.equals(CarrierFingerprint.of(book.copy())))
            helper.fail("Equivalent copied carriers need a stable nonempty fingerprint");
        ItemStack changed = book.copy();
        changed.getOrCreateTag().putInt(CrossCastNbt.TAG_SPELL_INDEX, 1);
        if (original.equals(CarrierFingerprint.of(changed))) helper.fail("A changed selection is a new revision");
        ItemStack otherItem = new ItemStack(Items.STICK);
        otherItem.setTag(book.getTag().copy());
        if (original.equals(CarrierFingerprint.of(otherItem))) helper.fail("An item swap must invalidate the request");
        CompoundTag oversized = new CompoundTag();
        oversized.putString("saved_payload", "x".repeat(65_537));
        changed.setTag(oversized);
        if (!CarrierFingerprint.of(changed).isEmpty() || !changed.getTag().equals(oversized))
            helper.fail("Oversized saved data must be refused without mutation");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void spectatorOffhandAndSwappedCarriersCannotCycle(GameTestHelper helper) {
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "network_state");
        ItemStack book = carrier();
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.setGameMode(GameType.SPECTATOR);
        if (cycle(player, book, InteractionHand.MAIN_HAND)) helper.fail("Spectators cannot mutate an inscription");
        player.setGameMode(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.OFF_HAND, book.copy());
        if (cycle(player, player.getOffhandItem(), InteractionHand.OFF_HAND)) helper.fail("Offhand is not a supported request");
        ItemStack stale = book.copy();
        if (cycle(player, stale, InteractionHand.MAIN_HAND)) helper.fail("Only the current held instance can mutate");
        if (book.getOrCreateTag().getInt(CrossCastNbt.TAG_SPELL_INDEX) != 0)
            helper.fail("Denied requests changed the selected spell");
        helper.succeed();
    }

    private static boolean cycle(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        return CrossCastingHandler.serverHandleCast(player, stack, hand,
            CrossCastRequestPacket.Action.CYCLE, UUID.randomUUID());
    }

    private static ItemStack carrier() {
        ItemStack book = new ItemStack(Items.BOOK);
        CompoundTag a = new CompoundTag(), b = new CompoundTag();
        a.putString("recipe", "a");
        b.putString("recipe", "b");
        IronsBookBindingUtil.appendArsSpellToBook(book, a);
        IronsBookBindingUtil.appendArsSpellToBook(book, b);
        return book;
    }
}
