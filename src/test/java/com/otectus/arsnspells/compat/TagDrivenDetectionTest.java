package com.otectus.arsnspells.compat;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit F-1 / F-2 - the mod's item and block detection must be tag-driven, so a pack can extend
 * it, rather than hardcoded registry-name sets or registry-path substring matches.
 *
 * <p>A substring match is the specific antipattern here: {@code path.contains("source_jar")}
 * silently catches an unrelated block from another mod, and silently misses a Source Jar an
 * addon registers under a different name. A tag does neither, and a datapack can correct it
 * without a code change.
 *
 * <p>The 1.20.1 version of this file also covered the Cursed Ring / Virtue Ring / Blasphemy
 * curio tags. Covenant of the Seven has no 1.21.1 release, so those tags and their detection
 * are not part of this build.
 */
class TagDrivenDetectionTest {

    private static String read(String path) throws IOException {
        return Files.readString(TestPaths.of(path));
    }

    @Test
    void sourceJarScan_usesABlockTagNotASubstring() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java");
        assertTrue(src.contains("ModTags.SOURCE_JARS"),
            "the synergy scan must check the ars_n_spells:source_jars block tag");
        assertFalse(src.contains("contains(\"source_jar\")"),
            "the registry-path substring match must be gone (audit F-2) - it both over- and "
                + "under-matches, and a pack cannot correct either");
    }

    @Test
    void curioDiscount_usesAnItemTag() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/events/CurioDiscountHandler.java");
        // The key is declared on the handler rather than in ModTags, which is fine - what
        // matters is that the decision is made by tag membership rather than by a hardcoded
        // id set, since that tag is the entire extension point for packs.
        assertTrue(src.contains("curio_spell_discount") && src.contains("TagKey"),
            "curio discounts must be driven by the ars_n_spells:curio_spell_discount item tag");
        assertFalse(src.contains("CURSED_RING_IDS") || src.contains("BLASPHEMY_IDS"),
            "the hardcoded ResourceLocation sets must stay gone (audit F-1)");
    }

    @Test
    void shippedTagJsons_mergeAndTolerateAbsentMods() throws IOException {
        for (String tag : new String[] {
                "src/main/resources/data/ars_n_spells/tags/block/source_jars.json",
                "src/main/resources/data/ars_n_spells/tags/item/curio_spell_discount.json",
                "src/main/resources/data/ars_n_spells/tags/item/cross_cast_blacklist.json",
                "src/main/resources/data/ars_n_spells/tags/item/irons_spell_books.json"}) {
            Path p = TestPaths.of(tag);
            assertTrue(Files.exists(p), tag + " must ship with the mod");
            String json = Files.readString(p);
            assertTrue(json.contains("\"replace\": false"),
                tag + " must merge with, not clobber, datapack additions");
            // Entries naming an optional mod must be optional, or the tag itself errors on an
            // install without that mod - and tags load unconditionally, unlike recipes.
            if (json.contains("irons_spellbooks:") || json.contains("ars_nouveau:")) {
                assertTrue(json.contains("\"required\": false") || json.contains("#"),
                    tag + " names another mod's content, so its entries must be optional or "
                        + "tag references");
            }
        }
    }
}
