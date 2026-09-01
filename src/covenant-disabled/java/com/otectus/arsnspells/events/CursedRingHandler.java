package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.compat.ScrollLPTracker;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Handles Cursed Ring LP consumption for Ars Nouveau spells.
 * Uses event-based approach instead of mixins for better compatibility.
 *
 * <p>Consumption is split across Pre and Post:
 * <ul>
 *   <li>{@code SpellCostCalcEvent}: stamps pending LP cost, zeros mana cost.</li>
 *   <li>{@code SpellResolveEvent.Pre}: validation-only — re-verifies the player still
 *       wears the Cursed Ring; drops the pending cost if state changed.</li>
 *   <li>{@code SpellResolveEvent.Post}: actually consumes LP. Post only fires for
 *       resolved (non-cancelled) spells — prevents "paid but didn't cast" if another
 *       HIGHEST handler cancels at Pre after ours runs.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class CursedRingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CursedRingHandler.class);

    // ANS-3.0.0: a per-player FIFO deque (not a single slot). Back-to-back casts of
    // delayed-resolution spells (e.g. Ars projectiles whose SpellResolveEvent.Post fires
    // on impact) used to overwrite each other's staged LP, so the first cast paid the
    // second's price and the second went free. Enqueue at cost-calc, poll FIFO at resolve.
    // ConcurrentHashMap + ConcurrentLinkedDeque because handlers can fire from
    // network/tick threads.
    private static final Map<UUID, Deque<PendingLPCost>> pendingCosts = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> ringConflictNotified = ConcurrentHashMap.newKeySet();

    /**
     * Calculate and stamp LP cost when Cursed Ring is equipped.
     * This runs at the cost-calc event, BEFORE the spell resolves.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellCostCalc(SpellCostCalcEvent event) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }

        if (!AnsConfig.ENABLE_LP_SYSTEM.get()) {
            return;
        }

        LivingEntity caster = event.context != null ? event.context.getUnwrappedCaster() : null;
        if (!(caster instanceof Player player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        // Check if wearing Cursed Ring - handle it regardless of mana unification setting
        // This prevents Sanctified Legacy's native (buggy) handling from interfering
        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            // Make sure no stale entry from a previous wearing session can be consumed.
            pendingCosts.remove(player.getUUID());
            return;
        }


        LOGGER.debug("Cursed Ring detected on {} - Spell will use LP instead of mana",
            player.getName().getString());

        int manaCost = event.currentCost;
        if (manaCost <= 0) {
            LOGGER.debug("Zero cost spell - allowing");
            return;
        }

        // ANS-HIGH-003: read the spell directly from the event context instead of a
        // ThreadLocal. The old CasterContext ThreadLocal leaked between casts when
        // canCast threw (the @At("RETURN") clear didn't fire on exception), so a
        // subsequent player's cost-calc could read the previous caster's spell.
        Spell spell = event.context != null ? event.context.getSpell() : null;
        SpellAnalysis.Result analysis = spell != null ? SpellAnalysis.analyze(spell) : null;
        AbstractSpellPart spellPart = analysis != null ? analysis.firstEffect() : null;

        // Calculate LP cost
        int lpCost = SanctifiedLegacyCompat.calculateLPCost(manaCost, spellPart);

        // Apply Blasphemy multiplier
        String spellSchool = analysis != null ? analysis.dominantSchool() : "generic";
        double blasphemyMultiplier = SanctifiedLegacyCompat.getBlasphemyLPMultiplier(player, spellSchool);
        if (blasphemyMultiplier < 1.0) {
            int originalCost = lpCost;
            lpCost = (int) Math.max(AnsConfig.ARS_LP_MINIMUM_COST.get(), Math.round(lpCost * blasphemyMultiplier));
            LOGGER.debug("Blasphemy discount applied: {} LP -> {} LP", originalCost, lpCost);
        }

        LOGGER.debug("Spell will cost {} LP (base mana: {})", lpCost, manaCost);

        // ANS-HIGH-011: stamp with level.getGameTime() (server-global) instead of
        // player.tickCount (per-player), so the periodic sweep can compare ALL
        // entries against a single now-value without mixing player tickCounts.
        // ANS-3.0.0: enqueue (addLast) rather than overwrite.
        pendingCosts.computeIfAbsent(player.getUUID(), k -> new ConcurrentLinkedDeque<>())
            .addLast(new PendingLPCost(lpCost, player.level().getGameTime()));

        // Set mana cost to 0 so Ars Nouveau doesn't consume mana
        event.currentCost = 0;

        LOGGER.debug("Mana cost set to 0 (LP will be consumed on spell resolve)");
    }

    /**
     * Get the pending LP cost for a player if it is still valid.
     * Returns -1 if no pending cost is available.
     */
    public static int getPendingLPCost(Player player) {
        if (player == null) {
            return -1;
        }

        Deque<PendingLPCost> queue = pendingCosts.get(player.getUUID());
        if (queue == null) {
            return -1;
        }
        PendingLPCost pending = queue.peekFirst();
        if (pending == null) {
            return -1;
        }

        if (player.level().getGameTime() - pending.gameTimeStamp > PENDING_COST_TTL_TICKS) {
            // Stale front — drop it so callers see the next valid entry (or none).
            queue.pollFirst();
            return -1;
        }

        return pending.lpCost;
    }

    /**
     * Clear any pending LP cost for the player.
     */
    public static void clearPendingLPCost(Player player) {
        if (player != null) {
            pendingCosts.remove(player.getUUID());
        }
    }

    /**
     * Clear any pending LP cost by UUID (use when only the UUID is available, e.g.
     * curio change events).
     */
    public static void clearPendingLPCost(UUID uuid) {
        if (uuid != null) {
            pendingCosts.remove(uuid);
        }
    }

    /**
     * Pre-resolve gate: re-verify the player still wears the ring. If they don't
     * (e.g. unequipped between cost calc and resolve), drop the pending cost so the
     * Post handler can't consume it.
     *
     * <p>This does NOT consume LP — that happens on Post.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellResolvePre(SpellResolveEvent.Pre event) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }
        if (!AnsConfig.ENABLE_LP_SYSTEM.get()) {
            return;
        }
        if (event.context == null) {
            return;
        }

        LivingEntity caster = event.context.getUnwrappedCaster();
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }

        Deque<PendingLPCost> queue = pendingCosts.get(player.getUUID());
        if (queue == null || queue.isEmpty()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            // Ring came off between cost calc and resolve — drop ALL staged costs for this
            // player so no Post handler can consume them. cost-calc only stages while the
            // ring is worn, so every queued entry predates the removal.
            LOGGER.debug("Cursed Ring no longer equipped on {} between cost calc and resolve - dropping pending costs",
                player.getName().getString());
            pendingCosts.remove(player.getUUID());
        }
    }

    /**
     * Post-resolve commit: spell cast succeeded, charge LP.
     * Post only fires for resolved (non-cancelled) spells — this is the safest place to
     * deduct the resource.
     *
     * <p>If LP consumption fails here (e.g. Blood Magic syphoned LP between canCast and Post),
     * the death-penalty config determines fallback behaviour. The spell has already cast either way.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellResolvePost(SpellResolveEvent.Post event) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }
        if (!AnsConfig.ENABLE_LP_SYSTEM.get()) {
            return;
        }
        if (event.context == null) {
            return;
        }

        LivingEntity caster = event.context.getUnwrappedCaster();
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }

        // FIFO: pull the oldest still-valid staged cost, discarding any expired heads
        // (e.g. a projectile whose Post never fired). An emptied deque is left in place
        // for the periodic sweep to evict — removing it here would race a concurrent
        // cost-calc that re-uses the same deque object and silently drop its entry.
        Deque<PendingLPCost> queue = pendingCosts.get(player.getUUID());
        if (queue == null) {
            return;
        }
        long now = player.level().getGameTime();
        PendingLPCost pending = null;
        PendingLPCost candidate;
        while ((candidate = queue.pollFirst()) != null) {
            if (now - candidate.gameTimeStamp > PENDING_COST_TTL_TICKS) {
                LOGGER.warn("Pending LP cost expired for {}", player.getName().getString());
                continue;
            }
            pending = candidate;
            break;
        }
        if (pending == null) {
            return;
        }

        if (pending.consumed) {
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            LOGGER.debug("Cursed Ring removed before resolve completion on {} - skipping LP consume",
                player.getName().getString());
            return;
        }

        LOGGER.debug("Consuming {} LP from {}'s Soul Network", pending.lpCost, player.getName().getString());

        boolean success = SanctifiedLegacyCompat.consumeLP(player, pending.lpCost);

        if (!success) {
            LOGGER.warn("LP consumption failed at Post for {} (spell already cast)",
                player.getName().getString());

            boolean deathPenalty = AnsConfig.DEATH_ON_INSUFFICIENT_LP.get();

            if (deathPenalty) {
                // Death penalty: spell cast, but kill the player.
                pending.consumed = true;
                // ANS-MED-024: defer player.hurt(MAX_VALUE) to the next server tick.
                // Firing LivingHurtEvent / LivingDeathEvent inside the spell-resolve
                // call stack invites re-entry hazards (a third-party death listener
                // could trigger spell events that re-enter cost-calc with stale state).
                // The deferred call breaks the stack cleanly.
                final ServerPlayer victim = player;
                if (victim.getServer() != null) {
                    victim.getServer().tell(new net.minecraft.server.TickTask(0,
                        () -> victim.hurt(victim.damageSources().magic(), Float.MAX_VALUE)));
                } else {
                    victim.hurt(victim.damageSources().magic(), Float.MAX_VALUE);
                }

                if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                    player.displayClientMessage(
                        Component.translatable("message.ars_n_spells.lp.death", pending.lpCost)
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        true);
                }
            } else {
                // Safe mode at Post: can't cancel any more (spell already cast).
                // Apply minor health penalty silently so the player notices the failure.
                LPDeathPrevention.setLPImmune(player);
                SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);

                if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                    player.displayClientMessage(
                        Component.translatable("message.ars_n_spells.lp.insufficient")
                            .withStyle(ChatFormatting.RED),
                        true);
                }
                LPDeathPrevention.clearLPImmune(player);
            }
            return;
        }

        LOGGER.debug("LP consumed successfully");
        pending.consumed = true;

        if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
            player.displayClientMessage(
                Component.translatable("message.ars_n_spells.lp.consumed", pending.lpCost)
                    .withStyle(ChatFormatting.GOLD),
                true);
        }
    }

    /**
     * Clean up expired pending costs periodically.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }

        if (event.player.tickCount % 100 == 0) { // Every 5 seconds
            // ANS-HIGH-011: sweep against server-global gameTime so a long-uptime player A's
            // sweep does not evict a freshly-joined player B's pending cost (which was stamped
            // from B's own tickCount, which is much lower than A's).
            // ANS-3.0.0: prune expired entries within each deque, then drop empty keys.
            long now = event.player.level().getGameTime();
            pendingCosts.entrySet().removeIf(entry -> {
                entry.getValue().removeIf(c -> now - c.gameTimeStamp > PENDING_COST_TTL_TICKS);
                return entry.getValue().isEmpty();
            });

            // Ring conflict notification
            if (SanctifiedLegacyCompat.isAvailable() && !event.player.level().isClientSide()) {
                if (SanctifiedLegacyCompat.hasBothRings(event.player)) {
                    if (ringConflictNotified.add(event.player.getUUID())) {
                        event.player.displayClientMessage(
                            Component.translatable("message.ars_n_spells.ring_conflict")
                                .withStyle(ChatFormatting.YELLOW),
                            true
                        );
                    }
                } else {
                    ringConflictNotified.remove(event.player.getUUID());
                }
            }
        }
    }

    /**
     * Evict per-player state on disconnect so stale UUIDs don't linger until TTL sweep.
     * Also clears the shared curio-state cache so logins under the same UUID re-scan.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        pendingCosts.remove(id);
        ringConflictNotified.remove(id);
        SanctifiedLegacyCompat.clearCacheFor(id);
        // ANS-HIGH-025: also drain the scroll LP tracker. Without this, a scroll cast
        // whose MixinScrollItem RETURN inject is suppressed by another mod's cancel
        // would leak its staged entry forever.
        ScrollLPTracker.clear(id);
    }

    private static final long PENDING_COST_TTL_TICKS = 100L; // 5 seconds at 20 TPS

    /**
     * Container for pending LP costs.
     *
     * <p>ANS-HIGH-011: {@code gameTimeStamp} (long) replaces the old {@code tickStamp}
     * (int) so the periodic sweep can compare every entry against a single
     * server-global {@code level.getGameTime()} value. The previous design used the
     * casting player's {@code tickCount}, which mixed per-player counters and let a
     * long-uptime player's sweep evict a freshly-joined player's pending cost.
     */
    private static class PendingLPCost {
        final int lpCost;
        final long gameTimeStamp;
        boolean consumed = false;

        PendingLPCost(int lpCost, long gameTimeStamp) {
            this.lpCost = lpCost;
            this.gameTimeStamp = gameTimeStamp;
        }
    }
}
