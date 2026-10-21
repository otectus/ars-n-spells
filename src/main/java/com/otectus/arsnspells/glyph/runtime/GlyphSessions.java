package com.otectus.arsnspells.glyph.runtime;

import com.hollingsworth.arsnouveau.api.ANFakePlayer;
import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.common.entity.EntityProjectileSpell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.casting.QuoteService;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.glyph.IronGlyphBridge;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.casting.PaymentMessages;
import com.otectus.arsnspells.util.CrossCastTrace;
import com.otectus.arsnspells.util.LogPrivacy;
import com.otectus.arsnspells.util.LogThrottle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Admission and lifetime of casts that contain imported glyphs.
 *
 * <h2>Admission (at Ars's cast event, before any effect)</h2>
 * The caster must be a player casting through a player route (not a turret, rune, entity or Ars's
 * fake player). Each imported occurrence's spell must be off its native cooldown (or have a native
 * recast pending) and not already reserved by another cast still in flight. The first execution of
 * every occurrence is priced and reserved together; any shortage cancels the cast event, so Ars
 * neither resolves nor charges the Ars leg. A cast admitted here can still lose its Ars leg at
 * ANS's own reservation right after this event, in which case everything held here is released.
 *
 * <h2>Lifetime</h2>
 * The session id rides in the cast's {@code SpellContext} tag, which Ars copies into every clone,
 * projectile and delayed event. The session stays open while anything carrying it is still pending
 * (Ars projectiles, Ars delays, ANS charges and channels) and closes when the root has finished and
 * nothing is pending, or at the published lifetime bound ({@link #MAX_SESSION_TICKS}). Closing
 * releases every reservation that was never spent; spent ones are never refunded.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class GlyphSessions {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphSessions.class);

    /** The context tag key carrying the session id. */
    public static final String TAG = "ars_n_spells:glyph_session";
    /**
     * Published lifetime bound of a session: 90 seconds, longer than Ars's default projectile
     * lifespan (60 s) plus the longest native charge or channel in the base catalog (Cloud of
     * Regeneration, 10 s). Anything still pending then is cancelled and its unspent reservation
     * released; effects that already happened (a summon, a lingering area) are untouched.
     */
    public static final long MAX_SESSION_TICKS = 1800L;

    private static final Map<UUID, GlyphCastSession> SESSIONS = new LinkedHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, UUID>> COOLDOWN_PENDING = new HashMap<>();
    private static final Map<UUID, UUID> CARRIERS = new HashMap<>();
    private static final Map<SpellContext, UUID> DELAYED = new WeakHashMap<>();
    private static final Map<SpellContext, UUID> ROOTS = new WeakHashMap<>();
    private static final Set<GlyphContinuation> CONTINUATIONS = new HashSet<>();
    private static final Map<UUID, java.util.Deque<OutcomeRecord>> OUTCOMES = new HashMap<>();
    /** Outcomes remembered per player for {@code /ans glyph last} and tests; bounded. */
    public static final int OUTCOME_HISTORY = 16;

    /** One execution's result, kept briefly for diagnostics. */
    public record OutcomeRecord(ResourceLocation spell, int level, GlyphOutcome outcome, String detail, long gameTime) {}

    private GlyphSessions() {
    }

    // ---- Lookup ----

    @Nullable
    public static GlyphCastSession of(SpellContext context) {
        if (context == null || context.tag == null || !context.tag.hasUUID(TAG)) return null;
        GlyphCastSession session = SESSIONS.get(context.tag.getUUID(TAG));
        return session == null || session.closed ? null : session;
    }

    public static List<GlyphCastSession> forPlayer(UUID player) {
        List<GlyphCastSession> out = new ArrayList<>();
        for (GlyphCastSession s : SESSIONS.values()) if (s.owner.equals(player) && !s.closed) out.add(s);
        return out;
    }

    public static int activeCount() {
        return SESSIONS.size();
    }

    /** For the GameTest harness only: bring a player's open sessions to their lifetime bound now. */
    public static void expireForTesting(UUID player) {
        for (GlyphCastSession session : SESSIONS.values()) {
            if (session.owner.equals(player)) session.expiresTick = 0;
        }
    }

    /** The caster as a supported player route, or null (turrets, runes, entities, Ars's fake player). */
    @Nullable
    public static ServerPlayer supportedCaster(SpellContext context) {
        if (context == null) return null;
        LivingEntity caster = context.getUnwrappedCaster();
        if (!(caster instanceof ServerPlayer player) || caster instanceof ANFakePlayer || context.castingTile != null) {
            return null;
        }
        var wrapped = context.getCaster();
        if (wrapped == null) return null;
        var type = wrapped.getCasterType();
        if (type != SpellContext.CasterType.PLAYER && type != SpellContext.CasterType.LIVING_ENTITY) return null;
        return player;
    }

    // ---- Admission ----

    /** One imported occurrence of a recipe, with its effective level. */
    record Occurrence(int index, ImportedIronEffect effect, int effectiveLevel) {}

    static List<Occurrence> occurrences(Spell spell, @Nullable LivingEntity caster) {
        List<Occurrence> out = new ArrayList<>();
        List<AbstractSpellPart> recipe = spell.unsafeList();
        for (int i = 0; i < recipe.size(); i++) {
            if (recipe.get(i) instanceof ImportedIronEffect effect) {
                out.add(new Occurrence(i, effect, effectiveLevel(effect,
                    spell.getBuffsAtIndex(i, caster, AugmentAmplify.INSTANCE),
                    spell.getBuffsAtIndex(i, caster, AugmentDampen.INSTANCE))));
            }
        }
        return out;
    }

    /** Base level, raised by Amplify (at most two levels) and lowered by Dampen, within the catalogued levels. */
    public static int effectiveLevel(ImportedIronEffect effect, int amplify, int dampen) {
        return GlyphArithmetic.effectiveLevel(effect.key().baseLevel(), effect.entry().minLevel(), effect.entry().maxLevel(),
            amplify, dampen);
    }

    /** Runs before other listeners can act on a cast that will be refused anyway. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSpellCast(SpellCastEvent event) {
        if (event.isCanceled() || event.context == null || event.spell == null) return;
        LivingEntity entity = event.context.getUnwrappedCaster();
        if (entity == null || entity.level().isClientSide()) return;
        List<Occurrence> occurrences = occurrences(event.spell, entity);
        if (occurrences.isEmpty()) return;
        if (of(event.context) != null) return; // Already admitted: the same context entered the resolver again.
        ServerPlayer player = supportedCaster(event.context);
        if (player == null) {
            if (LogThrottle.allow(new UUID(0, 0), 5_000))
                LOGGER.info("[ImportedGlyphs] refused a cast with imported glyphs from an unsupported caster ({}); only player casts may run them",
                    event.context.getCaster() == null ? "none" : event.context.getCaster().getCasterType().id);
            event.setCanceled(true);
            return;
        }
        Component refusal = admit(player, event.context, event.spell, occurrences);
        if (refusal != null) {
            player.displayClientMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            for (Occurrence occurrence : occurrences) {
                recordOutcome(player, occurrence.effect().key(), occurrence.effectiveLevel(), GlyphOutcome.REJECTED,
                    "admission: " + detail(refusal));
            }
            event.setCanceled(true);
        }
    }

    /** Admit a root cast. Returns the refusal, or null when admitted (session created and stamped). */
    @Nullable
    static Component admit(ServerPlayer player, SpellContext context, Spell spell, List<Occurrence> occurrences) {
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        if (forPlayer(player.getUUID()).size() >= snapshot.settings().maxActiveSessions()) {
            return Component.translatable("ars_n_spells.glyph.refused.sessions", snapshot.settings().maxActiveSessions());
        }
        IronGlyphBridge bridge = IronGlyphBridges.get();
        Set<ResourceLocation> spells = new java.util.LinkedHashSet<>();
        for (Occurrence occurrence : occurrences) {
            VariantBehavior behavior = snapshot.variant(occurrence.effect().key());
            if (!behavior.available()) {
                return Component.translatable("ars_n_spells.glyph.validation.dormant", occurrence.effect().spellName(),
                    Component.translatable(behavior.dormancy().translationKey()));
            }
            ResourceLocation spellId = occurrence.effect().key().ironSpellId();
            if (spells.add(spellId)) {
                Component nativeRefusal = bridge.nativeRefusal(player, spellId, !bridge.hasPendingRecast(player, spellId));
                if (nativeRefusal != null) return nativeRefusal;
                UUID pendingFor = COOLDOWN_PENDING.getOrDefault(player.getUUID(), Map.of()).get(spellId);
                if (pendingFor != null && SESSIONS.containsKey(pendingFor)) {
                    return Component.translatable("ars_n_spells.glyph.refused.pending", occurrence.effect().spellName());
                }
            }
        }
        CostRules rules = QuoteService.currentRules();
        GlyphCastSession session = new GlyphCastSession(player, spell, rules,
            snapshot.generation(), player.level().getGameTime(), MAX_SESSION_TICKS);
        boolean charges = bridge.chargesMana(player);
        List<GlyphPayments.Reservation> made = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            GlyphCastSession.Token token = new GlyphCastSession.Token(occurrence.index(), occurrence.effect().key());
            if (charges) {
                CostQuote quote = GlyphPayments.quote(player, bridge.manaCost(occurrence.effect().key().ironSpellId(),
                    occurrence.effectiveLevel()), rules);
                GlyphPayments.Shortage[] shortage = new GlyphPayments.Shortage[1];
                GlyphPayments.Reservation reservation = GlyphPayments.reserve(player, quote,
                    "glyph:" + session.id + ":" + occurrence.index(), MAX_SESSION_TICKS + 200, shortage);
                if (reservation == null) {
                    made.forEach(GlyphPayments::release);
                    return shortageMessage(occurrence.effect(), shortage[0]);
                }
                made.add(reservation);
                token.reservation = reservation;
            }
            session.tokens.put(occurrence.index(), token);
        }
        for (ResourceLocation spellId : spells) {
            session.cooldownPending.add(spellId);
            COOLDOWN_PENDING.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(spellId, session.id);
        }
        SESSIONS.put(session.id, session);
        ROOTS.put(context, session.id);
        context.tag.putUUID(TAG, session.id);
        session.record("admitted " + occurrences.size() + " occurrence(s), reserved " + made.size());
        trace(player, session, "admitted", "occurrences", occurrences.size(), "reserved", made.size(), "mode", rules.modeName());
        return null;
    }

    /**
     * A refusal as recorded in the outcome history: its translation key when it has one, so
     * diagnostics and tests read a stable reason whatever language the server resolves.
     */
    static String detail(Component why) {
        return why.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
            ? translatable.getKey() : why.getString();
    }

    static Component shortageMessage(ImportedIronEffect effect, @Nullable GlyphPayments.Shortage shortage) {
        if (shortage == null || !Double.isFinite(shortage.required())) {
            return Component.translatable("message.ars_n_spells.cast.payment_failed");
        }
        Component unit = Component.translatable("ars_n_spells.resource." + shortage.unit().name().toLowerCase(java.util.Locale.ROOT));
        return Component.translatable("message.ars_n_spells.cast.insufficient", unit, effect.spellName(),
            PaymentMessages.amount(shortage.required()), PaymentMessages.amount(Math.max(0, shortage.available())));
    }

    // ---- Root lifecycle hooks (from MixinSpellResolverPreCast / MixinSpellResolverMana) ----

    /** The root's Ars leg was committed: the form launched. */
    public static void rootLaunched(SpellContext context) {
        UUID id = ROOTS.get(context);
        GlyphCastSession session = id == null ? null : SESSIONS.get(id);
        if (session != null) session.launched = true;
    }

    /**
     * A root resolver entry returned. {@code refusedBeforeEffects}: the cast event was cancelled
     * or the Ars leg could not be reserved, so nothing ran and everything is released.
     */
    public static void rootFinished(SpellContext context, boolean refusedBeforeEffects) {
        UUID id = ROOTS.remove(context);
        GlyphCastSession session = id == null ? null : SESSIONS.get(id);
        if (session == null) return;
        if (refusedBeforeEffects && session.emissions == 0 && session.pending == 0) {
            close(session, GlyphOutcome.CANCELED, "root refused before effects");
            return;
        }
        session.rootFinished = true;
        maybeClose(session);
    }

    // ---- Pending work tracking ----

    static void pendingStarted(GlyphCastSession session) {
        session.pending++;
    }

    static void pendingEnded(GlyphCastSession session) {
        session.pending = Math.max(0, session.pending - 1);
        maybeClose(session);
    }

    static void register(GlyphContinuation continuation) {
        CONTINUATIONS.add(continuation);
    }

    static void unregister(GlyphContinuation continuation) {
        CONTINUATIONS.remove(continuation);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityProjectileSpell projectile)) return;
        var resolver = projectile.resolver();
        if (resolver == null) return;
        GlyphCastSession session = of(resolver.spellContext);
        if (session != null && CARRIERS.putIfAbsent(projectile.getUUID(), session.id) == null) {
            pendingStarted(session);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        UUID id = CARRIERS.remove(event.getEntity().getUUID());
        GlyphCastSession session = id == null ? null : SESSIONS.get(id);
        if (session != null) pendingEnded(session);
    }

    /** Ars delays: a resume that ended delayed has more to do later; one that ended undelayed is done. */
    @SubscribeEvent
    public static void onResolvePost(SpellResolveEvent.Post event) {
        SpellContext context = event.context;
        GlyphCastSession session = of(context);
        if (session == null) {
            DELAYED.remove(context);
            return;
        }
        boolean ours = context.getDelayedSpellEvent() instanceof GlyphContinuation;
        if (context.isDelayed() && !ours) {
            if (DELAYED.putIfAbsent(context, session.id) == null) pendingStarted(session);
        } else if (DELAYED.remove(context) != null) {
            pendingEnded(session);
        }
    }

    // ---- Closing ----

    static void maybeClose(GlyphCastSession session) {
        if (!session.closed && session.rootFinished && session.pending <= 0) {
            close(session, session.emissions > 0 ? GlyphOutcome.COMPLETED : GlyphOutcome.CANCELED, "nothing pending");
        }
    }

    static void close(GlyphCastSession session, GlyphOutcome outcome, String why) {
        if (session.closed) return;
        session.closed = true;
        int released = 0;
        for (GlyphCastSession.Token token : session.tokens.values()) {
            if (token.prepaid()) {
                GlyphPayments.release(token.reservation);
                released++;
            }
        }
        Map<ResourceLocation, UUID> pending = COOLDOWN_PENDING.get(session.owner);
        if (pending != null) {
            pending.values().removeIf(session.id::equals);
            if (pending.isEmpty()) COOLDOWN_PENDING.remove(session.owner);
        }
        for (GlyphContinuation continuation : List.copyOf(CONTINUATIONS)) {
            if (continuation.session == session) continuation.cancel("session closed: " + why);
        }
        session.lastOutcome = session.lastOutcome == null ? outcome : session.lastOutcome;
        session.record("closed (" + why + "), released " + released);
        SESSIONS.remove(session.id);
        LOGGER.debug("[ImportedGlyphs] session {} closed: {} emissions, released {} reservation(s) ({})",
            session.id, session.emissions, released, why);
    }

    /** The first execution of {@code spellId} committed its native cooldown: drop the pending mark. */
    static void cooldownCommitted(GlyphCastSession session, ResourceLocation spellId) {
        session.cooldownPending.remove(spellId);
        Map<ResourceLocation, UUID> pending = COOLDOWN_PENDING.get(session.owner);
        if (pending != null && session.id.equals(pending.get(spellId))) {
            pending.remove(spellId);
            if (pending.isEmpty()) COOLDOWN_PENDING.remove(session.owner);
        }
    }

    public static void closeAllFor(UUID player, String why) {
        for (GlyphCastSession session : List.copyOf(SESSIONS.values())) {
            if (session.owner.equals(player)) close(session, GlyphOutcome.CANCELED, why);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (SESSIONS.isEmpty() || server.getTickCount() % 20 != 0) return;
        long now = server.overworld().getGameTime();
        for (GlyphCastSession session : List.copyOf(SESSIONS.values())) {
            if (now >= session.expiresTick) {
                close(session, GlyphOutcome.CANCELED, "lifetime bound reached");
            } else if (session.ownerGone()) {
                close(session, GlyphOutcome.CANCELED, "owner gone");
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            closeAllFor(event.getEntity().getUUID(), "owner logged out");
            OUTCOMES.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) closeAllFor(player.getUUID(), "owner died");
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            for (GlyphContinuation continuation : List.copyOf(CONTINUATIONS)) {
                if (continuation.session.owner.equals(event.getEntity().getUUID())) continuation.cancel("caster changed dimension");
            }
        }
    }

    /** Iron's own rule for long casts: damage interrupts a charge unless the spell or gear says otherwise. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || CONTINUATIONS.isEmpty()) return;
        IronGlyphBridge bridge = IronGlyphBridges.get();
        for (GlyphContinuation continuation : List.copyOf(CONTINUATIONS)) {
            if (continuation.charging() && continuation.session.owner.equals(player.getUUID())
                    && bridge.interruptsCharge(continuation.request.key().ironSpellId(), player, event.getSource())) {
                continuation.cancel("interrupted by damage");
                player.displayClientMessage(Component.translatable("ars_n_spells.glyph.charge_interrupted",
                    continuation.effect.spellName()).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (GlyphCastSession session : List.copyOf(SESSIONS.values())) close(session, GlyphOutcome.CANCELED, "server stopping");
        SESSIONS.clear();
        COOLDOWN_PENDING.clear();
        CARRIERS.clear();
        DELAYED.clear();
        ROOTS.clear();
        CONTINUATIONS.clear();
        OUTCOMES.clear();
    }

    static void recordOutcome(ServerPlayer player, com.otectus.arsnspells.glyph.ImportedGlyphKey key, int level,
                              GlyphOutcome outcome, String detail) {
        java.util.Deque<OutcomeRecord> history = OUTCOMES.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayDeque<>());
        if (history.size() >= OUTCOME_HISTORY) history.removeFirst();
        history.addLast(new OutcomeRecord(key.ironSpellId(), level, outcome, detail, player.level().getGameTime()));
    }

    /** The most recent outcomes for {@code player}, oldest first. */
    public static List<OutcomeRecord> outcomes(UUID player) {
        java.util.Deque<OutcomeRecord> history = OUTCOMES.get(player);
        return history == null ? List.of() : List.copyOf(history);
    }

    public static void clearOutcomes(UUID player) {
        OUTCOMES.remove(player);
    }

    static void trace(Player player, GlyphCastSession session, String stage, Object... kv) {
        Object[] all = new Object[kv.length + 4];
        all[0] = "session";
        all[1] = session.id;
        all[2] = "stage";
        all[3] = stage;
        System.arraycopy(kv, 0, all, 4, kv.length);
        CrossCastTrace.log(session.id, player, CrossCastTrace.Side.S, CrossCastTrace.Stage.GLYPH_INVOCATION, all);
    }

    /** Diagnostics: one line per open session of {@code player}. */
    public static List<String> describe(UUID player) {
        List<String> lines = new ArrayList<>();
        for (GlyphCastSession s : forPlayer(player)) {
            double held = 0;
            for (GlyphPayments.Reservation r : s.heldReservations())
                for (ResourceAmount leg : r.quote().legs()) held += leg.amount();
            lines.add(s.id.toString().substring(0, 8) + " emissions=" + s.emissions + " pending=" + s.pending
                + " held=" + PaymentMessages.amount(held) + " generation=" + s.behaviorGeneration
                + " root=" + (s.rootFinished ? "finished" : "running") + " last=" + s.lastOutcome
                + " owner=" + LogPrivacy.token(s.owner));
        }
        return lines;
    }
}
