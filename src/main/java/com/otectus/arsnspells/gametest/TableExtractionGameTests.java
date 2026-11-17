package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectLight;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.IronsSpellbookBinder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 3.4.1 review UNBIND-01: taking one bound Ars spell back off a mixed Iron's spellbook at a real
 * Inscription Table menu, through the menu's own click handling. Only that entry and its wheel slot
 * leave the book, as a carrier that binds and casts again; the book's native spells and other Ars
 * entries stay; a stale offer cannot be taken; a damaged entry is not offered; and Iron's own spells
 * are still extracted natively.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class TableExtractionGameTests {
    private static final String NATIVE = "irons_spellbooks:firebolt";

    private TableExtractionGameTests() {}

    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_extract_oneOfTwoArsEntriesComesBackAsACarrier(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = player(helper, "extract_one");
        ItemStack book = mixedBook(helper, player);
        int heal = poolOf(book, "Extract Heal"), light = poolOf(book, "Extract Light");
        List<String> nativeBefore = Glyph341IronsSupport.nativeSpells(book);
        CompoundTag healEntry = CrossCastNbt.findEntryByProxyPoolId(book.getOrCreateTag(), heal).copy();
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
        helper.assertTrue(IronsTableDriver.click(menu, player, IronsTableDriver.proxyIndex(book, heal)), "select the Heal entry");
        ItemStack offered = IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT);
        helper.assertTrue(!offered.isEmpty() && IronsBookBindingUtil.extractSingleEntry(offered).isPresent(),
            "the table offers an ANS carrier, not a bare proxy: " + offered + " " + offered.getTag());
        helper.assertTrue(CrossCastNbt.usedProxyPoolIds(IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK).getOrCreateTag()).size() == 2,
            "the offer changes nothing until it is taken");
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        ItemStack after = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        ItemStack carrier = carrierIn(player);
        helper.assertTrue(!carrier.isEmpty(), "the carrier is in the player's inventory");
        CompoundTag returned = IronsBookBindingUtil.extractSingleEntry(carrier).orElseThrow();
        helper.assertTrue(returned.getCompound(CrossCastNbt.TAG_ARS_SPELL).equals(healEntry.getCompound(CrossCastNbt.TAG_ARS_SPELL))
                && "Extract Heal".equals(returned.getString(CrossCastNbt.TAG_CUSTOM_NAME))
                && healEntry.getString(CrossCastNbt.TAG_NATURE).equals(returned.getString(CrossCastNbt.TAG_NATURE))
                && healEntry.getString(CrossCastNbt.TAG_ICON_SYMBOL).equals(returned.getString(CrossCastNbt.TAG_ICON_SYMBOL))
                && !returned.contains(CrossCastNbt.TAG_PROXY_POOL_ID),
            "the carrier holds the whole entry, name and cosmetics, without the old proxy id: " + returned);
        helper.assertTrue(CrossCastNbt.findEntryByProxyPoolId(after.getOrCreateTag(), heal) == null
                && IronsTableDriver.proxyIndex(after, heal) < 0, "the Heal entry and its wheel slot are gone");
        helper.assertTrue(CrossCastNbt.findEntryByProxyPoolId(after.getOrCreateTag(), light) != null
                && IronsTableDriver.proxyIndex(after, light) >= 0, "the other Ars entry stays");
        helper.assertTrue(Glyph341IronsSupport.nativeSpells(after).equals(nativeBefore), "the native spells stay: " + Glyph341IronsSupport.nativeSpells(after));
        helper.assertTrue(after.getHoverName().getString().equals("Mixed Book"), "the book's name stays");
        // The returned scroll binds again, to a fresh wheel slot, and casts.
        ItemStack again = freshBook(helper);
        helper.assertTrue(IronsSpellbookBinder.bind(player, carrier, again, IronsSpellbookBinder.Caller.TABLE).wasAdded(), "the scroll binds again");
        var pools = CrossCastNbt.usedProxyPoolIds(again.getOrCreateTag());
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(10.0f);
        IronsProxyCastDriver.castViaEquippedSpellbook(player, again, pools.iterator().next());
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
        IronsTableDriver.click(menu, player, IronsTableDriver.proxyIndex(book, heal));
        helper.assertTrue(!IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT).isEmpty(), "offered");
        // The book changes under the offer without the menu noticing (another mod, say).
        IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK).getOrCreateTag().putString("ans_gametest_stale", "changed");
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        helper.assertTrue(carrierIn(player).isEmpty(), "a stale offer is not taken");
        helper.assertTrue(CrossCastNbt.findEntryByProxyPoolId(IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK).getOrCreateTag(), heal) != null,
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
        IronsTableDriver.click(menu, player, IronsTableDriver.proxyIndex(book, heal));
        helper.assertTrue(IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT).isEmpty(), "nothing is offered for an unreadable entry");
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        helper.assertTrue(ItemStack.matches(before, IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK)), "the book is untouched");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_extract_nativeSpellsAreStillExtractedNatively(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = player(helper, "extract_native");
        ItemStack book = mixedBook(helper, player);
        int ars = CrossCastNbt.usedProxyPoolIds(book.getOrCreateTag()).size();
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
        int index = IronsTableDriver.nativeSpellIndex(book);
        helper.assertTrue(index >= 0 && NATIVE.equals(IronsTableDriver.spellIdAt(book, index)), "fixture: the native Firebolt");
        IronsTableDriver.click(menu, player, index);
        ItemStack offered = IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT);
        helper.assertTrue(!offered.isEmpty() && IronsBookBindingUtil.extractSingleEntry(offered).isEmpty(), "Iron's own scroll is offered");
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        ItemStack after = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        helper.assertTrue(!Glyph341IronsSupport.nativeSpells(after).contains(NATIVE + "@3"), "the native spell left the book natively");
        helper.assertTrue(CrossCastNbt.usedProxyPoolIds(after.getOrCreateTag()).size() == ars, "every Ars entry stays");
        helper.succeed();
    }

    /** Cursor pickup, a number key and throwing each take the carrier and commit exactly once. */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_extract_everyTakeRouteCommitsOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        for (String route : new String[]{"PICKUP", "SWAP", "THROW"}) {
            ServerPlayer player = player(helper, "extract_" + route.toLowerCase(java.util.Locale.ROOT));
            ItemStack book = mixedBook(helper, player);
            int heal = poolOf(book, "Extract Heal");
            Object menu = IronsTableDriver.openTable(player);
            IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
            IronsTableDriver.click(menu, player, IronsTableDriver.proxyIndex(book, heal));
            IronsTableDriver.takeResult(menu, player, route);
            IronsTableDriver.takeResult(menu, player, route);
            ItemStack after = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
            helper.assertTrue(CrossCastNbt.findEntryByProxyPoolId(after.getOrCreateTag(), heal) == null, route + ": the entry is gone");
            helper.assertTrue(CrossCastNbt.usedProxyPoolIds(after.getOrCreateTag()).size() == 1, route + ": exactly one entry left");
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

    /** A full inventory leaves the offer in place and the book untouched; closing the menu takes nothing. */
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
        IronsTableDriver.click(menu, player, IronsTableDriver.proxyIndex(book, poolOf(book, "Extract Heal")));
        IronsTableDriver.takeResult(menu, player, "QUICK_MOVE");
        helper.assertTrue(ItemStack.matches(before, IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK)), "a full inventory removes nothing");
        helper.assertTrue(!IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT).isEmpty(), "the offer stays");
        player.getInventory().clearContent();
        // The driver's menu has no level access, so Iron's removed() hands nothing back; what matters
        // here is that closing commits nothing and never yields the result.
        IronsTableDriver.close(menu, player);
        helper.assertTrue(carrierIn(player).isEmpty() && IronsTableDriver.carried(menu).isEmpty(), "closing the menu yields no carrier");
        helper.assertTrue(ItemStack.matches(before, IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK)), "the book is unchanged");
        helper.succeed();
    }

    /** An orphan ANS proxy (its sidecar entry lost) counts as inscribed, and erasing removes it but not native spells. */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void ironsLoaded_uninscription_repairsAnOrphanProxy(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        ServerPlayer player = player(helper, "orphan_repair");
        ItemStack book = mixedBook(helper, player);
        List<String> nativeBefore = Glyph341IronsSupport.nativeSpells(book);
        CrossCastNbt.clearCrossModSpells(book);
        helper.assertTrue(IronsTableDriver.proxySlotCount(book) == 2 && !CrossCastNbt.hasCrossModSpells(book.getOrCreateTag()),
            "fixture: two wheel proxies, no sidecar");
        helper.assertTrue(com.otectus.arsnspells.rituals.InscriptionInputs.isInscribed(book), "the damaged book still reads as ANS-inscribed");
        IronsBookBindingUtil.removeAllArsEntries(book);
        helper.assertTrue(IronsTableDriver.proxySlotCount(book) == 0, "the orphan proxies are gone");
        helper.assertTrue(Glyph341IronsSupport.nativeSpells(book).equals(nativeBefore), "native spells are kept");
        helper.succeed();
    }

    // ---- Fixtures --------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = CrossCastGameTests.scenarioPlayer(helper, name);
        player.getInventory().clearContent();
        return player;
    }

    /** A named spellbook with a native Firebolt III and two bound Ars spells, bound through the binder. */
    private static ItemStack mixedBook(GameTestHelper helper, ServerPlayer player) {
        ItemStack book = freshBook(helper);
        Glyph341IronsSupport.addNativeSpells(book, new String[]{NATIVE}, new int[]{3});
        book.setHoverName(net.minecraft.network.chat.Component.literal("Mixed Book"));
        bind(helper, player, book, new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE), "Extract Heal", "water", "heart");
        bind(helper, player, book, new Spell(MethodSelf.INSTANCE, EffectLight.INSTANCE), "Extract Light", "fire", "star");
        return book;
    }

    private static void bind(GameTestHelper helper, ServerPlayer player, ItemStack book, Spell spell, String name, String nature, String icon) {
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(spell, name, nature, icon);
        helper.assertTrue(!carrier.isEmpty(), "export must yield a carrier");
        helper.assertTrue(IronsSpellbookBinder.bind(player, carrier, book, IronsSpellbookBinder.Caller.TABLE).wasAdded(), "bind " + name);
    }

    private static ItemStack freshBook(GameTestHelper helper) {
        Item item = CrossCastGameTests.findIronsSpellBook();
        helper.assertTrue(item != null, "an Iron's spellbook is registered");
        ItemStack book = new ItemStack(item);
        helper.assertTrue(IronsTableDriver.initializeNativeContainer(book), "the book gets its native container");
        return book;
    }

    private static int poolOf(ItemStack book, String name) {
        var list = book.getOrCreateTag().getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (name.equals(entry.getString(CrossCastNbt.TAG_CUSTOM_NAME))) return entry.getInt(CrossCastNbt.TAG_PROXY_POOL_ID);
        }
        throw new IllegalStateException("no entry " + name);
    }

    private static ItemStack carrierIn(ServerPlayer player) {
        List<ItemStack> found = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) if (!stack.isEmpty() && IronsBookBindingUtil.extractSingleEntry(stack).isPresent()) found.add(stack);
        return found.isEmpty() ? ItemStack.EMPTY : found.get(0);
    }
}
