package com.otectus.arsnspells.augmentation;

import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Per-player resonance: a spell-damage multiplier derived from how full the mana pool is.
 *
 * <p>The value produced here is multiplied into <em>both</em> damage paths — Iron's via
 * {@code MixinIronsSpellDamage} and Ars via {@code SpellScalingUtil} — so every input and the
 * output must be bounded. All four clamps below exist because of specific audit findings
 * (`ANS-HIGH-006`, `ANS-HIGH-007`) and were missing from the 1.21.1 port:
 *
 * <ul>
 *   <li>{@code manaPercent} is clamped to [0,1]. It is {@code mana / max}, and mana above max
 *       is reachable — Iron's clamps writes down to the {@code max_mana} attribute, so a
 *       ceiling that has drifted leaves the pool above it. Unclamped, the multiplier grows
 *       without bound, and on the Iron's path nothing downstream catches it.</li>
 *   <li>The result is capped at {@code max_damage_multiplier}. Without this the config key is
 *       dead and the ceiling it advertises does not exist.</li>
 *   <li>{@link #setClientResonance} rejects non-finite values and clamps to [0, 100]. A
 *       negative multiplier would heal the target; {@code Math.min(NaN, cap)} returns NaN and
 *       sails straight through {@code SpellScalingUtil}'s own cap.</li>
 * </ul>
 *
 * <p>The arithmetic lives in {@link #resonanceFor} and {@link #clampClientResonance} as pure
 * static functions so it carries unit tests without a Minecraft bootstrap and without Iron's
 * on the test classpath (Iron's is {@code compileOnly}, so it is absent at test runtime).
 */
public class ResonanceManager {
    /** Hard ceiling on any resonance value, independent of config. */
    static final double MAX_RESONANCE = 100.0;

    /** Neutral multiplier: no resonance bonus. */
    static final double NEUTRAL = 1.0;

    // Fixed: Use UUID instead of Player to prevent garbage collection issues
    private static final Map<UUID, Double> resonanceCache = new ConcurrentHashMap<>();

    /**
     * Game time at which each player was last at or above {@code resonance_threshold}, which
     * is what {@code resonance_duration} lingers from. Evicted alongside {@link
     * #resonanceCache} by {@code StateEvictionHandler}; entries are meaningless once the
     * player is gone.
     */
    private static final Map<UUID, Long> lastAboveThreshold = new ConcurrentHashMap<>();

    /** volatile: written from the payload handler, read from the render path. */
    private static volatile double clientResonance = NEUTRAL;

    public static double getResonance(Player player) {
        // flag(), not get(): this is reached from AbstractSpell.getSpellPower, which the
        // client calls while rendering the spell wheel and the inscription table - where the
        // SERVER config may not be loaded and get() throws straight into the render loop.
        if (player == null || !AnsConfig.flag(AnsConfig.ENABLE_RESONANCE_SYSTEM, false)) {
            return NEUTRAL;
        }
        if (player.level().isClientSide()) {
            return clientResonance;
        }
        // Not getOrDefault: the NEUTRAL literal would be autoboxed into a fresh Double on
        // every call, including cache hits. This is reached from AbstractSpell.getSpellPower
        // (per cast, and per tick for channelled spells) and from SpellScalingUtil.
        Double cached = resonanceCache.get(player.getUUID());
        return cached == null ? NEUTRAL : cached;
    }

    public static void setClientResonance(float value) {
        double clamped = clampClientResonance(value, clientResonance);
        clientResonance = clamped;
    }

    /**
     * The value {@link #setClientResonance} should store.
     *
     * <p>A non-finite input keeps {@code previous} rather than poisoning the field: NaN
     * propagates through every later multiplication and defeats {@code Math.min}-style caps,
     * so it must never be stored. Finite values are clamped to [0, {@link #MAX_RESONANCE}].
     */
    static double clampClientResonance(double value, double previous) {
        if (!Double.isFinite(value)) {
            return previous;
        }
        return Math.max(0.0, Math.min(MAX_RESONANCE, value));
    }

    /** {@code mana / max} clamped to the [0,1] it is supposed to be in. */
    static double clampManaPercent(double rawManaPercent) {
        if (!Double.isFinite(rawManaPercent)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, rawManaPercent));
    }

    /**
     * Whether the resonance bonus applies at all right now.
     *
     * <p>The gate and the curve are orthogonal: {@link #resonanceFor} still decides <em>how
     * large</em> the bonus is from how full the pool is, and this decides <em>whether</em> it
     * is granted. Layering them is what lets the two config keys mean something without
     * rewriting the curve every existing server is balanced around.
     *
     * <p>At the default {@code threshold} of 0 this is unconditionally true - any clamped
     * mana fraction is at or above 0 - so the historical always-on behaviour is reproduced
     * exactly, and {@code duration} never comes into play.
     *
     * <p>The linger exists because a raised threshold is otherwise self-defeating: spending
     * mana to cast necessarily drops the pool below the threshold, so the bonus would switch
     * off on the very cast that earned it.
     *
     * @param manaPercent      already clamped to [0,1]
     * @param threshold        {@code resonance_threshold}
     * @param ticksSinceAbove  game ticks since this player was last at or above the
     *                         threshold, or {@link Long#MAX_VALUE} if never
     * @param duration         {@code resonance_duration}, in ticks
     */
    static boolean gateOpen(double manaPercent, double threshold,
                            long ticksSinceAbove, long duration) {
        double safeThreshold = Double.isFinite(threshold)
            ? Math.max(0.0, Math.min(1.0, threshold)) : 0.0;
        if (manaPercent >= safeThreshold) {
            return true;
        }
        if (duration <= 0 || ticksSinceAbove < 0) {
            return false;
        }
        return ticksSinceAbove <= duration;
    }

    /**
     * The resonance multiplier for a pool that is {@code rawManaPercent} full.
     *
     * @param rawManaPercent {@code mana / max}; values outside [0,1] are clamped, not trusted
     * @param strength       {@code resonance_strength}
     * @param cap            {@code max_damage_multiplier}
     */
    static double resonanceFor(double rawManaPercent, double strength, double cap) {
        if (!Double.isFinite(rawManaPercent) || !Double.isFinite(strength)) {
            return NEUTRAL;
        }
        double manaPercent = clampManaPercent(rawManaPercent);
        double safeStrength = Math.max(0.0, strength);
        double effectiveCap = Double.isFinite(cap) ? Math.max(NEUTRAL, cap) : MAX_RESONANCE;
        double resonance = NEUTRAL + (manaPercent * safeStrength * 0.2);
        return Math.min(effectiveCap, Math.min(MAX_RESONANCE, resonance));
    }

    /**
     * Recompute and cache this player's resonance.
     *
     * @return true if the cached value changed, so the caller can skip the sync packet when
     *         it did not. Resonance only moves when the mana fraction moves, which for an
     *         idle player is never - and the sync ran unconditionally once per second per
     *         player, which is a packet per player per second of pure noise.
     */
    public static boolean computeResonance(Player player) {
        try {
            if (player == null || !AnsConfig.flag(AnsConfig.ENABLE_RESONANCE_SYSTEM, false)) {
                return false;
            }
            if (!ModList.get().isLoaded("irons_spellbooks")) {
                return false;
            }
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) {
                return false;
            }
            double maxMana = player.getAttributeValue(AttributeRegistry.MAX_MANA);
            double rawPercent = data.getMana() / Math.max(1.0, maxMana);
            double strength = AnsConfig.RESONANCE_STRENGTH.get();
            double cap = AnsConfig.MAX_DAMAGE_MULTIPLIER.get();
            double threshold = AnsConfig.RESONANCE_THRESHOLD.get();
            long duration = AnsConfig.RESONANCE_DURATION.get();

            UUID uuid = player.getUUID();
            double manaPercent = clampManaPercent(rawPercent);
            long now = player.level().getGameTime();
            if (manaPercent >= Math.max(0.0, Math.min(1.0, threshold))) {
                lastAboveThreshold.put(uuid, now);
            }
            long ticksSinceAbove = ticksSinceAbove(uuid, now);

            double next = gateOpen(manaPercent, threshold, ticksSinceAbove, duration)
                ? resonanceFor(rawPercent, strength, cap)
                : NEUTRAL;
            Double previous = resonanceCache.put(uuid, next);
            return previous == null || previous.doubleValue() != next;
        } catch (Exception e) {
            // Silently fail if Iron's API is unavailable
            return false;
        }
    }

    /**
     * Ticks since {@code uuid} was last at or above the threshold, or {@link Long#MAX_VALUE}
     * when it never has been. Guards against a negative result, which is reachable: game time
     * is per-level, so a dimension change can hand back a smaller value than the one recorded.
     */
    private static long ticksSinceAbove(UUID uuid, long now) {
        Long last = lastAboveThreshold.get(uuid);
        if (last == null) {
            return Long.MAX_VALUE;
        }
        long since = now - last;
        return since < 0 ? Long.MAX_VALUE : since;
    }

    public static void clear(Player player) {
        if (player != null) {
            resonanceCache.remove(player.getUUID());
            lastAboveThreshold.remove(player.getUUID());
        }
    }

    /**
     * Remove cache entries for players not currently online.
     * Call periodically to prevent memory leaks from disconnected players.
     */
    public static void cleanupOfflinePlayers(MinecraftServer server) {
        if (server == null) return;
        Set<UUID> onlineUUIDs = server.getPlayerList().getPlayers().stream()
            .map(p -> p.getUUID())
            .collect(Collectors.toSet());
        resonanceCache.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
        lastAboveThreshold.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    }

    /**
     * Clear all cached resonance values. Call on server stop.
     */
    public static void clearAll() {
        resonanceCache.clear();
        lastAboveThreshold.clear();
        clientResonance = NEUTRAL;
    }
}
