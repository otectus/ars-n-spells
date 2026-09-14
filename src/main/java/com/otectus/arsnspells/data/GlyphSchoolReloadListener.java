package com.otectus.arsnspells.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.otectus.arsnspells.util.SchoolMappings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.TreeMap;

/** Loads legacy scalar and version-2 ordered school metadata with deterministic precedence. */
public class GlyphSchoolReloadListener extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphSchoolReloadListener.class);
    public static final String FOLDER = "ans_glyph_schools";
    public GlyphSchoolReloadListener() { super(new Gson(), FOLDER); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, JsonElement> inputs = new TreeMap<>();
        files.forEach((key, value) -> inputs.put(key.toString(), value));
        SchoolMappingLoader.Result result = SchoolMappingLoader.merge(inputs, id -> ModList.get().isLoaded(id));
        result.warnings().stream().limit(100).forEach(warning -> LOGGER.warn("School mapping: {}", warning));
        if (result.warnings().size() > 100) LOGGER.warn("{} additional school mapping warnings suppressed",
            result.warnings().size() - 100);
        SchoolMappings.applyKeyOverlay(result.glyphs(), result.arsSchools(), result.provenance());
        LOGGER.info("Loaded {} glyph and {} Ars school overrides; digest {}",
            result.glyphs().size(), result.arsSchools().size(), SchoolMappings.get().digest());
    }
}
