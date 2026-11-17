package com.otectus.arsnspells.glyph.behavior;

import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.network.GlyphLiveSyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The server-authoritative behaviour of imported glyphs: the datapack layer
 * ({@link GlyphMappingSnapshot}) combined with Iron's live, effective configuration
 * ({@link GlyphLiveSnapshot}), under one {@link #generation()} and one {@link #digest()}.
 *
 * <p><b>Lifecycle.</b> Iron's builds its spell configuration during {@code OnDatapackSyncEvent}
 * (at normal priority; the first login of a server, and every {@code /reload}). This class rebuilds
 * its live layer after that, at the lowest priority, and sends it to the player(s) being synced. At
 * server start it builds a {@link GlyphLiveSnapshot.Readiness#PENDING} layer from the catalog's
 * defaults; nothing final may be decided on that. An unchanged rebuild (a second login) keeps the
 * current snapshot and its generation, so a join costs one bounded rebuild and no invalidation.
 *
 * <p><b>Sides.</b> One static view per JVM, as with the mapping layer: a dedicated server and a
 * single-player host read the server's own snapshot; a remote client reads the copy the server
 * sent, and is {@link GlyphLiveSnapshot.Readiness#NOT_SYNCED} until it arrives.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class GlyphBehavior {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphBehavior.class);

    private static volatile GlyphLiveSnapshot live;
    private static volatile boolean ironsConfigReady;
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final AtomicBoolean REBUILD_QUEUED = new AtomicBoolean();
    @Nullable private static volatile Runnable viewerRefresh;
    private static volatile String cachedDigest;
    private static volatile String cachedFor;

    private GlyphBehavior() {}

    /** The live layer on this side. */
    public static GlyphLiveSnapshot live() {
        GlyphLiveSnapshot current = live;
        if (current == null) {
            synchronized (GlyphBehavior.class) {
                current = live;
                if (current == null) {
                    current = GlyphLiveSnapshot.defaults(ImportedGlyphCatalog.get(), IronGlyphBridges.get().present()
                        ? GlyphLiveSnapshot.Readiness.PENDING : GlyphLiveSnapshot.Readiness.ABSENT);
                    live = current;
                }
            }
        }
        return current;
    }

    /** Bumped whenever the mapping layer or the live layer that is in force changes. */
    public static int generation() {
        return GENERATION.get();
    }

    /** The combined identity of the behaviour in force: mapping digest and live digest. Equal on server and synced client. */
    public static String digest() {
        String mapping = GlyphMappingSnapshot.current().digest();
        String liveDigest = live().digest();
        String key = mapping + "|" + liveDigest;
        String cached = cachedDigest;
        if (cached != null && key.equals(cachedFor)) return cached;
        try {
            String computed = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
            cachedDigest = computed;
            cachedFor = key;
            return computed;
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Called by {@link GlyphMappingSnapshot#publish}. */
    public static void mappingsPublished() {
        GENERATION.incrementAndGet();
        refreshViewer();
    }

    /** Make {@code snapshot} the live layer in force; an identical one changes nothing. */
    public static boolean publishLive(GlyphLiveSnapshot snapshot) {
        GlyphLiveSnapshot current = live;
        if (current != null && current.digest().equals(snapshot.digest())) return false;
        live = snapshot;
        GENERATION.incrementAndGet();
        refreshViewer();
        return true;
    }

    /** Rebuild the live layer from Iron's current state. Server thread. */
    public static GlyphLiveSnapshot rebuild() {
        IronGlyphBridge bridge = IronGlyphBridges.get();
        GlyphLiveSnapshot built = GlyphLiveSnapshot.build(ImportedGlyphCatalog.get(), new GlyphLiveSnapshot.LiveIrons() {
            @Override public boolean present() { return bridge.present(); }
            @Override public LiveSpellFacts facts(ResourceLocation spell, int registeredMax) { return bridge.liveFacts(spell, registeredMax); }
        }, ironsConfigReady).withGlyphsEnabled(com.otectus.arsnspells.config.AnsConfig.ironGlyphsEnabled());
        if (publishLive(built))
            LOGGER.debug("[Glyph] live layer {} digest {} generation {}", built.readiness(), built.digest().substring(0, 12), generation());
        return live();
    }

    // ---- Effective values, the ones every consumer must use ----------------------------------

    /** The live facts for {@code definition}'s spell, or its catalog defaults when there are none. */
    public static LiveSpellFacts facts(ImportedGlyphDefinition definition) {
        LiveSpellFacts facts = live().facts(definition.spell());
        return facts != null ? facts : new LiveSpellFacts(true, definition.enabledByDefault(), definition.minLevel(), definition.maxLevel(),
            definition.school(), definition.requiresLearning(), java.util.List.of());
    }

    /** Iron's effective maximum level for the spell now (may be below or above the registered envelope). */
    public static int effectiveMax(ImportedGlyphDefinition definition) {
        return facts(definition).maxLevel();
    }

    /** The spell's effective school now: what policy, progression, discounts and colours must use. */
    public static ResourceLocation school(ImportedGlyphDefinition definition) {
        return facts(definition).school();
    }

    /** Whether Iron's requires the spell to be learned now (it follows the effective school). */
    public static boolean requiresLearning(ImportedGlyphDefinition definition) {
        return facts(definition).requiresLearning();
    }

    // ---- Recipe viewer -----------------------------------------------------------------------

    /** Set by the optional recipe-viewer integration; run on every behaviour change. */
    public static void setViewerRefresh(@Nullable Runnable refresh) {
        viewerRefresh = refresh;
    }

    private static void refreshViewer() {
        Runnable refresh = viewerRefresh;
        if (refresh == null) return;
        try {
            refresh.run();
        } catch (RuntimeException failure) {
            LOGGER.warn("[Glyph] recipe viewer refresh failed; the Press itself is unaffected", failure);
        }
    }

    // ---- Server lifecycle --------------------------------------------------------------------

    /**
     * Readiness is not reset here: it is false from the previous stop (or JVM start), and a
     * listener of this same event may already have fired the first datapack sync (the GameTest
     * environment does), which must not be undone.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuild();
        try {
            com.otectus.arsnspells.glyph.GlyphIdentityRecord.check(event.getServer(), ImportedGlyphCatalog.get());
        } catch (RuntimeException failure) {
            LOGGER.error("[Glyph] could not check this world's imported-glyph identity record", failure);
        }
    }

    /** After Iron's rebuilt its configuration on the same event; sends the result to the synced player(s). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ironsConfigReady = true;
        rebuild();
        // 3.4.1 review SYNC-01: the mapping view and the live facts, as one publication.
        if (event.getPlayer() != null) com.otectus.arsnspells.network.GlyphPublication.send(event.getPlayer());
        else com.otectus.arsnspells.network.GlyphPublication.send(event.getPlayerList().getPlayers());
    }

    /**
     * The ANS server config changed: rebuild once on the server thread and resend. Several reload
     * notifications in one tick coalesce into one rebuild.
     */
    public static void onConfigChanged() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !REBUILD_QUEUED.compareAndSet(false, true)) return;
        server.execute(() -> {
            REBUILD_QUEUED.set(false);
            GENERATION.incrementAndGet();
            // Turning imported glyphs off ends what is in flight with its unspent funds returned.
            if (!com.otectus.arsnspells.config.AnsConfig.ironGlyphsEnabled())
                com.otectus.arsnspells.glyph.GlyphSessions.closeEverything("feature_disabled");
            rebuild();
            com.otectus.arsnspells.network.GlyphPublication.send(server.getPlayerList().getPlayers());
            refreshViewer();
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ironsConfigReady = false;
        live = null;
        GENERATION.incrementAndGet();
    }

    // ---- Client side -------------------------------------------------------------------------

    /** A remote client received the server's live layer. */
    public static void acceptClient(GlyphLiveSnapshot snapshot) {
        publishLive(snapshot);
    }

    /**
     * 3.4.1 review SYNC-01: a remote client applies one complete publication: the mapping view and the
     * live facts together, with one viewer refresh for the pair.
     */
    public static void acceptClientGeneration(com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot mappings, GlyphLiveSnapshot snapshot) {
        Runnable refresh = viewerRefresh;
        viewerRefresh = null;
        try {
            com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot.publish(mappings);
            publishLive(snapshot);
        } finally {
            viewerRefresh = refresh;
        }
        GENERATION.incrementAndGet();
        refreshViewer();
    }

    /** Something a viewer derives from (a tag's members) changed without the behaviour changing. */
    public static void viewerInputsChanged() {
        refreshViewer();
    }

    /** A remote client connected or disconnected: nothing of a previous server survives. */
    public static void resetClient(boolean connecting) {
        live = connecting ? GlyphLiveSnapshot.defaults(ImportedGlyphCatalog.get(), GlyphLiveSnapshot.Readiness.NOT_SYNCED) : null;
        GENERATION.incrementAndGet();
        refreshViewer();
    }

    // ---- Tests -------------------------------------------------------------------------------

    /** For the GameTest harness: what a real server learns from its first datapack sync. */
    public static void ironsConfigReadyForTesting(boolean ready) {
        ironsConfigReady = ready;
        rebuild();
    }

    public static boolean ironsConfigReady() {
        return ironsConfigReady;
    }
}
