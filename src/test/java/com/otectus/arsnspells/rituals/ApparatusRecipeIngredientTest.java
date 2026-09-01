package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit F1 - the apparatus recipes must not reference {@code irons_spellbooks:spell_book}.
 *
 * <p>Iron's registers only tiered books ({@code copper_spell_book}, {@code iron_spell_book},
 * ...), so that id fails ingredient resolution with a parse error whenever Iron's IS loaded -
 * making both rituals uncraftable in exactly the configuration they exist for.
 *
 * <p>The fix is a mod-owned item tag, {@code ars_n_spells:irons_spell_books}, whose entries are
 * all {@code "required": false}: the tag file itself loads unconditionally (only the recipes
 * are behind a mod-loaded condition), so mandatory entries would error on Iron's-less servers.
 */
class ApparatusRecipeIngredientTest {

    private static final Path TAG_PATH = TestPaths.of(
        "src/main/resources/data/ars_n_spells/tags/item/irons_spell_books.json");
    private static final String[] RECIPES = {
        "src/main/resources/data/ars_n_spells/recipe/apparatus/spell_transcription.json",
        "src/main/resources/data/ars_n_spells/recipe/apparatus/spellbook_binding.json",
    };

    @Test
    void recipes_useTagInsteadOfUnregisteredItem() throws IOException {
        for (String path : RECIPES) {
            String json = Files.readString(TestPaths.of(path));
            assertFalse(json.contains("irons_spellbooks:spell_book\""),
                path + " must not reference the unregistered item irons_spellbooks:spell_book");
            assertTrue(json.contains("ars_n_spells:irons_spell_books"),
                path + " must reference the ars_n_spells:irons_spell_books item tag");
        }
    }

    @Test
    void tag_entriesAreAllOptionalIronsBooks() throws IOException {
        String json = Files.readString(TAG_PATH);
        Matcher m = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"required\"\\s*:\\s*(true|false)\\s*}")
            .matcher(json);
        int count = 0;
        while (m.find()) {
            count++;
            String id = m.group(1);
            assertTrue(id.startsWith("irons_spellbooks:") && id.endsWith("_spell_book"),
                "tag entry must be a tiered Iron's spell book: " + id);
            assertEquals("false", m.group(2),
                "tag entry must be optional so the tag loads without Iron's: " + id);
        }
        assertEquals(16, count,
            "tag must list the 16 tiered Iron's spell books as optional entries");
        assertFalse(json.matches("(?s).*\\[\\s*\"irons_spellbooks.*"),
            "tag must not contain bare (implicitly required) entries");
    }

    @Test
    void packMcmeta_declaresThe121DataPackFormat() throws IOException {
        String json = Files.readString(TestPaths.of("src/main/resources/pack.mcmeta"));
        assertTrue(json.contains("\"pack_format\": 34"),
            "1.21.1 pack format is 34");
    }
}
