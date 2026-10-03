package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.contract.AnsModifierIds;
import com.otectus.arsnspells.contract.ModifierKeyMapper;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The NeoForge 1.21.1 half of the shared modifier registry (audit V07, V14).
 *
 * <p>{@link AnsModifierIds} owns the modifier keys loader-neutrally, as strings. This class is
 * this loader's one {@link ModifierKeyMapper}: it turns a key into the {@link ResourceLocation}
 * that 1.21.1 identifies an {@code AttributeModifier} by. The Forge 1.20.1 build maps the same
 * keys onto {@code UUID}s; neither build keeps a second literal list.
 */
public final class AnsModifierIdentities implements ModifierKeyMapper<ResourceLocation> {

    /** The single instance; the mapper is stateless. */
    public static final AnsModifierIdentities MAPPER = new AnsModifierIdentities();

    private AnsModifierIdentities() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>Current keys are {@code namespace:path} strings and map to that location. A legacy key
     * that is a {@code UUID} or a display name belongs to the Forge build and correctly maps to
     * {@code null} here rather than being parsed into an unrelated {@code minecraft:} location.
     */
    @Override
    public ResourceLocation map(String key) {
        if (key == null || key.indexOf(':') < 0) {
            return null;
        }
        return ResourceLocation.tryParse(key);
    }

    /** Every identity, current and legacy, that ANS may have written on this loader. */
    public static Set<ResourceLocation> allIdentities() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String key : AnsModifierIds.allKeys()) {
            ids.addAll(identitiesFor(key));
        }
        return ids;
    }

    /** The current and legacy identities of one key. */
    public static Set<ResourceLocation> identitiesFor(String key) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String candidate : AnsModifierIds.currentAndLegacyKeysFor(key)) {
            ResourceLocation id = MAPPER.map(candidate);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }
}
