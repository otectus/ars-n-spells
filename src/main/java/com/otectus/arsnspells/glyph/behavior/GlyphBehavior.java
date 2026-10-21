package com.otectus.arsnspells.glyph.behavior;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.IronGlyphBridge;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.network.ImportedGlyphSyncPayload;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Publishes the current {@link GlyphBehaviorSnapshot} on each logical side.
 *
 * <p>The server builds a snapshot at start, after every data pack sync (login and {@code /reload},
 * after Iron's has rebuilt its own spell config on the same event) and when the ANS config
 * reloads, then sends a bounded copy to clients. Callers on the server thread read the server's
 * snapshot; everything else (the client's spellbook GUI, tooltips) reads the copy the client
 * received, which is "not synced" until the first one arrives.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class GlyphBehavior {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphBehavior.class);

    private static volatile GlyphBehaviorSnapshot server;
    private static volatile GlyphBehaviorSnapshot client;
    private static volatile GlyphMappings.Result mappings = GlyphMappings.Result.EMPTY;
    private static volatile boolean mappingsLoaded;
    private static volatile List<String> rejectedReload = List.of();
    private static volatile boolean ironsConfigReady;
    private static int generation;

    private GlyphBehavior() {
    }

    /** The snapshot for the calling side. */
    public static GlyphBehaviorSnapshot current() {
        MinecraftServer running = ServerLifecycleHooks.getCurrentServer();
        GlyphBehaviorSnapshot serverSnapshot = server;
        if (serverSnapshot != null && running != null
                && (running.isSameThread() || FMLEnvironment.dist == Dist.DEDICATED_SERVER)) {
            return serverSnapshot;
        }
        GlyphBehaviorSnapshot clientSnapshot = client;
        if (clientSnapshot != null) return clientSnapshot;
        if (serverSnapshot != null && FMLEnvironment.dist == Dist.DEDICATED_SERVER) return serverSnapshot;
        return GlyphBehaviorSnapshot.unsynced(ImportedGlyphCatalog.get());
    }

    /** The server's snapshot; only meaningful while a server runs. */
    @Nullable
    public static GlyphBehaviorSnapshot serverSnapshot() {
        return server;
    }

    public static VariantBehavior variant(ImportedGlyphKey key) {
        return current().variant(key);
    }

    /** Rebuild the server snapshot from the current settings, mappings and Iron's state. */
    public static synchronized GlyphBehaviorSnapshot rebuild() {
        IronGlyphBridge bridge = IronGlyphBridges.get();
        GlyphBehaviorSnapshot.LiveIrons live = new GlyphBehaviorSnapshot.LiveIrons() {
            @Override
            public boolean present() {
                return bridge.available();
            }

            @Override
            public GlyphBehaviorSnapshot.LiveSpell spell(ResourceLocation spellId) {
                return bridge.liveSpell(spellId);
            }

            @Override
            public boolean configReady() {
                return ironsConfigReady;
            }
        };
        GlyphBehaviorSnapshot built = GlyphBehaviorSnapshot.build(++generation, ImportedGlyphCatalog.get(), mappings,
            settings(), live);
        server = built;
        LOGGER.debug("[ImportedGlyphs] behaviour generation {} digest {} dormancy {}", built.generation(),
            built.digest().substring(0, 12), built.dormancyCounts());
        return built;
    }

    /** For the GameTest harness only; in play this is set by the first data pack sync. */
    public static void ironsConfigReadyForTesting(boolean ready) {
        ironsConfigReady = ready;
    }

    static GlyphBehaviorSnapshot.Settings settings() {
        try {
            return new GlyphBehaviorSnapshot.Settings(
                AnsConfig.flag(AnsConfig.ENABLE_IRON_GLYPHS, true),
                AnsConfig.flag(AnsConfig.ENABLE_GLYPH_PRESS, true),
                AnsConfig.IRON_GLYPH_MANA_BONUS_POLICY.get(),
                Math.min(AnsConfig.HARD_MAX_IMPORTED_EFFECTS_PER_SPELL, AnsConfig.MAX_IMPORTED_EFFECTS_PER_SPELL.get()),
                Math.min(AnsConfig.HARD_MAX_FOREIGN_EMISSIONS_PER_CAST, AnsConfig.MAX_FOREIGN_EMISSIONS_PER_CAST.get()),
                Math.min(AnsConfig.HARD_MAX_ACTIVE_GLYPH_SESSIONS_PER_PLAYER, AnsConfig.MAX_ACTIVE_GLYPH_SESSIONS_PER_PLAYER.get()),
                AnsConfig.GLYPH_PRESS_SOURCE_CAPACITY.get());
        } catch (IllegalStateException configNotLoaded) {
            return GlyphBehaviorSnapshot.Settings.DEFAULTS;
        }
    }

    /**
     * Take a freshly parsed mapping layer. At first load whatever validated is applied and the
     * spells named by rejected files fail closed; on a later reload an invalid candidate is
     * rejected whole and the last valid layer stays in force.
     */
    public static synchronized void acceptMappings(GlyphMappings.Result candidate) {
        if (!mappingsLoaded || candidate.valid()) {
            mappings = candidate;
            rejectedReload = List.of();
            mappingsLoaded = true;
            for (String error : candidate.errors()) LOGGER.error("[ImportedGlyphs] mapping: {}", error);
        } else {
            rejectedReload = candidate.errors();
            LOGGER.error("[ImportedGlyphs] reloaded glyph mappings rejected ({} error(s)); the previous mappings stay in force",
                candidate.errors().size());
            for (String error : candidate.errors()) LOGGER.error("[ImportedGlyphs] mapping: {}", error);
        }
    }

    public static GlyphMappings.Result mappings() {
        return mappings;
    }

    /** Errors of the last reload that was rejected whole; empty when the last reload applied. */
    public static List<String> rejectedReload() {
        return rejectedReload;
    }

    public static void receiveClient(GlyphBehaviorSnapshot snapshot) {
        client = snapshot;
    }

    public static void clearClient() {
        client = null;
    }

    /** Called when the ANS config reloads: republish so a toggled feature applies at once. */
    public static void onConfigChanged() {
        MinecraftServer running = ServerLifecycleHooks.getCurrentServer();
        if (running == null || server == null) return;
        running.execute(() -> {
            rebuild();
            for (ServerPlayer player : running.getPlayerList().getPlayers()) send(player);
        });
    }

    private static void send(ServerPlayer player) {
        GlyphBehaviorSnapshot snapshot = server;
        if (snapshot != null) PacketHandler.sendToClient(new ImportedGlyphSyncPayload(snapshot), player);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new GlyphMappingReloadListener());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuild();
    }

    /**
     * After Iron's rebuilt its spell config on the same event (it listens at normal priority).
     * Iron's builds that config on the first sync only, so on a dedicated server it is unknown
     * until the first player joins or {@code /reload} runs; until then the snapshot is pending.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ironsConfigReady = true;
        rebuild();
        if (event.getPlayer() != null) {
            send(event.getPlayer());
        } else {
            for (ServerPlayer player : event.getPlayerList().getPlayers()) send(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        server = null;
        mappings = GlyphMappings.Result.EMPTY;
        mappingsLoaded = false;
        rejectedReload = List.of();
        ironsConfigReady = false;
    }
}
