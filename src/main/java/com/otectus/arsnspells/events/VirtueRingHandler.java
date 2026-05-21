package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
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
 * Handles Virtue Ring aura consumption for Ars Nouveau spells.
 *
 * <p>When the Ring of Seven Virtues is equipped, mana costs are replaced with
 * aura costs. The aura itself is owned by Covenant of the Seven — this handler
 * computes the cost we want to charge for the cast, then routes the read and
 * write through {@link SanctifiedLegacyCompat}'s reflection bridges so that
 * Covenant's native green HUD reflects every deduction.
 *
 * <p>Consumption is split across Pre and Post:
 * <ul>
 *   <li>{@code SpellCostCalcEvent}: stamps the pending aura cost and zeros the mana cost.</li>
 *   <li>{@code SpellResolveEvent.Pre}: validation-only — re-verifies the player still wears
 *       the ring; cancels the cast and drops the pending cost if state changed.</li>
 *   <li>{@code SpellResolveEvent.Post}: actually consumes aura, since Post only fires when
 *       the cast succeeded. This prevents "paid but didn't cast" if another HIGHEST handler
 *       cancels at Pre after ours runs.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class VirtueRingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtueRingHandler.class);

    // ConcurrentHashMap because event handlers can fire from network/tick threads.
    private static final Map<UUID, PendingAuraCost> pendingCosts = new ConcurrentHashMap<>();

    /**
     * Calculate aura cost and replace mana cost when Virtue Ring is equipped.
     * Runs at HIGHEST priority to ensure it processes before other cost modifiers.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellCostCalc(SpellCostCalcEvent event) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }

        LivingEntity caster = event.context != null ? event.context.getUnwrappedCaster() : null;
        if (!(caster instanceof Player player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
            // Make sure no stale entry from a previous wearing session can be consumed.
            pendingCosts.remove(player.getUUID());
            return;
        }

        int manaCost = event.currentCost;
        if (manaCost <= 0) {
            return;
        }

        LOGGER.debug("Virtue Ring detected on {} - Spell will use Aura instead of mana",
            player.getName().getString());

        // Ars has no native mapping in Covenant's getAuraCost (which expects an Iron's
        // AbstractSpell). Use the mana cost directly, scaled by the configurable
        // ARS_VIRTUE_AURA_MULTIPLIER knob (default 1.0).
        double multiplier = AnsConfig.ARS_VIRTUE_AURA_MULTIPLIER.get();
        int auraCost = (int) Math.max(1, Math.round(manaCost * multiplier));

        LOGGER.debug("Spell will cost {} aura (base mana: {}, multiplier: {})",
            auraCost, manaCost, multiplier);

        // ANS-HIGH-011: stamp with level.getGameTime() (server-global) instead of
        // player.tickCount (per-player) — see CursedRingHandler for rationale.
        pendingCosts.put(player.getUUID(),
            new PendingAuraCost(auraCost, player.level().getGameTime()));

        // Set mana cost to 0 so Ars Nouveau doesn't consume mana
        event.currentCost = 0;
    }

    /**
     * Get the pending aura cost for a player. Returns -1 if none.
     */
    public static int getPendingAuraCost(Player player) {
        if (player == null) {
            return -1;
        }
        PendingAuraCost pending = pendingCosts.get(player.getUUID());
        if (pending == null) {
            return -1;
        }
        if (player.level().getGameTime() - pending.gameTimeStamp > PENDING_COST_TTL_TICKS) {
            pendingCosts.remove(player.getUUID());
            return -1;
        }
        return pending.auraCost;
    }

    /**
     * Clear any pending aura cost for the player.
     */
    public static void clearPendingAuraCost(Player player) {
        if (player != null) {
            pendingCosts.remove(player.getUUID());
        }
    }

    /**
     * Clear any pending aura cost by UUID (use when only the UUID is available, e.g.
     * curio change events).
     */
    public static void clearPendingAuraCost(UUID uuid) {
        if (uuid != null) {
            pendingCosts.remove(uuid);
        }
    }

    /**
     * Pre-resolve gate: re-verify the player still wears the ring. If they don't
     * (e.g. unequipped between cost calc and resolve), drop the pending cost so the
     * Post handler can't consume it.
     *
     * <p>This does NOT consume aura — that happens on Post.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellResolvePre(SpellResolveEvent.Pre event) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }
        if (event.context == null) {
            return;
        }

        LivingEntity caster = event.context.getUnwrappedCaster();
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }

        PendingAuraCost pending = pendingCosts.get(player.getUUID());
        if (pending == null) {
            return;
        }

        if (player.level().getGameTime() - pending.gameTimeStamp > PENDING_COST_TTL_TICKS) {
            pendingCosts.remove(player.getUUID());
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
            LOGGER.debug("Virtue Ring no longer equipped on {} between cost calc and resolve - dropping pending cost",
                player.getName().getString());
            pendingCosts.remove(player.getUUID());
        }
    }

    /**
     * Post-resolve commit: spell cast succeeded, charge aura.
     * Post only fires for resolved (non-cancelled) spells — this is the safest place to
     * deduct the resource.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellResolvePost(SpellResolveEvent.Post event) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }
        if (event.context == null) {
            return;
        }

        LivingEntity caster = event.context.getUnwrappedCaster();
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }

        PendingAuraCost pending = pendingCosts.remove(player.getUUID());
        if (pending == null) {
            return;
        }

        if (player.level().getGameTime() - pending.gameTimeStamp > PENDING_COST_TTL_TICKS) {
            LOGGER.warn("Pending aura cost expired for {}", player.getName().getString());
            return;
        }

        if (pending.consumed) {
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
            LOGGER.debug("Virtue Ring removed before resolve completion on {} - skipping aura consume",
                player.getName().getString());
            return;
        }

        LOGGER.debug("Consuming {} aura from {}", pending.auraCost, player.getName().getString());

        boolean success = SanctifiedLegacyCompat.consumeCovenantAura(player, pending.auraCost);
        if (!success) {
            // Should be rare — the canCast pre-validation in MixinSpellResolverPreCast already
            // gated this. Log and skip. Note: in "degraded mode" (Covenant reflection unresolved),
            // consumeCovenantAura intentionally returns false; this is logged at startup, not here.
            LOGGER.debug("Aura consumption failed at Post for {} (spell already cast, no payment made)",
                player.getName().getString());

            int currentAura = SanctifiedLegacyCompat.getCovenantAura(player);
            player.displayClientMessage(
                Component.translatable("message.ars_n_spells.aura.insufficient", pending.auraCost, currentAura)
                    .withStyle(ChatFormatting.AQUA),
                true
            );
            return;
        }

        pending.consumed = true;

        int remaining = SanctifiedLegacyCompat.getCovenantAura(player);
        player.displayClientMessage(
            Component.translatable("message.ars_n_spells.aura.consumed", pending.auraCost, remaining)
                .withStyle(ChatFormatting.AQUA),
            true
        );
    }

    /**
     * Clean up expired pending costs.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (event.player.tickCount % 100 == 0) {
            // ANS-HIGH-011: server-global gameTime; see CursedRingHandler for rationale.
            long now = event.player.level().getGameTime();
            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().gameTimeStamp > PENDING_COST_TTL_TICKS);
        }
    }

    /**
     * Evict per-player state on disconnect.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        pendingCosts.remove(id);
        // ScrollAuraTracker cleanup removed: the tracker was deleted along with the
        // Iron's-aura-scroll intercept (Covenant handles Iron's scroll aura natively).
    }

    private static final long PENDING_COST_TTL_TICKS = 100L; // 5 seconds at 20 TPS

    /** ANS-HIGH-011: gameTimeStamp (long, server-global) replaces tickStamp (int, per-player). */
    private static class PendingAuraCost {
        final int auraCost;
        final long gameTimeStamp;
        boolean consumed = false;

        PendingAuraCost(int auraCost, long gameTimeStamp) {
            this.auraCost = auraCost;
            this.gameTimeStamp = gameTimeStamp;
        }
    }
}
