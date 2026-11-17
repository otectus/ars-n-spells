package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.compat.IronsCompat;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * ANS-HIGH-002: Iron's-importing read path extracted out of {@link InscriptionInputs}
 * so the Iron's API classes are never classloaded on Iron's-less servers.
 *
 * <p>Previously {@code InscriptionInputs} held three top-level Iron's imports
 * ({@code AbstractSpell}, {@code ISpellContainer}, {@code SpellData}). Because
 * {@code InscriptionInputs} is invoked from {@code SpellUninscriptionRitual}
 * (which is registered <em>unconditionally</em> — uninscription is the documented
 * Iron's-uninstall recovery flow), the JVM would verify {@code InscriptionInputs}
 * on first call, hit the Iron's class references, and raise
 * {@code NoClassDefFoundError} — defeating the recovery flow's whole point.
 *
 * <p>By splitting the Iron's-only code into this dedicated reader and gating
 * invocation behind {@link IronsCompat#isLoaded()}, the JVM verifier never resolves
 * this class on Iron's-less installs.
 *
 * <p>Callers MUST guard via {@link IronsCompat#isLoaded()} before invoking
 * {@link #tryRead(ItemStack)} — we re-check at the helper boundary too, but the
 * caller-side gate is what keeps the JVM from loading this class in the first place.
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
            if (!ISpellContainer.isSpellContainer(stack)) {
                return null;
            }
            ISpellContainer container = ISpellContainer.get(stack);
            if (container == null || container.isEmpty()) {
                return null;
            }
            // The first real native spell in slot order. Slot 0 alone was wrong twice over: an
            // empty or "none" slot 0 hid a real spell behind it, and on a bound book slot 0 can
            // be an ANS proxy, which is a placeholder for an Ars inscription, not a native spell.
            for (SpellSlot slot : container.getActiveSpells()) {
                SpellData data = slot == null ? null : slot.spellData();
                AbstractSpell spell = data == null ? null : data.getSpell();
                if (spell == null || spell == SpellRegistry.none() || spell.getSpellId() == null) continue;
                if (spell instanceof com.otectus.arsnspells.spell.irons.ArsCrossProxySpell
                        || com.otectus.arsnspells.spell.CrossCastNbt.isArsCrossProxyId(spell.getSpellId())) continue;
                ResourceLocation id = ResourceLocation.tryParse(spell.getSpellId());
                if (id == null || data.getLevel() < 1) continue;
                return InscriptionSource.irons(id, data.getLevel());
            }
            return null;
        } catch (Exception ignored) {
            // Narrow to Exception (not Throwable) so LinkageError / OutOfMemoryError
            // still propagate — see ANS-MED-020 for the rationale on InscriptionInputs.
            return null;
        }
    }
}
