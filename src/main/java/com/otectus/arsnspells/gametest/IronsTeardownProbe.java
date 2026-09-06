package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ModDataComponents;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
import com.otectus.arsnspells.spell.irons.IronsProxySlotWriter;
import com.otectus.arsnspells.spell.irons.IronsScrollFactory;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Iron's-typed half of the V20 teardown assertion, kept out of
 * {@link UninscribeTeardownGameTests} so that class never resolves an Iron's type on an
 * install without Iron's.
 *
 * <p>Same isolation rule as {@code spell.irons.IronsCastSourceAdapter}: the Iron's names live
 * behind a class the caller only reaches through {@link OptionalModGate}, and every method
 * here returns a plain {@link String} so nothing in a signature drags an Iron's class into a
 * scanned holder.
 */
final class IronsTeardownProbe {

    /** Pool id claimed by the ANS entry under test. */
    private static final int POOL_ID = 1;

    private IronsTeardownProbe() {}

    /**
     * Bind an ANS entry onto a real Iron's spellbook that also holds a genuine native spell,
     * run the public full removal, and check what survived.
     *
     * @return {@code null} when the teardown behaved, otherwise the failure to report
     */
    static String fullRemovalLeavesNoAnsState() {
        ItemStack book = realSpellBook();
        if (book.isEmpty()) {
            return "no Iron's spellbook item is registered, so this scenario cannot run";
        }

        // A genuine native spell the player owns, plus an unrelated component. Both must
        // survive: "remove ANS state" is not licence to reset the item.
        AbstractSpell native0 = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        ISpellContainerMutable mutable = ISpellContainer.getOrCreate(book).mutableCopy();
        if (mutable.getMaxSpellCount() < 1) {
            mutable.setMaxSpellCount(1);
        }
        if (!mutable.addSpellAtIndex(native0, 1, 0, false)) {
            return "could not seed the book with a native Iron's spell";
        }
        ISpellContainer.set(book, mutable.toImmutable());
        book.set(DataComponents.CUSTOM_NAME, Component.literal("Player's Book"));

        CompoundTag payload = new CompoundTag();
        payload.putString("recipe", "gametest_spell");
        CrossModSpellComponents.addArsEntryWithMeta(book,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, payload,
            POOL_ID, "Test Spell", "fire", "flame");
        book.set(ModDataComponents.EXPORT_MODE.get(), "irons_scroll_carrier");
        book.set(ModDataComponents.SCHEMA_VERSION.get(), 1);
        if (!IronsProxySlotWriter.addProxySlot(book, POOL_ID, 1)) {
            return "could not seed the book with an ars_cross proxy slot";
        }
        if (ISpellContainer.get(book).getIndexForSpell(ArsCrossProxyRegistry.get(POOL_ID)) < 0) {
            return "fixture did not actually create a selectable proxy slot";
        }

        IronsBookBindingUtil.removeAllArsEntries(book);

        if (CrossModSpellComponents.has(book)) {
            return "the cross-mod spell component survived the full removal";
        }
        if (book.has(ModDataComponents.EXPORT_MODE.get())) {
            return "the export marker survived the full removal";
        }
        if (book.has(ModDataComponents.SCHEMA_VERSION.get())) {
            return "the schema stamp survived the full removal";
        }

        ISpellContainer after = ISpellContainer.get(book);
        if (after == null) {
            return "the native container was destroyed along with the ANS state";
        }
        if (after.getIndexForSpell(ArsCrossProxyRegistry.get(POOL_ID)) >= 0) {
            return "the ars_cross proxy slot is still selectable in the native wheel";
        }
        for (SpellSlot slot : after.getActiveSpells()) {
            AbstractSpell spell = slot.getSpell();
            if (spell != null && ArsCrossProxyRegistry.poolIdOf(
                    net.minecraft.resources.ResourceLocation.tryParse(spell.getSpellId())) >= 0) {
                return "an ars_cross_* entry is still active at index " + slot.index();
            }
        }
        if (after.getIndexForSpell(native0) < 0) {
            return "the player's own native spell was removed by the ANS teardown";
        }
        if (after.getSpellAtIndex(0) == null || after.getSpellAtIndex(0).getLevel() != 1) {
            return "the player's own native spell was re-indexed or re-levelled";
        }
        if (!Component.literal("Player's Book").equals(book.get(DataComponents.CUSTOM_NAME))) {
            return "an unrelated component was stripped by the ANS teardown";
        }
        return null;
    }

    /** The first registered item Iron's itself considers a spellbook. */
    private static ItemStack realSpellBook() {
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (IronsScrollFactory.isSpellBookItem(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
