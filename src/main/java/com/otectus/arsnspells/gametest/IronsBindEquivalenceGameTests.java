package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.CrossModSpellList;
import com.otectus.arsnspells.spell.IronsSpellbookBinder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/**
 * The three bind routes -- ritual, command, Inscription Table -- must be the same bind.
 *
 * <p>Before 3.3.3 each route carried its own copy of the checks and its own append call, and
 * they drifted. The routes now share {@link IronsSpellbookBinder}, and the property that
 * proves it is that the same carrier and the same fresh book come out component-identical
 * whichever route ran -- so a spell bound at the table casts, shows and unbinds exactly like
 * one bound at the brazier.
 *
 * <p>The second scenario closes the loop the component comparison cannot: it casts what the
 * table bound, through Iron's own {@code attemptInitiateCast}/{@code castSpell} pair, and
 * asserts the observable effect (Self+Heal on a hurt player raises health).
 *
 * <p>Iron's-only, gated through {@link OptionalModGate}.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class IronsBindEquivalenceGameTests {

    private IronsBindEquivalenceGameTests() {
    }

    private static ItemStack freshBook(GameTestHelper helper) {
        ItemStack book = IronsTableDriver.freshBook();
        if (book.isEmpty()) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        return book;
    }

    private static ItemStack healCarrier(GameTestHelper helper) {
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
            new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE), "Route Heal", "arcane", "spark");
        if (carrier.isEmpty()) {
            helper.fail("export must yield a carrier when Iron's is loaded");
        }
        return carrier;
    }

    private static CrossModSpellList sidecar(ItemStack book) {
        return CrossModSpellComponents.get(book);
    }

    /** Ritual, command and table produce component-identical books with one entry each. */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void bindRoutes_produceIdenticalBookComponents(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = IronsInscriptionTableGameTests.scenarioPlayer(helper, "bind_routes");
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

        // Whole-stack equality covers Iron's native spell_container component as well as the
        // ANS sidecar, so a route that wrote a different wheel slot cannot slip through.
        if (!ItemStack.isSameItemSameComponents(ritualBook, commandBook)
            || !ItemStack.isSameItemSameComponents(ritualBook, tableBound)) {
            helper.fail("the three routes wrote different components: ritual="
                + ritualBook.getComponentsPatch() + " command=" + commandBook.getComponentsPatch()
                + " table=" + tableBound.getComponentsPatch());
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
        ServerPlayer player = IronsInscriptionTableGameTests.scenarioPlayer(helper, "bind_tbl_cast");
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, freshBook(helper));
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_SCROLL, healCarrier(helper));
        if (!IronsTableDriver.click(menu, player, -1)) {
            helper.fail("the table route must handle the inscribe click");
        }
        ItemStack bound = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        Set<Integer> pool = CrossModSpellComponents.usedProxyPoolIds(sidecar(bound));
        if (pool.size() != 1) {
            helper.fail("the table bind must allocate exactly one pool id, got " + pool);
        }
        int poolId = pool.iterator().next();
        if (CrossModSpellComponents.findEntryByProxyPoolId(bound, poolId).isEmpty()) {
            helper.fail("the allocated pool id " + poolId + " has no sidecar entry to resolve");
        }

        player.setGameMode(GameType.CREATIVE);
        player.setHealth(10.0f);
        if (!IronsTableDriver.castBoundProxyFromMainHand(player, bound, poolId)) {
            helper.fail("Iron's refused to initiate the proxy cast for pool id " + poolId
                + "; a spell bound at the table cannot be cast at all");
        }
        if (player.getHealth() <= 10.0f) {
            helper.fail("a spell bound at the Inscription Table must cast like one bound by the "
                + "ritual; health unchanged means the table bind produced a wheel entry that "
                + "resolves nothing");
        }
        helper.succeed();
    }
}
