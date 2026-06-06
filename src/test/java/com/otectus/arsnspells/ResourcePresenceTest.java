package com.otectus.arsnspells;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards against the failure mode described in the compatibility plan (§2.7 /
 * §7.6): data files silently missing from the built artifact, or shipped at a
 * stale directory path that no longer loads. The test source set inherits
 * {@code main.output}, so every resource asserted here is checked on the same
 * classpath the jar ships.
 *
 * <p>It also pins the 1.20.1 -> 1.21 directory flattening: 1.21 reads singular
 * {@code recipe/} and {@code tags/item/} (verified against the Ars Nouveau and
 * Iron's Spellbooks 1.21.1 jars). The old plural paths build green but never
 * load at runtime, so they must NOT be present.
 */
class ResourcePresenceTest {

    private static InputStream res(String path) {
        return ResourcePresenceTest.class.getResourceAsStream(path);
    }

    @Test
    void shippedJsonResourcesExistAndParse() {
        String[] jsonResources = {
            "/data/ars_n_spells/recipe/apparatus/spell_transcription.json",
            "/data/ars_n_spells/recipe/apparatus/spell_uninscription.json",
            "/data/ars_n_spells/tags/item/curio_spell_discount.json",
            "/data/ars_n_spells/tags/entity_type/magical_companions.json",
            "/assets/ars_n_spells/lang/en_us.json",
            "/ars_n_spells.mixins.json",
            "/pack.mcmeta",
        };
        for (String path : jsonResources) {
            try (InputStream in = res(path)) {
                assertNotNull(in, "Missing resource on classpath: " + path);
                JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception e) {
                fail("Resource " + path + " is missing or not valid JSON: " + e);
            }
        }
    }

    @Test
    void modMetadataIsPresent() {
        try (InputStream in = res("/META-INF/neoforge.mods.toml")) {
            assertNotNull(in, "neoforge.mods.toml must ship in the jar");
        } catch (Exception e) {
            fail("neoforge.mods.toml not readable: " + e);
        }
    }

    @Test
    void dataDirsUseSingular121Paths() {
        // Present at the correct singular 1.21 paths...
        assertNotNull(res("/data/ars_n_spells/recipe/apparatus/spell_transcription.json"),
            "transcription recipe must be at singular recipe/ for 1.21.1");
        assertNotNull(res("/data/ars_n_spells/tags/item/curio_spell_discount.json"),
            "curio discount tag must be at singular tags/item/ for 1.21.1");
        // ...and absent at the stale plural paths that 1.21 ignores.
        assertNull(res("/data/ars_n_spells/recipes/apparatus/spell_transcription.json"),
            "stale plural recipes/ path must not ship (1.21 ignores it)");
        assertNull(res("/data/ars_n_spells/tags/items/curio_spell_discount.json"),
            "stale plural tags/items/ path must not ship (1.21 ignores it)");
    }
}
