package com.otectus.arsnspells.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.catalog.SpellInventoryEntry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The shipped imported-glyph catalog against the Iron's registry actually loaded.
 *
 * <p>With {@code -PglyphCatalogDump=<file>} the inventory is also written out, which is how the
 * committed catalog is regenerated ({@code tools/generate_iron_glyph_catalog.py}).
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class IronGlyphCatalogGameTests {

    private IronGlyphCatalogGameTests() {
    }

    @GameTest(template = "platform", batch = "ans_glyph_catalog")
    public static void ironsLoaded_inventoryDumpAndCatalogCoverage(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        List<SpellInventoryEntry> inventory = IronGlyphBridges.get().inventory();
        helper.assertTrue(!inventory.isEmpty(), "the loaded Iron's registry must not be empty");
        String dump = System.getProperty("ans.glyphCatalogDump", "");
        if (!dump.isBlank()) {
            JsonObject root = new JsonObject();
            root.addProperty("mod_id", IronsCompat.MODID);
            root.addProperty("mod_version", ModList.get().getModContainerById(IronsCompat.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
            JsonArray spells = new JsonArray();
            inventory.forEach(e -> spells.add(e.toJson()));
            root.add("spells", spells);
            try {
                Files.writeString(Path.of(dump), new GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardCharsets.UTF_8);
            } catch (Exception e) {
                helper.fail("could not write the inventory dump to " + dump + ": " + e);
                return;
            }
        }
        helper.succeed();
    }
}
