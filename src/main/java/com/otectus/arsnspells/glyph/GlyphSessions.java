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
 * <p>A session is closed, and whatever it still holds is returned, when its lifetime
 * ({@link #LIFETIME_TICKS}) ends, when its root cast fails, when its owner logs out or dies, and
 * when the server stops. Closing never refunds an execution that already ran. Sessions are
 * bounded twice: {@code max_active_glyph_sessions_per_player} counts those still holding a
 * reservation or a charged child, and {@link #MAX_SESSIONS_PER_PLAYER} caps every open session,
 * evicting the oldest idle one first.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class GlyphSessions {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphSessions.class);
    /** Context tag key; Ars copies the tag into every child context, projectile and delay. */
    public static final String CONTEXT_TAG = "ars_n_spells:glyph_session";
    /**
     * Published lifetime of a session: 60 seconds. Longer than any base Iron's cast time or
     * channel and than an Ars projectile's flight; afterwards an unredeemed reservation is
     * returned and a late delivery does nothing.
     */
    public static final long LIFETIME_TICKS = 1200L;
    public static final int MAX_SESSIONS_PER_PLAYER = 64;

    private static final Map<UUID, GlyphCastSession> SESSIONS = new LinkedHashMap<>();

    private GlyphSessions() {}

    static GlyphCastSession open(ServerPlayer player, GlyphCastSession session) {
        List<GlyphCastSession> mine = forPlayer(player.getUUID());
        if (mine.size() >= MAX_SESSIONS_PER_PLAYER) {
            for (GlyphCastSession old : mine) {
                if (!old.hasPendingWork()) { close(old, "evicted"); break; }
            }
        }
        SESSIONS.put(session.id, session);
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
        List<GlyphCastSession> out = new ArrayList<>();
        for (GlyphCastSession session : SESSIONS.values())
            if (session.owner.equals(player) && session.state == GlyphCastSession.State.OPEN) out.add(session);
        return out;
    }

    /** Sessions still holding a reservation or a charged child: what the per-player bound counts. */
    public static int activeFor(UUID player) {
        int count = 0;
        for (GlyphCastSession session : forPlayer(player)) if (session.hasPendingWork()) count++;
        return count;
    }

    public static boolean spellPending(UUID player, net.minecraft.resources.ResourceLocation spell) {
        for (GlyphCastSession session : forPlayer(player)) if (session.pendingCooldownFor(spell)) return true;
        return false;
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
        if (!resolved && !session.rootCommitted) close(session, "root_refused");
    }

    static void close(GlyphCastSession session, String reason) {
        if (session.state == GlyphCastSession.State.CLOSED) return;
        session.state = GlyphCastSession.State.CLOSED;
        session.closeReason = reason;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (GlyphCastSession.Occurrence occurrence : session.occurrences.values()) {
            CastAttempt reservation = occurrence.reservation;
            if (!occurrence.redeemed && reservation != null && !reservation.state().isTerminal()) {
                ServerPlayer owner = session.ownerRef.get();
                if (owner == null && server != null) owner = server.getPlayerList().getPlayer(session.owner);
                if (owner != null) CastLedger.cancel(reservation, CastLedger.forPlayer(owner));
                else if (server != null) CastLedger.cancel(reservation, CastLedger.forServer(server));
            }
            occurrence.redeemed = true;
        }
        GlyphChildren.sessionClosed(session);
        SESSIONS.remove(session.id);
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty() || event.getServer() == null) return;
        long now = event.getServer().overworld().getGameTime();
        Iterator<GlyphCastSession> it = new ArrayList<>(SESSIONS.values()).iterator();
        while (it.hasNext()) {
            GlyphCastSession session = it.next();
            if (now >= session.expiresTick) close(session, "expired");
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) closeAll(event.getEntity().getUUID(), "logout");
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) closeAll(player.getUUID(), "death");
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (GlyphCastSession session : new ArrayList<>(SESSIONS.values())) close(session, "server_stopping");
        SESSIONS.clear();
    }
}
