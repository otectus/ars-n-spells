package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the loaded Iron's spell inventory to {@code <run dir>/ans-iron-spell-inventory.json}.
 * The committed imported-glyph catalog is generated from these files (see
 * {@code tools/generate_iron_glyph_catalog.py}); this test only records, it asserts nothing
 * beyond the write succeeding.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class GlyphCatalogInventoryGameTests {
    @GameTest(template = "platform")
    public static void ironsLoaded_writeLoadedSpellInventory(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        try {
            String json = com.otectus.arsnspells.compat.irons.glyph.IronsSpellInventory.toJson();
            Files.writeString(Path.of("ans-iron-spell-inventory.json"), json, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            helper.fail("could not write the Iron's spell inventory: " + failure);
            return;
        }
        helper.succeed();
    }
}
