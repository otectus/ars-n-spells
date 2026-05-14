package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // Track which spells have had LP consumed (to prevent double-consumption).
    // ConcurrentHashMap because event handlers can fire from network/tick threads.
    private static final Map<UUID, PendingLPCost> pendingCosts = new ConcurrentHashMap<>();
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

        // Get first effect glyph for tier calculation from CasterContext
        com.otectus.arsnspells.util.SpellAnalysis.Result analysis = com.otectus.arsnspells.util.CasterContext.getSpell()
            .map(com.otectus.arsnspells.util.SpellAnalysis::analyze)
            .orElse(null);
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

        // Store the LP cost for consumption in the resolve event
        pendingCosts.put(player.getUUID(), new PendingLPCost(lpCost, player.tickCount));

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

        PendingLPCost pending = pendingCosts.get(player.getUUID());
        if (pending == null) {
            return -1;
        }

        if (player.tickCount - pending.tickStamp > PENDING_COST_TTL_TICKS) {
            pendingCosts.remove(player.getUUID());
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

        PendingLPCost pending = pendingCosts.get(player.getUUID());
        if (pending == null) {
            return;
        }

        if (player.tickCount - pending.tickStamp > PENDING_COST_TTL_TICKS) {
            pendingCosts.remove(player.getUUID());
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            LOGGER.debug("Cursed Ring no longer equipped on {} between cost calc and resolve - dropping pending cost",
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

        PendingLPCost pending = pendingCosts.remove(player.getUUID());
        if (pending == null) {
            return;
        }

        if (player.tickCount - pending.tickStamp > PENDING_COST_TTL_TICKS) {
            LOGGER.warn("Pending LP cost expired for {}", player.getName().getString());
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
                player.hurt(player.damageSources().magic(), Float.MAX_VALUE);

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
            int now = event.player.tickCount;
            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().tickStamp > PENDING_COST_TTL_TICKS);

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
    }

    private static final int PENDING_COST_TTL_TICKS = 100; // 5 seconds at 20 TPS

    /**
     * Container for pending LP costs.
     */
    private static class PendingLPCost {
        final int lpCost;
        final int tickStamp;
        boolean consumed = false;

        PendingLPCost(int lpCost, int tickStamp) {
            this.lpCost = lpCost;
            this.tickStamp = tickStamp;
        }
    }
}
