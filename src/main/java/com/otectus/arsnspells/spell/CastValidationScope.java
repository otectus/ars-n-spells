package com.otectus.arsnspells.spell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-thread marker for "we are currently inside Iron's
 * {@code AbstractSpell.canBeCastedBy} for this {@code MagicData}", carrying the transform that
 * should be applied to any {@code MagicData.getMana()} read made from inside that window.
 *
 * <p><b>Why this class exists.</b> The cast-gate mana adjustment used to be a {@code @Redirect}
 * onto the {@code INVOKE MagicData.getMana()} instruction inside {@code canBeCastedBy}.
 * Instruction-level injection points are rejected outright by {@code Injector.findTargetNodes}
 * when another mod has {@code @Overwrite}-merged the target method at an equal-or-higher
 * priority:
 *
 * <pre>
 * if (injectorTarget.isMerged()
 *         &amp;&amp; !mixin.getClassName().equals(injectorTarget.getMergedBy())
 *         &amp;&amp; !injectionPoint.checkPriority(injectorTarget.getMergedPriority(), mixin.getPriority())) {
 *     throw new InvalidInjectionException(...);
 * }
 * </pre>
 *
 * <p>That throw happens during the PREPARE phase, before the {@code require} count is ever
 * consulted, so {@code require = 0} cannot soften it. One Mana Bar
 * ({@code milesperhour.one_mana_bar.mixin.IronsAbstractSpellMixin}, priority 1000) overwrites
 * {@code canBeCastedBy}, and on the 1.20.1 line the resulting hard failure took down twelve
 * mods in a user's pack.
 *
 * <p>{@code MethodHead}, {@code BeforeReturn} and {@code BeforeFinalReturn} all override
 * {@code InjectionPoint.checkPriority} to return {@code true}, so {@code @At("HEAD")},
 * {@code @At("RETURN")} and {@code @At("TAIL")} are structurally immune to that failure — at
 * any priority, into any overwritten body. This class is the state that lets the behaviour be
 * expressed with only those injection points.
 *
 * <p><b>Why a non-mixin package.</b> Sponge Mixin forbids a mixin class from referencing its
 * own inner classes, and any state declared on a mixin is merged into the target class — where
 * an un-{@code @Unique} name can collide with the target's own fields or with another mod's
 * mixin. Both the scope state and the throttled logger therefore live here rather than on
 * {@code MixinIronsCastValidation}, following the same precedent as
 * {@link com.otectus.arsnspells.compat.ScrollLPTracker}.
 *
 * <p><b>Keyed on the {@code MagicData} instance</b>, by identity. The consumer is a hook on
 * {@code MagicData.getMana} and knows exactly which instance it is running for, so identity
 * makes the producer/consumer contract exact — a mismatched pair simply produces no adjustment
 * rather than the wrong one. The player UUID is carried alongside for logging only.
 *
 * <p><b>Leak safety.</b> {@code @At("RETURN")} callbacks do not run when the target method
 * exits by throwing, and — because Mixin runs the PREINJECT pass for every mixin on a class
 * before the INJECT pass for any of them — they also do not cover a {@code RETURN} that another
 * mod's cancelling HEAD callback inserts later. A leaked scope would mean a player reading
 * permanently rescaled mana on that thread, so three guards bound it: {@link #push} replaces
 * any stale scope, {@link #apply} ignores a scope belonging to a different {@code MagicData},
 * and every scope self-expires after {@link #MAX_AGE_NANOS}.
 *
 * <p>Only one slot is kept rather than a stack. {@code canBeCastedBy} does not recurse in stock
 * Iron's — {@code AbstractEldritchSpell} reaches it via {@code invokespecial super}, which is a
 * single frame and is not hooked — and a stack would leak a frame permanently if a nested call
 * threw. If some third-party mod does recurse, the inner exit clears the slot and the outer
 * call degrades to unscoped behaviour, which is graceful rather than wrong.
 *
 * <p><b>1.21.1 note.</b> The 1.20.1 original also carried a ring-bypass flag, which let a
 * Cursed/Virtue ring wearer read {@code Float.MAX_VALUE} so Covenant of the Seven's LP/aura
 * listener could take the cost instead. Covenant has no 1.21.1 release, so the flag has no
 * producer and is not carried here; the scope is conversion-only.
 */
public final class CastValidationScope {
    private static final Logger LOGGER = LoggerFactory.getLogger(CastValidationScope.class);

    /**
     * Validity window for a scope. {@code canBeCastedBy} is a handful of field reads and
     * comparisons and always completes well within a single 50ms tick, so a scope still
     * present after that is leaked state rather than a live cast check.
     */
    static final long MAX_AGE_NANOS = 50_000_000L;

    /** Throttle map to avoid info-level log spam on attribute reads. */
    private static final ConcurrentHashMap<UUID, Long> LAST_LOG_MS = new ConcurrentHashMap<>();
    private static final long LOG_THROTTLE_MS = 1000;

    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private CastValidationScope() {
    }

    /**
     * Open a scope on the current thread, replacing any scope already present.
     *
     * @param magicData the {@code MagicData} whose cast is being validated; identity-compared
     * @param playerId  the owning player, for logging only
     * @param rate      ARS_PRIMARY Iron's-to-Ars conversion rate; {@code <= 0} disables scaling
     */
    public static void push(Object magicData, UUID playerId, double rate) {
        push(magicData, playerId, rate, System.nanoTime());
    }

    /** Package-private seam so tests can pin the stamp and exercise expiry. */
    static void push(Object magicData, UUID playerId, double rate, long stampNanos) {
        if (magicData == null) {
            ACTIVE.remove();
            return;
        }
        ACTIVE.set(new Scope(magicData, playerId, rate, stampNanos));
    }

    /**
     * Close the scope. Idempotent — {@code @At("RETURN")} fires once per exit instruction, so
     * this can be called several times for a single call.
     */
    public static void clear() {
        ACTIVE.remove();
    }

    /**
     * Apply the active scope's transform to a mana value.
     *
     * <p>Reproduces the old redirect exactly: the conversion is applied to the value the mana
     * read <em>would otherwise have produced</em>.
     *
     * @return the adjusted value, or {@code value} unchanged when no scope for this
     *         {@code MagicData} is active on this thread
     */
    public static float apply(Object magicData, float value) {
        Scope scope = current(magicData);
        if (scope == null || scope.rate <= 0.0) {
            return value;
        }
        return (float) (value / scope.rate);
    }

    /**
     * Whether a scope for this {@code MagicData} is live on the current thread. Lets a caller
     * skip an otherwise pointless {@code setReturnValue} when nothing would change.
     */
    public static boolean isActive(Object magicData) {
        return current(magicData) != null;
    }

    /** Throttled info logging, at most one line per player per second. */
    public static void throttledLog(UUID playerId, String message, Object... args) {
        if (playerId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_LOG_MS.get(playerId);
        if (last != null && now - last < LOG_THROTTLE_MS) {
            return;
        }
        // ANS-MED-003: opportunistic eviction every 64th call so the throttle map does not
        // grow unbounded across player churn. We only ever look at the most recent timestamp
        // per player, so anything older than 60s is dead state.
        if ((LAST_LOG_MS.size() & 63) == 0) {
            long cutoff = now - 60_000L;
            LAST_LOG_MS.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        LAST_LOG_MS.put(playerId, now);
        LOGGER.info(message, args);
    }

    /** Drop every throttle entry. Server stop, alongside the other per-player stores. */
    public static void clearAll() {
        LAST_LOG_MS.clear();
        ACTIVE.remove();
    }

    private static Scope current(Object magicData) {
        Scope scope = ACTIVE.get();
        if (scope == null || magicData == null || scope.magicData != magicData) {
            return null;
        }
        if (System.nanoTime() - scope.stampNanos > MAX_AGE_NANOS) {
            // Leaked by a canBeCastedBy that exited without reaching our RETURN hook.
            ACTIVE.remove();
            return null;
        }
        return scope;
    }

    private static final class Scope {
        private final Object magicData;
        private final UUID playerId;
        private final double rate;
        private final long stampNanos;

        private Scope(Object magicData, UUID playerId, double rate, long stampNanos) {
            this.magicData = magicData;
            this.playerId = playerId;
            this.rate = rate;
            this.stampNanos = stampNanos;
        }
    }
}
