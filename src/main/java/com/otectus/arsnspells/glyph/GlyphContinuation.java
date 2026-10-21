package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.event.DelayedSpellEvent;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * The rest of an Ars recipe, held while a charged or channelled imported glyph runs.
 *
 * <p>Uses Ars's own delay facility, exactly as {@code EffectDelay} does: the resolver's context is
 * marked delayed, which stops its loop after this glyph, and the same resolver is resumed later
 * from the next part, at the same target, as Ars's own delayed spells are. The event is never put
 * on Ars's timed queue; it is held here and released by the charged cast's outcome. Resumption is
 * scheduled for the next server tick so it never runs inside Iron's own cast completion.
 *
 * <p>Resumed at most once; a cancelled continuation is dropped and its context stopped.
 */
public final class GlyphContinuation {
    private final SpellResolver resolver;
    private final HitResult hit;
    private final Level level;
    private DelayedSpellEvent hold;
    private boolean finished;

    public GlyphContinuation(SpellResolver resolver, HitResult hit, Level level) {
        this.resolver = resolver;
        this.hit = hit;
        this.level = level;
    }

    /** Stop the current resolution after this part; nothing has been delayed until this is called. */
    public void suspend() {
        if (hold != null || finished) return;
        if (!resolver.spellContext.hasNextPart()) return;
        hold = new DelayedSpellEvent(Integer.MAX_VALUE, hit, level, resolver);
        resolver.spellContext.delay(hold);
    }

    public boolean suspended() {
        return hold != null;
    }

    /** Resume the remaining parts on the next tick. */
    public void resume(MinecraftServer server) {
        if (finished) return;
        finished = true;
        if (hold == null) return;
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            hold.duration = 0;
            if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity().isRemoved()) {
                resolver.spellContext.stop();
                return;
            }
            resolver.resume(level);
        }));
    }

    /** Drop the remaining parts: the charged cast did not complete. */
    public void cancel() {
        if (finished) return;
        finished = true;
        if (hold != null) {
            hold.duration = 0;
            resolver.spellContext.stop();
        }
    }

    public boolean finished() {
        return finished;
    }
}
