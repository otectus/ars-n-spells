package com.otectus.arsnspells.modifier;

import com.otectus.arsnspells.contract.FeatureCleanup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The NeoForge 1.21.1 half of {@link FeatureCleanup} (audit V14).
 *
 * <p><b>No feature gate, anywhere in this class.</b> That is the whole point of the finding:
 * every cleanup path used to be guarded by the same condition that had enabled the feature, so
 * turning the feature off, or removing the mod it bridged to, made the guard false and the
 * removal never ran - leaving the bonus applied forever. {@link #removeAll} consults no config
 * and asks no question about which mods are loaded; it walks every id
 * {@link AnsModifierIdMapper#allIds()} knows about, current and legacy, on every attribute the
 * player has.
 *
 * <p>Only <em>transient modifiers</em> are removed. Persistent state - the progression
 * attachment's cast counts, affinity levels - is untouched, so disabling a feature and
 * re-enabling it restores the same bonuses rather than starting the player over.
 *
 * <p>Iron's-safe without Iron's: the attributes are reached through
 * {@link BuiltInRegistries#ATTRIBUTE}, so nothing here classloads an Iron's type and an
 * Iron's-less install simply finds no matching attribute instances.
 */
public final class AnsFeatureCleanup implements FeatureCleanup {

    /** Stateless; one instance so callers need not allocate. */
    public static final AnsFeatureCleanup INSTANCE = new AnsFeatureCleanup();

    private AnsFeatureCleanup() {}

    @Override
    public void removeAll(UUID player) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || player == null) {
            return;
        }
        removeAll(server.getPlayerList().getPlayer(player));
    }

    /**
     * Remove every ANS modifier, under every identity, from every attribute this player has.
     *
     * <p>Safe to call when nothing was ever applied, when the features are disabled, and
     * repeatedly - which is what lets the on-disable, on-logout and on-mode-change paths all
     * be the same call.
     */
    public static void removeAll(Player player) {
        removeIds(player, AnsModifierIdMapper.INSTANCE.allIds());
    }

    /**
     * The {@link #removeAll} machinery restricted to one feature's identities, for callers that
     * rebuild their own modifier immediately afterwards and must not disturb another feature's.
     */
    public static void removeIds(Player player, Collection<ResourceLocation> ids) {
        if (player == null || ids == null || ids.isEmpty()) {
            return;
        }
        for (AttributeInstance instance : attributesOf(player)) {
            for (ResourceLocation id : ids) {
                instance.removeModifier(id);
            }
        }
    }

    /** How many ANS modifiers are on this player - 0 is the post-cleanup invariant. */
    public static int ansModifierCount(Player player) {
        if (player == null) {
            return 0;
        }
        List<ResourceLocation> ids = AnsModifierIdMapper.INSTANCE.allIds();
        int count = 0;
        for (AttributeInstance instance : attributesOf(player)) {
            for (ResourceLocation id : ids) {
                if (instance.getModifier(id) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Every attribute instance the player actually has.
     *
     * <p>{@code AttributeMap} exposes no view of its instances in 1.21.1 -
     * {@code getSyncableAttributes} is only the client-visible subset - so the registry is
     * walked instead and {@code getAttribute} filters it down to the ones this entity supports.
     * A registry with a few dozen entries, on paths that run at most once per player per config
     * reload or logout.
     */
    private static List<AttributeInstance> attributesOf(Player player) {
        List<AttributeInstance> instances = new java.util.ArrayList<>();
        for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instances.add(instance);
            }
        }
        return instances;
    }
}
