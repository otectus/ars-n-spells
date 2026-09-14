package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
import com.otectus.arsnspells.spell.irons.IronsTableBindHandler;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Drives a <em>real</em> {@code InscriptionTableMenu} from a GameTest, so the table bind route
 * is tested through the same button click a player performs rather than through a hand-rolled
 * stand-in for it.
 *
 * <p><b>Iron's-isolated and test-only</b>, in the shape {@link IronsProxyCastDriver}
 * established: every Iron's import lives here, no method signature names an Iron's type (the
 * menu travels as an opaque {@link Object} handle), and callers gate on
 * {@code IronsCompat.isLoaded()} before touching this class at all -- so the JVM never
 * resolves it on the Iron's-absent profile.
 *
 * <p>The menu is built with {@link ContainerLevelAccess#NULL}: nothing under test reads the
 * access except {@code stillValid}, which the vanilla button-click path does not call, and a
 * null access keeps the fixture free of a placed table block.
 */
final class IronsTableDriver {

    /** Iron's own slot indices: 36 player slots, then spellbook, scroll, result. */
    static final int SLOT_BOOK = 36;
    static final int SLOT_SCROLL = 37;
    static final int SLOT_RESULT = 38;

    private IronsTableDriver() {
    }

    /** A fresh Inscription Table menu owned by {@code player}, as an opaque handle. */
    static Object openTable(ServerPlayer player) {
        if (!IronsCompat.isLoaded()) {
            return null;
        }
        return new InscriptionTableMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
    }

    /** Put {@code stack} into menu slot {@code slot}, exactly as a player drag would. */
    static void put(Object menu, int slot, ItemStack stack) {
        ((InscriptionTableMenu) menu).getSlot(slot).set(stack);
    }

    /** Whatever is in menu slot {@code slot} right now. */
    static ItemStack item(Object menu, int slot) {
        return ((InscriptionTableMenu) menu).getSlot(slot).getItem();
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
     * A book Iron's <em>own</em> inscription accepts, already carrying its native container.
     *
     * <p>Deliberately stricter than {@code CrossCastGameTests.findIronsSpellBook}, which
     * answers "can ANS bind onto this" and matches every {@code ISpellbook} item. Iron's
     * {@code doInscription} requires {@code instanceof SpellBook} and a free wheel slot, so the
     * upstream-behaviour test has to pick on those terms or it measures Iron's declining to
     * act rather than ANS interfering.
     */
    static ItemStack findNativeInscribableBook() {
        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof SpellBook book && book.getMaxSpellSlots() > 0) {
                ItemStack stack = new ItemStack(item);
                book.initializeSpellContainer(stack);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

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
}
