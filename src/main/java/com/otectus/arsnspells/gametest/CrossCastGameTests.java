package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.network.CrossCastRequestPacket;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

/**
 * GameTests for the cross-cast pipeline's runtime behavior on a live server.
 *
 * <p>Run with {@code ./gradlew runGameTestServer}. The {@code build.gradle}
 * {@code gameTestServer} run target gates on the {@code ars_n_spells} namespace so
 * unrelated mods' tests do not run.
 *
 * <p>{@link #crossCastCycle_advancesSelectedIndex} is Iron-agnostic and runs in every
 * profile. {@link #ironsLoaded_exportBindCoexist_roundTrip} is the 3.0.0 Iron-LOADED
 * integration scenario; it self-skips when Iron's is absent and only executes under the
 * opt-in {@code -PwithIronsRuntimeGameTests} profile (see {@code build.gradle}), where
 * real {@code irons_spellbooks} items are on the classpath.
 *
 * <p>The cross-cast <em>cost</em> regressions (CRIT-002 SEPARATE-mode one-way Ars drain,
 * CRIT-004 multiplier-before-ring) are guarded in production by inline server-side
 * assertions + {@code CrossCastTrace} logging in
 * {@link CrossCastingHandler#onArsSpellCost} and {@code CrossCastIronsHandler}. They are
 * validated manually per {@code TESTING_GUIDE.md} because reproducing them requires a live
 * mana/LP/Sanctified-Legacy runtime state that the GameTest harness does not stand up;
 * they are deliberately NOT represented here as fake-passing {@code helper.succeed()} stubs.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CrossCastGameTests {

    private static final GameProfile FAKE_PROFILE =
        new GameProfile(UUID.fromString("0a000000-0000-0000-0000-00000000a115"), "ans_gametest");

    private CrossCastGameTests() {
    }

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    private static int selectedIndex(ItemStack stack) {
        return stack.getOrCreateTag().getInt(CrossCastNbt.TAG_SPELL_INDEX);
    }

    /** Find any registered Iron's spellbook item (tier-agnostic), or null if none. */
    private static Item findIronsSpellBook() {
        for (Item item : ForgeRegistries.ITEMS) {
            if (IronsBookBindingUtil.isIronsSpellBook(new ItemStack(item))) {
                return item;
            }
        }
        return null;
    }

    /**
     * Sanity test: confirms the gameTestServer run target is wired and the test class is
     * discovered. Uses the empty vanilla {@code platform} template.
     */
    @GameTest(template = "platform")
    public static void scaffoldIsWired(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * Drives the real server-authoritative entry point ({@link CrossCastingHandler#serverHandleCast})
     * with a CYCLE action and asserts the selected-spell index advances and wraps. This is
     * the production sneak-right-click cycle path, exercised end-to-end with a real
     * {@link ServerPlayer} on a live server. Iron-agnostic: both entries are Ars payloads.
     */
    @GameTest(template = "platform")
    public static void crossCastCycle_advancesSelectedIndex(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("a"));
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("b"));

        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.get(level, FAKE_PROFILE);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);

        if (selectedIndex(book) != 0) {
            helper.fail("a freshly inscribed item must start at index 0");
        }

        // First cycle: 0 -> 1. The index is set before the (action-bar) feedback message,
        // so a FakePlayer's connectionless message send cannot prevent the mutation we assert.
        cycleTolerant(player, book);
        if (selectedIndex(book) != 1) {
            helper.fail("first cycle must advance the selected index to 1, got " + selectedIndex(book));
        }

        // Second cycle wraps: 1 -> 0 (two entries).
        cycleTolerant(player, book);
        if (selectedIndex(book) != 0) {
            helper.fail("second cycle must wrap the selected index back to 0, got " + selectedIndex(book));
        }

        helper.succeed();
    }

    private static void cycleTolerant(ServerPlayer player, ItemStack book) {
        try {
            CrossCastingHandler.serverHandleCast(player, book, InteractionHand.MAIN_HAND,
                CrossCastRequestPacket.Action.CYCLE, UUID.randomUUID());
        } catch (Throwable ignored) {
            // A FakePlayer has no client connection; the action-bar feedback send may throw.
            // The index mutation under test happens before that send, so tolerate it.
        }
    }

    /**
     * 3.0.0 Iron-LOADED round-trip: a real {@code irons_spellbooks:scroll} carries an Ars
     * spell, and a real Iron's spellbook accepts the bound Ars entry alongside an untouched
     * native {@code ISB_Spells} container, with payload-keyed dedup. Self-skips when Iron's
     * is absent (default run); executes under {@code -PwithIronsRuntimeGameTests}.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_exportBindCoexist_roundTrip(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            // Iron's not on the runtime classpath — nothing to integrate against. Skip
            // rather than assert the Iron-loaded contract (the Iron-absent contract is
            // covered by ArsIronsExportGameTests#ironAbsent_predicatesAreSafe).
            helper.succeed();
            return;
        }

        // Production export path yields a real, recognized Iron's scroll item.
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(new Spell());
        if (carrier.isEmpty() || !IronsBookBindingUtil.isIronsScroll(carrier)) {
            helper.fail("createIronsScrollCarrier must return a real irons_spellbooks:scroll when Iron's is loaded");
        }

        // A valid carrier (non-empty payload) extracts to a single Ars entry.
        ItemStack scroll = new ItemStack(carrier.getItem());
        CompoundTag payload = arsPayload("glyph_touch,glyph_break");
        if (!IronsBookBindingUtil.appendArsSpellToBook(scroll, payload)) {
            helper.fail("appending a fresh Ars payload to a real scroll must succeed");
        }
        Optional<CompoundTag> extracted = IronsBookBindingUtil.extractSingleArsEntry(scroll);
        if (extracted.isEmpty()) {
            helper.fail("a single-entry carrier scroll must yield its Ars payload");
        }

        // Bind onto a real Iron's spellbook carrying a native ISB_Spells container.
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        ItemStack book = new ItemStack(bookItem);
        CompoundTag isb = new CompoundTag();
        isb.putInt("maxSpells", 3);
        book.getOrCreateTag().put("ISB_Spells", isb);
        CompoundTag isbBaseline = isb.copy();

        if (!IronsBookBindingUtil.appendArsSpellToBook(book, extracted.get())) {
            helper.fail("binding the extracted Ars entry onto a real spellbook must succeed");
        }

        // Coexistence: the ANS sidecar lands and the native container is untouched.
        if (!CrossCastNbt.hasCrossModSpells(book.getOrCreateTag())) {
            helper.fail("bound spellbook must carry the ANS cross_spells sidecar");
        }
        if (!isbBaseline.equals(book.getOrCreateTag().getCompound("ISB_Spells"))) {
            helper.fail("native ISB_Spells container must be untouched by binding");
        }

        // Dedup by payload on the real item: re-binding the same payload is rejected.
        if (IronsBookBindingUtil.appendArsSpellToBook(book, extracted.get())) {
            helper.fail("re-binding an equal Ars payload must be rejected as a duplicate");
        }

        helper.succeed();
    }

    /**
     * 3.0.0 native-proxy: binding an Ars spell onto a real Iron's spellbook (Iron's
     * loaded) allocates a native-wheel proxy pool id on the sidecar entry and writes
     * the proxy slot into Iron's modern container without throwing — exercising the
     * real {@code IronsProxySlotWriter} path. Assertions stay NBT-only so this class
     * never classloads Iron's on the default (Iron-absent) gametest run.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_bindAllocatesProxyPoolId(GameTestHelper helper) {
        if (!IronsCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        ItemStack book = new ItemStack(bookItem);

        // Two distinct Ars spells -> two distinct proxy pool ids (1 then 2).
        IronsBookBindingUtil.AppendResult first = IronsBookBindingUtil.appendArsSpellToBook(
            book, arsPayload("glyph_touch"), "Alpha", "fire", "flame", 0xFF112233, -1);
        IronsBookBindingUtil.AppendResult second = IronsBookBindingUtil.appendArsSpellToBook(
            book, arsPayload("glyph_break"), "Beta", "ice", "spark", 0xFF445566, -1);
        if (first != IronsBookBindingUtil.AppendResult.ADDED
            || second != IronsBookBindingUtil.AppendResult.ADDED) {
            helper.fail("binding two distinct Ars spells onto a real book must both ADD");
        }

        CompoundTag tag = book.getOrCreateTag();
        java.util.Set<Integer> used = CrossCastNbt.usedProxyPoolIds(tag);
        if (!used.contains(1) || !used.contains(2)) {
            helper.fail("two binds must allocate distinct proxy pool ids 1 and 2; got " + used);
        }
        CompoundTag alpha = CrossCastNbt.findEntryByProxyPoolId(tag, 1);
        if (alpha == null || !"Alpha".equals(alpha.getString(CrossCastNbt.TAG_CUSTOM_NAME))
            || !"fire".equals(alpha.getString(CrossCastNbt.TAG_NATURE))) {
            helper.fail("proxy pool id 1 must carry the Alpha/fire display metadata");
        }

        // The modern Iron's container received the proxy slots (NBT-level check, no
        // Iron's classes): its serialized form must reference our proxy spell ids.
        String modern = tag.getCompound("irons_spellbooks:spell_container").toString();
        if (!modern.contains("ars_n_spells:ars_cross_")) {
            helper.fail("the native container must hold ars_cross_* proxy slots after binding");
        }

        helper.succeed();
    }
}
