package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.CrossModSpellList;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ModDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * The Inscription Table bind route (3.3.3), driven through a real
 * {@code InscriptionTableMenu} and the real {@code clickMenuButton} a player's click sends.
 *
 * <p>Until 3.3.3 an ANS carrier was refused at the native table and the player was told to
 * use the ritual or the command. The table now binds it itself, which puts three things at
 * risk at once: Iron's own inscription (which must still work untouched), the player's items
 * (a refused click must consume nothing), and the book's existing contents. Every case below
 * asserts observable state -- the book's sidecar component and native container, and the
 * scroll slot's count -- rather than a return code alone.
 *
 * <p>The menu router is exercised through {@link IronsTableDriver#click}; the handler is
 * called directly only where the finer status ({@code DUPLICATE} vs {@code BOOK_FULL} vs
 * {@code DISABLED}) is the property under test.
 *
 * <p>Iron's-only, gated through {@link OptionalModGate} so an absent-profile pass is recorded
 * as the skip it is. The two config scenarios own their own batch, because the config values
 * they flip are process-wide globals (audit V26/T0.4).
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class IronsInscriptionTableGameTests {

    private IronsInscriptionTableGameTests() {
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * A FakePlayer private to {@code scenario}: a distinct {@link GameProfile} means
     * {@code FakePlayerFactory} hands back a distinct instance, so two tests running
     * concurrently in one batch cannot see each other's inventory, health or cast state.
     *
     * <p>Iron's-free on purpose. The Iron's-side cast state a player needs is set up by
     * {@link IronsTableDriver#prepareCaster}, which only the casting scenario reaches.
     */
    static ServerPlayer scenarioPlayer(GameTestHelper helper, String scenario) {
        String name = scenario.length() > 16 ? scenario.substring(0, 16) : scenario;
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
            UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)),
            name));
        player.moveTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        // Hurt, so a resolved heal is observable as a health increase.
        player.setHealth(10.0f);
        // Permissive defaults so a click can only be refused for the reason under test.
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    /** A real Iron's spellbook with the native container Iron's creates one with. */
    private static ItemStack freshBook(GameTestHelper helper) {
        ItemStack book = IronsTableDriver.freshBook();
        if (book.isEmpty()) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        return book;
    }

    /** A well-formed ANS carrier holding a real, castable Self+Heal. */
    private static ItemStack healCarrier(GameTestHelper helper, int count) {
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
            new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE), "Table Heal", "arcane", "spark");
        if (carrier.isEmpty()) {
            helper.fail("export must yield a carrier when Iron's is loaded");
        }
        carrier.setCount(count);
        return carrier;
    }

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    /** Occupy {@code count} proxy pool ids on {@code book} with distinct filler payloads. */
    private static void fillProxyPool(GameTestHelper helper, ItemStack book, int count) {
        for (int i = 0; i < count; i++) {
            IronsBookBindingUtil.AppendResult result = IronsBookBindingUtil.appendArsSpellToBook(
                book, arsPayload("filler_" + i), null, null, null, -1);
            if (result != IronsBookBindingUtil.AppendResult.ADDED) {
                helper.fail("pre-filling proxy slot " + i + " must ADD, got " + result);
            }
        }
    }

    private static int arsEntries(ItemStack stack) {
        return CrossModSpellComponents.countArsEntries(CrossModSpellComponents.get(stack));
    }

    private static Set<Integer> poolIds(ItemStack stack) {
        return CrossModSpellComponents.usedProxyPoolIds(CrossModSpellComponents.get(stack));
    }

    /** A table with {@code book} in the spellbook slot and {@code scroll} in the scroll slot. */
    private static Object tableWith(ServerPlayer player, ItemStack book, ItemStack scroll) {
        Object menu = IronsTableDriver.openTable(player);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_BOOK, book);
        IronsTableDriver.put(menu, IronsTableDriver.SLOT_SCROLL, scroll);
        return menu;
    }

    // ------------------------------------------------------------------ (a) the happy path

    /**
     * One click on a carrier binds it: exactly one {@code ars_cross_N} wheel slot appears,
     * its N is the pool id recorded on the book's sidecar entry, and exactly one scroll is
     * paid for it.
     */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void table_carrierBindsOnInscribeClick(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_bind");
        ItemStack book = freshBook(helper);
        Object menu = tableWith(player, book, healCarrier(helper, 3));

        if (!IronsTableDriver.click(menu, player, -1)) {
            helper.fail("the inscribe click on a carrier must be handled, not refused");
        }

        ItemStack bound = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        if (arsEntries(bound) != 1) {
            helper.fail("one click must leave exactly one Ars sidecar entry on the book, found "
                + arsEntries(bound));
        }
        Set<Integer> pool = poolIds(bound);
        if (pool.size() != 1) {
            helper.fail("exactly one proxy pool id must be allocated, got " + pool);
        }
        int poolId = pool.iterator().next();
        if (IronsTableDriver.proxySlotIndex(bound, poolId) < 0) {
            helper.fail("the sidecar entry's pool id " + poolId + " has no ars_cross_" + poolId
                + " slot in the book's native container: the wheel entry the player would "
                + "select does not exist");
        }
        if (IronsTableDriver.proxySlotCount(bound) != 1) {
            helper.fail("a single bind must add a single proxy wheel slot, found "
                + IronsTableDriver.proxySlotCount(bound));
        }
        if (IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).getCount() != 2) {
            helper.fail("exactly one scroll of the three must be consumed, "
                + IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).getCount() + " left");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ (b) replay safety

    /** Clicking the same carrier twice is a duplicate, and the second click costs nothing. */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void table_secondClickOfSameCarrierIsDuplicate(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_dupe");
        Object menu = tableWith(player, freshBook(helper), healCarrier(helper, 3));

        IronsTableDriver.click(menu, player, -1);
        int afterFirst = IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).getCount();

        String outcome = IronsTableDriver.handle(menu, player);
        if (!"REJECTED:DUPLICATE".equals(outcome)) {
            helper.fail("a second click on an identical carrier must be refused as a duplicate, "
                + "got " + outcome);
        }
        if (IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).getCount() != afterFirst) {
            helper.fail("a duplicate refusal must not consume a scroll");
        }
        ItemStack bound = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        if (arsEntries(bound) != 1 || IronsTableDriver.proxySlotCount(bound) != 1) {
            helper.fail("the duplicate click added a second entry or wheel slot: entries="
                + arsEntries(bound) + " proxySlots=" + IronsTableDriver.proxySlotCount(bound));
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ (c) upstream intact

    /**
     * The regression that matters most to everyone who does not use ANS: a genuine Iron's
     * scroll must still inscribe through Iron's own code, at the index the player selected,
     * and still be consumed.
     */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void table_nativeScrollStillInscribesUpstream(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_native");
        // Iron's own inscription only acts on an `instanceof SpellBook` with a free slot, so
        // this scenario picks its book on Iron's terms rather than on ANS's broader one.
        ItemStack book = IronsTableDriver.findNativeInscribableBook();
        if (book.isEmpty()) {
            helper.fail("no Iron's SpellBook item with a free wheel slot is registered; this "
                + "scenario cannot run");
        }
        ItemStack scroll = IronsTableDriver.makeNativeScroll();
        if (scroll.isEmpty()) {
            helper.fail("no genuine Iron's spell is registered; this scenario cannot run");
        }
        if (IronsTableDriver.nativeSpellCount(book) != 0) {
            helper.fail("the fixture book must start with no native spells");
        }
        Object menu = tableWith(player, book, scroll);

        // Iron's own path requires a selected wheel index; that is what the screen's spell
        // list click sends, and it is a plain menu button with a non-negative id.
        IronsTableDriver.click(menu, player, 0);
        IronsTableDriver.click(menu, player, -1);

        ItemStack inscribed = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        if (IronsTableDriver.nativeSpellCount(inscribed) != 1) {
            helper.fail("Iron's own inscription must still write its spell onto the book; found "
                + IronsTableDriver.nativeSpellCount(inscribed) + " native spells");
        }
        if (IronsTableDriver.proxySlotCount(inscribed) != 0 || arsEntries(inscribed) != 0) {
            helper.fail("a native inscription must not leave any ANS data on the book");
        }
        if (!IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).isEmpty()) {
            helper.fail("Iron's consumes the scroll it inscribed; the scroll slot is not empty");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ (d) pool exhausted

    /** With all eight proxy pool ids taken, a ninth bind is refused and costs nothing. */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void table_ninthCarrierIsRefusedWhenProxyPoolIsFull(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_full");
        ItemStack book = freshBook(helper);
        fillProxyPool(helper, book, CrossModSpellComponents.PROXY_POOL_SIZE);
        Object menu = tableWith(player, book, healCarrier(helper, 2));

        String outcome = IronsTableDriver.handle(menu, player);
        if (!"REJECTED:BOOK_FULL".equals(outcome)) {
            helper.fail("with every proxy pool id taken the bind must be refused as BOOK_FULL, "
                + "got " + outcome);
        }
        ItemStack refused = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        if (arsEntries(refused) != CrossModSpellComponents.PROXY_POOL_SIZE) {
            helper.fail("a refused bind changed the book's entry count: " + arsEntries(refused));
        }
        if (IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).getCount() != 2) {
            helper.fail("a refused bind must not consume a scroll");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ (e)(f) config

    /** A cap of one means the second distinct spell is refused, however much pool is free. */
    @GameTest(template = "platform", batch = "ans_irons_table_config")
    public static void table_configCapRefusesSecondSpell(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_cap");
        ItemStack book = freshBook(helper);
        fillProxyPool(helper, book, 1);
        Object menu = tableWith(player, book, healCarrier(helper, 2));

        int previous = AnsConfig.MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK.get();
        AnsConfig.MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK.set(1);
        try {
            String outcome = IronsTableDriver.handle(menu, player);
            if (!"REJECTED:BOOK_FULL".equals(outcome)) {
                helper.fail("with max_ars_cross_spells_per_irons_spellbook=1 a second distinct "
                    + "spell must be refused, got " + outcome);
            }
            ItemStack refused = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
            if (arsEntries(refused) != 1) {
                helper.fail("the capped bind wrote an entry anyway: " + arsEntries(refused));
            }
            if (IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).getCount() != 2) {
                helper.fail("a capped bind must not consume a scroll");
            }
        } finally {
            AnsConfig.MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK.set(previous);
        }
        helper.succeed();
    }

    /** The kill switch is honoured at the table, exactly as it is at the ritual. */
    @GameTest(template = "platform", batch = "ans_irons_table_config")
    public static void table_killSwitchRefusesBind(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_kill");
        Object menu = tableWith(player, freshBook(helper), healCarrier(helper, 2));

        boolean previous = AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.get();
        AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.set(false);
        try {
            String outcome = IronsTableDriver.handle(menu, player);
            if (!"REJECTED:DISABLED".equals(outcome)) {
                helper.fail("with allow_ars_spells_in_irons_spellbooks=false the table must "
                    + "refuse the bind as DISABLED, got " + outcome);
            }
            ItemStack refused = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
            if (arsEntries(refused) != 0 || IronsTableDriver.proxySlotCount(refused) != 0) {
                helper.fail("a disabled bind still wrote to the book");
            }
            if (IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).getCount() != 2) {
                helper.fail("a disabled bind must not consume a scroll");
            }
        } finally {
            AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.set(previous);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ (g) malformed

    /**
     * Three ways a carrier can be broken -- more than one Ars entry, an {@code ars_spell}
     * payload that no longer deserializes, and a sidecar stamped with a schema version from
     * the future -- and all must be refused with the item left exactly as it was. A broken
     * scroll is reported, never repaired by deletion.
     */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void table_malformedCarriersAreRefusedIntact(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_broken");

        ItemStack twoEntries = healCarrier(helper, 2);
        CrossModSpellComponents.addArsEntryWithMeta(twoEntries,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, arsPayload("second"),
            CrossModSpellComponents.NO_PROXY_POOL_ID, null, null, null);
        assertRefusedIntact(helper, player, twoEntries, "a carrier with two Ars entries");

        // Replace the whole component rather than editing a list tag in place: in 1.21.1 the
        // sidecar is an immutable data component, so the only way to plant an unreadable
        // payload is to write a new list carrying it.
        ItemStack garbage = healCarrier(helper, 2);
        CompoundTag rubbish = new CompoundTag();
        rubbish.putString("not_a_spell", " ");
        garbage.set(ModDataComponents.CROSS_SPELLS.get(), CrossModSpellComponents.withArsEntry(
            CrossModSpellList.EMPTY, CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, rubbish,
            CrossModSpellComponents.NO_PROXY_POOL_ID, null, null, null));
        assertRefusedIntact(helper, player, garbage, "a carrier whose payload cannot deserialize");

        ItemStack future = healCarrier(helper, 2);
        future.set(ModDataComponents.SCHEMA_VERSION.get(),
            CrossModSpellComponents.SCHEMA_VERSION + 1);
        assertRefusedIntact(helper, player, future, "a carrier stamped with a future schema");
        helper.succeed();
    }

    /**
     * Both refusal routes for one broken scroll: the menu router (which returns false without
     * touching the menu) and the handler (which names the reason). Neither may mutate the
     * scroll or the book.
     */
    private static void assertRefusedIntact(GameTestHelper helper, ServerPlayer player,
                                            ItemStack scroll, String what) {
        ItemStack book = freshBook(helper);
        Object menu = tableWith(player, book, scroll);
        ItemStack before = scroll.copy();

        if (IronsTableDriver.click(menu, player, -1)) {
            helper.fail(what + " must be refused at the menu button, not accepted");
        }
        String outcome = IronsTableDriver.handle(menu, player);
        if (!"REJECTED:INVALID_CARRIER".equals(outcome)) {
            helper.fail(what + " must be refused as INVALID_CARRIER, got " + outcome);
        }
        ItemStack after = IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL);
        if (after.getCount() != before.getCount()
            || !ItemStack.isSameItemSameComponents(before, after)) {
            helper.fail(what + " was mutated or consumed by a refusal: "
                + before.getComponentsPatch() + " -> " + after.getComponentsPatch());
        }
        ItemStack refusedBook = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        if (arsEntries(refusedBook) != 0 || IronsTableDriver.proxySlotCount(refusedBook) != 0) {
            helper.fail(what + " wrote an entry onto the book");
        }
    }

    // ------------------------------------------------------------------ (h) legacy repair

    /**
     * A pre-3.2 carrier has an Ars payload but no native container at all -- the shape that
     * used to crash Iron's. {@code CarrierReconciler} repairs it inside the bind, so one
     * click both fixes and binds it rather than branding it broken forever.
     */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void table_legacyContainerlessCarrierIsReconciledAndBound(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_legacy");
        Item scrollItem = BuiltInRegistries.ITEM
            .getOptional(ArsSpellExportUtil.IRONS_SCROLL_ID).orElse(null);
        if (scrollItem == null) {
            helper.fail("Iron's scroll item must be registered when Iron's is loaded");
        }
        ItemStack legacy = new ItemStack(scrollItem, 2);
        CrossModSpellComponents.addArsEntryWithMeta(legacy,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1,
            com.otectus.arsnspells.spell.CrossCastingHandler.encodeArsSpell(
                new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE)),
            CrossModSpellComponents.NO_PROXY_POOL_ID, "Legacy Heal", "arcane", "spark");
        // Precondition: this really is the container-less shape, or the repair proves nothing.
        if (IronsTableDriver.scrollContainerDereferenceSucceeds(legacy)) {
            helper.fail("the fixture already has a readable container; it is not the legacy shape");
        }

        Object menu = tableWith(player, freshBook(helper), legacy);
        if (!IronsTableDriver.click(menu, player, -1)) {
            helper.fail("a repairable legacy carrier must be handled, not refused");
        }

        ItemStack bound = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        if (arsEntries(bound) != 1 || IronsTableDriver.proxySlotCount(bound) != 1) {
            helper.fail("the repaired legacy carrier must bind exactly once: entries="
                + arsEntries(bound) + " proxySlots=" + IronsTableDriver.proxySlotCount(bound));
        }
        if (IronsTableDriver.item(menu, IronsTableDriver.SLOT_SCROLL).getCount() != 1) {
            helper.fail("exactly one legacy scroll must be consumed");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ (i) data preservation

    /**
     * A bind adds; it never overwrites. Native spell, enchantment, custom name and another
     * mod's {@code custom_data} all survive.
     *
     * <p>The point in 1.21.1 is component-patch discipline: {@code appendArsSpellToBook}
     * mutates a copy and re-applies its patch, so anything it does not know about has to
     * travel unchanged.
     */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void table_bindPreservesEverythingElseOnTheBook(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_keep");
        ItemStack book = freshBook(helper);
        if (!IronsTableDriver.addNativeSpellToBook(book)) {
            helper.fail("could not put a genuine Iron's spell on the fixture book");
        }
        int nativeIndex = IronsTableDriver.nativeSpellIndex(book);
        String nativeSpell = IronsTableDriver.spellIdAt(book, nativeIndex);
        Holder<Enchantment> unbreaking = helper.getLevel().registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
        book.enchant(unbreaking, 1);
        book.set(DataComponents.CUSTOM_NAME, Component.literal("Grandfather's Codex"));
        CompoundTag foreign = new CompoundTag();
        foreign.putInt("charges", 7);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(foreign));

        Object menu = tableWith(player, book, healCarrier(helper, 1));
        if (!IronsTableDriver.click(menu, player, -1)) {
            helper.fail("the bind click must be handled");
        }

        ItemStack bound = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        if (arsEntries(bound) != 1) {
            helper.fail("the bind did not happen, so preservation is untested");
        }
        if (!nativeSpell.equals(IronsTableDriver.spellIdAt(bound, nativeIndex))) {
            helper.fail("the native spell at wheel index " + nativeIndex + " was overwritten: "
                + nativeSpell + " -> " + IronsTableDriver.spellIdAt(bound, nativeIndex));
        }
        if (IronsTableDriver.nativeSpellCount(bound) != 1) {
            helper.fail("the book's genuine Iron's spell count changed to "
                + IronsTableDriver.nativeSpellCount(bound));
        }
        if (EnchantmentHelper.getItemEnchantmentLevel(unbreaking, bound) != 1) {
            helper.fail("the book's enchantment was dropped by the bind");
        }
        if (!"Grandfather's Codex".equals(bound.getHoverName().getString())) {
            helper.fail("the book's custom name was dropped by the bind");
        }
        CustomData kept = bound.get(DataComponents.CUSTOM_DATA);
        if (kept == null || kept.copyTag().getInt("charges") != 7) {
            helper.fail("another mod's custom_data was dropped by the bind");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ (j) extraction guard

    /**
     * A proxy entry cannot be extracted back onto a scroll: that would hand the player a
     * proxy with no payload and strand the real entry on the book. The native spell in the
     * same book is still extractable, so the guard is targeted rather than a blanket block.
     */
    @GameTest(template = "platform", batch = "ans_irons_table")
    public static void table_proxyEntryCannotBeExtracted(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ServerPlayer player = scenarioPlayer(helper, "table_extract");
        ItemStack book = freshBook(helper);
        if (!IronsTableDriver.addNativeSpellToBook(book)) {
            helper.fail("could not put a genuine Iron's spell on the fixture book");
        }
        Object menu = tableWith(player, book, healCarrier(helper, 1));
        IronsTableDriver.click(menu, player, -1);

        ItemStack bound = IronsTableDriver.item(menu, IronsTableDriver.SLOT_BOOK);
        Set<Integer> pool = poolIds(bound);
        if (pool.size() != 1) {
            helper.fail("the fixture needs exactly one bound proxy, got pool ids " + pool);
        }
        int nativeIndex = IronsTableDriver.nativeSpellIndex(bound);
        int proxyIndex = IronsTableDriver.proxySlotIndex(bound, pool.iterator().next());
        if (nativeIndex < 0 || proxyIndex < 0) {
            helper.fail("the fixture needs both a native spell and a bound proxy; got indices "
                + nativeIndex + " and " + proxyIndex);
        }

        // Positive control first: Iron's extraction offer still works for its own spells.
        IronsTableDriver.click(menu, player, nativeIndex);
        if (IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT).isEmpty()) {
            helper.fail("selecting a genuine Iron's spell must still offer it for extraction; "
                + "the guard is blocking more than ANS proxies");
        }
        IronsTableDriver.click(menu, player, proxyIndex);
        if (!IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT).isEmpty()) {
            helper.fail("selecting a bound Ars entry must offer nothing for extraction, but the "
                + "result slot holds "
                + IronsTableDriver.item(menu, IronsTableDriver.SLOT_RESULT));
        }
        helper.succeed();
    }
}
