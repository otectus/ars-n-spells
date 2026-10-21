package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Where Ars hands an imported glyph its target. Filled in by the invocation runtime. */
public final class ImportedGlyphRuntime {
    private ImportedGlyphRuntime() {}

    public static void resolve(ImportedIronEffect effect, HitResult hit, Level world, LivingEntity shooter, SpellStats stats,
                               SpellContext context, SpellResolver resolver) {
        GlyphInvocations.resolve(effect, hit, world, shooter, stats, context, resolver);
    }
}
