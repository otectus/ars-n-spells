package com.otectus.arsnspells.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariants behind the mod's creative tab that a dedicated server can never observe.
 *
 * <p>{@code CreativeTabGameTests} is the behavioural proof — it builds the real tabs and fires
 * the real {@code BuildCreativeModeTabContentsEvent}. Two things fall outside its reach, and
 * they are what this covers: the tab title, which lives in a client asset a GameTest server
 * never loads, and the "null-safe by construction" property of the display generator, which is
 * about how the code is written rather than what it does on the profile under test.
 *
 * <p>Deliberately a file read rather than a class load, matching {@link ItemAssetCompletenessTest}:
 * {@link ModCreativeTabs} holds a {@code DeferredRegister} whose static initialiser needs a live
 * Forge registry.
 */
class CreativeTabWiringTest {

    private static final Path LANG =
        Paths.get("src/main/resources/assets/ars_n_spells/lang/en_us.json");
    private static final Path TABS =
        Paths.get("src/main/java/com/otectus/arsnspells/registry/ModCreativeTabs.java");
    private static final Path BLOCKS =
        Paths.get("src/main/java/com/otectus/arsnspells/registry/ModBlocksRegistry.java");

    /** Must match the key built in {@code ModCreativeTabs.MAIN}. */
    private static final String TAB_TITLE_KEY = "itemGroup.ars_n_spells";

    @Test
    void creativeTabTitle_existsInLangFile() throws IOException {
        String lang = Files.readString(LANG);
        assertTrue(lang.contains("\"" + TAB_TITLE_KEY + "\""),
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
        String source = Files.readString(TABS);
        assertTrue(source.contains("\"itemGroup.\" + ArsNSpells.MODID"),
            "ModCreativeTabs must build its title key as \"itemGroup.\" + ArsNSpells.MODID so it "
                + "stays in step with " + TAB_TITLE_KEY + " in en_us.json");
    }

    /**
     * The display generator iterates the DeferredRegisters instead of naming items. That is what
     * makes it null-safe without Iron's Spellbooks: the four Iron's-gated tablets are simply not
     * in the register to iterate, whereas the per-item accessors return a <em>null</em>
     * RegistryObject on an Iron's-less install and would NPE.
     */
    @Test
    void creativeTab_doesNotNameTheIronsGatedAccessors() throws IOException {
        String source = codeOf(TABS);
        for (String accessor : new String[] {
                "spellTranscriptionTablet", "spellbookBindingTablet",
                "manaInfusionTablet", "manaWellTablet"}) {
            assertTrue(!source.contains(accessor + "()"),
                "ModCreativeTabs must not call ModItemsRegistry." + accessor + "() — it returns "
                    + "null when irons_spellbooks is absent. Iterate ModItemsRegistry.ITEMS "
                    + "instead, which simply has no entry for a tablet that was never registered.");
        }
    }

    /**
     * Items must reach the creative Search tab. They only do because the generator accepts them
     * with the default {@code PARENT_AND_SEARCH_TABS} visibility — Search is a union over the
     * category tabs' search sets, and ANS items are removed from every foreign tab, so ours is
     * their only route in.
     */
    @Test
    void creativeTab_addsItemsWithSearchVisibility() throws IOException {
        String source = codeOf(TABS);
        assertTrue(!source.contains("PARENT_TAB_ONLY"),
            "ModCreativeTabs must not add items with PARENT_TAB_ONLY visibility — the mod's own "
                + "tab is the only tab supplying them to creative search");
    }

    /** The Spell Loom's old vanilla placement is gone for good, not just commented out. */
    @Test
    void blockRegistrar_noLongerPlacesTheLoomInAVanillaTab() throws IOException {
        String source = codeOf(BLOCKS);
        assertTrue(!source.contains("BuildCreativeModeTabContentsEvent")
                && !source.contains("event.accept("),
            "ModBlocksRegistry must not place the Spell Loom into a creative tab — ModCreativeTabs "
                + "owns every ars_n_spells tab placement now");
    }

    /**
     * A file's source with comments stripped, so these checks read what the code does rather
     * than what the javadoc says about it — the javadoc on {@code ModCreativeTabs} names the
     * accessors precisely to explain why they are not called. Naive enough to also blank a
     * {@code //} inside a string literal; neither file under test has one.
     */
    private static String codeOf(Path file) throws IOException {
        return Files.readString(file)
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("//[^\\n]*", "");
    }
}
