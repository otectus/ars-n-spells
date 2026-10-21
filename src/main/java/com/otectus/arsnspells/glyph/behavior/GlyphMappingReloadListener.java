package com.otectus.arsnspells.glyph.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code data/<namespace>/ans_iron_glyphs/**.json} on every data pack (re)load.
 *
 * <p>For each file path the highest-priority pack's copy is used, and which pack supplied it is
 * kept for diagnostics. Parsing and validation happen off the server thread in {@link #prepare};
 * the result is handed to {@link GlyphBehavior#acceptMappings} on the server thread, and the next
 * data pack sync publishes it.
 */
final class GlyphMappingReloadListener extends SimplePreparableReloadListener<GlyphMappings.Result> {

    @Override
    protected GlyphMappings.Result prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<GlyphMappings.Source> sources = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        Map<ResourceLocation, List<Resource>> stacks = manager.listResourceStacks(GlyphMappings.DIRECTORY,
            id -> id.getPath().endsWith(".json"));
        for (var entry : stacks.entrySet()) {
            List<Resource> stack = entry.getValue();
            if (stack.isEmpty()) continue;
            Resource top = stack.get(stack.size() - 1);
            String path = entry.getKey().getPath();
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(),
                path.substring(GlyphMappings.DIRECTORY.length() + 1, path.length() - ".json".length()));
            try (Reader reader = top.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                sources.add(new GlyphMappings.Source(file, top.sourcePackId(), json));
            } catch (Exception e) {
                unreadable.add(file + " (pack " + top.sourcePackId() + "): unreadable JSON (" + e.getMessage() + ")");
            }
        }
        GlyphMappings.Result parsed = GlyphMappings.parse(sources, ImportedGlyphCatalog.get());
        if (unreadable.isEmpty()) return parsed;
        List<String> errors = new ArrayList<>(parsed.errors());
        errors.addAll(unreadable);
        return new GlyphMappings.Result(parsed.mappings(), parsed.policy(), List.copyOf(errors), parsed.failedClosed(), parsed.winners());
    }

    @Override
    protected void apply(GlyphMappings.Result result, ResourceManager manager, ProfilerFiller profiler) {
        GlyphBehavior.acceptMappings(result);
    }
}
