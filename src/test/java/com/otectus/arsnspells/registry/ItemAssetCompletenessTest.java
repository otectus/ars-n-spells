package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.TestPaths;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every item the mod registers must ship a model and a texture.
 *
 * <p>The mod shipped three craftable ritual tablets with neither for four releases: the
 * {@code models/item} JSONs were lost when the 1.20.1 line was reconstructed at 3.0.0, and
 * {@code textures/item} did not exist at all. Nothing failed at build time, nothing logged
 * an error, and players just saw the purple-and-black missing-texture checkerboard in their
 * inventory and on the brazier.
 *
 * <p>Deliberately bootstrap-free: it reads the resource tree and the registrar source rather
 * than loading {@code ModItemsRegistry}, whose {@code DeferredRegister} static initialiser
 * needs a live Forge registry. The registry paths below are compile-time String constants,
 * so referencing them inlines the literal and loads no Ars classes either — but the count
 * check against the source keeps them honest if a new item is added.
 */
class ItemAssetCompletenessTest {

    private static final Path ASSETS = TestPaths.of("src/main/resources/assets/ars_n_spells");
    private static final Path MODELS = ASSETS.resolve("models/item");
    private static final Path TEXTURES = ASSETS.resolve("textures/item");

    /** Item registry paths published by {@link ModItemsRegistry}, plus the block item. */
    private static final List<String> ITEM_PATHS = List.of(
        com.otectus.arsnspells.rituals.SpellTranscriptionRitual.REGISTRY_PATH,
        com.otectus.arsnspells.rituals.SpellbookBindingRitual.REGISTRY_PATH,
        com.otectus.arsnspells.rituals.SpellUninscriptionRitual.REGISTRY_PATH,
        com.otectus.arsnspells.rituals.ManaInfusionRitual.REGISTRY_PATH,
        com.otectus.arsnspells.rituals.ManaWellRitual.REGISTRY_PATH
    );

    @Test
    void everyRegisteredItemHasAModel() {
        List<String> missing = new ArrayList<>();
        for (String path : ITEM_PATHS) {
            if (!Files.isRegularFile(MODELS.resolve(path + ".json"))) {
                missing.add("models/item/" + path + ".json");
            }
        }
        assertTrue(missing.isEmpty(),
            "registered items with no item model (they render as the missing-texture "
                + "checkerboard in game): " + missing);
    }

    @Test
    void everyItemModelResolvesItsLayer0Texture() throws IOException {
        List<String> broken = new ArrayList<>();
        for (String path : ITEM_PATHS) {
            Path model = MODELS.resolve(path + ".json");
            if (!Files.isRegularFile(model)) {
                continue;   // reported by everyRegisteredItemHasAModel
            }
            String json = Files.readString(model);
            Matcher m = Pattern.compile("\"layer0\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (!m.find()) {
                broken.add(path + ": model has no layer0 texture");
                continue;
            }
            String texture = m.group(1);          // e.g. ars_n_spells:item/spell_transcription
            String[] parts = texture.split(":", 2);
            String namespace = parts.length == 2 ? parts[0] : "minecraft";
            String texturePath = parts.length == 2 ? parts[1] : parts[0];
            if (!"ars_n_spells".equals(namespace)) {
                // A cross-namespace reference (e.g. into Ars Nouveau) cannot be checked from
                // here; it is resolved from the other mod's jar at runtime.
                continue;
            }
            Path png = ASSETS.resolve("textures").resolve(texturePath + ".png");
            if (!Files.isRegularFile(png)) {
                broken.add(path + ": layer0 " + texture + " has no file at " + png);
            }
        }
        assertTrue(broken.isEmpty(), "item models with unresolvable textures: " + broken);
    }

    @Test
    void texturesAndModelsDoNotDriftApart() throws IOException {
        if (!Files.isDirectory(TEXTURES)) {
            return;   // covered by everyRegisteredItemHasAModel
        }
        List<String> orphanTextures;
        try (Stream<Path> png = Files.list(TEXTURES)) {
            orphanTextures = png
                .filter(p -> p.getFileName().toString().endsWith(".png"))
                .map(p -> p.getFileName().toString().replace(".png", ""))
                .filter(name -> !Files.isRegularFile(MODELS.resolve(name + ".json")))
                .collect(Collectors.toList());
        }
        assertTrue(orphanTextures.isEmpty(),
            "item textures with no model referencing them: " + orphanTextures);
    }

    /**
     * If someone registers a sixth item, this fails and points them at the art. It is a
     * source check on purpose — the registrar cannot be class-loaded without Forge.
     */
    @Test
    void registrarPublishesExactlyTheItemsThisTestKnowsAbout() throws IOException {
        String source = Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/registry/ModItemsRegistry.java"));
        // Count the tablet suppliers, not `ITEMS.register(` — the latter also matches the
        // bus registration at the bottom of the class.
        long registrations = Pattern.compile("new RitualTablet\\(").matcher(source).results().count();
        assertEquals(ITEM_PATHS.size(), registrations,
            "ModItemsRegistry registers " + registrations + " items but this test knows about "
                + ITEM_PATHS.size() + ". Add the new item to ITEM_PATHS and ship a "
                + "models/item JSON plus a textures/item PNG for it.");
    }
}
