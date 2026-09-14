package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsSpellbookBinder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The three bind routes -- ritual, command, Inscription Table -- must be the same bind.
 *
 * <p>Before 3.3.3 each route carried its own copy of the checks and its own append call, and
 * they drifted. The routes now share {@link IronsSpellbookBinder}, and the property that
 * proves it is that the same carrier and the same fresh book come out with byte-identical
 * Iron's containers and ANS sidecars whichever route ran -- so a spell bound at the table
 * casts, shows and unbinds exactly like one bound at the brazier.
 *
 * <p>The second scenario closes the loop the NBT comparison cannot: it casts what the table
 * bound, and asserts the same observable effect {@code CrossCastGameTests} asserts for a
 * ritual-shaped bind (Self+Heal on a hurt player raises health).
 *
 * <p>Iron's-only, gated through {@link OptionalModGate}.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class IronsBindEquivalenceGameTests {

    private IronsBindEquivalenceGameTests() {
    }

    private static ItemStack freshBook(GameTestHelper helper) {
        Item item = CrossCastGameTests.findIronsSpellBook();
        if (item == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        ItemStack book = new ItemStack(item);
        if (!IronsTableDriver.initializeNativeContainer(book)) {
            helper.fail("could not give a fresh Iron's spellbook its native container");
        }
        return book;
    }

    private static ItemStack healCarrier(GameTestHelper helper) {
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
            new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE), "Route Heal", "water", "heart");
        if (carrier.isEmpty()) {
            helper.fail("export must yield a carrier when Iron's is loaded");
        }
        return carrier;
    }

    private static ListTag sidecar(ItemStack book) {
        return book.getOrCreateTag().getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
    }

    /** Ritual, command and table produce identical native containers and identical sidecars. */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void bindRoutes_produceIdenticalBookNbt(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "bind_routes");
        ItemStack carrier = healCarrier(helper);

        ItemStack ritualBook = freshBook(helper);
        IronsSpellbookBinder.BindResult ritual = IronsSpellbookBinder.bind(
            player, carrier.copy(), ritualBook, IronsSpellbookBinder.Caller.RITUAL);
        ItemStack commandBook = freshBook(helper);
        IronsSpellbookBinder.BindResult command = IronsSpellbookBinder.bind(
            player, carrier.copy(), commandBook, IronsSpellbookBinder.Caller.COMMAND);
        if (!ritual.wasAdded() || !command.wasAdded()) {
            helper.fail("both direct routes must ADD; got ritual=" + ritual + " command=" + command);
        }

        ItemStack tableBook = freshBook(helper);
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, tableBook);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_SCROLL, carrier.copy());
        if (!IronsTableDriver.click(menu, player, -1)) {
            helper.fail("the table route must handle the inscribe click");
        }
        ItemStack tableBound = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);

        String key = "irons_spellbooks:spell_container";
        if (!ritualBook.getOrCreateTag().getCompound(key)
                .equals(commandBook.getOrCreateTag().getCompound(key))
            || !ritualBook.getOrCreateTag().getCompound(key)
                .equals(tableBound.getOrCreateTag().getCompound(key))) {
            helper.fail("the three routes wrote different native containers: ritual="
                + ritualBook.getOrCreateTag().getCompound(key) + " command="
                + commandBook.getOrCreateTag().getCompound(key) + " table="
                + tableBound.getOrCreateTag().getCompound(key));
        }
        if (!sidecar(ritualBook).equals(sidecar(commandBook))
            || !sidecar(ritualBook).equals(sidecar(tableBound))) {
            helper.fail("the three routes wrote different ANS sidecars: ritual="
                + sidecar(ritualBook) + " command=" + sidecar(commandBook)
                + " table=" + sidecar(tableBound));
        }
        if (sidecar(tableBound).size() != 1) {
            helper.fail("each route must write exactly one entry, found "
                + sidecar(tableBound).size());
        }
        helper.succeed();
    }

    /** What the table bound actually casts: a hurt player who casts the bound heal gets better. */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void tableBoundSpell_castsWithTheSameObservableEffect(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, "bind_table_cast");
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, freshBook(helper));
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_SCROLL, healCarrier(helper));
        if (!IronsTableDriver.click(menu, player, -1)) {
            helper.fail("the table route must handle the inscribe click");
        }
        ItemStack bound = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        var pool = CrossCastNbt.usedProxyPoolIds(bound.getOrCreateTag());
        if (pool.size() != 1) {
            helper.fail("the table bind must allocate exactly one pool id, got " + pool);
        }

        player.setGameMode(GameType.CREATIVE);
        player.setHealth(10.0f);
        IronsProxyCastDriver.castViaEquippedSpellbook(player, bound, pool.iterator().next());
        if (player.getHealth() <= 10.0f) {
            helper.fail("a spell bound at the Inscription Table must cast like one bound by the "
                + "ritual; health unchanged means the table bind produced a wheel entry that "
                + "resolves nothing");
        }
        helper.succeed();
    }
}
