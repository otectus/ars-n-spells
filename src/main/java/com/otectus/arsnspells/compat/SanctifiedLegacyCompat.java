package com.otectus.arsnspells.compat;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.util.CuriosUtil;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility layer for Sanctified Legacy (Covenant of the Seven) mod.
 * 
 * Sanctified Legacy adds the Cursed Ring which replaces mana costs with Blood Magic LP.
 * This class detects when the player is wearing the Cursed Ring and applies LP costs
 * to Ars Nouveau spells (which Sanctified Legacy doesn't natively support).
 */
public class SanctifiedLegacyCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(SanctifiedLegacyCompat.class);
    private static final String MOD_ID = "covenant_of_the_seven";
    private static final String ENIGMATIC_LEGACY_MOD_ID = "enigmaticlegacy";
    private static final String BLOOD_MAGIC_MOD_ID = "bloodmagic";

    private static boolean isLoaded = false;
    private static boolean isEnigmaticLegacyLoaded = false;
    private static boolean isBloodMagicLoaded = false;
    private static boolean initialized = false;

    // Pre-built set of all Blasphemy curio IDs for single-scan optimization
    private static final Set<ResourceLocation> BLASPHEMY_IDS = new HashSet<>();
    static {
        String[] types = {
            "fire_blasphemy", "ice_blasphemy", "lightning_blasphemy", "holy_blasphemy",
            "ender_blasphemy", "blood_blasphemy", "evocation_blasphemy", "nature_blasphemy",
            "eldritch_blasphemy", "aqua_blasphemy", "geo_blasphemy", "wind_blasphemy",
            "dormant_blasphemy"
        };
        for (String type : types) {
            BLASPHEMY_IDS.add(new ResourceLocation(MOD_ID, type));
        }
    }

    // Cached ring resource locations — avoid allocating per check.
    // Sets so both Enigmatic Legacy and Covenant of the Seven namespaces resolve to
    // the same ring type when present (they ship equivalent items in some packs).
    private static final Set<ResourceLocation> CURSED_RING_IDS = Set.of(
        new ResourceLocation(ENIGMATIC_LEGACY_MOD_ID, "cursed_ring"),
        new ResourceLocation(MOD_ID, "cursed_ring")
    );
    private static final Set<ResourceLocation> VIRTUE_RING_IDS = Set.of(
        new ResourceLocation(MOD_ID, "virtue_ring")
    );

    // ------------------------------------------------------------------
    // Curio-state cache
    //
    // Scanning the entire curio inventory runs once per spell cast per player
    // for ring/blasphemy detection. We scan once, cache the result for a short
    // TTL, and serve subsequent queries from cache. Entries are evicted on
    // player logout (see clearCacheFor / CursedRingHandler listener).
    // ------------------------------------------------------------------
    private static final int CURIO_CACHE_TTL_TICKS = 20; // 1 second at 20 TPS
    private static final ConcurrentHashMap<UUID, CurioState> CURIO_STATE_CACHE = new ConcurrentHashMap<>();

    /**
     * Snapshot of the Covenant-of-the-Seven / Enigmatic Legacy curio state for
     * a single player. Populated by {@link #scanCurios(Player, long)}.
     */
    private static final class CurioState {
        final boolean cursedRing;
        final boolean virtueRing;
        final Set<ResourceLocation> blasphemies; // immutable
        final long cachedAtTick;

        CurioState(boolean cursedRing, boolean virtueRing, Set<ResourceLocation> blasphemies, long cachedAtTick) {
            this.cursedRing = cursedRing;
            this.virtueRing = virtueRing;
            this.blasphemies = blasphemies;
            this.cachedAtTick = cachedAtTick;
        }
    }

    private static final CurioState EMPTY_STATE = new CurioState(false, false, Collections.emptySet(), Long.MIN_VALUE);

    // Blood Magic reflection cache
    private static Class<?> bloodMagicNetworkClass = null;
    private static java.lang.reflect.Method getSoulNetworkMethod = null;
    private static java.lang.reflect.Method getCurrentEssenceMethod = null;
    private static java.lang.reflect.Method syphonMethod = null;

    // --- Covenant aura bridge ---
    // Path 0 (PREFERRED for reads): ClientResourceData.getCurrentAura() — the same static
    //                               getter Covenant's own HUD reads from. Always available
    //                               on the client when Covenant is loaded; verified by
    //                               `javap -p` on the deployed Covenant jar.
    // Path A: ModUtils.consumeAura / getCurrentAura if Covenant exposes them on ModUtils.
    //         As of Covenant 2.2.6 these do NOT exist on ModUtils; the previous deploy
    //         logged [DEGRADED] because all three name-probes failed. Kept for future
    //         versions in case the API surface shifts.
    // Path B: ModUtils.getVirtuousFraction(Player) × ModUtils.getMaxAura(Player) — also
    //         currently fails because getMaxAura doesn't exist.
    // Path C (degraded fallback): consume returns false (no payment), hasEnough returns
    //                             true (don't block the cast). Visible in the green bar
    //                             not moving when an Ars Nouveau spell is cast.
    private static java.lang.reflect.Method clientResourceDataGetCurrentAuraMethod = null; // path 0
    private static java.lang.reflect.Method covenantConsumeAuraMethod = null;
    private static java.lang.reflect.Method covenantGetCurrentAuraMethod = null;
    private static java.lang.reflect.Method covenantGetMaxAuraMethod = null;
    private static java.lang.reflect.Method covenantGetVirtuousFractionMethod = null;
    private static boolean covenantAuraReflectionResolved = false;

    // --- Nature's Aura bridge ---
    // Covenant's "aura" is not a per-player resource — it's a sample of the world's
    // ambient aura via IAuraChunk.triangulateAuraInArea(level, playerPos, 35). To
    // deduct aura we must drain it from the surrounding chunks via the same public
    // Nature's Aura API. drainAura at the highest-aura spot is the canonical pattern
    // (Aura Cache, Sky Channeler, every Nature's Aura consumer item does this).
    private static java.lang.reflect.Method auraChunkGetAuraChunkMethod = null;
    private static java.lang.reflect.Method auraChunkGetHighestSpotMethod = null;
    private static java.lang.reflect.Method auraChunkTriangulateMethod = null;
    private static java.lang.reflect.Method auraChunkDrainAuraMethod = null;
    private static boolean naturesAuraReflectionResolved = false;

    /**
     * LP Source modes for config.
     */
    public enum LPSourceMode {
        BLOOD_MAGIC_PRIORITY,  // Use Blood Magic if available, fall back to health
        HEALTH_ONLY,           // Always use health
        BLOOD_MAGIC_ONLY       // Only use Blood Magic, fail if not installed
    }

    /**
     * Initialize the compatibility layer.
     * Detects Blood Magic for Soul Network support, with health fallback.
     */
    public static void init() {
        if (initialized) {
            return;
        }

        isLoaded = ModList.get().isLoaded(MOD_ID);
        isEnigmaticLegacyLoaded = ModList.get().isLoaded(ENIGMATIC_LEGACY_MOD_ID);
        isBloodMagicLoaded = ModList.get().isLoaded(BLOOD_MAGIC_MOD_ID);

        if (isLoaded || isEnigmaticLegacyLoaded) {
            LOGGER.info("Initializing Sanctified Legacy compatibility...");
            LOGGER.info("  Covenant of the Seven loaded: {}", isLoaded);
            LOGGER.info("  Enigmatic Legacy loaded: {}", isEnigmaticLegacyLoaded);
            LOGGER.info("  Blood Magic loaded: {}", isBloodMagicLoaded);
            LOGGER.info("  Using Curios API for curio detection");

            if (isBloodMagicLoaded) {
                initBloodMagicReflection();
            }

            if (isLoaded) {
                initCovenantAuraReflection();
                initNaturesAuraReflection();
            }

            // Warn if LP_SOURCE_MODE is BLOOD_MAGIC_ONLY but Blood Magic isn't installed
            LPSourceMode lpMode = getLPSourceMode();
            if (lpMode == LPSourceMode.BLOOD_MAGIC_ONLY && !isBloodMagicLoaded) {
                LOGGER.warn("  LP_SOURCE_MODE is BLOOD_MAGIC_ONLY but Blood Magic is not installed!");
                LOGGER.warn("  Cursed Ring LP costs will ALWAYS fail. Change to BLOOD_MAGIC_PRIORITY or HEALTH_ONLY.");
            }

            LOGGER.info("Sanctified Legacy compatibility layer initialized successfully");
        } else {
            LOGGER.info("Sanctified Legacy compatibility skipped: covenant_of_the_seven={}, enigmaticlegacy={}",
                isLoaded, isEnigmaticLegacyLoaded);
        }

        initialized = true;
    }

    /**
     * Initialize Blood Magic reflection for Soul Network access.
     */
    private static void initBloodMagicReflection() {
        try {
            // Try to find Blood Magic's NetworkHelper class
            bloodMagicNetworkClass = Class.forName("wayoftime.bloodmagic.util.helper.NetworkHelper");
            getSoulNetworkMethod = bloodMagicNetworkClass.getMethod("getSoulNetwork", java.util.UUID.class);

            // Get SoulNetwork class methods
            Class<?> soulNetworkClass = Class.forName("wayoftime.bloodmagic.core.data.SoulNetwork");
            getCurrentEssenceMethod = soulNetworkClass.getMethod("getCurrentEssence");
            syphonMethod = soulNetworkClass.getMethod("syphon", int.class);

            LOGGER.info("  [OK] Blood Magic Soul Network API initialized via reflection");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("  [WARN] Blood Magic classes not found - will use health fallback");
            isBloodMagicLoaded = false;
        } catch (NoSuchMethodException e) {
            LOGGER.warn("  [WARN] Blood Magic API methods not found - will use health fallback");
            isBloodMagicLoaded = false;
        } catch (Exception e) {
            LOGGER.error("  [FAIL] Failed to initialize Blood Magic reflection", e);
            isBloodMagicLoaded = false;
        }
    }

    /**
     * Check if Blood Magic is available for LP consumption.
     */
    public static boolean isBloodMagicAvailable() {
        return isBloodMagicLoaded && bloodMagicNetworkClass != null;
    }

    /**
     * Resolve Covenant of the Seven's public aura API at startup. We name-probe
     * several plausible method names so we survive small refactors on Covenant's
     * side; missing methods leave the field null and the caller falls back to a
     * lower-fidelity path.
     *
     * <p>The result is logged once so the user can tell which path resolved.
     * "Degraded" (neither consume nor getCurrent resolved) means Virtue Ring
     * casts will succeed but won't deduct aura — the green HUD won't move. Bring
     * the log back if that happens.
     */
    private static void initCovenantAuraReflection() {
        try {
            // Path 0: ClientResourceData.getCurrentAura() — the canonical read path on the
            // client. Verified by `javap -p` on Covenant 2.2.6-hotfix:
            //   public static int getCurrentAura()
            // No Player arg; reads a static field that Covenant's own HUD also reads.
            try {
                Class<?> crd = Class.forName("net.llenzzz.covenant_of_the_seven.client.ClientResourceData");
                clientResourceDataGetCurrentAuraMethod = tryGetStatic(crd, "getCurrentAura");
            } catch (ClassNotFoundException e) {
                // No client-side data class (e.g. dedicated-server context) — leave null.
            }

            // Paths A/B: ModUtils name-probes. Kept as belt-and-suspenders. As of Covenant
            // 2.2.6 the consume/getCurrentAura/getMaxAura methods do not exist on ModUtils;
            // only getVirtuousFraction(Player) resolves. Verified via deployment log:
            // "[DEGRADED] consume=false, getCurrent=false, getMax=false, fraction=true".
            Class<?> modUtils = Class.forName("net.llenzzz.covenant_of_the_seven.util.ModUtils");
            covenantGetVirtuousFractionMethod = tryGetStatic(modUtils, "getVirtuousFraction", Player.class);
            for (String name : new String[]{"consumeAura", "drainAura", "spendAura", "tryConsumeAura"}) {
                covenantConsumeAuraMethod = tryGetStatic(modUtils, name, Player.class, int.class);
                if (covenantConsumeAuraMethod != null) break;
            }
            for (String name : new String[]{"getCurrentAura", "getAura", "getStoredAura", "getVirtuousAmount"}) {
                covenantGetCurrentAuraMethod = tryGetStatic(modUtils, name, Player.class);
                if (covenantGetCurrentAuraMethod != null) break;
            }
            for (String name : new String[]{"getMaxAura", "getMaxStoredAura", "getMaxVirtuousAmount"}) {
                covenantGetMaxAuraMethod = tryGetStatic(modUtils, name, Player.class);
                if (covenantGetMaxAuraMethod != null) break;
            }

            covenantAuraReflectionResolved = true;
            boolean haveCRD = clientResourceDataGetCurrentAuraMethod != null;
            boolean haveConsume = covenantConsumeAuraMethod != null;
            boolean haveGetCurrent = covenantGetCurrentAuraMethod != null;
            boolean haveGetMax = covenantGetMaxAuraMethod != null;
            boolean haveFraction = covenantGetVirtuousFractionMethod != null;
            boolean canRead = haveCRD || haveGetCurrent || (haveFraction && haveGetMax);
            if (canRead) {
                LOGGER.info("  [OK] Covenant aura reflection initialized — clientResourceData={}, consume={}, getCurrent={}, getMax={}, fraction={}",
                    haveCRD, haveConsume, haveGetCurrent, haveGetMax, haveFraction);
            } else {
                LOGGER.error("  [DEGRADED] Covenant aura reflection partial — clientResourceData={}, consume={}, getCurrent={}, getMax={}, fraction={}",
                    haveCRD, haveConsume, haveGetCurrent, haveGetMax, haveFraction);
                LOGGER.error("  [DEGRADED] No read path resolved — peak tracker will stay at 1 and the bar will look broken.");
            }
            if (!haveConsume) {
                LOGGER.warn("  [WARN] Covenant has no public consumeAura helper; Ars-spell aura deduction will rely on Covenant's own SpellPreCastEvent handling.");
            }
        } catch (Throwable t) {
            covenantAuraReflectionResolved = false;
            LOGGER.error("  [FAIL] Covenant aura reflection failed entirely — Virtue Ring Ars casts will skip aura payment", t);
        }
    }

    /**
     * Look up a static method by name + parameter types; return null on miss.
     * Varargs allows zero-arg lookups for static getters (e.g. ClientResourceData.getCurrentAura).
     */
    private static java.lang.reflect.Method tryGetStatic(Class<?> owner, String name, Class<?>... params) {
        try {
            java.lang.reflect.Method m = owner.getMethod(name, params);
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                return m;
            }
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Look up an instance method by name + parameter types; return null on miss. */
    private static java.lang.reflect.Method tryGetInstance(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Resolve Nature's Aura's IAuraChunk API at startup. This is the actual write path
     * for "consuming Covenant aura" — Covenant samples ambient aura via
     * IAuraChunk.triangulateAuraInArea, so any drain on the surrounding chunks shows up
     * in Covenant's bar on the next tick.
     *
     * <p>Required signatures (Nature's Aura 39.4, verified by javap):
     * <pre>
     *   public static IAuraChunk getAuraChunk(Level, BlockPos)
     *   public static BlockPos getHighestSpot(Level, BlockPos, int radius, BlockPos defaultPos)
     *   public static int triangulateAuraInArea(Level, BlockPos, int radius)
     *   public abstract int drainAura(BlockPos, int amount)  // returns amount actually drained
     * </pre>
     */
    private static void initNaturesAuraReflection() {
        try {
            Class<?> chunkCls = Class.forName("de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk");
            Class<?> levelCls = Class.forName("net.minecraft.world.level.Level");
            Class<?> posCls = Class.forName("net.minecraft.core.BlockPos");

            auraChunkGetAuraChunkMethod = tryGetStatic(chunkCls, "getAuraChunk", levelCls, posCls);
            auraChunkGetHighestSpotMethod = tryGetStatic(chunkCls, "getHighestSpot", levelCls, posCls, int.class, posCls);
            auraChunkTriangulateMethod = tryGetStatic(chunkCls, "triangulateAuraInArea", levelCls, posCls, int.class);
            auraChunkDrainAuraMethod = tryGetInstance(chunkCls, "drainAura", posCls, int.class);

            naturesAuraReflectionResolved = true;
            boolean ok = auraChunkGetAuraChunkMethod != null
                && auraChunkGetHighestSpotMethod != null
                && auraChunkTriangulateMethod != null
                && auraChunkDrainAuraMethod != null;
            if (ok) {
                LOGGER.info("  [OK] Nature's Aura reflection initialized — drain=true, triangulate=true, getHighestSpot=true, getAuraChunk=true");
            } else {
                LOGGER.error("  [DEGRADED] Nature's Aura reflection partial — drain={}, triangulate={}, getHighestSpot={}, getAuraChunk={}",
                    auraChunkDrainAuraMethod != null,
                    auraChunkTriangulateMethod != null,
                    auraChunkGetHighestSpotMethod != null,
                    auraChunkGetAuraChunkMethod != null);
                LOGGER.error("  [DEGRADED] Ars-spell aura deduction will silently fail; the green HUD bar won't move on Ars casts.");
            }
        } catch (Throwable t) {
            naturesAuraReflectionResolved = false;
            LOGGER.error("  [FAIL] Nature's Aura reflection failed (class missing?) — Ars-spell aura deduction disabled", t);
        }
    }

    /**
     * Return true if the player has at least {@code cost} Covenant aura.
     *
     * <p>Degraded behaviour: when reflection didn't resolve, returns {@code true}
     * so the cast isn't blocked on an internal failure of ours. The companion
     * {@link #consumeCovenantAura(Player, int)} then returns false (no payment),
     * which is visible because the green HUD doesn't move.
     */
    public static boolean hasEnoughCovenantAura(Player player, int cost) {
        if (player == null || cost <= 0) return true;
        // Server-authoritative path first — IAuraChunk.triangulateAuraInArea returns
        // the same number Covenant uses for the bar. Works on both single-player and
        // dedicated servers (ClientResourceData isn't populated on dedicated).
        try {
            if (player instanceof net.minecraft.server.level.ServerPlayer
                && naturesAuraReflectionResolved
                && auraChunkTriangulateMethod != null) {
                Object v = auraChunkTriangulateMethod.invoke(null,
                    player.level(), player.blockPosition(), 35);
                if (v instanceof Number) {
                    return ((Number) v).intValue() >= cost;
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("hasEnoughCovenantAura: triangulate failed", t);
        }
        if (!covenantAuraReflectionResolved) return true; // degraded: allow

        try {
            if (clientResourceDataGetCurrentAuraMethod != null) {
                Object v = clientResourceDataGetCurrentAuraMethod.invoke(null);
                if (v instanceof Number) {
                    return ((Number) v).intValue() >= cost;
                }
            }
            if (covenantGetCurrentAuraMethod != null) {
                Object v = covenantGetCurrentAuraMethod.invoke(null, player);
                if (v instanceof Number) {
                    return ((Number) v).intValue() >= cost;
                }
            }
            if (covenantGetVirtuousFractionMethod != null && covenantGetMaxAuraMethod != null) {
                Object f = covenantGetVirtuousFractionMethod.invoke(null, player);
                Object m = covenantGetMaxAuraMethod.invoke(null, player);
                if (f instanceof Number && m instanceof Number) {
                    double fraction = ((Number) f).doubleValue();
                    int max = ((Number) m).intValue();
                    return (int) (fraction * max) >= cost;
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("hasEnoughCovenantAura reflection failed", t);
        }
        return true; // degraded: don't block on reflection bugs
    }

    /**
     * Spend {@code cost} aura from the world around the player. Returns {@code true}
     * iff a positive amount was actually drained.
     *
     * <p>Covenant has no per-player consume API because its aura value is just a
     * sample of the ambient Nature's Aura level in the chunks around the player
     * (radius 35, same as Covenant's {@code triangulateAuraInArea} call). To
     * "consume aura" we drain it from the highest-aura spot in that area via
     * Nature's Aura's public {@code IAuraChunk.drainAura}. Covenant's own
     * {@code ResourceSyncEvents.getPlayerAuraChunk} will pick up the change on
     * the next server tick and ship a fresh {@code CurrentAuraSyncPacket} to the
     * client, which is what makes the HUD bar move.
     *
     * <p>This is the canonical Nature's Aura consume pattern used by every
     * official aura sink in the ecosystem (Aura Cache, Sky Channeler, etc.).
     *
     * <p>Must be called on the logical server (we touch world state). The
     * companion check {@code player instanceof ServerPlayer} short-circuits
     * otherwise — calling from a non-server context returns false silently.
     */
    public static boolean consumeCovenantAura(Player player, int cost) {
        if (player == null || cost <= 0) return false;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)) return false;
        if (!naturesAuraReflectionResolved
            || auraChunkGetAuraChunkMethod == null
            || auraChunkGetHighestSpotMethod == null
            || auraChunkDrainAuraMethod == null) {
            return false;
        }
        try {
            net.minecraft.world.level.Level level = player.level();
            net.minecraft.core.BlockPos playerPos = player.blockPosition();
            // 35 matches Covenant's triangulation radius — anywhere inside this area is
            // guaranteed to affect the next ambient-aura sample.
            Object highest = auraChunkGetHighestSpotMethod.invoke(null, level, playerPos, 35, playerPos);
            if (!(highest instanceof net.minecraft.core.BlockPos)) return false;
            net.minecraft.core.BlockPos spot = (net.minecraft.core.BlockPos) highest;
            Object chunkObj = auraChunkGetAuraChunkMethod.invoke(null, level, spot);
            if (chunkObj == null) return false;
            Object drainedObj = auraChunkDrainAuraMethod.invoke(chunkObj, spot, cost);
            if (drainedObj instanceof Number) {
                int drained = ((Number) drainedObj).intValue();
                if (drained > 0) {
                    LOGGER.debug("Drained {} aura from chunk spot {} for {} (requested {})",
                        drained, spot, player.getName().getString(), cost);
                    return true;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("consumeCovenantAura: IAuraChunk drain failed", t);
        }
        return false;
    }

    /**
     * Read the player's current Covenant aura. Returns 0 if reflection didn't
     * resolve a getCurrentAura method and the fallback (fraction × max) wasn't
     * available either.
     */
    public static int getCovenantAura(Player player) {
        try {
            // Server-authoritative path: ask Nature's Aura directly for the ambient aura
            // around the player. This is exactly what Covenant samples in its server-tick
            // sync handler, so it's the truth — and it works on dedicated servers where
            // ClientResourceData is never populated.
            if (player instanceof net.minecraft.server.level.ServerPlayer
                && naturesAuraReflectionResolved
                && auraChunkTriangulateMethod != null) {
                Object v = auraChunkTriangulateMethod.invoke(null,
                    player.level(), player.blockPosition(), 35);
                if (v instanceof Number) return ((Number) v).intValue();
            }
            if (!covenantAuraReflectionResolved) return 0;
            // Client-context path: ClientResourceData.getCurrentAura() — what Covenant's
            // own HUD reads from. Player arg is unused (it's a per-client singleton).
            if (clientResourceDataGetCurrentAuraMethod != null) {
                Object v = clientResourceDataGetCurrentAuraMethod.invoke(null);
                if (v instanceof Number) return ((Number) v).intValue();
            }
            if (player == null) return 0;
            if (covenantGetCurrentAuraMethod != null) {
                Object v = covenantGetCurrentAuraMethod.invoke(null, player);
                if (v instanceof Number) return ((Number) v).intValue();
            }
            if (covenantGetVirtuousFractionMethod != null && covenantGetMaxAuraMethod != null) {
                Object f = covenantGetVirtuousFractionMethod.invoke(null, player);
                Object m = covenantGetMaxAuraMethod.invoke(null, player);
                if (f instanceof Number && m instanceof Number) {
                    return (int) (((Number) f).doubleValue() * ((Number) m).intValue());
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("getCovenantAura reflection failed", t);
        }
        return 0;
    }

    /**
     * Check if Sanctified Legacy or Enigmatic Legacy is loaded.
     * Note: We only need one of these mods for the Cursed Ring to work.
     */
    public static boolean isAvailable() {
        return (isLoaded || isEnigmaticLegacyLoaded) && initialized;
    }
    
    /**
     * Check if the player is wearing the Cursed Ring (and not the Virtue Ring).
     * When wearing both rings, they cancel out and normal mana is used.
     * The Cursed Ring is from Enigmatic Legacy mod.
     */
    public static boolean isWearingCursedRing(Player player) {
        if (!isEnigmaticLegacyLoaded && !isLoaded) {
            return false;
        }
        CurioState state = getState(player);
        // Cursed Ring only active if wearing it WITHOUT Virtue Ring
        return state.cursedRing && !state.virtueRing;
    }

    /**
     * Check if the player is wearing the Virtue Ring (and not the Cursed Ring).
     * The Virtue Ring is from Covenant of the Seven mod.
     */
    public static boolean isWearingVirtueRing(Player player) {
        if (!isLoaded) {
            return false;
        }
        CurioState state = getState(player);
        // Virtue Ring only active if wearing it WITHOUT Cursed Ring
        return state.virtueRing && !state.cursedRing;
    }

    /**
     * Check if the player has both the Cursed Ring and Virtue Ring equipped (conflict state).
     * Covenant of the Seven ships both rings, so either source mod is sufficient — the previous
     * AND-gate silently dropped the conflict notification on C7-only setups.
     */
    public static boolean hasBothRings(Player player) {
        if (!isLoaded && !isEnigmaticLegacyLoaded) return false;
        CurioState state = getState(player);
        return state.cursedRing && state.virtueRing;
    }

    /**
     * Scan curio inventory once and cache ring/blasphemy presence for {@value #CURIO_CACHE_TTL_TICKS}
     * ticks. Called by all ring/blasphemy checks; scans are deduplicated within the TTL window.
     */
    private static CurioState getState(Player player) {
        if (player == null) {
            return EMPTY_STATE;
        }
        UUID id = player.getUUID();
        // ANS-HIGH-011: server-global gameTime (monotonic per world) instead of the
        // per-player tickCount, for consistency with the ring handlers. The now >=
        // cachedAtTick guard still cheaply rejects any non-monotonic edge (e.g. /time set).
        long now = player.level().getGameTime();
        CurioState cached = CURIO_STATE_CACHE.get(id);
        if (cached != null && now - cached.cachedAtTick < CURIO_CACHE_TTL_TICKS && now >= cached.cachedAtTick) {
            return cached;
        }
        CurioState fresh = scanCurios(player, now);
        CURIO_STATE_CACHE.put(id, fresh);
        return fresh;
    }

    /**
     * Single-pass scan of the player's curio inventory. Populates ring presence and
     * collects every equipped Blasphemy curio ID in one traversal.
     */
    private static CurioState scanCurios(Player player, long tick) {
        boolean[] cursed = {false};
        boolean[] virtue = {false};
        @SuppressWarnings("unchecked")
        Set<ResourceLocation>[] blasphemiesRef = new Set[]{null};
        try {
            CuriosUtil.getAllWornItems(player).ifPresent(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (itemId == null) continue;
                    if (CURSED_RING_IDS.contains(itemId)) {
                        cursed[0] = true;
                    } else if (VIRTUE_RING_IDS.contains(itemId)) {
                        virtue[0] = true;
                    } else if (BLASPHEMY_IDS.contains(itemId)) {
                        if (blasphemiesRef[0] == null) blasphemiesRef[0] = new HashSet<>(2);
                        blasphemiesRef[0].add(itemId);
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to scan curios for {}", player.getName().getString(), e);
        }
        Set<ResourceLocation> blasphemies = blasphemiesRef[0];
        return new CurioState(cursed[0], virtue[0],
            blasphemies == null ? Collections.emptySet() : Collections.unmodifiableSet(blasphemies),
            tick);
    }

    /**
     * Evict cached curio state for a player (call on logout).
     */
    public static void clearCacheFor(UUID playerId) {
        if (playerId != null) {
            CURIO_STATE_CACHE.remove(playerId);
        }
    }

    /**
     * Calculate LP cost for an Ars Nouveau spell using configurable formula.
     * 
     * @param manaCost The base mana cost of the spell
     * @param spellPart The spell part (for tier info)
     * @return The LP cost
     */
    public static int calculateLPCost(int manaCost, AbstractSpellPart spellPart) {
        if (!isAvailable()) {
            return 0;
        }
        
        // Get base multiplier from config
        double baseMultiplier = com.otectus.arsnspells.config.AnsConfig.ARS_LP_BASE_MULTIPLIER.get();
        double baseLPCost = manaCost * baseMultiplier;
        
        // Get tier multiplier from config
        int tier = spellPart != null ? spellPart.getConfigTier().value : 1;
        double tierMultiplier;
        
        switch (tier) {
            case 1:
                tierMultiplier = com.otectus.arsnspells.config.AnsConfig.ARS_LP_TIER1_MULTIPLIER.get();
                break;
            case 2:
                tierMultiplier = com.otectus.arsnspells.config.AnsConfig.ARS_LP_TIER2_MULTIPLIER.get();
                break;
            case 3:
                tierMultiplier = com.otectus.arsnspells.config.AnsConfig.ARS_LP_TIER3_MULTIPLIER.get();
                break;
            default:
                tierMultiplier = 1.0;
                break;
        }
        
        int finalCost = (int) Math.round(baseLPCost * tierMultiplier);
        
        // Apply minimum cost from config
        int minimumCost = com.otectus.arsnspells.config.AnsConfig.ARS_LP_MINIMUM_COST.get();
        return Math.max(minimumCost, finalCost);
    }
    
    /**
     * Calculate LP cost for an Iron's Spellbooks spell using configurable formula.
     * 
     * @param manaCost The base mana cost of the spell
     * @param spellLevel The spell level
     * @param rarity The spell rarity (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
     * @return The LP cost
     */
    public static int calculateIronsLPCost(int manaCost, int spellLevel, String rarity) {
        if (!isAvailable()) {
            return 0;
        }
        
        // Get base multiplier from config
        double baseMultiplier = com.otectus.arsnspells.config.AnsConfig.IRONS_LP_BASE_MULTIPLIER.get();
        double baseLPCost = manaCost * baseMultiplier;
        
        // Apply level scaling
        double levelMultiplier = com.otectus.arsnspells.config.AnsConfig.IRONS_LP_PER_LEVEL_MULTIPLIER.get();
        double levelScaling = 1.0 + (spellLevel * levelMultiplier);
        baseLPCost *= levelScaling;
        
        // Apply rarity multiplier
        double rarityMultiplier = getRarityMultiplier(rarity);
        int finalCost = (int) Math.round(baseLPCost * rarityMultiplier);
        
        // Apply minimum cost from config
        int minimumCost = com.otectus.arsnspells.config.AnsConfig.IRONS_LP_MINIMUM_COST.get();
        return Math.max(minimumCost, finalCost);
    }
    
    /**
     * Get the rarity multiplier for Iron's Spellbooks spells.
     * 
     * @param rarity The spell rarity
     * @return The multiplier
     */
    private static double getRarityMultiplier(String rarity) {
        if (rarity == null) {
            return 1.0;
        }
        
        switch (rarity.toUpperCase()) {
            case "COMMON":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_COMMON_MULTIPLIER.get();
            case "UNCOMMON":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_UNCOMMON_MULTIPLIER.get();
            case "RARE":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_RARE_MULTIPLIER.get();
            case "EPIC":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_EPIC_MULTIPLIER.get();
            case "LEGENDARY":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_LEGENDARY_MULTIPLIER.get();
            default:
                return 1.0;
        }
    }
    
    /**
     * Get the configured LP source mode.
     */
    public static LPSourceMode getLPSourceMode() {
        String mode = com.otectus.arsnspells.config.AnsConfig.LP_SOURCE_MODE.get();
        try {
            return LPSourceMode.valueOf(mode.toUpperCase());
        } catch (Exception e) {
            return LPSourceMode.BLOOD_MAGIC_PRIORITY;
        }
    }

    /**
     * Check if player has enough LP for a spell.
     * Checks Blood Magic Soul Network first (if available and configured), then health.
     *
     * @param player The player
     * @param lpCost The LP cost
     * @return true if player has enough LP
     */
    public static boolean hasEnoughLP(Player player, int lpCost) {
        if (player == null) {
            return false;
        }
        if (lpCost <= 0) {
            // Non-positive costs would bypass health/LP checks — treat as unavailable
            // so callers don't silently "succeed" with a free cast.
            return false;
        }

        LPSourceMode mode = getLPSourceMode();

        // Try Blood Magic first if configured
        if (mode == LPSourceMode.BLOOD_MAGIC_PRIORITY || mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
            if (isBloodMagicAvailable()) {
                int currentLP = getBloodMagicLP(player);
                if (currentLP >= lpCost) {
                    return true;
                }
                // If Blood Magic only mode and not enough LP, fail
                if (mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
                    return false;
                }
                // Fall through to health check
            } else if (mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
                LOGGER.warn("Blood Magic not available but LP_SOURCE_MODE is BLOOD_MAGIC_ONLY");
                return false;
            }
        }

        // Health-based check (fallback or HEALTH_ONLY mode)
        float healthCost = lpCost / 10.0f; // 100 LP = 10 health = 5 hearts
        float currentHealth = player.getHealth();
        return currentHealth > healthCost + 1.0f;
    }

    /**
     * Attempt to consume LP from the player.
     * Uses Blood Magic Soul Network first (if available and configured), then health.
     *
     * @param player The player
     * @param lpCost The LP cost
     * @return true if LP was successfully consumed, false otherwise
     */
    public static boolean consumeLP(Player player, int lpCost) {
        if (player == null) {
            LOGGER.warn("Cannot consume LP: player is null");
            return false;
        }
        if (lpCost <= 0) {
            LOGGER.warn("Refusing to consume non-positive LP cost: {}", lpCost);
            return false;
        }

        LPSourceMode mode = getLPSourceMode();

        // Try Blood Magic first if configured
        if (mode == LPSourceMode.BLOOD_MAGIC_PRIORITY || mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
            if (isBloodMagicAvailable()) {
                int currentLP = getBloodMagicLP(player);
                LOGGER.debug("Attempting to consume {} LP from {}'s Soul Network (has {} LP)",
                    lpCost, player.getName().getString(), currentLP);

                if (currentLP >= lpCost) {
                    boolean success = consumeBloodMagicLP(player, lpCost);
                    if (success) {
                        LOGGER.debug("Successfully consumed {} LP from Soul Network", lpCost);
                        return true;
                    }
                }

                // If Blood Magic only mode and failed, don't fall back to health
                if (mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
                    LOGGER.debug("Insufficient LP in Soul Network: need {} but only have {}", lpCost, currentLP);
                    return false;
                }

                LOGGER.debug("Insufficient Soul Network LP, falling back to health");
            } else if (mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
                LOGGER.warn("Blood Magic not available but LP_SOURCE_MODE is BLOOD_MAGIC_ONLY");
                return false;
            }
        }

        // Health-based consumption (fallback or HEALTH_ONLY mode)
        float healthCost = lpCost / 10.0f; // 100 LP = 10 health = 5 hearts
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();

        LOGGER.debug("Attempting to consume {} LP ({} health) from {}'s health",
            lpCost, healthCost, player.getName().getString());
        LOGGER.debug("Current health: {}/{}", currentHealth, maxHealth);

        if (currentHealth <= healthCost + 1.0f) {
            LOGGER.debug("Insufficient health: need {} but only have {} (keeping 1 HP buffer)",
                healthCost, currentHealth);
            return false;
        }

        // Clamp to preserve the 1 HP buffer even under floating-point drift.
        float newHealth = Math.max(1.0f, currentHealth - healthCost);
        player.setHealth(newHealth);

        LOGGER.debug("Successfully consumed {} LP ({} health) - new health: {}",
            lpCost, healthCost, newHealth);

        return true;
    }

    /**
     * Apply a silent health loss without triggering damage events.
     * Used for LP penalties when spells are cancelled in safe mode.
     *
     * @param player The player
     * @param healthLoss The amount of health to subtract
     */
    public static void applySilentHealthLoss(Player player, float healthLoss) {
        if (player == null) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (healthLoss <= 0.0f) {
            return;
        }
        float newHealth = Math.max(1.0f, player.getHealth() - healthLoss);
        player.setHealth(newHealth);
    }

    /**
     * Get player's current LP from Blood Magic Soul Network.
     */
    public static int getBloodMagicLP(Player player) {
        if (!isBloodMagicAvailable() || player == null) {
            return 0;
        }

        try {
            Object soulNetwork = getSoulNetworkMethod.invoke(null, player.getUUID());
            if (soulNetwork == null) {
                LOGGER.debug("Blood Magic Soul Network is null for {}", player.getName().getString());
                return 0;
            }
            Object essence = getCurrentEssenceMethod.invoke(soulNetwork);
            if (essence instanceof Number) {
                return ((Number) essence).intValue();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get Blood Magic LP", e);
        }
        return 0;
    }

    /**
     * Consume LP from Blood Magic Soul Network.
     */
    public static boolean consumeBloodMagicLP(Player player, int amount) {
        if (!isBloodMagicAvailable() || player == null || amount <= 0) {
            return false;
        }

        try {
            Object soulNetwork = getSoulNetworkMethod.invoke(null, player.getUUID());
            if (soulNetwork == null) {
                LOGGER.debug("Blood Magic Soul Network is null for {}", player.getName().getString());
                return false;
            }
            // The syphon method returns the amount actually syphoned
            Object result = syphonMethod.invoke(soulNetwork, amount);
            if (result instanceof Number) {
                int syphoned = ((Number) result).intValue();
                return syphoned >= amount;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to consume Blood Magic LP", e);
        }
        return false;
    }

    /**
     * Check if the player has a Blasphemy curio that matches the spell school.
     * Blasphemy curios reduce LP costs by 85% for matching schools.
     *
     * @param player The player
     * @param schoolType The spell school (e.g., "fire", "ice", "lightning")
     * @return true if player has matching Blasphemy curio
     */
    public static boolean hasMatchingBlasphemy(Player player, String schoolType) {
        if (!isAvailable() || schoolType == null) {
            return false;
        }
        ResourceLocation id = new ResourceLocation(MOD_ID, schoolType.toLowerCase() + "_blasphemy");
        return getState(player).blasphemies.contains(id);
    }
    
    /**
     * Get the LP cost multiplier based on Blasphemy curios.
     * 
     * @param player The player
     * @param schoolType The spell school
     * @return 0.15 if has matching Blasphemy (85% discount), 1.0 otherwise
     */
    @Deprecated
    public static double getBlasphemyMultiplier(Player player, String schoolType) {
        return hasMatchingBlasphemy(player, schoolType) ? 0.15 : 1.0;
    }

    /**
     * Get the LP-specific Blasphemy multiplier using the configurable discount.
     * @return (1.0 - discount) if matching Blasphemy equipped, 1.0 otherwise
     */
    public static double getBlasphemyLPMultiplier(Player player, String schoolType) {
        return hasMatchingBlasphemy(player, schoolType)
            ? (1.0 - AnsConfig.BLASPHEMY_LP_DISCOUNT.get()) : 1.0;
    }

    // getBlasphemyAuraMultiplier removed alongside the parallel aura subsystem.
    // Covenant of the Seven applies its own Blasphemy-based aura discount natively.


    // ========================================
    // MANA DISCOUNT SUPPORT (Ring of Virtue & Blasphemy)
    // ========================================
    
    /**
     * Check if the player is wearing the Ring of the Seven Virtues.
     * This is the standalone check (doesn't care about Cursed Ring).
     *
     * @param player The player
     * @return true if wearing Ring of Virtue
     */
    public static boolean hasVirtueRing(Player player) {
        if (!isLoaded) {
            return false;
        }
        return getState(player).virtueRing;
    }
    
    /**
     * Check if the player has any Blasphemy curio equipped.
     *
     * @param player The player
     * @return true if wearing any Blasphemy curio
     */
    public static boolean hasAnyBlasphemy(Player player) {
        if (!isAvailable()) {
            return false;
        }
        return !getState(player).blasphemies.isEmpty();
    }

    /**
     * Check if the player has a specific Blasphemy curio type.
     *
     * @param player The player
     * @param blasphemyType The Blasphemy type (e.g., "fire_blasphemy")
     * @return true if wearing that specific Blasphemy
     */
    public static boolean hasBlasphemyType(Player player, String blasphemyType) {
        if (!isLoaded || blasphemyType == null) {
            return false;
        }
        return getState(player).blasphemies.contains(new ResourceLocation(MOD_ID, blasphemyType));
    }
    
    /**
     * Get the Blasphemy type that matches the given spell school.
     * 
     * @param spellSchool The spell school (e.g., "fire", "ice", "lightning")
     * @return The matching Blasphemy type, or null if no match
     */
    public static String getMatchingBlasphemyType(String spellSchool) {
        if (spellSchool == null) {
            return null;
        }
        
        String school = spellSchool.toLowerCase();
        
        // Direct mapping for most schools
        if (school.contains("fire") || school.contains("flame")) {
            return "fire_blasphemy";
        }
        if (school.contains("ice") || school.contains("frost") || school.contains("cold")) {
            return "ice_blasphemy";
        }
        if (school.contains("lightning") || school.contains("shock") || school.contains("storm")) {
            return "lightning_blasphemy";
        }
        if (school.contains("holy") || school.contains("light") || school.contains("heal")) {
            return "holy_blasphemy";
        }
        if (school.contains("ender") || school.contains("void") || school.contains("teleport")) {
            return "ender_blasphemy";
        }
        if (school.contains("blood") || school.contains("essence") || school.contains("drain")) {
            return "blood_blasphemy";
        }
        if (school.contains("evocation") || school.contains("machina") || school.contains("projectile")) {
            return "evocation_blasphemy";
        }
        if (school.contains("nature") || school.contains("wilds") || school.contains("earth") || school.contains("grow")) {
            return "nature_blasphemy";
        }
        if (school.contains("eldritch") || school.contains("anomaly") || school.contains("dark")) {
            return "eldritch_blasphemy";
        }
        if (school.contains("aqua") || school.contains("ocean") || school.contains("water")) {
            return "aqua_blasphemy";
        }
        if (school.contains("geo") || school.contains("stone") || school.contains("rock")) {
            return "geo_blasphemy";
        }
        if (school.contains("wind") || school.contains("air") || school.contains("sky") || school.contains("gust")) {
            return "wind_blasphemy";
        }
        
        return null;
    }
    
    /**
     * Determine the spell school from an Ars Nouveau spell part.
     * 
     * @param spellPart The spell part
     * @return The spell school identifier, or "generic" if unknown
     */
    public static String determineSpellSchool(AbstractSpellPart spellPart) {
        if (spellPart == null || spellPart.getRegistryName() == null) {
            return "generic";
        }
        
        String path = spellPart.getRegistryName().getPath().toLowerCase();
        
        // Analyze glyph name to determine school
        if (path.contains("fire") || path.contains("ignite") || path.contains("flare") || path.contains("burn")) {
            return "fire";
        }
        if (path.contains("ice") || path.contains("freeze") || path.contains("frost") || path.contains("cold")) {
            return "ice";
        }
        if (path.contains("lightning") || path.contains("shock") || path.contains("storm")) {
            return "lightning";
        }
        if (path.contains("heal") || path.contains("holy") || path.contains("light") && !path.contains("lightning")) {
            return "holy";
        }
        if (path.contains("ender") || path.contains("blink") || path.contains("warp") || path.contains("teleport")) {
            return "ender";
        }
        if (path.contains("blood") || path.contains("drain") || path.contains("life")) {
            return "blood";
        }
        if (path.contains("projectile") || path.contains("fang") || path.contains("evocation")) {
            return "evocation";
        }
        if (path.contains("grow") || path.contains("nature") || path.contains("plant") || path.contains("harvest")) {
            return "nature";
        }
        if (path.contains("wither") || path.contains("dark") || path.contains("hex")) {
            return "eldritch";
        }
        if (path.contains("water") || path.contains("conjure_water")) {
            return "aqua";
        }
        if (path.contains("earth") || path.contains("stone") || path.contains("crush")) {
            return "geo";
        }
        if (path.contains("wind") || path.contains("gust") || path.contains("air")) {
            return "wind";
        }
        
        return "generic";
    }
}
