package com.otectus.arsnspells.config;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.SettingsSyncPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The one server-side way world settings change (3.4.1 review CONFIG-01): the {@code /ans mode set}
 * command, the in-game settings screen and a config file reload all end here, on the server thread.
 *
 * <p>An apply validates every requested key and value first and changes nothing if any is invalid.
 * Then it writes the values, republishes the mana routing, which reconciles every online player's
 * modifiers and pools to it ({@code ModeChangeCleanup}), lets the
 * imported-glyph behaviour rebuild, saves the file and sends every client the new settings. Applying
 * the values already in force changes nothing and is safe to repeat, which is what a duplicate
 * file-watcher notification does.
 *
 * <p>Work in flight keeps the pricing it was admitted under: an imported glyph's session keeps its
 * rules snapshot, and a native cast whose pricing changed is refused at its effect boundary and
 * refunded, never repriced into another pool.
 */
@EventBusSubscriber(modid = "ars_n_spells")
public final class AnsSettingsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnsSettingsService.class);

    /**
     * The outcome of one apply.
     *
     * @param resultKey translation key of the outcome shown to whoever asked
     * @param detail    an extra argument for that message (a key, a value, a save error)
     */
    public record Result(boolean applied, boolean changed, String resultKey, String detail, String requestedMode, String effectiveMode) {}

    private AnsSettingsService() {}

    /**
     * Apply {@code request} for {@code actor} (null for the console or a file reload, which are already
     * authorized). Server thread only.
     */
    public static Result apply(@Nullable ServerPlayer actor, Map<String, String> request) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.isSameThread()) throw new IllegalStateException("settings are applied on the server thread");
        if (actor != null && !ConfigAuthority.mayChange(actor))
            return result(false, false, "ars_n_spells.settings.result.not_allowed", "");
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : request.entrySet()) {
            AnsSettings.Setting setting = AnsSettings.setting(entry.getKey());
            if (setting == null) return result(false, false, "ars_n_spells.settings.result.unknown_key", entry.getKey());
            Object value = AnsSettings.parse(setting, entry.getValue());
            if (value == null) return result(false, false, "ars_n_spells.settings.result.invalid_value", entry.getKey() + "=" + entry.getValue());
            parsed.put(setting.key(), value);
        }
        boolean changed = false;
        synchronized (AnsConfig.SAVE_LOCK) {
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                var value = AnsSettings.value(entry.getKey());
                if (!Objects.equals(value.get(), entry.getValue())) {
                    value.set(entry.getValue());
                    changed = true;
                }
            }
        }
        if (!changed) return result(true, false, "ars_n_spells.settings.result.unchanged", "");
        String saveError = refreshAndSave();
        LOGGER.info("Ars 'n' Spells settings changed by {}: {} (mode {} effective {})",
            actor == null ? "the server" : actor.getGameProfile().getName(), request,
            AnsConfig.MANA_UNIFICATION_MODE.get(), BridgeManager.getCurrentMode().getConfigName());
        broadcast(server);
        return saveError == null ? result(true, true, "ars_n_spells.settings.result.applied", "")
            : result(true, true, "ars_n_spells.settings.result.applied_not_saved", saveError);
    }

    /** Everything that follows a change of the values in force: policies, routing, players, glyphs, then the file. */
    private static String refreshAndSave() {
        // NeoForge's refreshMode reconciles every online player itself (ModeChangeCleanup).
        BridgeManager.refreshMode();
        com.otectus.arsnspells.glyph.behavior.GlyphBehavior.onConfigChanged();
        try {
            // Saved here, on the server thread, so the reply can say whether the file was written.
            synchronized (AnsConfig.SAVE_LOCK) {
                AnsConfig.SPEC.save();
            }
            return null;
        } catch (RuntimeException failure) {
            LOGGER.warn("Ars 'n' Spells settings were applied but could not be saved: {}", failure.toString());
            return failure.getClass().getSimpleName();
        }
    }

    private static Result result(boolean applied, boolean changed, String key, String detail) {
        return new Result(applied, changed, key, detail, safeMode(), BridgeManager.getCurrentMode().getConfigName());
    }

    private static String safeMode() {
        try {
            return AnsConfig.MANA_UNIFICATION_MODE.get();
        } catch (IllegalStateException notLoaded) {
            return "";
        }
    }

    // ---- Telling clients -----------------------------------------------------------------------

    /** The settings as {@code player} sees them, with an optional outcome message for them. */
    public static AnsSettings.Snapshot snapshotFor(ServerPlayer player, @Nullable Result outcome) {
        return new AnsSettings.Snapshot(AnsSettings.current(), BridgeManager.getCurrentMode().getConfigName(),
            BridgeManager.isIronsSpellbooksLoaded(), ConfigAuthority.mayChange(player),
            outcome == null ? "" : outcome.resultKey(), outcome == null ? "" : outcome.detail(),
            AnsSettings.ClientPolicy.current());
    }

    /** Send every connected player the settings in force. */
    public static void broadcast(@Nullable MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            PacketHandler.sendToClient(new SettingsSyncPayload(snapshotFor(player, null)), player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PacketHandler.sendToClient(new SettingsSyncPayload(snapshotFor(player, null)), player);
    }

    /** A config file edit or reload: the values are already loaded; make everything else follow and tell clients. */
    public static void onFileReloaded() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> broadcast(server));
    }
}
