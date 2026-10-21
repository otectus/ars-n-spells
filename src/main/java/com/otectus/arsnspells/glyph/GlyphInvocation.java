package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.spell.target.ValidatedCastTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/**
 * One execution of an imported glyph, as the common runtime hands it to the Iron's bridge.
 * Immutable; holds the live caster only for the duration of the call.
 *
 * @param caster         the player who owns the session (never a fake player)
 * @param definition     which glyph
 * @param sessionId      the root cast's session
 * @param recipeIndex    the glyph's position in the Ars recipe
 * @param augmentedLevel base level after Amplify and Dampen, before Iron's equipment bonuses
 * @param target         the server's description of the Ars target
 * @param hit            the live Ars hit, as Ars resolved it
 * @param form           the recipe's Ars form
 * @param payment        this execution's payment, redeemed at the effect boundary
 * @param casterTool     the Ars context's caster tool, for Iron's casting-item bookkeeping
 * @param continuation   resumes the rest of the Ars recipe after a charged or channelled cast
 */
public record GlyphInvocation(ServerPlayer caster, ImportedGlyphDefinition definition, UUID sessionId, int recipeIndex,
                              int augmentedLevel, ValidatedCastTarget target, HitResult hit, ResourceLocation form,
                              EmissionPayment payment, ItemStack casterTool, GlyphContinuation continuation) {
    /** Self casts the spell as its caster would; every other form points it at the Ars target. */
    public boolean selfForm() {
        return GlyphFamily.Forms.SELF.equals(form);
    }
}
