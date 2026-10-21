package com.otectus.arsnspells.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/<namespace>/ans_iron_glyphs/**.json} into a {@link GlyphMappingSnapshot}.
 * Each file is the top-most copy of its path (vanilla datapack replacement); its pack's position
 * in the active pack list is its rank, so a higher pack's file for a spell wins.
 *
 * <p>A candidate is validated as a whole. A valid one is published. An invalid one is rejected
 * and the last valid snapshot kept; at the first load of a server there is none, so the
 * candidate is published with every spell named by a broken file failing closed.
 */
public class ImportedGlyphReloadListener extends SimplePreparableReloadListener<List<GlyphMappingSnapshot.Source>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphReloadListener.class);
    public static final String FOLDER = "ans_iron_glyphs";
    private static final int LOGGED_ERRORS = 100;

    private static volatile GlyphMappingSnapshot lastValid;
    private static volatile List<String> lastRejected = List.of();

    @Override
    protected List<GlyphMappingSnapshot.Source> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Integer> ranks = new HashMap<>();
        manager.listPacks().forEach(pack -> ranks.putIfAbsent(pack.packId(), ranks.size()));
        List<GlyphMappingSnapshot.Source> sources = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation path = file.getKey();
            ResourceLocation id = new ResourceLocation(path.getNamespace(),
                path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length()));
            Resource resource = file.getValue();
            JsonElement json;
            try (Reader reader = resource.openAsReader()) {
                json = JsonParser.parseReader(reader);
            } catch (Exception malformed) {
                LOGGER.error("Imported-glyph mapping {} [{}] is not valid JSON: {}", id, resource.sourcePackId(), malformed.getMessage());
                json = JsonNull.INSTANCE;
            }
            sources.add(new GlyphMappingSnapshot.Source(id, resource.sourcePackId(), ranks.getOrDefault(resource.sourcePackId(), -1), json));
        }
        return sources;
    }

    @Override
    protected void apply(List<GlyphMappingSnapshot.Source> sources, ResourceManager manager, ProfilerFiller profiler) {
        GlyphMappingSnapshot candidate = GlyphMappingSnapshot.build(sources, ImportedGlyphCatalog.get(), GlyphMappingSnapshot.nextGeneration());
        candidate.winners().forEach(winner -> LOGGER.debug("Imported-glyph mapping: {}", winner));
        if (candidate.errors().isEmpty()) {
            lastValid = candidate;
            lastRejected = List.of();
            GlyphMappingSnapshot.publish(candidate);
            LOGGER.info("Imported-glyph behaviour generation {}: {} files, {} spell overrides, digest {}",
                candidate.generation(), sources.size(), candidate.winners().size(), candidate.digest());
            return;
        }
        candidate.errors().stream().limit(LOGGED_ERRORS).forEach(error -> LOGGER.error("Imported-glyph mapping: {}", error));
        if (candidate.errors().size() > LOGGED_ERRORS)
            LOGGER.error("{} further imported-glyph mapping errors suppressed", candidate.errors().size() - LOGGED_ERRORS);
        lastRejected = candidate.errors();
        GlyphMappingSnapshot kept = lastValid;
        if (kept != null) {
            LOGGER.error("Rejected imported-glyph behaviour generation {} ({} errors); keeping generation {} ({})",
                candidate.generation(), candidate.errors().size(), kept.generation(), kept.digest());
            return;
        }
        GlyphMappingSnapshot.publish(candidate);
        LOGGER.error("First imported-glyph behaviour load had {} errors; these spells fail closed: {}",
            candidate.errors().size(), candidate.failedClosed());
    }

    /** The errors of the most recent rejected or fail-closed load, for {@code /ans glyph mappings}. */
    public static List<String> lastRejected() {
        return lastRejected;
    }

    /** Server stop: the next world starts from the shipped defaults, not this world's packs. */
    public static void reset() {
        lastValid = null;
        lastRejected = List.of();
        GlyphMappingSnapshot.publish(GlyphMappingSnapshot.defaults());
    }
}
