package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.compat.IronsCompat;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Iron's-importing read path extracted out of {@link InscriptionInputs} so the
 * Iron's API classes are never classloaded on Iron's-less servers.
 *
 * <p>{@code InscriptionInputs} is invoked from {@code SpellUninscriptionRitual},
 * which is registered <em>unconditionally</em> (uninscription is the documented
 * Iron's-uninstall recovery flow). If {@code InscriptionInputs} held Iron's
 * imports directly, the JVM would verify it on first call, hit the Iron's class
 * references, and raise {@code NoClassDefFoundError}. Splitting the Iron's-only
 * code here and gating invocation behind {@link IronsCompat#isLoaded()} keeps the
 * verifier from ever resolving this class on Iron's-less installs.
 */
public final class IronsInscriptionReader {

    private IronsInscriptionReader() {
    }

    /**
     * Attempt to parse an Iron's spell payload from the stack.
     *
     * @return an {@link InscriptionSource} on success, or {@code null} if the stack
     *         does not carry a readable Iron's spell, if Iron's is not loaded, or
     *         if the Iron's API throws.
     */
    public static InscriptionSource tryRead(ItemStack stack) {
        if (!IronsCompat.isLoaded()) {
            return null;
        }
        try {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container == null || container.isEmpty()) {
                return null;
            }
            // The first real native spell in slot order: an empty or "none" slot 0 used to hide a real
            // spell behind it, and on a bound book slot 0 can be an ANS proxy, a placeholder for an
            // Ars inscription rather than a native spell.
            for (io.redspace.ironsspellbooks.api.spells.SpellSlot slot : container.getActiveSpells()) {
                SpellData data = slot == null ? null : slot.spellData();
                AbstractSpell spell = data == null ? null : data.getSpell();
                if (spell == null || spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none() || spell.getSpellId() == null) continue;
                if (com.otectus.arsnspells.spell.CrossModSpellComponents.isArsCrossProxyId(spell.getSpellId())) continue;
                ResourceLocation id = ResourceLocation.tryParse(spell.getSpellId());
                if (id == null || data.getLevel() < 1) continue;
                return InscriptionSource.irons(id, data.getLevel());
            }
            return null;
        } catch (Exception ignored) {
            // Narrow to Exception (not Throwable) so LinkageError / OutOfMemoryError
            // still propagate instead of being silently masked.
            return null;
        }
    }
}
