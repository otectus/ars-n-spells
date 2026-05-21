package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.compat.IronsCompat;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
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
            ISpellContainer container = ISpellContainer.get(stack);
            if (container == null) {
                return null;
            }
            SpellData data = container.getSpellAtIndex(0);
            if (data == null) {
                return null;
            }
            AbstractSpell spell = data.getSpell();
            if (spell == null || spell.getSpellId() == null) {
                return null;
            }
            ResourceLocation id = ResourceLocation.tryParse(spell.getSpellId());
            if (id == null) {
                return null;
            }
            return InscriptionSource.irons(id, Math.max(1, data.getLevel()));
        } catch (Exception ignored) {
            // Narrow to Exception (not Throwable) so LinkageError / OutOfMemoryError
            // still propagate — see ANS-MED-020 for the rationale on InscriptionInputs.
            return null;
        }
    }
}
