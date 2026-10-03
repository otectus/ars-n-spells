package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.contract.AnsModifierIds;
import com.otectus.arsnspells.contract.FeatureCleanup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Function;

/**
 * The loader implementation of {@link FeatureCleanup} (audit V14), the NeoForge counterpart of
 * the Forge 1.20.1 class of the same name.
 *
 * <p>Closes the finding that disabling a feature could leave its bonus on the player, because
 * every cleanup path was guarded by the same condition that had enabled the feature.
 * {@link #removeAll} consults no config, no mode, and no mod list. It walks every key in
 * {@link AnsModifierIds}, every identity of each key, and every registered attribute, and
 * removes what it finds. It is safe when the feature never applied anything, when the target
 * mod is absent, and on repeat calls.
 */
public final class AnsFeatureCleanup implements FeatureCleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnsFeatureCleanup.class);

    private final Function<UUID, Player> resolver;

    private AnsFeatureCleanup(Function<UUID, Player> resolver) {
        this.resolver = resolver;
    }

    /** A cleanup bound to one player. */
    public static AnsFeatureCleanup forPlayer(Player player) {
        return new AnsFeatureCleanup(uuid -> uuid.equals(player.getUUID()) ? player : null);
    }

    /** A cleanup that resolves any online player, for the config-reload sweep. */
    public static AnsFeatureCleanup forServer(MinecraftServer server) {
        return new AnsFeatureCleanup(uuid -> server.getPlayerList().getPlayer(uuid));
    }

    @Override
    public void removeAll(UUID player) {
        Player resolved = player == null ? null : resolver.apply(player);
        if (resolved != null) {
            removeAll(resolved);
        }
    }

    /**
     * Remove every ANS-owned modifier from {@code player}, under every key and every legacy
     * identity. No gate of any kind.
     *
     * @return how many modifiers were actually removed
     */
    public static int removeAll(Player player) {
        return removeKeys(player, AnsModifierIds.allKeys().toArray(new String[0]));
    }

    /**
     * Remove only the named keys, still unconditionally and still including each key's legacy
     * identities. Used by the narrower callers that must leave a sibling modifier in place.
     *
     * @return how many modifiers were actually removed
     */
    public static int removeKeys(Player player, String... keys) {
        if (player == null || keys == null) {
            return 0;
        }
        int removed = 0;
        for (String key : keys) {
            var identities = AnsModifierIdentities.identitiesFor(key);
            for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                for (ResourceLocation id : identities) {
                    if (instance.getModifier(id) != null) {
                        instance.removeModifier(id);
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
            LOGGER.debug("Removed {} ANS attribute modifier(s) from {}",
                removed, player.getName().getString());
        }
        return removed;
    }

    /** How many ANS-owned modifiers are currently sitting on {@code player}. Diagnostic only. */
    public static int countRemaining(Player player) {
        if (player == null) {
            return 0;
        }
        int found = 0;
        var identities = AnsModifierIdentities.allIdentities();
        for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (ResourceLocation id : identities) {
                if (instance.getModifier(id) != null) {
                    found++;
                }
            }
        }
        return found;
    }
}
