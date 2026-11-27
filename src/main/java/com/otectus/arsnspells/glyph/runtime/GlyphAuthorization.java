package com.otectus.arsnspells.glyph.runtime;

import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * One authorization for every stage of an imported glyph's execution (3.4.1 review CAST-03): the
 * first resolution, a charge's completion and each channel pulse all consult it immediately before
 * they run, against the policy in force <em>now</em>.
 *
 * <ul>
 *   <li>imported glyphs are still enabled and the variant still available (catalogued, supported, its
 *       spell registered and enabled by Iron's);</li>
 *   <li>the Ars-adjusted level is still within Iron's live maximum. The native level fixed for the
 *       occurrence is kept, so equipment and level-event bonuses may still lift a legal cast above
 *       the scroll maximum;</li>
 *   <li>a data pack's Self-only rule and the glyph's target kind still allow the hit; Iron's own
 *       conditions (learning, adventure mode, enabled) still allow the spell;</li>
 *   <li>the session is open and owned by this caster, who is alive, connected and in the session's
 *       world;</li>
 *   <li>the target is still in that world, alive if it is an entity, in a loaded chunk and inside the
 *       world border. Nothing loads a chunk to run a stale cast.</li>
 * </ul>
 *
 * <p><b>Reload policy.</b> The current policy gates any effect that has not started; an effect that
 * ran keeps its result and payment; a channel stops before its next disallowed pulse; a refused stage
 * releases only what it still holds. A route change never reprices accepted work: the session's
 * pricing rules are fixed at admission. This matches the Forge build's {@code GlyphExecutionPolicy}.
 */
public final class GlyphAuthorization {
    private GlyphAuthorization() {}

    /** Null when the stage may run, otherwise why not. */
    @Nullable
    public static Component refusal(ServerPlayer player, GlyphCastSession session, ImportedIronEffect effect, GlyphRequest request,
                                    Level world) {
        Component caster = casterRefusal(player, session, world, effect);
        if (caster != null) return caster;
        Component policy = policyRefusal(player, effect, request.effectiveLevel(), request.hit(), request.selfHit());
        if (policy != null) return policy;
        return targetRefusal(world, request.hit(), effect.entry().target());
    }

    /** The session and its caster: open, owned, alive, connected and in the session's world. */
    @Nullable
    public static Component casterRefusal(ServerPlayer player, @Nullable GlyphCastSession session, Level world, ImportedIronEffect effect) {
        if (session == null || session.closed || !session.owner.equals(player.getUUID())
                || player.isRemoved() || !player.isAlive() || player.hasDisconnected()
                // ANS-002: a session never transfers to a fake caster, even one sharing the owner's UUID.
                || GlyphSessions.isAutomation(player))
            return Component.translatable("ars_n_spells.glyph.refused.not_admitted");
        if (player.level() != world || !world.dimension().equals(session.dimension))
            return Component.translatable("ars_n_spells.glyph.refused.other_world", effect.spellName());
        return null;
    }

    /** The glyph's own policy now: enabled, available, within the live maximum, a hit it accepts, Iron's own conditions. */
    @Nullable
    public static Component policyRefusal(ServerPlayer player, ImportedIronEffect effect, int arsLevel, HitResult hit, boolean selfHit) {
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        if (!snapshot.settings().glyphsEnabled())
            return Component.translatable("ars_n_spells.glyph.validation.dormant", effect.spellName(),
                Component.translatable(Dormancy.FEATURE_DISABLED.translationKey()));
        VariantBehavior behavior = snapshot.variant(effect.key());
        if (!behavior.available())
            return Component.translatable("ars_n_spells.glyph.validation.dormant", effect.spellName(),
                Component.translatable(behavior.dormancy().translationKey()));
        if (GlyphSessions.requestedAboveMax(effect, arsLevel))
            return Component.translatable("ars_n_spells.glyph.refused.above_max_now", effect.spellName());
        Component kind = GlyphRuntime.targetRefusal(effect, effect.entry(), hit, selfHit, snapshot.selfOnly(effect.entry().spellId()));
        if (kind != null) return kind;
        return IronGlyphBridges.get().nativeRefusal(player, effect.key().ironSpellId(), false);
    }

    /**
     * The target now. A glyph that targets a place ({@code position}) needs only that place: in this
     * world, in a loaded chunk, inside the border; whatever stood there may since have died, as a
     * native position spell does not care. Every other glyph acts on the entity it hit, which must
     * still be alive, not removed and in this world. A saved entity hit whose entity was removed is
     * refused, and nothing is executed against an entity in another level.
     */
    @Nullable
    public static Component targetRefusal(Level world, HitResult hit, com.otectus.arsnspells.glyph.catalog.CatalogEntry.TargetKind kind) {
        if (hit instanceof EntityHitResult entityHit && kind != com.otectus.arsnspells.glyph.catalog.CatalogEntry.TargetKind.POSITION) {
            Entity entity = entityHit.getEntity();
            if (entity == null || entity.isRemoved() || entity.level() != world
                    || entity instanceof LivingEntity living && !living.isAlive())
                return Component.translatable("ars_n_spells.glyph.refused.target_gone");
            BlockPos at = entity.blockPosition();
            return world.isLoaded(at) && world.getWorldBorder().isWithinBounds(at) ? null
                : Component.translatable("ars_n_spells.glyph.refused.target_gone");
        }
        BlockPos at = hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
            ? block.getBlockPos() : BlockPos.containing(hit.getLocation());
        return world.isLoaded(at) && world.getWorldBorder().isWithinBounds(at) ? null
            : Component.translatable("ars_n_spells.glyph.refused.target_gone");
    }
}
