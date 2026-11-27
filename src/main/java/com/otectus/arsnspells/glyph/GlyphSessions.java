package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every open {@link GlyphCastSession}, server thread only.
 *
 * <p><b>Completion (3.4.1).</b> A session closes as soon as nothing can use it any more: its root
 * cast has returned and no Ars projectile, Ars delay or charged child that carries it is still
 * pending. An instant Touch or Self cast therefore closes when its resolution returns; a projectile
 * cast when its last projectile leaves the world. The lifetime ({@link #lifetimeTicks()}) is only the
 * safety bound. A session is also closed, and whatever it still holds returned, when its root cast
 * fails, when its owner logs out, dies or changes dimension, when imported glyphs are disabled, and
 * when the server stops. Closing never refunds an execution that already ran, and it cancels the
 * session's own charged child in Iron's slot, never an unrelated cast.
 *
 * <p><b>Repeated casts (3.4.3).</b> A projectile that hits nothing flies on until Ars removes it, a
 * minute by default. Its session no longer refuses a later cast of the same Iron's spell: admitting
 * that cast hands back what earlier sessions still hold for the spell ({@link #releaseHoldsFor}), and
 * only a charged or channelled child still refuses it ({@link #childPending}). A delivery that arrives
 * after its session ended by its lifetime bound is told so ({@link #expiredFor}).
 *
 * <p>Sessions are indexed by owner, so per-player checks never scan every session. They are bounded
 * twice: {@code max_active_glyph_sessions_per_player} counts those still holding a reservation or a
 * charged child, and {@link #MAX_SESSIONS_PER_PLAYER} caps every open session, evicting the oldest
 * idle one first.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class GlyphSessions {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphSessions.class);
    /** Context tag key; Ars copies the tag into every child context, projectile and delay. */
    public static final String CONTEXT_TAG = "ars_n_spells:glyph_session";
    /**
     * The shortest lifetime of a session: 60 seconds, longer than any base Iron's cast time or channel.
     * When a session's lifetime runs out, an unredeemed reservation is returned and a late delivery does
     * nothing; since 3.4.3 it is told that its cast expired.
     */
    public static final long LIFETIME_TICKS = 1200L;
    /** 3.4.3: the longest lifetime of a session, 10 minutes, however long Ars lets a projectile fly. */
    public static final long MAX_LIFETIME_TICKS = 12_000L;
    /**
     * 3.4.3: how long a session outlives Ars's longest projectile flight: 15 seconds, room for a charge or
     * channel that a projectile starts at the end of its flight (the longest base one is 10 seconds).
     */
    public static final long LIFETIME_MARGIN_TICKS = 300L;
    public static final int MAX_SESSIONS_PER_PLAYER = 64;

    private static final Map<UUID, GlyphCastSession> SESSIONS = new LinkedHashMap<>();
    /** Open session ids by owner, in opening order. */
    private static final Map<UUID, java.util.LinkedHashSet<UUID>> BY_OWNER = new java.util.HashMap<>();
    /** Ars projectiles carrying a session, by entity id. */
    private static final Map<UUID, UUID> CARRIERS = new java.util.HashMap<>();
    /** Ars contexts delayed (Ars's own Delay) while carrying a session. */
    private static final Map<SpellContext, UUID> DELAYED = new java.util.WeakHashMap<>();
    /** The last imported-glyph outcomes per player, oldest first; bounded, cleared on logout. */
    private static final Map<UUID, java.util.ArrayDeque<CastRecord>> RECORDS = new java.util.HashMap<>();
    public static final int RECORD_HISTORY = 16;
    /** 3.4.3: how many sessions that ended by their lifetime bound (or were evicted) are remembered. */
    static final int EXPIRED_HISTORY = 1024;
    /** 3.4.3: those sessions' owners by session id, oldest first; bounded, cleared on logout and server stop. */
    private static final Map<UUID, UUID> EXPIRED = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, UUID> eldest) {
            return size() > EXPIRED_HISTORY;
        }
    };

    /**
     * One imported-glyph outcome, as {@code /ans glyph last} explains it. Only the spell, its levels,
     * the payment and the stage: nothing that identifies the player or where they were.
     *
     * @param base       the glyph's base level
     * @param augmented  after Amplify and Dampen
     * @param nativeLevel Iron's level for it (equipment, level events), frozen at admission; -1 when not reached
     * @param finalLevel what Iron's cast event settled on; -1 when no effect ran
     * @param paid       the payment legs taken, or why none were
     * @param stage      admission or execution
     */
    public record CastRecord(net.minecraft.resources.ResourceLocation spell, int base, int augmented, int nativeLevel, int finalLevel, String paid,
                             String outcome, String stage, long gameTime) {}

    /** A refusal's translation key when it has one, so records read a stable reason in any language. */
    static String detail(net.minecraft.network.chat.Component why) {
        return why.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
            ? translatable.getKey() : why.getString();
    }

    public static void record(ServerPlayer player, CastRecord record) {
        java.util.ArrayDeque<CastRecord> history = RECORDS.computeIfAbsent(player.getUUID(), ignored -> new java.util.ArrayDeque<>());
        if (history.size() >= RECORD_HISTORY) history.removeFirst();
        history.addLast(record);
    }

    public static List<CastRecord> records(UUID player) {
        java.util.ArrayDeque<CastRecord> history = RECORDS.get(player);
        return history == null ? List.of() : List.copyOf(history);
    }

    private GlyphSessions() {}

    static GlyphCastSession open(ServerPlayer player, GlyphCastSession session) {
        List<GlyphCastSession> mine = forPlayer(player.getUUID());
        if (mine.size() >= MAX_SESSIONS_PER_PLAYER) {
            for (GlyphCastSession old : mine) {
                if (!old.hasPendingWork()) { close(old, "evicted"); break; }
            }
        }
        SESSIONS.put(session.id, session);
        BY_OWNER.computeIfAbsent(session.owner, ignored -> new java.util.LinkedHashSet<>()).add(session.id);
        return session;
    }

    public static void stamp(SpellContext context, GlyphCastSession session) {
        if (context.tag != null) context.tag.putUUID(CONTEXT_TAG, session.id);
    }

    /** The open session a context belongs to, or null. */
    public static GlyphCastSession forContext(SpellContext context) {
        if (context == null) return null;
        CompoundTag tag = context.tag;
        if (tag == null || !tag.hasUUID(CONTEXT_TAG)) return null;
        GlyphCastSession session = SESSIONS.get(tag.getUUID(CONTEXT_TAG));
        return session == null || session.state != GlyphCastSession.State.OPEN ? null : session;
    }

    public static GlyphCastSession byId(UUID id) {
        GlyphCastSession session = SESSIONS.get(id);
        return session == null || session.state != GlyphCastSession.State.OPEN ? null : session;
    }

    public static List<GlyphCastSession> forPlayer(UUID player) {
        java.util.LinkedHashSet<UUID> ids = BY_OWNER.get(player);
        if (ids == null || ids.isEmpty()) return List.of();
        List<GlyphCastSession> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            GlyphCastSession session = SESSIONS.get(id);
            if (session != null && session.state == GlyphCastSession.State.OPEN) out.add(session);
        }
        return out;
    }

    /** Sessions still holding a reservation or a charged child: what the per-player bound counts. */
    public static int activeFor(UUID player) {
        int count = 0;
        for (GlyphCastSession session : forPlayer(player)) if (session.hasPendingWork()) count++;
        return count;
    }

    /**
     * 3.4.3: whether a charged or channelled child of {@code spell} is running in Iron's cast slot, or
     * waiting for it, in one of {@code player}'s casts. Iron's has one cast slot, so that refuses a second
     * cast of the spell; a cast that is only travelling or delayed does not.
     */
    public static boolean childPending(UUID player, net.minecraft.resources.ResourceLocation spell) {
        for (GlyphCastSession session : forPlayer(player)) if (session.childPendingFor(spell)) return true;
        return false;
    }

    /**
     * 3.4.3: a newer cast of {@code spell} is being admitted: hand back what the player's earlier casts still
     * hold for it. Their deliveries stay live. One that still arrives settles its price at the effect
     * boundary like any later execution, and meets the cooldown check there. The money moves exactly as
     * {@link #close} moves it: the reservation is cancelled through the ledger, an LP leg is released, and
     * the occurrence is marked redeemed, so its payment takes the later-execution path.
     */
    static void releaseHoldsFor(ServerPlayer player, net.minecraft.resources.ResourceLocation spell) {
        for (GlyphCastSession session : forPlayer(player.getUUID())) {
            for (GlyphCastSession.Occurrence occurrence : session.occurrences.values()) {
                if (occurrence.childPending || !occurrence.holding() || !occurrence.definition.spell().equals(spell)) continue;
                CastAttempt reservation = occurrence.reservation;
                if (reservation != null && !reservation.state().isTerminal())
                    CastLedger.cancel(reservation, CastLedger.forPlayer(player));
                if (occurrence.lpLeg != null) EmissionPayment.releaseLp(occurrence, session.owner, player, player.getServer());
                occurrence.redeemed = true;
                if (CrossCastTrace.enabled())
                    LOGGER.info("[GlyphSession] session={} owner={} released its hold on {} for a newer cast", session.id,
                        com.otectus.arsnspells.util.LogPrivacy.token(session.owner), spell);
            }
        }
    }

    /**
     * 3.4.3: the lifetime of a session admitted now. Ars removes a projectile that hits nothing after its
     * {@code max_lifespan}, a server setting (60 seconds by default, unbounded); a session lives that long plus
     * {@link #LIFETIME_MARGIN_TICKS}, at least {@link #LIFETIME_TICKS} and at most {@link #MAX_LIFETIME_TICKS}.
     */
    public static long lifetimeTicks() {
        int seconds;
        try {
            seconds = com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile.INSTANCE.getProjectileLifespan();
        } catch (RuntimeException unavailable) {
            seconds = 60; // Ars's own default, as it answers before its config is loaded
        }
        return lifetimeFor(seconds);
    }

    /** {@link #lifetimeTicks()} for an Ars projectile lifespan of {@code projectileSeconds}. */
    public static long lifetimeFor(long projectileSeconds) {
        long flight = Math.max(0L, projectileSeconds) * 20L;
        return Math.min(MAX_LIFETIME_TICKS, Math.max(LIFETIME_TICKS, flight + LIFETIME_MARGIN_TICKS));
    }

    /**
     * 3.4.3: whether {@code context} belongs to a session of {@code player}'s that ended by its lifetime bound
     * (or was evicted as the oldest idle one), so a delivery that arrives now is told its cast expired.
     */
    public static boolean expiredFor(SpellContext context, UUID player) {
        if (context == null || context.tag == null || !context.tag.hasUUID(CONTEXT_TAG)) return false;
        return player.equals(EXPIRED.get(context.tag.getUUID(CONTEXT_TAG)));
    }

    public static EmissionPayment payment(ServerPlayer player, GlyphCastSession session, GlyphCastSession.Occurrence occurrence) {
        return new EmissionPayment(player, session, occurrence);
    }

    /** The root Ars cast spent its own cost: its deliveries are real and may still arrive. */
    public static void rootCommitted(SpellContext context) {
        GlyphCastSession session = forContext(context);
        if (session != null) session.rootCommitted = true;
    }

    /**
     * A resolver entry returned. A root that did not succeed has no deliveries in flight, so its
     * session is closed; executions that already ran keep their effects and their payments.
     */
    public static void rootReturned(SpellContext context, boolean resolved) {
        GlyphCastSession session = forContext(context);
        if (session == null) return;
        if (!resolved && !session.rootCommitted) {
            close(session, "root_refused");
            return;
        }
        session.rootFinished = true;
        maybeClose(session);
    }

    // ---- Pending deliveries --------------------------------------------------------------

    static void pendingStarted(GlyphCastSession session) {
        session.pending++;
    }

    static void pendingEnded(GlyphCastSession session) {
        session.pending = Math.max(0, session.pending - 1);
        maybeClose(session);
    }

    // ---- Queued continuations (3.4.1 review CAST-02) ------------------------------------------

    /**
     * Recipe tails waiting for the next server tick, in queue order. Drained at the end of a server
     * tick, never in the tick they were queued in, and never inside Iron's own cast completion:
     * {@code server.tell(new TickTask(...))} gives no such guarantee.
     */
    private static final java.util.ArrayDeque<GlyphContinuation> QUEUED = new java.util.ArrayDeque<>();

    static void queue(GlyphContinuation continuation) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        continuation.queuedTick = server == null ? 0 : server.getTickCount();
        QUEUED.addLast(continuation);
    }

    /** Continuations waiting to resume, for diagnostics and the leak tests. */
    public static int queuedCount() {
        return QUEUED.size();
    }

    /** {@code player}'s continuations waiting to resume. */
    public static int queuedFor(UUID player) {
        int count = 0;
        for (GlyphContinuation continuation : QUEUED) if (continuation.owner().equals(player)) count++;
        return count;
    }

    private static void drainQueue(MinecraftServer server) {
        if (QUEUED.isEmpty()) return;
        int now = server.getTickCount();
        int budget = QUEUED.size();
        while (budget-- > 0 && !QUEUED.isEmpty()) {
            GlyphContinuation next = QUEUED.peekFirst();
            if (next.queuedTick >= now) break;
            QUEUED.pollFirst();
            try {
                next.runQueued();
            } catch (RuntimeException failure) {
                LOGGER.warn("[Glyph] resuming a held recipe failed: {}", failure.toString());
                LOGGER.debug("[Glyph] resume failure", failure);
                next.cancel();
            }
        }
    }

    static void traceContinuation(UUID owner, UUID session, String stage) {
        if (CrossCastTrace.enabled())
            LOGGER.info("[GlyphSession] session={} owner={} continuation {}", session, com.otectus.arsnspells.util.LogPrivacy.token(owner), stage);
    }

    /** Close a session nothing can use any more: root returned, nothing in flight, no charged child. */
    static void maybeClose(GlyphCastSession session) {
        if (session.state == GlyphCastSession.State.OPEN && session.rootFinished && session.pending <= 0 && !session.childPending())
            close(session, "completed");
    }

    @SubscribeEvent
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (SESSIONS.isEmpty() || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof com.hollingsworth.arsnouveau.common.entity.EntityProjectileSpell projectile)) return;
        var resolver = projectile.spellResolver;
        GlyphCastSession session = resolver == null ? null : forContext(resolver.spellContext);
        if (session != null && CARRIERS.putIfAbsent(projectile.getUUID(), session.id) == null) pendingStarted(session);
    }

    @SubscribeEvent
    public static void onEntityLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (CARRIERS.isEmpty() || event.getLevel().isClientSide()) return;
        UUID id = CARRIERS.remove(event.getEntity().getUUID());
        GlyphCastSession session = id == null ? null : SESSIONS.get(id);
        if (session != null) pendingEnded(session);
    }

    /** Ars delays: a resolution that ended delayed has more to do later; one that ended undelayed is done. */
    @SubscribeEvent
    public static void onResolvePost(com.hollingsworth.arsnouveau.api.event.SpellResolveEvent.Post event) {
        SpellContext context = event.context;
        if (context == null) return;
        GlyphCastSession session = forContext(context);
        if (session == null) {
            DELAYED.remove(context);
            return;
        }
        boolean ours = GlyphContinuation.isHold(context.getDelayedSpellEvent());
        if (context.isDelayed() && !ours) {
            if (DELAYED.putIfAbsent(context, session.id) == null) pendingStarted(session);
        } else if (DELAYED.remove(context) != null) {
            pendingEnded(session);
        } else {
            // A resumed charged-glyph recipe finishing, for instance: nothing else announces it.
            maybeClose(session);
        }
    }

    static void close(GlyphCastSession session, String reason) {
        if (session.state == GlyphCastSession.State.CLOSED) return;
        session.state = GlyphCastSession.State.CLOSED;
        session.closeReason = reason;
        if ("expired".equals(reason) || "evicted".equals(reason)) EXPIRED.put(session.id, session.owner);
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (GlyphCastSession.Occurrence occurrence : session.occurrences.values()) {
            CastAttempt reservation = occurrence.reservation;
            ServerPlayer owner = session.ownerRef.get();
            if (owner == null && server != null) owner = server.getPlayerList().getPlayer(session.owner);
            if (!occurrence.redeemed && reservation != null && !reservation.state().isTerminal()) {
                if (owner != null) CastLedger.cancel(reservation, CastLedger.forPlayer(owner));
                else if (server != null) CastLedger.cancel(reservation, CastLedger.forServer(server));
            }
            // 3.4.1 review PAYMENT-01: an unspent LP leg goes back too.
            if (!occurrence.redeemed && occurrence.lpLeg != null) EmissionPayment.releaseLp(occurrence, session.owner, owner, server);
            occurrence.redeemed = true;
        }
        GlyphChildren.sessionClosed(session);
        for (GlyphContinuation queued : new ArrayList<>(QUEUED)) {
            if (queued.sessionId().equals(session.id)) {
                QUEUED.remove(queued);
                queued.cancel();
            }
        }
        SESSIONS.remove(session.id);
        java.util.LinkedHashSet<UUID> owned = BY_OWNER.get(session.owner);
        if (owned != null) {
            owned.remove(session.id);
            if (owned.isEmpty()) BY_OWNER.remove(session.owner);
        }
        CARRIERS.values().removeIf(session.id::equals);
        DELAYED.values().removeIf(session.id::equals);
        if (CrossCastTrace.enabled())
            LOGGER.info("[GlyphSession] session={} owner={} closed reason={} emissions={}/{}", session.id,
                com.otectus.arsnspells.util.LogPrivacy.token(session.owner), reason, session.budget.used(), session.budget.limit());
    }

    public static void closeAll(UUID player, String reason) {
        for (GlyphCastSession session : forPlayer(player)) close(session, reason);
    }

    public static int openCount() {
        return SESSIONS.size();
    }

    /** Bookkeeping sizes, for diagnostics and the leak tests: sessions, owners, carriers, delays. */
    public static String bookkeeping() {
        return "sessions=" + SESSIONS.size() + " owners=" + BY_OWNER.size() + " carriers=" + CARRIERS.size() + " delayed=" + DELAYED.size()
            + " queued=" + QUEUED.size() + " children=" + GlyphChildren.all().size() + " expired=" + EXPIRED.size();
    }

    /** Close every open session (imported glyphs were disabled). */
    public static void closeEverything(String reason) {
        for (GlyphCastSession session : new ArrayList<>(SESSIONS.values())) close(session, reason);
    }

    /** For the GameTest harness only: bring a player's open sessions to their lifetime bound now. */
    public static void expireForTesting(UUID player) {
        for (GlyphCastSession session : forPlayer(player)) session.expiresTick = 0;
    }

    /**
     * The lifetime is a safety bound, so it is checked once a second rather than every tick; with
     * eager completion the map holds only casts genuinely in flight.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        drainQueue(event.getServer());
        if (SESSIONS.isEmpty() || event.getServer().getTickCount() % 20 != 0) return;
        long now = event.getServer().overworld().getGameTime();
        Iterator<GlyphCastSession> it = new ArrayList<>(SESSIONS.values()).iterator();
        while (it.hasNext()) {
            GlyphCastSession session = it.next();
            if (now >= session.expiresTick) close(session, "expired");
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer) closeAll(event.getEntity().getUUID(), "dimension_change");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            closeAll(event.getEntity().getUUID(), "logout");
            RECORDS.remove(event.getEntity().getUUID());
            EXPIRED.values().removeIf(event.getEntity().getUUID()::equals);
            com.otectus.arsnspells.casting.PaymentTimeline.clear(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) closeAll(player.getUUID(), "death");
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (GlyphCastSession session : new ArrayList<>(SESSIONS.values())) close(session, "server_stopping");
        SESSIONS.clear();
        BY_OWNER.clear();
        CARRIERS.clear();
        DELAYED.clear();
        QUEUED.clear();
        RECORDS.clear();
        EXPIRED.clear();
        com.otectus.arsnspells.casting.PaymentTimeline.clearAll();
    }
}
