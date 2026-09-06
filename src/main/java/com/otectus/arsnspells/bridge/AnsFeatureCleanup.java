package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.contract.AnsModifierIds;
import com.otectus.arsnspells.contract.FeatureCleanup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Function;

/**
 * The loader implementation of {@link FeatureCleanup} (audit V14).
 *
 * <p>Closes the finding that disabling a feature could leave its bonus on the player, because
 * every cleanup path was guarded by the same condition that had enabled the feature. Turn
 * unification off, switch mode, or take a ring away and the guard now evaluated false, so the
 * removal never ran and the transient modifier stayed on the attribute map until the player
 * entity was rebuilt.
 *
 * <p>{@link #removeAll} consults no config, no mode, and no mod list. It walks every key in
 * {@link AnsModifierIds}, every historical identity of each key, and every attribute that key's
 * modifier can sit on, and removes what it finds. It is safe when the feature never applied
 * anything, when the target mod is absent, and on repeat calls.
 *
 * <p><b>Iron's-absent safety.</b> Attributes are resolved by {@code ResourceLocation} through
 * {@code ForgeRegistries}, never by naming Iron's {@code AttributeRegistry}. An unresolvable
 * attribute is skipped. That is what lets this class - and therefore the config-reload cleanup
 * handler that calls it - load and run on a server with no Iron's Spellbooks installed, which
 * is precisely the install where a leftover modifier can no longer be removed by the feature
 * that applied it.
 *
 * <p>Progression <em>data</em> is untouched. Only the transient modifier goes; the persisted
 * cast counts stay, so re-enabling the feature restores the same bonus.
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
            for (String target : AnsModifierIdentities.targetAttributesFor(key)) {
                Attribute attribute = attribute(target);
                if (attribute == null) {
                    continue;
                }
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                for (String identityKey : AnsModifierIds.currentAndLegacyKeysFor(key)) {
                    UUID id = AnsModifierIdentities.MAPPER.map(identityKey);
                    if (id == null) {
                        // A legacy key that only the ResourceLocation loader can express.
                        continue;
                    }
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
        for (String key : AnsModifierIds.allKeys()) {
            for (String target : AnsModifierIdentities.targetAttributesFor(key)) {
                Attribute attribute = attribute(target);
                if (attribute == null) {
                    continue;
                }
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                for (String identityKey : AnsModifierIds.currentAndLegacyKeysFor(key)) {
                    UUID id = AnsModifierIdentities.MAPPER.map(identityKey);
                    if (id != null && instance.getModifier(id) != null) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    private static Attribute attribute(String target) {
        ResourceLocation id = ResourceLocation.tryParse(target);
        return id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
    }
}
