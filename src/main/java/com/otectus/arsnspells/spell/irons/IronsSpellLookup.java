package com.otectus.arsnspells.spell.irons;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Exact Iron's spell lookup (audit ANS-014). Iron's-isolated: only reached after an
 * {@code IronsCompat.isLoaded()} / {@code ModList} check, never from a common static initializer.
 *
 * <p>At the pinned Iron's 1.21.1-3.16.3, {@code SpellRegistry.getSpell(id)} answers its
 * {@code noneSpell} sentinel for an id it does not know, while {@code SpellRegistry.REGISTRY.get(id)}
 * answers null, but does return the sentinel for its own registered {@code none} id. Neither is
 * evidence that the requested spell exists. This answers the requested spell or null, so the sentinel
 * can never become a cross-cast spell. The saved id is never rewritten, so reinstalling the addon
 * restores the entry.
 */
public final class IronsSpellLookup {
    private IronsSpellLookup() {}

    /** The registered spell with exactly {@code id}; null for an unknown id and for the none sentinel. */
    @Nullable
    public static AbstractSpell lookupExact(@Nullable ResourceLocation id) {
        if (id == null) return null;
        AbstractSpell spell = SpellRegistry.getSpell(id);
        return spell != null && spell != SpellRegistry.none() && id.equals(spell.getSpellResource()) ? spell : null;
    }

    public static boolean resolvable(@Nullable ResourceLocation id) {
        return lookupExact(id) != null;
    }
}
