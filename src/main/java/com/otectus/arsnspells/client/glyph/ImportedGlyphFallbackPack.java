package com.otectus.arsnspells.client.glyph;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A built-in, always-on and hidden client pack serving {@link ImportedGlyphFallbackContent}: the
 * models and names of imported-glyph identities that no installed mod file supplies (3.4.1 review
 * CATALOG-02), so a loose catalog in {@code config/ars_n_spells/glyph_catalogs/} works without a
 * companion resource pack. It sits at the bottom of the pack list and generates only what is
 * missing, so it never shadows a catalog-only jar, ANS's own files or a user's resource pack.
 *
 * <p>The content is built from the startup catalog the first time a resource is asked for, each time
 * resources load; a failure to build it is logged and leaves the pack empty rather than breaking the
 * reload. The Forge build's pack of the same name follows the same rules.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ImportedGlyphFallbackPack implements PackResources {
    public static final String ID = "ars_n_spells_imported_glyphs";
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphFallbackPack.class);
    private static final Set<String> NAMESPACES = Set.of(ArsNSpells.MODID);

    private final PackLocationInfo location;
    @Nullable
    private volatile Map<ResourceLocation, byte[]> files;

    private ImportedGlyphFallbackPack(PackLocationInfo location) {
        this.location = location;
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(packs -> {
            PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Ars 'n' Spells imported glyphs"), PackSource.BUILT_IN,
                Optional.empty());
            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo location) {
                    return new ImportedGlyphFallbackPack(location);
                }

                @Override
                public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                    return new ImportedGlyphFallbackPack(location);
                }
            };
            Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            if (pack != null) packs.accept(pack);
        });
    }

    private Map<ResourceLocation, byte[]> files() {
        Map<ResourceLocation, byte[]> built = files;
        if (built != null) return built;
        synchronized (this) {
            if (files != null) return files;
            Map<ResourceLocation, byte[]> out = new HashMap<>();
            try {
                InstalledFiles installed = new InstalledFiles();
                ImportedGlyphFallbackContent.build(ImportedGlyphCatalog.get(), installed::has)
                    .forEach((id, json) -> out.put(id, json.getBytes(StandardCharsets.UTF_8)));
                long models = out.keySet().stream().filter(id -> id.getPath().startsWith("models/")).count();
                if (models > 0) LOGGER.info("[ImportedGlyphs] generated client resources: {} glyph models no installed file supplies", models);
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.error("[ImportedGlyphs] could not generate imported-glyph client resources; glyphs without a model of their "
                    + "own will show the missing model", failure);
                out.clear();
            }
            files = out;
            return out;
        }
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES || !NAMESPACES.contains(location.getNamespace())) return null;
        byte[] bytes = files().get(location);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !NAMESPACES.contains(namespace)) return;
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> file : files().entrySet()) {
            ResourceLocation id = file.getKey();
            if (!id.getNamespace().equals(namespace) || !id.getPath().startsWith(prefix)) continue;
            byte[] bytes = file.getValue();
            output.accept(id, () -> new ByteArrayInputStream(bytes));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? NAMESPACES : Set.of();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (!"pack".equals(serializer.getMetadataSectionName())) return null;
        return (T) new PackMetadataSection(Component.literal("Imported-glyph models and names that no installed file supplies"),
            SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void close() {
    }

    /**
     * Which files installed mod files contain, looked up only in the mod files that have an
     * {@code assets/<namespace>} directory at all, so a large pack costs one directory probe per mod
     * file and namespace rather than one per glyph.
     */
    private static final class InstalledFiles {
        private final Map<String, List<IModFileInfo>> byNamespace = new HashMap<>();

        boolean has(String namespace, String path) {
            for (IModFileInfo info : byNamespace.computeIfAbsent(namespace, InstalledFiles::withAssets)) {
                try {
                    Path found = info.getFile().findResource(("assets/" + namespace + "/" + path).split("/"));
                    if (found != null && Files.exists(found)) return true;
                } catch (RuntimeException unreadable) {
                    // A mod file that cannot be read supplies nothing.
                }
            }
            return false;
        }

        private static List<IModFileInfo> withAssets(String namespace) {
            List<IModFileInfo> out = new ArrayList<>();
            for (IModFileInfo info : ModList.get().getModFiles()) {
                try {
                    Path dir = info.getFile().findResource("assets", namespace);
                    if (dir != null && Files.isDirectory(dir)) out.add(info);
                } catch (RuntimeException unreadable) {
                    // Skip it.
                }
            }
            return out;
        }
    }
}
