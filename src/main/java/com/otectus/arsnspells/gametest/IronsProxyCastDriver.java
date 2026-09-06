package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.gui.overlays.SpellSelection;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Drives real Iron's Spellbooks cast machinery from a GameTest, so the cross-cast
 * proxy path is exercised end-to-end instead of being asserted at the NBT layer.
 *
 * <p><b>Iron's-isolated and test-only.</b> Every Iron's import lives here; callers in
 * {@link CrossCastGameTests} gate on {@code IronsCompat.isLoaded()} before touching this
 * class, so it is never classloaded on the default (Iron-absent) gametest run. It
 * deliberately reaches past the public API ({@code compat.Curios},
 * {@code gui.overlays.SpellSelection}) because that is the only way to reproduce what a
 * player actually does: equip a book into the Curios spellbook slot, select a wheel entry,
 * and cast. Both symbols are present and identical in Iron's 3.15.0 and 3.16.2.
 *
 * <p><b>Guards here are defence in depth, not the mechanism.</b> Every entry below re-checks
 * {@code IronsCompat.isLoaded()} and no-ops, but that check runs only once the JVM has already
 * resolved this class -- and resolving it needs Iron's on the classpath. No method's signature
 * names an Iron's type, so the class as a whole is the isolation holder and the guard that
 * actually matters stays in the caller. See {@code CrossCastGameTests#emptyHandedPlayer}.
 *
 * <p>Failures throw rather than returning a status, so a broken harness reports as a test
 * error at the offending step instead of as a silent false pass further down.
 */
final class IronsProxyCastDriver {

    private IronsProxyCastDriver() {
    }

    /**
     * The full player-realistic path: equip {@code book} into the Curios spellbook slot,
     * have Iron's arm the cast, then fire the proxy for {@code poolId}.
     */
    static void castViaEquippedSpellbook(ServerPlayer player, ItemStack book, int poolId) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        AbstractSpell proxy = requireProxy(poolId);
        equipSpellbook(player, book);
        armCast(player, proxy);
        proxy.castSpell(player.serverLevel(), 1, player, CastSource.SPELLBOOK, true);
    }

    /**
     * Fire the proxy without equipping anything, so the only way it can find its payload
     * is the main/off-hand fallback in {@code ArsCrossProxySpell.resolveCastingBook}.
     */
    static void castViaArmedProxyWithoutEquipping(ServerPlayer player, int poolId) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        AbstractSpell proxy = requireProxy(poolId);
        armCast(player, proxy);
        proxy.castSpell(player.serverLevel(), 1, player, CastSource.SPELLBOOK, true);
    }

    private static AbstractSpell requireProxy(int poolId) {
        AbstractSpell proxy = ArsCrossProxyRegistry.get(poolId);
        if (proxy == null) {
            throw new IllegalArgumentException("no proxy spell registered for pool id " + poolId);
        }
        return proxy;
    }

    /**
     * Ask Iron's to initiate the cast. Without this the spell's {@code onCast} is reachable
     * but the surrounding cast state (cooldown, casting slot) is never set up, and the test
     * would be measuring something a player can never trigger.
     */
    private static void armCast(ServerPlayer player, AbstractSpell proxy) {
        boolean armed = proxy.attemptInitiateCast(ItemStack.EMPTY, 1, player.serverLevel(),
            player, CastSource.SPELLBOOK, true, Curios.SPELLBOOK_SLOT);
        if (!armed) {
            throw new IllegalStateException("Iron's refused to initiate the proxy cast for "
                + proxy.getSpellId() + "; the test cannot reach onCast");
        }
    }

    /** Put {@code book} in the Curios spellbook slot, failing loudly if the slot is absent. */
    static void equipSpellbook(ServerPlayer player, ItemStack book) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        Utils.setPlayerSpellbookStack(player, book);
        if (!book.isEmpty() && Utils.getPlayerSpellbookStack(player) == null) {
            throw new IllegalStateException("failed to equip the spellbook into the Curios '"
                + Curios.SPELLBOOK_SLOT + "' slot; the curios inventory capability or its slot "
                + "data is missing on this player");
        }
    }

    /**
     * Select wheel slot {@code index} on the equipped {@code book} and let Iron's own
     * server-side entry point start the cast — the closest thing to a real keypress.
     */
    static boolean initiateViaSpellSelection(ServerPlayer player, ItemStack book, int index) {
        if (!IronsCompat.isLoaded()) {
            return false;
        }
        equipSpellbook(player, book);
        MagicData.getPlayerMagicData(player).getSyncedData()
            .setSpellSelection(new SpellSelection(Curios.SPELLBOOK_SLOT, index));
        return Utils.serverSideInitiateCast(player);
    }

    /** True when Iron's recorded no casting item for the in-flight cast. */
    static boolean castingItemIsEmpty(ServerPlayer player) {
        if (!IronsCompat.isLoaded()) {
            return true;
        }
        ItemStack stack = MagicData.getPlayerMagicData(player).getPlayerCastingItem();
        return stack == null || stack.isEmpty();
    }

    /** Index of the {@code ars_cross_<poolId>} proxy in {@code book}'s native container, or -1. */
    static int proxySlotIndex(ItemStack book, int poolId) {
        if (!IronsCompat.isLoaded()) {
            return -1;
        }
        if (!ISpellContainer.isSpellContainer(book)) {
            return -1;
        }
        String wanted = ArsCrossProxyRegistry.spellId(poolId).toString();
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells()) {
            if (slot.getSpell() != null && wanted.equals(slot.getSpell().getSpellId())) {
                return slot.index();
            }
        }
        return -1;
    }

    /**
     * Perform the exact dereference Iron's Inscription Table performs on the scroll slot,
     * and report whether it survives.
     *
     * <p>This is the crash reproduction, spelled the way Iron's spells it:
     * {@code ISpellContainer.get(stack).getSpellAtIndex(0)}. Asserting
     * {@code isSpellContainer(...)} instead would test a <em>different</em> function —
     * that one reads the tag, this one decodes it — and would not catch a container that
     * is present but fails to decode.
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

    /**
     * A scroll carrying the proxy for {@code poolId} in a native container — i.e. exactly what
     * Iron's creative-tab and JEI generators produce for a registered spell.
     */
    static ItemStack makeProxyScroll(int poolId) {
        if (!IronsCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(requireProxy(poolId), 1, scroll);
        return scroll;
    }

    /** A scroll carrying a genuine (non-proxy) Iron's spell, or EMPTY if none is registered. */
    static ItemStack makeNativeScroll() {
        if (!IronsCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            if (spell == SpellRegistry.none()
                || ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
                continue;
            }
            ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
            ISpellContainer.createScrollContainer(spell, spell.getMinLevel(), scroll);
            return scroll;
        }
        return ItemStack.EMPTY;
    }

    /** Add a genuine Iron's spell into {@code book}'s native container, alongside whatever is there. */
    static boolean addNativeSpellToBook(ItemStack book) {
        if (!IronsCompat.isLoaded()) {
            return false;
        }
        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            if (spell == SpellRegistry.none()
                || ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
                continue;
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
        return false;
    }

    /**
     * How many genuine (non-proxy) Iron's spells {@code book}'s native container holds.
     *
     * <p>Counts through the modern container Iron's actually writes, so a coexistence test
     * cannot pass by inspecting the legacy {@code ISB_Spells} key that current books no longer
     * use.
     */
    static int nativeSpellCount(ItemStack book) {
        if (!IronsCompat.isLoaded()) {
            return 0;
        }
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

    static void setIronsMana(ServerPlayer player, float mana) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        MagicData.getPlayerMagicData(player).setMana(mana);
    }

    static void resetCastingState(ServerPlayer player) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        MagicData.getPlayerMagicData(player).resetCastingState();
    }
}
