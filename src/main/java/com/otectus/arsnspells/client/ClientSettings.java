package com.otectus.arsnspells.client;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The world settings this client was last told (3.4.1 review CONFIG-01), and what follows from them.
 *
 * <p>The settings screen edits a draft of these and never the client's own copy of the config. On a
 * remote server the client's mana routing follows the server's effective mode, so a mode change made
 * by the owner or an operator reaches every connected client without a reconnect. On a single-player
 * or LAN host the client shares the server's own state and only the screen uses this.
 */
public final class ClientSettings {
    @Nullable private static volatile AnsSettings.Snapshot current;

    private ClientSettings() {}

    @Nullable
    public static AnsSettings.Snapshot current() {
        return current;
    }

    public static void accept(AnsSettings.Snapshot snapshot) {
        current = snapshot;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.hasSingleplayerServer()) BridgeManager.applyServerMode(snapshot.effectiveMode());
        if (!snapshot.resultKey().isEmpty() && minecraft.player != null) {
            boolean failed = snapshot.resultKey().endsWith("not_allowed") || snapshot.resultKey().endsWith("invalid_value")
                || snapshot.resultKey().endsWith("unknown_key") || snapshot.resultKey().endsWith("not_saved");
            minecraft.player.displayClientMessage(Component.translatable(snapshot.resultKey(),
                    snapshot.values().getOrDefault(AnsSettings.MODE, ""), snapshot.effectiveMode(), snapshot.resultDetail())
                .withStyle(failed ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        }
        if (minecraft.screen instanceof com.otectus.arsnspells.client.screen.ConfigScreenFactory.ArsNSpellsConfigScreen screen)
            screen.onSettings(snapshot);
    }

    public static void reset() {
        current = null;
    }
}
