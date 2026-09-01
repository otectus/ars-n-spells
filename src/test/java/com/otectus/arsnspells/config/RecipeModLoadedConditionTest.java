package com.otectus.arsnspells.config;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-001 - every recipe that needs Iron's Spellbooks must be behind a mod-loaded
 * condition.
 *
 * <p>Iron's is an optional dependency, so without the condition these recipes fail to load on
 * an Iron's-less server and spam the recipe manager with unresolvable-ingredient errors.
 *
 * <p><b>Port divergence.</b> The 1.20.1 line wrapped the whole recipe in a
 * {@code forge:conditional} envelope with a {@code forge:mod_loaded} predicate. 1.21.1 flattens
 * that: the recipe stays at the top level and carries a {@code neoforge:conditions} array. Same
 * guarantee, different shape.
 */
class RecipeModLoadedConditionTest {

    /** Recipes whose ingredients only exist when Iron's is installed. */
    private static final String[] IRONS_GATED = {
        "src/main/resources/data/ars_n_spells/recipe/apparatus/spell_transcription.json",
        "src/main/resources/data/ars_n_spells/recipe/apparatus/spellbook_binding.json",
    };

    @Test
    void ironsGatedRecipesCarryAModLoadedCondition() throws IOException {
        for (String path : IRONS_GATED) {
            String json = Files.readString(TestPaths.of(path));
            assertTrue(json.contains("neoforge:conditions"),
                path + " must carry a neoforge:conditions array (ANS-HIGH-001)");
            assertTrue(json.contains("neoforge:mod_loaded"),
                path + " must use the mod_loaded condition type");
            assertTrue(json.contains("irons_spellbooks"),
                path + " must target irons_spellbooks in its condition");
            assertFalse(json.contains("forge:conditional"),
                path + " still uses the 1.20.1 conditional envelope, which 1.21.1 ignores - "
                    + "meaning the recipe would load unconditionally and error without Iron's");
            assertTrue(json.contains("ars_nouveau:enchanting_apparatus"),
                path + " must still be an enchanting apparatus recipe");
        }
    }
}
