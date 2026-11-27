package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectLight;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.IronsSpellbookBinder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 3.4.1 review UNBIND-01 on NeoForge: taking one bound Ars spell back off a mixed Iron's spellbook at a
 * real Inscription Table menu, through the menu's own click handling. Only that entry and its wheel
 * slot leave the book, as a carrier that binds and casts again; the book's native spell and other Ars
 * entry stay; a stale offer cannot be taken; a damaged entry is not offered; every take route commits
 * once; a full inventory takes nothing.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class TableExtractionGameTests {
    private TableExtractionGameTests() {}

    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_extract_oneOfTwoArsEntriesComesBackAsACarrier(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = player(helper, "extract_one");
        ItemStack book = mixedBook(helper, player);
        int heal = poolOf(book, "Extract Heal"), light = poolOf(book, "Extract Light");
        List<String> nativeBefore = Glyph341IronsSupport.nativeSpells(book);
        CrossModSpell healEntry = CrossModSpellComponents.findEntryByProxyPoolId(book, heal).orElseThrow();
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
        helper.assertTrue(IronsTableDriver.click(menu, player, IronsTableDriver.proxySlotIndex(book, heal)), "select the Heal entry");
        ItemStack offered = IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT);
        helper.assertTrue(!offered.isEmpty() && IronsBookBindingUtil.extractSingleEntry(offered).isPresent(),
            "the table offers an ANS carrier, not a bare proxy: " + offered);
        helper.assertTrue(CrossModSpellComponents.usedProxyPoolIds(CrossModSpellComponents.get(IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK))).size() == 2,
            "the offer changes nothing until it is taken");
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        ItemStack after = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        ItemStack carrier = carrierIn(player);
        helper.assertTrue(!carrier.isEmpty(), "the carrier is in the player's inventory");
        CrossModSpell returned = IronsBookBindingUtil.extractSingleEntry(carrier).orElseThrow();
        helper.assertTrue(returned.arsSpellTag().equals(healEntry.arsSpellTag()) && returned.customName().equals(healEntry.customName())
                && returned.nature().equals(healEntry.nature()) && returned.iconSymbol().equals(healEntry.iconSymbol())
                && returned.proxyPoolId() == CrossModSpellComponents.NO_PROXY_POOL_ID,
            "the carrier holds the whole entry, name and cosmetics, without the old proxy id: " + returned);
        helper.assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(after, heal).isEmpty()
            && IronsTableDriver.proxySlotIndex(after, heal) < 0, "the Heal entry and its wheel slot are gone");
        helper.assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(after, light).isPresent()
            && IronsTableDriver.proxySlotIndex(after, light) >= 0, "the other Ars entry stays");
        helper.assertTrue(Glyph341IronsSupport.nativeSpells(after).equals(nativeBefore), "the native spell stays: " + Glyph341IronsSupport.nativeSpells(after));
        ItemStack again = IronsTableDriver.freshBook();
        helper.assertTrue(IronsSpellbookBinder.bind(player, carrier, again, IronsSpellbookBinder.Caller.TABLE).wasAdded(), "the scroll binds again");
        int pool = CrossModSpellComponents.usedProxyPoolIds(CrossModSpellComponents.get(again)).iterator().next();
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(10.0f);
        IronsTableDriver.castBoundProxyFromMainHand(player, again, pool);
        helper.assertTrue(player.getHealth() > 10.0f, "the re-bound Heal casts");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_extract_aStaleOfferCannotBeTaken(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = player(helper, "extract_stale");
        ItemStack book = mixedBook(helper, player);
        int heal = poolOf(book, "Extract Heal");
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
        IronsTableDriver.click(menu, player, IronsTableDriver.proxySlotIndex(book, heal));
        helper.assertTrue(!IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT).isEmpty(), "offered");
        IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component.literal("changed underneath"));
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        helper.assertTrue(carrierIn(player).isEmpty(), "a stale offer is not taken");
        helper.assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK), heal).isPresent(),
            "and nothing was removed");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_extract_anUnreadableEntryIsNotOffered(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = player(helper, "extract_broken");
        ItemStack book = mixedBook(helper, player);
        int heal = poolOf(book, "Extract Heal");
        IronsTableDriver.corruptPayload(book, heal);
        ItemStack before = book.copy();
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
        IronsTableDriver.click(menu, player, IronsTableDriver.proxySlotIndex(book, heal));
        helper.assertTrue(IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT).isEmpty(), "nothing is offered for an unreadable entry");
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        helper.assertTrue(ItemStack.matches(before, IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK)), "the book is untouched");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_extract_everyTakeRouteCommitsOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        for (String route : new String[]{"PICKUP", "SWAP", "THROW"}) {
            ServerPlayer player = player(helper, "extract_" + route.toLowerCase(java.util.Locale.ROOT));
            ItemStack book = mixedBook(helper, player);
            int heal = poolOf(book, "Extract Heal");
            Object menu = IronsTableDriver.openTable(player);
            IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
            IronsTableDriver.click(menu, player, IronsTableDriver.proxySlotIndex(book, heal));
            IronsTableDriver.takeResult(menu, player, route);
            IronsTableDriver.takeResult(menu, player, route);
            ItemStack after = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
            helper.assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(after, heal).isEmpty(), route + ": the entry is gone");
            helper.assertTrue(CrossModSpellComponents.usedProxyPoolIds(CrossModSpellComponents.get(after)).size() == 1, route + ": exactly one entry left");
            int carriers = (IronsBookBindingUtil.extractSingleEntry(IronsTableDriver.carried(menu)).isPresent() ? 1 : 0)
                + (carrierIn(player).isEmpty() ? 0 : 1);
            if (route.equals("THROW")) {
                var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    player.getBoundingBox().inflate(8), e -> IronsBookBindingUtil.extractSingleEntry(e.getItem()).isPresent());
                carriers += dropped.size();
                dropped.forEach(net.minecraft.world.entity.Entity::discard);
            }
            helper.assertTrue(carriers == 1, route + ": exactly one carrier came out of a repeated take, got " + carriers);
        }
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_extract_fullInventoryAndCloseTakeNothing(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = player(helper, "extract_full");
        ItemStack book = mixedBook(helper, player);
        ItemStack before = book.copy();
        for (int i = 0; i < player.getInventory().items.size(); i++)
            player.getInventory().items.set(i, new ItemStack(net.minecraft.world.item.Items.STONE, 64));
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
        IronsTableDriver.click(menu, player, IronsTableDriver.proxySlotIndex(book, poolOf(book, "Extract Heal")));
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        helper.assertTrue(ItemStack.matches(before, IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK)), "a full inventory removes nothing");
        helper.assertTrue(!IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT).isEmpty(), "the offer stays");
        player.getInventory().clearContent();
        IronsTableDriver.close(menu, player);
        helper.assertTrue(carrierIn(player).isEmpty() && IronsTableDriver.carried(menu).isEmpty(), "closing the menu yields no carrier");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_uninscription_repairsAnOrphanProxy(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = player(helper, "orphan_repair");
        ItemStack book = mixedBook(helper, player);
        List<String> nativeBefore = Glyph341IronsSupport.nativeSpells(book);
        CrossModSpellComponents.clearPayloadOnly(book);
        helper.assertTrue(IronsTableDriver.proxySlotCount(book) == 2 && !CrossModSpellComponents.has(book), "fixture: two wheel proxies, no entries");
        helper.assertTrue(com.otectus.arsnspells.rituals.InscriptionInputs.isInscribed(book), "the damaged book still reads as ANS-inscribed");
        IronsBookBindingUtil.removeAllArsEntries(book);
        helper.assertTrue(IronsTableDriver.proxySlotCount(book) == 0, "the orphan proxies are gone");
        helper.assertTrue(Glyph341IronsSupport.nativeSpells(book).equals(nativeBefore), "native spells are kept");
        helper.succeed();
    }

    // ---- Fixtures --------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = IronsInscriptionTableGameTests.scenarioPlayer(helper, name);
        player.getInventory().clearContent();
        return player;
    }

    /** A spellbook with a native spell and two bound Ars spells, bound through the binder. */
    private static ItemStack mixedBook(GameTestHelper helper, ServerPlayer player) {
        ItemStack book = IronsTableDriver.freshBook();
        helper.assertTrue(!book.isEmpty() && IronsTableDriver.addNativeSpellToBook(book), "fixture: a book with a native spell");
        bind(helper, player, book, new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE), "Extract Heal", "arcane", "spark");
        bind(helper, player, book, new Spell(MethodSelf.INSTANCE, EffectLight.INSTANCE), "Extract Light", "arcane", "spark");
        return book;
    }

    private static void bind(GameTestHelper helper, ServerPlayer player, ItemStack book, Spell spell, String name, String nature, String icon) {
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(spell, name, nature, icon);
        helper.assertTrue(!carrier.isEmpty(), "export must yield a carrier");
        helper.assertTrue(IronsSpellbookBinder.bind(player, carrier, book, IronsSpellbookBinder.Caller.TABLE).wasAdded(), "bind " + name);
    }

    private static int poolOf(ItemStack book, String name) {
        for (CrossModSpell entry : CrossModSpellComponents.get(book).spells())
            if (entry.customName().filter(name::equals).isPresent()) return entry.proxyPoolId();
        throw new IllegalStateException("no entry " + name);
    }

    private static ItemStack carrierIn(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items)
            if (!stack.isEmpty() && IronsBookBindingUtil.extractSingleEntry(stack).isPresent()) return stack;
        return ItemStack.EMPTY;
    }
}
