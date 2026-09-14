package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
import com.otectus.arsnspells.spell.irons.IronsTableBindHandler;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Drives a <em>real</em> {@code InscriptionTableMenu} from a GameTest, so the table bind route
 * is tested through the same button click a player performs rather than through a hand-rolled
 * stand-in for it.
 *
 * <p><b>Iron's-isolated and test-only.</b> Every Iron's import lives here, no method signature
 * names an Iron's type (the menu travels as an opaque {@link Object} handle), and callers gate
 * on {@code OptionalModGate.skipIfAbsent} before touching this class at all -- so the JVM never
 * resolves it on the Iron's-absent profile. Guards inside each method are defence in depth, not
 * the mechanism.
 *
 * <p>The menu is built with {@link ContainerLevelAccess#NULL}: nothing under test reads the
 * access except {@code stillValid}, which the vanilla button-click path does not call, and a
 * null access keeps the fixture free of a placed table block.
 *
 * <p>This class also carries the cast half of the harness, which the 1.20.1 line kept in a
 * separate {@code IronsProxyCastDriver}: the port has only one Iron's-isolated GameTest holder,
 * so folding the two together avoids a second class with the same isolation contract.
 */
final class IronsTableDriver {

    /** Iron's own slot indices: 36 player slots, then spellbook, scroll, result. */
    static final int SLOT_BOOK = 36;
    static final int SLOT_SCROLL = 37;
    static final int SLOT_RESULT = 38;

    private IronsTableDriver() {
    }

    // ------------------------------------------------------------------ the menu

    /** A fresh Inscription Table menu owned by {@code player}, as an opaque handle. */
    static Object openTable(ServerPlayer player) {
        if (!IronsCompat.isLoaded()) {
            return null;
        }
        return new InscriptionTableMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
    }

    /** Put {@code stack} into menu slot {@code slot}, exactly as a player drag would. */
    static void put(Object menu, int slot, ItemStack stack) {
        slot(menu, slot).set(stack);
    }

    /** Whatever is in menu slot {@code slot} right now. */
    static ItemStack item(Object menu, int slot) {
        return slot(menu, slot).getItem();
    }

    /**
     * Iron's exposes its three working slots by accessor, so the fixture asks for them by name
     * rather than trusting the index arithmetic to survive an upstream slot reorder.
     */
    private static Slot slot(Object menu, int slot) {
        InscriptionTableMenu table = (InscriptionTableMenu) menu;
        return switch (slot) {
            case SLOT_BOOK -> table.getSpellBookSlot();
            case SLOT_SCROLL -> table.getScrollSlot();
            case SLOT_RESULT -> table.getResultSlot();
            default -> table.getSlot(slot);
        };
    }

    /**
     * Click the menu button Iron's screen sends: {@code id < 0} inscribes, {@code id >= 0}
     * selects that wheel index. This is the call the ANS router mixin injects into.
     */
    static boolean click(Object menu, ServerPlayer player, int id) {
        return ((InscriptionTableMenu) menu).clickMenuButton(player, id);
    }

    /**
     * Call the bind handler directly, for assertions finer than the boolean the menu button
     * returns. Encoded as {@code STATUS:RESULT} (result {@code -} when the status carries
     * none) so no Iron's-adjacent type crosses back into the test class.
     */
    static String handle(Object menu, ServerPlayer player) {
        IronsTableBindHandler.Outcome outcome =
            IronsTableBindHandler.handle(player, (InscriptionTableMenu) menu);
        return outcome.status().name() + ":"
            + (outcome.result() == null ? "-" : outcome.result().name());
    }

    // ------------------------------------------------------------------ book fixtures

    /**
     * Give {@code book} the native container a real spellbook is created with. Iron's writes
     * it in {@code IPresetSpellContainer.initializeSpellContainer} on creation, and
     * {@code doInscription} dereferences it unguarded, so a {@code new ItemStack(book)}
     * fixture has to be initialized the same way or the native-path test measures an NPE.
     */
    static boolean initializeNativeContainer(ItemStack book) {
        if (!(book.getItem() instanceof IPresetSpellContainer preset)) {
            return false;
        }
        preset.initializeSpellContainer(book);
        return ISpellContainer.isSpellContainer(book);
    }

    /**
     * A real Iron's spellbook with the native container Iron's creates one with, or
     * {@link ItemStack#EMPTY} when none is registered.
     *
     * <p>The 1.20.1 line took this from {@code CrossCastGameTests.findIronsSpellBook}; the port
     * has no such helper, so the lookup lives here beside the rest of the Iron's isolation.
     */
    static ItemStack freshBook() {
        if (!IronsCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        return findNativeInscribableBook();
    }

    /**
     * A book Iron's <em>own</em> inscription accepts, already carrying its native container.
     *
     * <p>Iron's {@code doInscription} requires {@code instanceof SpellBook} and a free wheel
     * slot, so the upstream-behaviour test has to pick on those terms or it measures Iron's
     * declining to act rather than ANS interfering.
     */
    static ItemStack findNativeInscribableBook() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SpellBook book && book.getMaxSpellSlots() > 0) {
                ItemStack stack = new ItemStack(item);
                book.initializeSpellContainer(stack);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ container readers

    /** How many {@code ars_cross_*} proxy slots {@code book}'s native container holds. */
    static int proxySlotCount(ItemStack book) {
        if (!ISpellContainer.isSpellContainer(book)) {
            return 0;
        }
        int count = 0;
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells()) {
            if (slot != null && slot.getSpell() != null
                && ArsCrossProxyRegistry.poolIdOf(slot.getSpell().getSpellResource()) >= 0) {
                count++;
            }
        }
        return count;
    }

    /** Index of the {@code ars_cross_<poolId>} proxy in {@code book}'s native container, or -1. */
    static int proxySlotIndex(ItemStack book, int poolId) {
        if (!ISpellContainer.isSpellContainer(book)) {
            return -1;
        }
        String wanted = ArsCrossProxyRegistry.spellId(poolId).toString();
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells()) {
            if (slot != null && slot.getSpell() != null
                && wanted.equals(slot.getSpell().getSpellId())) {
                return slot.index();
            }
        }
        return -1;
    }

    /** Wheel index of the first genuine (non-proxy) Iron's spell in {@code book}, or -1. */
    static int nativeSpellIndex(ItemStack book) {
        if (!ISpellContainer.isSpellContainer(book)) {
            return -1;
        }
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells()) {
            if (slot != null && slot.getSpell() != null
                && ArsCrossProxyRegistry.poolIdOf(slot.getSpell().getSpellResource()) < 0) {
                return slot.index();
            }
        }
        return -1;
    }

    /** The spell id at wheel index {@code index}, or the empty string if the slot is empty. */
    static String spellIdAt(ItemStack book, int index) {
        if (!ISpellContainer.isSpellContainer(book) || index < 0) {
            return "";
        }
        SpellData data = ISpellContainer.get(book).getSpellAtIndex(index);
        if (data == null || data == SpellData.EMPTY || data.getSpell() == null) {
            return "";
        }
        return data.getSpell().getSpellId();
    }

    /**
     * How many genuine (non-proxy) Iron's spells {@code book}'s native container holds.
     *
     * <p>Counts through the modern container Iron's actually writes, so a coexistence test
     * cannot pass by inspecting a legacy key that current books no longer use.
     */
    static int nativeSpellCount(ItemStack book) {
        if (!ISpellContainer.isSpellContainer(book)) {
            return 0;
        }
        int count = 0;
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells()) {
            if (slot == null || slot.getSpell() == null) {
                continue;
            }
            if (ArsCrossProxyRegistry.poolIdOf(slot.getSpell().getSpellResource()) < 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Perform the exact dereference Iron's Inscription Table performs on the scroll slot,
     * and report whether it survives.
     *
     * <p>This is the crash reproduction, spelled the way Iron's spells it:
     * {@code ISpellContainer.get(stack).getSpellAtIndex(0)}. Asserting
     * {@code isSpellContainer(...)} instead would test a <em>different</em> function -- that
     * one reads the component, this one decodes it -- and would not catch a container that is
     * present but fails to decode.
     */
    static boolean scrollContainerDereferenceSucceeds(ItemStack stack) {
        if (!IronsCompat.isLoaded()) {
            return false;
        }
        try {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container == null) {
                return false;
            }
            container.getSpellAtIndex(0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ native spells

    /** A scroll carrying a genuine (non-proxy) Iron's spell, or EMPTY if none is registered. */
    static ItemStack makeNativeScroll() {
        if (!IronsCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        AbstractSpell spell = firstNativeSpell();
        if (spell == null) {
            return ItemStack.EMPTY;
        }
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, spell.getMinLevel(), scroll);
        return scroll;
    }

    /** Add a genuine Iron's spell into {@code book}'s native container, alongside whatever is there. */
    static boolean addNativeSpellToBook(ItemStack book) {
        if (!IronsCompat.isLoaded()) {
            return false;
        }
        AbstractSpell spell = firstNativeSpell();
        if (spell == null) {
            return false;
        }
        ISpellContainerMutable mutable = ISpellContainer.getOrCreate(book).mutableCopy();
        int index = mutable.getMaxSpellCount();
        mutable.setMaxSpellCount(index + 1);
        if (!mutable.addSpellAtIndex(spell, spell.getMinLevel(), index, false)) {
            return false;
        }
        ISpellContainer.set(book, mutable.toImmutable());
        return true;
    }

    private static AbstractSpell firstNativeSpell() {
        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            if (spell == SpellRegistry.none()
                || ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
                continue;
            }
            return spell;
        }
        return null;
    }

    // ------------------------------------------------------------------ casting

    /**
     * Cast the {@code ars_cross_<poolId>} proxy off {@code book} held in the main hand, the
     * way {@code NativeCastPaymentGameTests} casts a native Iron's spell for a FakePlayer:
     * arm the cast with {@code attemptInitiateCast}, then fire {@code castSpell}.
     *
     * <p>Main hand rather than Curios because a FakePlayer reliably has hands but not
     * necessarily a Curios inventory. The book is both passed as the casting item and placed
     * in the main hand: Iron's records the former on {@code MagicData}, and
     * {@code ProxyCarrierResolver.casting} reads {@code getPlayerCastingItem()} before the
     * equipment slot, so that is the branch this fixture actually exercises -- emptying the
     * main hand alone does not break the cast. The Curios route is kept as a fallback for the
     * same reason the 1.20.1 line used it first: it is the shape a real player casts in.
     *
     * @return true when Iron's armed and ran the cast; false when it refused to initiate
     */
    static boolean castBoundProxyFromMainHand(ServerPlayer player, ItemStack book, int poolId) {
        if (!IronsCompat.isLoaded()) {
            return false;
        }
        AbstractSpell proxy = ArsCrossProxyRegistry.get(poolId);
        if (proxy == null) {
            throw new IllegalArgumentException("no proxy spell registered for pool id " + poolId);
        }
        prepareCaster(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        if (proxy.attemptInitiateCast(book, 1, player.level(), player, CastSource.SPELLBOOK,
                false, SpellSelectionManager.MAINHAND)) {
            proxy.castSpell(player.level(), 1, player, CastSource.SPELLBOOK, false);
            return true;
        }
        // Fallback: the Curios spellbook slot, which is where a real player carries the book.
        MagicData.getPlayerMagicData(player).resetCastingState();
        Utils.setPlayerSpellbookStack(player, book);
        if (!proxy.attemptInitiateCast(book, 1, player.level(), player, CastSource.SPELLBOOK,
                false, Curios.SPELLBOOK_SLOT)) {
            return false;
        }
        proxy.castSpell(player.level(), 1, player, CastSource.SPELLBOOK, false);
        return true;
    }

    /**
     * The Iron's-side state a FakePlayer needs before it can cast anything, mirroring
     * {@code NativeCastPaymentGameTests.Loaded.prepare}: a bound server player, no in-flight
     * cast, and mana enough that a refusal can only be about the spell under test.
     */
    static void prepareCaster(ServerPlayer player) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        MagicData data = MagicData.getPlayerMagicData(player);
        data.setServerPlayer(player);
        data.resetCastingState();
        player.getAttribute(AttributeRegistry.MAX_MANA).setBaseValue(10000);
        data.setMana(10000);
    }
}
