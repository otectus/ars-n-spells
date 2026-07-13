package com.otectus.arsnspells;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            "/data/ars_n_spells/recipe/apparatus/spellbook_binding.json",
            "/data/ars_n_spells/recipe/spell_loom.json",
            "/data/ars_n_spells/tags/item/curio_spell_discount.json",
            "/data/ars_n_spells/tags/item/irons_spell_books.json",
            "/data/ars_n_spells/tags/entity_type/magical_companions.json",
            "/data/ars_n_spells/loot_table/blocks/spell_loom.json",
            "/data/ars_n_spells/advancement/recipes/misc/spell_loom.json",
            "/assets/ars_n_spells/blockstates/spell_loom.json",
            "/assets/ars_n_spells/models/block/spell_loom.json",
            "/assets/ars_n_spells/models/item/spell_loom.json",
            "/assets/ars_n_spells/models/item/spellbook_binding.json",
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
    void wheelIconTexturesShipForEveryWhitelistedKey() {
        // The icon mixin only returns paths built from these whitelists, and the
        // spell_icons/ars_cross_k.png files are Iron's un-mixed fallback path —
        // a missing file here means a purple checkerboard in the spell wheel.
        for (String symbol : com.otectus.arsnspells.spell.CrossModSpellComponents.ICON_SYMBOLS) {
            assertNotNull(res("/assets/ars_n_spells/textures/gui/icons/spell/icon_" + symbol + ".png"),
                "missing icon texture for whitelisted symbol: " + symbol);
        }
        for (String nature : com.otectus.arsnspells.spell.CrossModSpellComponents.NATURE_KEYS) {
            assertNotNull(res("/assets/ars_n_spells/textures/gui/icons/spell/nature_" + nature + ".png"),
                "missing nature texture for whitelisted key: " + nature);
        }
        assertNotNull(res("/assets/ars_n_spells/textures/gui/icons/spell/ars_cross_default.png"));
        for (int k = 1; k <= com.otectus.arsnspells.spell.CrossModSpellComponents.PROXY_POOL_SIZE; k++) {
            assertNotNull(res("/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_" + k + ".png"),
                "missing un-mixed fallback texture for proxy slot " + k);
        }
        for (String tex : new String[] {"spell_loom_top", "spell_loom_side", "spell_loom_bottom"}) {
            assertNotNull(res("/assets/ars_n_spells/textures/block/" + tex + ".png"),
                "missing Spell Loom block texture: " + tex);
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

    @Test
    void apparatusRecipesUseCurrent121Format() {
        // Guards the 1.20.1 -> 1.21 enchanting_apparatus format change. AN's codec
        // requires result{id,count} and reagent as a single object; the old
        // output{item} / reagent-array form is valid JSON but throws
        // "No key result" at runtime. (Existing presence test only checks JSON
        // validity, not schema — this closes that gap.)
        for (String path : new String[] {
            "/data/ars_n_spells/recipe/apparatus/spell_transcription.json",
            "/data/ars_n_spells/recipe/apparatus/spell_uninscription.json",
            "/data/ars_n_spells/recipe/apparatus/spellbook_binding.json",
        }) {
            try (InputStream in = res(path)) {
                assertNotNull(in, "Missing recipe on classpath: " + path);
                JsonObject o = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("ars_nouveau:enchanting_apparatus",
                    o.get("type").getAsString(), path + " wrong recipe type");
                assertFalse(o.has("output"), path + " uses the obsolete 'output' key (use 'result')");
                assertTrue(o.has("result"), path + " missing 'result'");
                JsonObject result = o.getAsJsonObject("result");
                assertNotNull(result.get("id"), path + " result missing 'id'");
                assertFalse(result.has("item"),
                    path + " result uses the obsolete 'item' key (use 'id'+'count')");
                assertTrue(o.get("reagent").isJsonObject(),
                    path + " 'reagent' must be a single object, not an array");
                assertTrue(o.getAsJsonObject("reagent").has("item"),
                    path + " reagent missing 'item'");
            } catch (Exception e) {
                fail("Recipe " + path + " failed schema check: " + e);
            }
        }
    }
}
