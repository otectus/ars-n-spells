package com.otectus.arsnspells.events;

import com.otectus.arsnspells.aura.AuraManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Virtue Ring aura consumption for Iron's Spellbooks spells.
 * Mirrors {@link IronsLPHandler} (which does the same for the Cursed Ring's LP).
 *
 * <p>NOT {@code @Mod.EventBusSubscriber} — would auto-load this class (which imports
 * Iron's APIs) on Iron's-less servers and crash at classload. Registered as an instance
 * by {@code ArsNSpells} behind {@code ModList.isLoaded("irons_spellbooks")}.
 */
public class IronsAuraHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsAuraHandler.class);

    private static final Map<UUID, PendingIronsAura> pendingCosts = new ConcurrentHashMap<>();
    private static final long PENDING_TTL_MS = 5000;

    /**
     * Validate aura cost before the Iron's spell casts. If insufficient, cancel and
     * notify the player. Stages a pending aura cost for the on-cast handler to consume.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIronsSpellPreCast(SpellPreCastEvent event) {
        Player player = event.getEntity();
        // ENTRY-POINT TRACE (always-on): proves SpellPreCastEvent reached this handler.
        // If you don't see this line in the log when casting Iron's, the cast never
        // reached the server-side event bus — investigate Iron's client-side gating.
        LOGGER.debug("[IronsAuraHandler] PreCast event received from Iron's (player={}, spell={}, level={}, side={})",
            player == null ? "null" : player.getName().getString(),
            event.getSpellId(), event.getSpellLevel(),
            player == null ? "?" : (player.level().isClientSide() ? "CLIENT" : "SERVER"));

        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isAvailable()) {
            LOGGER.debug("[IronsAuraHandler] PreCast skip: Sanctified Legacy compat not available");
            return;
        }

        if (!AnsConfig.ENABLE_AURA_SYSTEM.get()) {
            LOGGER.debug("[IronsAuraHandler] PreCast skip: enable_aura_system=false");
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
            pendingCosts.remove(player.getUUID());
            // Don't log every cast from non-ring wearers — too noisy.
            return;
        }

        if (player.isCreative()) {
            LOGGER.debug("[IronsAuraHandler] PreCast skip: player {} is creative", player.getName().getString());
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) {
            LOGGER.warn("[IronsAuraHandler] Spell not found in registry: {}", event.getSpellId());
            return;
        }

        int spellLevel = event.getSpellLevel();
        int manaCost = spell.getManaCost(spellLevel);
        if (manaCost <= 0) {
            LOGGER.debug("[IronsAuraHandler] PreCast: zero-cost spell {} — skipping aura charge", event.getSpellId());
            return;
        }

        SpellRarity rarity = spell.getRarity(spellLevel);
        if (rarity == null) {
            LOGGER.warn("[IronsAuraHandler] Null rarity for spell {} level {} - skipping aura cost",
                event.getSpellId(), spellLevel);
            return;
        }

        int auraCost = calculateIronsAuraCost(manaCost, spellLevel, rarity.name());

        // Apply Blasphemy discount
        String spellSchool = mapIronsSchoolToBlasphemy(spell);
        double blasphemyMultiplier = SanctifiedLegacyCompat.getBlasphemyAuraMultiplier(player, spellSchool);
        int discountedCost = auraCost;
        if (blasphemyMultiplier < 1.0) {
            discountedCost = (int) Math.max(AnsConfig.AURA_MINIMUM_COST.get(),
                Math.round(auraCost * blasphemyMultiplier));
        }

        int currentAura = AuraManager.getAura(player);
        int maxAura = AuraManager.getMaxAura(player);
        boolean hasEnough = currentAura >= discountedCost;

        LOGGER.debug("[IronsAuraHandler] PreCast fired: player={}, spell={}, level={}, rarity={}, mana={}, auraCost={}{}, aura={}/{}, sufficient={}",
            player.getName().getString(), event.getSpellId(), spellLevel, rarity.name(),
            manaCost, discountedCost,
            blasphemyMultiplier < 1.0 ? " (blasphemy " + auraCost + "->" + discountedCost + ")" : "",
            currentAura, maxAura, hasEnough);

        if (!hasEnough) {
            event.setCanceled(true);
            pendingCosts.remove(player.getUUID());

            if (AnsConfig.SHOW_AURA_MESSAGES.get()) {
                player.displayClientMessage(
                    Component.translatable("message.ars_n_spells.aura.insufficient", discountedCost, currentAura)
                        .withStyle(ChatFormatting.AQUA),
                    true);
            }
            LOGGER.debug("[IronsAuraHandler] Insufficient aura - cancelled Iron's spell for {} (need {}, have {})",
                player.getName().getString(), discountedCost, currentAura);
            return;
        }

        pendingCosts.put(player.getUUID(),
            new PendingIronsAura(discountedCost, manaCost, System.currentTimeMillis()));
    }

    /**
     * Consume aura when the spell actually casts. Also zeros the Iron's mana cost so
     * the player doesn't pay both.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIronsSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        int manaCostBefore = event.getManaCost();
        LOGGER.debug("[IronsAuraHandler] OnCast event received from Iron's (player={}, spell={}, manaCost={})",
            player == null ? "null" : player.getName().getString(),
            event.getSpellId(), manaCostBefore);

        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }

        if (!AnsConfig.ENABLE_AURA_SYSTEM.get()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
            pendingCosts.remove(player.getUUID());
            return;
        }

        PendingIronsAura pending = pendingCosts.remove(player.getUUID());
        if (pending == null) {
            LOGGER.debug("[IronsAuraHandler] OnCast: no pending aura cost staged for {} (PreCast may have skipped)",
                player.getName().getString());
            return;
        }

        if (System.currentTimeMillis() - pending.timestamp > PENDING_TTL_MS) {
            LOGGER.warn("[IronsAuraHandler] Pending aura cost expired for {}", player.getName().getString());
            return;
        }

        // Prevent Iron's mana consumption when using Virtue Ring
        event.setManaCost(0);

        boolean success = AuraManager.consumeAura(player, pending.auraCost);
        int remaining = AuraManager.getAura(player);

        LOGGER.debug("[IronsAuraHandler] OnCast fired: player={}, spell={}, pending={}, consumed={}, aura={}, manaCostBefore={}, manaCostAfter=0",
            player.getName().getString(), event.getSpellId(), pending.auraCost,
            success, remaining, manaCostBefore);

        if (!success) {
            // Pre-cast validation should have caught this; log for diagnostics.
            LOGGER.warn("[IronsAuraHandler] Aura consumption failed at cast for {} - spell will proceed without payment",
                player.getName().getString());

            if (AnsConfig.SHOW_AURA_MESSAGES.get()) {
                player.displayClientMessage(
                    Component.translatable("message.ars_n_spells.aura.insufficient", pending.auraCost, remaining)
                        .withStyle(ChatFormatting.AQUA),
                    true);
            }
            return;
        }

        if (AnsConfig.SHOW_AURA_MESSAGES.get()) {
            player.displayClientMessage(
                Component.translatable("message.ars_n_spells.aura.consumed", pending.auraCost, remaining)
                    .withStyle(ChatFormatting.AQUA),
                true);
        }
    }

    /**
     * Clean up expired pending costs.
     */
    @SubscribeEvent
    public void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (event.player.tickCount % 100 == 0) {
            long now = System.currentTimeMillis();
            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().timestamp > PENDING_TTL_MS);
        }
    }

    /**
     * Evict per-player state on disconnect.
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        pendingCosts.remove(event.getEntity().getUUID());
    }

    /**
     * Store a pending aura cost for scroll-based casting (used by {@code MixinScrollItem}
     * via {@link com.otectus.arsnspells.compat.ScrollAuraTracker}).
     */
    public static void clearPendingAuraCost(UUID uuid) {
        if (uuid != null) {
            pendingCosts.remove(uuid);
        }
    }

    /**
     * Calculate aura cost for Iron's spells.
     *
     * <p>ANS-HIGH-018: rarity multipliers now use {@code IRONS_AURA_<RARITY>_MULTIPLIER}
     * keys (introduced for this fix) instead of reusing the LP rarity table. The LP rarity
     * scale (defaults 1.0/1.5/2.0/3.0/5.0) was inappropriate for aura because aura regen
     * is much slower than LP regen; at level 10 legendary the old formula produced
     * ~10× the mana cost in aura, which is unspendable against a 1000-cap pool.
     * The new IRONS_AURA_* defaults are gentler (1.0/1.25/1.5/1.75/2.0).
     */
    private static int calculateIronsAuraCost(int manaCost, int spellLevel, String rarity) {
        double baseMultiplier = AnsConfig.AURA_BASE_MULTIPLIER.get();
        double base = manaCost * baseMultiplier;

        double levelMultiplier = AnsConfig.IRONS_LP_PER_LEVEL_MULTIPLIER.get();
        double levelScale = 1.0 + (spellLevel * levelMultiplier);
        base *= levelScale;

        double rarityMultiplier = auraRarityMultiplier(rarity);
        int finalCost = (int) Math.round(base * rarityMultiplier);

        int minimum = AnsConfig.AURA_MINIMUM_COST.get();
        return Math.max(minimum, finalCost);
    }

    private static double auraRarityMultiplier(String rarity) {
        if (rarity == null) {
            return 1.0;
        }
        switch (rarity.toUpperCase()) {
            case "COMMON":     return AnsConfig.IRONS_AURA_COMMON_MULTIPLIER.get();
            case "UNCOMMON":   return AnsConfig.IRONS_AURA_UNCOMMON_MULTIPLIER.get();
            case "RARE":       return AnsConfig.IRONS_AURA_RARE_MULTIPLIER.get();
            case "EPIC":       return AnsConfig.IRONS_AURA_EPIC_MULTIPLIER.get();
            case "LEGENDARY":  return AnsConfig.IRONS_AURA_LEGENDARY_MULTIPLIER.get();
            default:           return 1.0;
        }
    }

    /**
     * Map an Iron's spell to a Blasphemy school string compatible with
     * {@link SanctifiedLegacyCompat#getMatchingBlasphemyType(String)}.
     */
    private static String mapIronsSchoolToBlasphemy(AbstractSpell spell) {
        if (spell == null) {
            return "generic";
        }
        String schoolKey;
        try {
            schoolKey = spell.getSchoolType().getId().getPath();
        } catch (Throwable t) {
            return "generic";
        }
        return schoolKey == null ? "generic" : schoolKey;
    }

    private static class PendingIronsAura {
        final int auraCost;
        final int manaCost;
        final long timestamp;

        PendingIronsAura(int auraCost, int manaCost, long timestamp) {
            this.auraCost = auraCost;
            this.manaCost = manaCost;
            this.timestamp = timestamp;
        }
    }
}
