package com.otectus.arsnspells.glyph.runtime;

import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/**
 * One imported-effect execution, as handed to the Iron's bridge. Immutable.
 *
 * @param hit            where Ars resolved the effect (an entity hit or a block hit)
 * @param selfHit        whether that hit is the caster itself (Ars Self)
 * @param effectiveLevel the base level with Amplify/Dampen applied, already within native bounds
 * @param applyCooldown  whether this execution commits the spell's native cooldown (the first of
 *                       its occurrence; later fan-out executions share that admission)
 * @param speedSteps     Accelerate minus Decelerate on this effect
 */
public record GlyphRequest(ServerPlayer caster, ImportedGlyphKey key, CatalogEntry entry, HitResult hit,
                           boolean selfHit, int effectiveLevel, boolean applyCooldown, int speedSteps,
                           UUID sessionId, UUID invocationId) {
}
