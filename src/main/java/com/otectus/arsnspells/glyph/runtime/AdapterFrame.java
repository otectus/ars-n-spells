package com.otectus.arsnspells.glyph.runtime;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * The context an imported glyph hands Iron's native spell code for exactly one call.
 *
 * <p>While a frame is active on the server thread, and only for its caster:
 * <ul>
 *   <li>Iron's {@code RaycastBuilder} answers with {@link #raycast} instead of a pick along the
 *       caster's look (so pre-cast targeting, area and position spells use the Ars hit);</li>
 *   <li>Iron's {@code Utils.getTargetBlock} answers with {@link #targetBlock};</li>
 *   <li>entities the call adds to the level are collected in {@link #spawned}, so the adapter can
 *       place a native projectile or summon at the Ars hit afterwards.</li>
 * </ul>
 * Nothing else changes: no player is moved or rotated, no fake caster is used, and every other
 * cast of every spell, including native casts by the same player, sees Iron's normal behaviour.
 * Frames nest and are always popped in {@code finally}.
 */
public final class AdapterFrame {

    private static final ThreadLocal<Deque<AdapterFrame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public final Entity caster;
    @Nullable public final HitResult raycast;
    @Nullable public final BlockHitResult targetBlock;
    public final boolean capture;
    public final List<Entity> spawned = new ArrayList<>();

    public AdapterFrame(Entity caster, @Nullable HitResult raycast, @Nullable BlockHitResult targetBlock, boolean capture) {
        this.caster = caster;
        this.raycast = raycast;
        this.targetBlock = targetBlock;
        this.capture = capture;
    }

    @Nullable
    public static AdapterFrame current() {
        return STACK.get().peek();
    }

    /** The active frame for {@code entity}, or null. */
    @Nullable
    public static AdapterFrame forCaster(Entity entity) {
        AdapterFrame frame = current();
        return frame != null && frame.caster == entity ? frame : null;
    }

    public static <T> T call(AdapterFrame frame, Supplier<T> body) {
        Deque<AdapterFrame> stack = STACK.get();
        stack.push(frame);
        try {
            return body.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) STACK.remove();
        }
    }

    public static void run(AdapterFrame frame, Runnable body) {
        call(frame, () -> {
            body.run();
            return null;
        });
    }
}
