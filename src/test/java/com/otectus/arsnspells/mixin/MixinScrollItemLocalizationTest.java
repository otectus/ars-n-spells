package com.otectus.arsnspells.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit F9 (ANS-LOW-008) — the scroll LP messages must use translation keys, not
 * hardcoded English {@code Component.literal} strings. The three keys already exist
 * in {@code en_us.json}; this test keeps the mixin from regressing to literals.
 */
class MixinScrollItemLocalizationTest {

    private static final String MIXIN_PATH =
        "src/main/java/com/otectus/arsnspells/mixin/irons/MixinScrollItem.java";
    private static final String LANG_PATH =
        "src/main/resources/assets/ars_n_spells/lang/en_us.json";

    @Test
    void scrollMessages_useTranslatableComponents() throws IOException {
        String source = Files.readString(Paths.get(MIXIN_PATH));
        assertFalse(source.contains("Component.literal"),
            "MixinScrollItem must not build player-facing messages with Component.literal (audit F9)");
        for (String key : new String[] {
                "message.ars_n_spells.lp.scroll_cancelled",
                "message.ars_n_spells.lp.death",
                "message.ars_n_spells.lp.consumed"}) {
            assertTrue(source.contains("\"" + key + "\""),
                "MixinScrollItem must reference translation key " + key);
        }
    }

    @Test
    void translationKeys_existInLangFile() throws IOException {
        String lang = Files.readString(Paths.get(LANG_PATH));
        for (String key : new String[] {
                "message.ars_n_spells.lp.scroll_cancelled",
                "message.ars_n_spells.lp.death",
                "message.ars_n_spells.lp.consumed"}) {
            assertTrue(lang.contains("\"" + key + "\""), "en_us.json must define " + key);
        }
    }
}
