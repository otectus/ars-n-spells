package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * One authorization for every stage of an imported glyph's execution (3.4.1 review CAST-03).
 *
 * <p>Admission checks a cast once, at Ars's cast event. Its work can still run much later: a
 * projectile lands, a Delay expires, a charged child waits for the slot, then releases, a channel
 * pulses, a held recipe tail resumes. Each of those consults this class immediately before it runs,
 * against the policy in force <em>now</em>:
 *
 * <ul>
 *   <li>imported glyphs are still enabled, and the live layer is authoritative;</li>
 *   <li>the glyph is still available (catalogued, supported, its spell registered and enabled);</li>
 *   <li>the Ars-adjusted level is still within Iron's live maximum. The native level frozen at
 *       admission is kept: equipment and level-event bonuses may legitimately take the cast above
 *       the scroll maximum, so only the Ars-adjusted level is bounded;</li>
 *   <li>Iron's learning requirement is still met, and a data pack's Self-only rule still allows the form;</li>
 *   <li>the session is open and owned by this caster, who is alive, connected and in the session's
 *       world;</li>
 *   <li>the target is still in that world, in a loaded chunk inside the border, and alive if it is an
 *       entity. Nothing loads a chunk to run a stale cast;</li>
 *   <li>(audit ANS-001) the resource route admitted is still the route native casting would use, and
 *       an aura route is still one Covenant will charge ({@link GlyphRoute#refusal}).</li>
 * </ul>
 *
 * <p><b>Reload policy.</b> The current policy gates any effect that has not started. An effect that
 * already ran keeps its result and its payment; a channel stops before its next disallowed pulse; a
 * refused stage refunds only what it still holds. A change never reprices accepted work into another
 * pool: a route change is the session's pricing rules, fixed at admission.
 */
public final class GlyphExecutionPolicy {
    private GlyphExecutionPolicy() {}

    /** The authorization result: null when allowed, otherwise the translation key of the refusal. */
    @Nullable
    public static String refusal(ServerPlayer player, GlyphCastSession session, GlyphCastSession.Occurrence occurrence,
                                 Level world, @Nullable HitResult hit, @Nullable ResourceLocation form) {
        String caster = casterRefusal(player, session, world);
        if (caster != null) return caster;
        String policy = policyRefusal(player, occurrence, form);
        if (policy != null) return policy;
        // Audit ANS-001: the admitted resource route must still be the one native casting uses now,
        // and an aura route one Covenant will actually charge, before any Iron's event is posted.
        String route = GlyphRoute.refusal(player, occurrence.admittedRoute);
        if (route != null) return route;
        return hit == null ? null : targetRefusal(world, hit, occurrence.definition.family());
    }

    /** Whether imported glyphs may run at all now. */
    @Nullable
    public static String featureRefusal() {
        if (!AnsConfig.ironGlyphsEnabled()) return "message.ars_n_spells.glyph.dormant.server_disabled";
        if (!GlyphBehavior.live().authoritative()) return "message.ars_n_spells.glyph.refused.config_pending";
        return null;
    }

    /** The glyph's own policy now: availability, live maximum, learning and the Self-only rule. */
    @Nullable
    public static String policyRefusal(ServerPlayer player, GlyphCastSession.Occurrence occurrence, @Nullable ResourceLocation form) {
        String feature = featureRefusal();
        if (feature != null) return feature;
        ImportedGlyphDefinition definition = occurrence.definition;
        GlyphAvailability.Status status = GlyphAvailability.status(definition);
        if (!status.available()) return status.reason().key();
        if (occurrence.augmentedLevel > GlyphBehavior.effectiveMax(definition)) return "message.ars_n_spells.glyph.refused.above_max_now";
        if (!IronGlyphBridges.get().meetsLearning(player, definition.spell())) return "message.ars_n_spells.glyph.requires_irons_learning";
        if (form != null && GlyphMappingSnapshot.current().selfOnly(definition.spell()) && !GlyphFamily.Forms.SELF.equals(form))
            return "message.ars_n_spells.glyph.refused.self_only";
        return null;
    }

    /** The session and its caster: open, owned, alive, connected, and in the session's world. */
    @Nullable
    public static String casterRefusal(ServerPlayer player, GlyphCastSession session, Level world) {
        if (session == null || session.state() != GlyphCastSession.State.OPEN || !session.owner().equals(player.getUUID()))
            return "message.ars_n_spells.glyph.refused.no_session";
        // ANS-002: a session never transfers to a fake caster, even one sharing the owner's UUID.
        if (GlyphCasters.realPlayer(player) == null) return "message.ars_n_spells.glyph.refused.no_session";
        if (player.isRemoved() || !player.isAlive() || player.hasDisconnected()) return "message.ars_n_spells.glyph.refused.no_session";
        if (player.level() != world || !world.dimension().equals(session.dimension())) return "message.ars_n_spells.glyph.refused.other_world";
        return null;
    }

    /**
     * The target now. The movement and summon families act at the hit's place, so they need only
     * that place: in a loaded chunk inside the border; whatever stood there may since have died or
     * gone, and delivery then uses the place it was hit at. The aimed and projectile families act on
     * the entity they hit, which must still be alive, not removed and in this world. Nothing is
     * executed against an entity in another level. The NeoForge build applies the same rule through
     * its catalog's target kinds ({@code position} versus the rest).
     */
    @Nullable
    public static String targetRefusal(Level world, HitResult hit, GlyphFamily family) {
        return family == GlyphFamily.MOVEMENT || family == GlyphFamily.SUMMON ? placeRefusal(world, hit) : targetRefusal(world, hit, true);
    }

    /**
     * An entity hit's entity must still be present: not removed, in this world, in a loaded chunk
     * inside the border, and alive when {@code requireLiving}. A held recipe tail resumes Ars's own
     * parts, which need the entity present but not alive; an imported effect that acts on it needs it
     * alive.
     */
    @Nullable
    public static String targetRefusal(Level world, HitResult hit, boolean requireLiving) {
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity == null || entity.isRemoved() || entity.level() != world) return "message.ars_n_spells.glyph.refused.target_gone";
            if (requireLiving && entity instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive())
                return "message.ars_n_spells.glyph.refused.target_gone";
            BlockPos at = entity.blockPosition();
            return world.isLoaded(at) && world.getWorldBorder().isWithinBounds(at) ? null : "message.ars_n_spells.glyph.refused.target_gone";
        }
        return placeRefusal(world, hit);
    }

    /** The hit's place: a loaded chunk inside the world border. Never loads a chunk to check. */
    @Nullable
    public static String placeRefusal(Level world, HitResult hit) {
        BlockPos at = hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
            ? block.getBlockPos() : BlockPos.containing(hit.getLocation());
        return world.isLoaded(at) && world.getWorldBorder().isWithinBounds(at) ? null : "message.ars_n_spells.glyph.refused.target_gone";
    }
}
