package com.otectus.arsnspells.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-001 — verifies that {@code spell_transcription.json} is now wrapped in
 * a {@code forge:conditional} envelope with a {@code forge:mod_loaded} predicate.
 *
 * <p>Without this, the recipe would fail to load on Iron's-less servers
 * (Iron's is {@code mandatory=false} per mods.toml), spamming an error in the
 * recipe manager about the unknown {@code irons_spellbooks:spell_book} item.
 */
class SpellTranscriptionRecipeConditionalTest {

    @Test
    void recipe_isWrappedInForgeConditional() throws IOException {
        String json = Files.readString(Paths.get(
            "src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json"));
        assertTrue(json.contains("\"forge:conditional\""),
            "spell_transcription.json must use the forge:conditional envelope (ANS-HIGH-001)");
        assertTrue(json.contains("\"forge:mod_loaded\""),
            "the conditional must use forge:mod_loaded predicate");
        assertTrue(json.contains("\"modid\": \"irons_spellbooks\""),
            "the predicate must target irons_spellbooks");
        // The inner recipe should still be ars_nouveau:enchanting_apparatus
        assertTrue(json.contains("\"ars_nouveau:enchanting_apparatus\""),
            "the inner recipe must still be ars_nouveau:enchanting_apparatus");
    }
}
