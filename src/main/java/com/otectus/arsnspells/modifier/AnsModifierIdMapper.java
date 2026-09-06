package com.otectus.arsnspells.modifier;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.contract.AnsModifierIds;
import com.otectus.arsnspells.contract.ModifierKeyMapper;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The NeoForge 1.21.1 half of {@link ModifierKeyMapper}: a contract modifier key becomes a
 * {@link ResourceLocation} (audit V07, V14).
 *
 * <p>1.21 replaced UUID-keyed {@code AttributeModifier}s with {@code ResourceLocation}-keyed
 * ones, so only the namespaced keys in {@link AnsModifierIds} mean anything here. The legacy
 * {@code UUID} and display-name keys are the Forge 1.20.1 line's identities; they map to
 * {@code null}, as the interface allows, rather than being coerced into a bogus
 * {@code minecraft:d3e1f1d1-...} location (a raw UUID string is, unhelpfully, a valid path).
 *
 * <h2>Loader-local legacy identities</h2>
 * A modifier can also have a historical identity that only ever existed on <em>this</em>
 * loader, and so has no business in the loader-neutral contract list. The cross-mod school
 * progression bonus has exactly one: this port shipped it under two distinct
 * {@code ResourceLocation}s at once - {@code cross_mod_school_progression} from
 * {@code ProgressionAttributes} and {@code progression_element_xp} from
 * {@code ProgressionHandler} - so one bonus could be applied twice and only ever half removed.
 * {@link #mapAll} yields both, and cleanup removes both, which is what collapses the two
 * identities onto the one canonical id.
 */
public final class AnsModifierIdMapper implements ModifierKeyMapper<ResourceLocation> {

    /** Stateless; one instance so callers need not allocate. */
    public static final AnsModifierIdMapper INSTANCE = new AnsModifierIdMapper();

    /**
     * Historical {@code ResourceLocation} identities that never existed on Forge 1.20.1, keyed
     * by the contract key they are an alias of. Never shortened, for the same reason
     * {@link AnsModifierIds} never deletes a legacy key: it is the only record that a modifier
     * under that identity may still be on a player.
     */
    private static final Map<String, List<String>> LOADER_LEGACY_KEYS = Map.of(
        AnsModifierIds.CROSS_MOD_SCHOOL_PROGRESSION,
        List.of(ArsNSpells.MODID + ":progression_element_xp"));

    private AnsModifierIdMapper() {}

    @Override
    public ResourceLocation map(String key) {
        if (key == null || key.indexOf(':') < 0) {
            // No namespace: a bare UUID or a 1.20.1 display name. Not an identity on this loader.
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id != null && ArsNSpells.MODID.equals(id.getNamespace()) ? id : null;
    }

    /**
     * Every {@code ResourceLocation} this modifier has ever been written under on this loader:
     * the current key first, then the contract's legacy keys, then this loader's own. Keys with
     * no representation here are dropped.
     */
    public List<ResourceLocation> mapAll(String key) {
        Collection<String> keys = new LinkedHashSet<>(AnsModifierIds.currentAndLegacyKeysFor(key));
        keys.addAll(LOADER_LEGACY_KEYS.getOrDefault(key, List.of()));
        List<ResourceLocation> ids = new ArrayList<>(keys.size());
        for (String candidate : keys) {
            ResourceLocation id = map(candidate);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    /** Every modifier identity ANS owns on this loader - what unconditional cleanup walks. */
    public List<ResourceLocation> allIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String key : AnsModifierIds.allKeys()) {
            for (ResourceLocation id : mapAll(key)) {
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return List.copyOf(ids);
    }
}
