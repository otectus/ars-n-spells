package com.otectus.arsnspells.compat.irons_spells;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of the Iron's Spellbooks school registry, taken once at common setup
 * (after registries freeze). Used only for diagnostics/enumeration
 * ({@code /ans debug}, future advancements); affinity tracking itself never
 * consults this — it keys directly off the per-cast school id — so this class is
 * purely additive and off every hot path.
 *
 * <p><b>Iron's-only.</b> It imports {@link SchoolRegistry}, so it must only be
 * <em>referenced</em> behind a mod-presence gate. {@link #snapshot()} is invoked
 * from {@code ArsNSpells.commonSetup} inside an
 * {@code isLoaded("irons_spellbooks")} guard; when Iron's is absent the snapshot
 * never runs and {@link #allSchools()} returns empty.
 *
 * @since 2.5.0
 */
public final class SchoolIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchoolIndex.class);
    private static volatile List<String> ALL_SCHOOL_KEYS = List.of();

    private SchoolIndex() {}

    /** Iterates {@code SchoolRegistry.REGISTRY} once and caches every school id as a string. */
    public static void snapshot() {
        try {
            List<String> keys = new ArrayList<>();
            for (ResourceLocation id : SchoolRegistry.REGISTRY.keySet()) {
                keys.add(id.toString());
            }
            keys.sort(String::compareTo);
            ALL_SCHOOL_KEYS = List.copyOf(keys);
            LOGGER.info("[SchoolIndex] snapshotted {} Iron's schools", ALL_SCHOOL_KEYS.size());
        } catch (Throwable t) {
            // Diagnostics only — never let a registry-shape change crash setup.
            LOGGER.warn("[SchoolIndex] failed to snapshot Iron's school registry; roster unavailable", t);
            ALL_SCHOOL_KEYS = List.of();
        }
    }

    /** All registered Iron's school ids (sorted), or empty when Iron's is absent / not yet snapshotted. */
    public static List<String> allSchools() {
        return ALL_SCHOOL_KEYS;
    }
}
