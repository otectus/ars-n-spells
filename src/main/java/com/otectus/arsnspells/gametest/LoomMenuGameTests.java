package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.network.LoomInventoryRevision;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Audit ANS-012: the Spell Loom menu's slot layout never depends on whether the client happened
 * to have the block entity when the menu opened.
 *
 * <p>The client builds its menu from the opening buffer ({@code SpellLoomMenu(int, Inventory,
 * FriendlyByteBuf)}), resolving the block entity in its own level. Here that constructor is run
 * against a position holding no Loom, which is the client's view before the block entity arrives,
 * and the server's real menu-content and incremental slot updates are then applied to it the way
 * {@code ClientboundContainerSetContentPacket} and {@code ClientboundContainerSetSlotPacket} do.
 * Delayed-chunk network timing itself is not simulated; the layout and the packet application are.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class LoomMenuGameTests {
    private static final BlockPos LOOM = new BlockPos(2, 2, 2);
    private static final BlockPos EMPTY = new BlockPos(4, 2, 4);

    private LoomMenuGameTests() {}

    @GameTest(template = "platform")
    public static void loomMenu_withoutItsBlockEntityKeepsTheServerSlotLayout(GameTestHelper helper) {
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "loom_layout");
        Vec3 near = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        player.moveTo(near.x, near.y, near.z);
        helper.setBlock(LOOM, ModBlocksRegistry.SPELL_LOOM.get().defaultBlockState());
        helper.setBlock(EMPTY, Blocks.AIR);
        SpellLoomBlockEntity loom = (SpellLoomBlockEntity) helper.getBlockEntity(LOOM);
        SpellLoomMenu server = new SpellLoomMenu(3, player.getInventory(), loom);

        FriendlyByteBuf opening = new FriendlyByteBuf(Unpooled.buffer());
        opening.writeBlockPos(helper.absolutePos(EMPTY));
        opening.writeUUID(server.sessionId());
        SpellLoomMenu client = new SpellLoomMenu(3, player.getInventory(), opening);

        helper.assertTrue(server.slots.size() == SpellLoomBlockEntity.SLOT_COUNT + 36, "the server menu has 39 slots, got " + server.slots.size());
        helper.assertTrue(client.slots.size() == server.slots.size(),
            "the client menu must have the server's " + server.slots.size() + " slots, got " + client.slots.size());
        for (int i = 0; i < server.slots.size(); i++) {
            var a = server.getSlot(i);
            var b = client.getSlot(i);
            helper.assertTrue((a instanceof SlotItemHandler) == (b instanceof SlotItemHandler) && a.getContainerSlot() == b.getContainerSlot()
                && a.x == b.x && a.y == b.y, "slot " + i + " must be the same slot on both sides");
        }

        loom.getItems().setStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL, new ItemStack(Items.PAPER));
        player.getInventory().setItem(0, new ItemStack(Items.STICK, 3));
        // Full content, as the vanilla content packet applies it: must not throw or shift entries.
        client.initializeContents(server.incrementStateId(), server.getItems(), ItemStack.EMPTY);
        helper.assertTrue(client.getSlot(SpellLoomBlockEntity.SLOT_SCROLL).getItem().is(Items.PAPER),
            "the Loom's scroll slot arrives in the client's scroll slot");
        int hotbar0 = SpellLoomBlockEntity.SLOT_COUNT + 27;
        helper.assertTrue(client.getSlot(hotbar0).getItem().is(Items.STICK) && client.getSlot(hotbar0).getItem().getCount() == 3,
            "the player's hotbar is not shifted into the Loom slots");
        // An incremental update to one Loom slot.
        client.setItem(SpellLoomBlockEntity.SLOT_SOURCE, server.incrementStateId(), new ItemStack(Items.BOOK));
        helper.assertTrue(client.getSlot(SpellLoomBlockEntity.SLOT_SOURCE).getItem().is(Items.BOOK), "an incremental slot update lands");
        // The client's copy is its own: writing it never touches the server's Loom.
        helper.assertTrue(loom.getItems().getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE).isEmpty(),
            "the client's synchronized copy is not the block entity's inventory");
        helper.assertTrue(!LoomInventoryRevision.of(client).isEmpty(), "the client can fingerprint its synchronized slots for a request");
        helper.assertTrue(LoomInventoryRevision.of(client).length() == 64, "a full revision");

        // A removed Loom closes the authoritative menu.
        helper.assertTrue(server.stillValid(player), "the server menu is valid beside its Loom");
        helper.setBlock(LOOM, Blocks.AIR);
        helper.assertTrue(!server.stillValid(player), "a removed Loom invalidates the server menu");
        helper.succeed();
    }
}
