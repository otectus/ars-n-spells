package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
import com.otectus.arsnspells.spell.irons.IronsScrollFactory;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Builds the <em>real</em> Iron's carriers the cross-cast GameTests assert against, and reads
 * their native containers back.
 *
 * <p><b>Why the fixtures moved here.</b> Three tests used to construct a vanilla
 * {@code Items.BOOK} and then assert native carrier behaviour on it. A vanilla book is not a
 * spellbook and not a scroll, so those assertions were being made about a stack the production
 * code has no reason to recognise: the inscription guard waved it through because it is not an
 * Iron's scroll, and the proxy-hiding rule caught it because ANS's own bind had just created
 * the only native container it ever had. Neither outcome said anything about a real carrier.
 * Fixtures built from Iron's own registry items do.
 *
 * <p><b>Held at arm's length</b>, for the same reason as {@link IronsSpellDamageSupport}:
 * NeoForge's GameTest scanner links every {@code @GameTestHolder} class before any test runs,
 * and linking a method body that has to prove an Iron's type assignable to another loads Iron's
 * eagerly - which aborts the whole Iron's-less server before the {@link OptionalModGate} skips
 * can run. Nothing in this class is loaded until a gated test calls it.
 *
 * <p>Iron's-only, and test-only.
 */
final class IronsCarrierSupport {

    private IronsCarrierSupport() {}

    /** Any registered Iron's spellbook item (tier-agnostic), or {@link ItemStack#EMPTY}. */
    static ItemStack spellBook() {
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (IronsBookBindingUtil.isIronsSpellBook(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * A real Iron's spellbook already holding one genuine (non-proxy) Iron's spell - the stack
     * a player who has used the mod normally is carrying.
     *
     * <p>The genuine spell is the point: it is what makes the book a negative control for the
     * proxy-hiding rule, which by contract hides a container only when <em>every</em> active
     * slot is an ANS proxy.
     */
    static ItemStack spellBookWithNativeSpell() {
        ItemStack book = spellBook();
        if (book.isEmpty()) {
            return book;
        }
        AbstractSpell spell = nativeSpell();
        if (spell == null) {
            return ItemStack.EMPTY;
        }
        ISpellContainerMutable mutable = ISpellContainer.getOrCreate(book).mutableCopy();
        if (mutable.getMaxSpellCount() < 1) {
            mutable.setMaxSpellCount(1);
        }
        if (!mutable.addSpellAtIndex(spell, spell.getMinLevel(), 0, false)) {
            return ItemStack.EMPTY;
        }
        ISpellContainer.set(book, mutable.toImmutable());
        return book;
    }

    /**
     * A real ANS scroll carrier: the {@code irons_spellbooks:scroll} item with the valid empty
     * native container {@code IronsScrollFactory} writes, plus one Ars sidecar entry carrying
     * {@code payload}. This is the shape the Spell Loom and the transcription ritual hand out.
     */
    static ItemStack scrollCarrier(CompoundTag payload) {
        ItemStack scroll = BuiltInRegistries.ITEM.getOptional(ArsSpellExportUtil.IRONS_SCROLL_ID)
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);
        if (scroll.isEmpty() || !IronsScrollFactory.initializeCarrierContainer(scroll)) {
            return ItemStack.EMPTY;
        }
        CrossModSpellComponents.addArsEntryWithMeta(scroll,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, payload,
            CrossModSpellComponents.NO_PROXY_POOL_ID, null, null, null);
        return scroll;
    }

    /** True when {@code stack}'s native container still holds any {@code ars_cross_*} slot. */
    static boolean hasAnyProxySlot(ItemStack stack) {
        if (!IronsScrollFactory.hasReadableContainer(stack)) {
            return false;
        }
        for (SpellSlot slot : ISpellContainer.get(stack).getActiveSpells()) {
            SpellData data = slot == null ? null : slot.spellData();
            if (data == null || data.getSpell() == null) {
                continue;
            }
            if (ArsCrossProxyRegistry.poolIdOf(data.getSpell().getSpellResource()) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code stack}'s native container still holds {@code spellId}. */
    static boolean holdsNativeSpell(ItemStack stack, String spellId) {
        if (!IronsScrollFactory.hasReadableContainer(stack)) {
            return false;
        }
        for (SpellSlot slot : ISpellContainer.get(stack).getActiveSpells()) {
            SpellData data = slot == null ? null : slot.spellData();
            if (data != null && data.getSpell() != null
                && spellId.equals(data.getSpell().getSpellId())) {
                return true;
            }
        }
        return false;
    }

    /** The spell id a {@link #spellBookWithNativeSpell()} book carries, or null. */
    static String nativeSpellId() {
        AbstractSpell spell = nativeSpell();
        return spell == null ? null : spell.getSpellId();
    }

    /** A genuine Iron's spell - never one of ANS's {@code ars_cross_*} proxies. */
    private static AbstractSpell nativeSpell() {
        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            if (spell == SpellRegistry.none()
                || ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
                continue;
            }
            return spell;
        }
        return null;
    }
}
