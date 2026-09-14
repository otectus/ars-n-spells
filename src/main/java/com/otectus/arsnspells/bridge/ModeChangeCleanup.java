package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.contract.AnsModifierIds;
import com.otectus.arsnspells.events.CapabilityResyncHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Remove all current/legacy owned contributions, reapply enabled sources, clamp and sync. */
public final class ModeChangeCleanup {
    private ModeChangeCleanup() {}
    private static java.util.Set<ResourceLocation> ids() {
        var ids = new java.util.HashSet<ResourceLocation>();
        for (String key : AnsModifierIds.allKeys()) for (String candidate : AnsModifierIds.currentAndLegacyKeysFor(key)) {
            ResourceLocation id = ResourceLocation.tryParse(candidate);
            if (id != null) ids.add(id);
        }
        return ids;
    }
    public static void reconcileAll() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (!server.isSameThread()) { server.execute(ModeChangeCleanup::reconcileAll); return; }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) reconcile(player);
    }
    public static void reconcile(ServerPlayer player) {
        var owned = ids();
        BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
            var instance = player.getAttribute(holder);
            if (instance == null) return;
            for (ResourceLocation id : owned) if (instance.getModifier(id) != null) instance.removeModifier(id);
        });
        // This replays live equipment, active potion/perk attributes, progression and client snapshots.
        CapabilityResyncHandler.resync(player);
        var bridge = BridgeManager.getBridge();
        float current = bridge.getMana(player), maximum = bridge.getMaxMana(player);
        if (Float.isFinite(current) && Float.isFinite(maximum)) bridge.setMana(player, Math.max(0, Math.min(current, maximum)));
        NativeManaSync.send(player);
    }
}
