package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariants behind the mod's creative tab that a dedicated server can never observe.
 *
 * <p>{@code CreativeTabGameTests} is the behavioural proof - it builds the real tabs and fires
 * the real {@code BuildCreativeModeTabContentsEvent}. Two things fall outside its reach, and
 * they are what this covers: the tab title, which lives in a client asset a GameTest server
 * never loads, and the "null-safe by construction" property of the display generator, which is
 * about how the code is written rather than what it does on the profile under test.
 *
 * <p>Deliberately a file read rather than a class load, matching {@link ItemAssetCompletenessTest}:
 * {@code ModCreativeTabs} holds a {@code DeferredRegister} whose static initialiser needs a live
 * registry.
 */
class CreativeTabWiringTest {

    private static final Path LANG =
        TestPaths.of("src/main/resources/assets/ars_n_spells/lang/en_us.json");
    private static final Path TABS =
        TestPaths.of("src/main/java/com/otectus/arsnspells/registry/ModCreativeTabs.java");

    /** Must match the key built in {@code ModCreativeTabs}. */
    private static final String TAB_TITLE_KEY = "itemGroup.ars_n_spells";

    private static String codeOf(Path path) throws IOException {
        return Files.readString(path)
            .replaceAll("(?s)/[*].*?[*]/", "")
            .replaceAll("(?m)//.*$", "");
    }

    @Test
    void creativeTabTitle_existsInLangFile() throws IOException {
        assertTrue(Files.readString(LANG).contains("\"" + TAB_TITLE_KEY + "\""),
            "en_us.json must define " + TAB_TITLE_KEY + ", or the creative tab shows its raw "
                + "translation key as its label");
    }

    /**
     * The title key is built as {@code "itemGroup." + ArsNSpells.MODID}. If that becomes a
     * literal or a different prefix, the key above stops being the one the game looks up and
     * the test above would keep passing against a dead key.
     */
    @Test
    void creativeTab_derivesItsTitleKeyFromTheModId() throws IOException {
        assertTrue(Files.readString(TABS).contains("\"itemGroup.\" + ArsNSpells.MODID"),
            "ModCreativeTabs must build its title key as \"itemGroup.\" + ArsNSpells.MODID so it "
                + "stays in step with " + TAB_TITLE_KEY + " in en_us.json");
    }

    /**
     * The display generator iterates the DeferredRegister instead of naming items. That is what
     * makes it null-safe without Iron's Spellbooks: the four Iron's-gated tablets are simply
     * not in the register to iterate, whereas the per-item accessors return null on an
     * Iron's-less install and would NPE while the creative menu is being built.
     */
    @Test
    void creativeTab_doesNotNameTheIronsGatedAccessors() throws IOException {
        String source = codeOf(TABS);
        for (String accessor : new String[] {
                "spellTranscriptionTablet", "spellbookBindingTablet",
                "manaInfusionTablet", "manaWellTablet"}) {
            assertFalse(source.contains(accessor + "()"),
                "ModCreativeTabs must not call ModItemsRegistry." + accessor + "() - it returns "
                    + "null when irons_spellbooks is absent. Iterate the item register instead, "
                    + "which simply has no entry for a tablet that was never registered.");
        }
    }
}
