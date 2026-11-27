package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.catalog.RetainedCatalogs;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Which imported-glyph identities a world has ever run with, and where they came from, saved with
 * the world (3.4.1 review SAVE-01).
 *
 * <p>Ars stores spell parts and glyph items by id, so a world whose glyph ids are not registered (an
 * addon's catalog package removed or corrupted) loses them from spells, knowledge, inventories and
 * chunks as soon as that data is decoded and saved again. Two defences keep that from happening:
 * {@link RetainedCatalogs} re-registers, dormant, the identities of every package this game accepted,
 * and this record refuses a world start <em>before any level, chunk or player data is decoded</em>
 * ({@link ServerAboutToStartEvent}) when identities it recorded are still not registered, with a
 * report naming the catalog, version and files that registered them. A server operator who means to
 * drop them starts once with {@value #ACCEPT_MISSING_PROPERTY}{@code =true}; the record then notes the
 * abandoned identities and stops refusing them.
 *
 * <p>The record only grows: a spell's recorded range is the union of every range it was registered
 * with. Data a later build may add (unknown keys, a newer schema number) is kept as it was read and
 * written back unchanged around what this build updates. The Forge build's record of the same name
 * follows the same rules and file layout.
 */
@EventBusSubscriber(modid = "ars_n_spells")
public final class GlyphIdentityRecord extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphIdentityRecord.class);
    public static final String NAME = "ars_n_spells_glyph_identities";
    public static final int SCHEMA = 2;
    /** JVM system property: start a world even though identities it used are not registered, and stop refusing them. */
    public static final String ACCEPT_MISSING_PROPERTY = "ars_n_spells.acceptMissingGlyphIdentities";
    static final int MAX_SOURCES = 16;

    /** One spell's recorded identities: levels {@code min..max}, and where they came from. */
    record Entry(int min, int max, String catalog, String version, List<String> sources, CompoundTag raw) {}

    /** Identities of {@code spell} this world recorded and the catalog does not register now. */
    public record Missing(ResourceLocation spell, int fromLevel, int toLevel, String catalog, String version, List<String> sources) {
        public int count() {
            return toLevel - fromLevel + 1;
        }

        public String describe() {
            String levels = fromLevel == toLevel ? "level " + fromLevel : "levels " + fromLevel + "-" + toLevel;
            String from = catalog.isEmpty() ? "" : " from catalog '" + catalog + "'" + (version.isEmpty() ? "" : " version " + version)
                + (sources.isEmpty() ? "" : " (last loaded from " + String.join(", ", sources) + ")");
            return spell + " " + levels + from;
        }
    }

    private final CompoundTag raw;
    private final Map<String, Entry> spells = new TreeMap<>();
    private final Map<String, int[]> abandoned = new TreeMap<>();
    private static volatile List<Missing> lastMissing = List.of();

    GlyphIdentityRecord() {
        this(new CompoundTag());
    }

    private GlyphIdentityRecord(CompoundTag raw) {
        this.raw = raw;
    }

    public static SavedData.Factory<GlyphIdentityRecord> factory() {
        return new SavedData.Factory<>(GlyphIdentityRecord::new, GlyphIdentityRecord::load, null);
    }

    static GlyphIdentityRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        return load(tag);
    }

    /** Read schema 1 (spell to highest level) or 2 (ranges with provenance); entries this build cannot read are kept raw. */
    public static GlyphIdentityRecord load(CompoundTag tag) {
        GlyphIdentityRecord record = new GlyphIdentityRecord(tag.copy());
        CompoundTag spells = tag.getCompound("spells");
        for (String spell : spells.getAllKeys()) {
            if (ResourceLocation.tryParse(spell) == null) continue;
            Tag value = spells.get(spell);
            if (value instanceof CompoundTag entry) {
                int min = entry.getInt("min"), max = entry.getInt("max");
                if (min < 1 || max < min || max > ImportedGlyphKey.MAX_LEVEL) continue;
                List<String> sources = new ArrayList<>();
                for (Tag source : entry.getList("sources", Tag.TAG_STRING)) sources.add(source.getAsString());
                record.spells.put(spell, new Entry(min, max, entry.getString("catalog"), entry.getString("version"), sources, entry.copy()));
            } else if (value != null && value.getId() == Tag.TAG_INT) {
                int level = spells.getInt(spell);
                if (level >= 1 && level <= ImportedGlyphKey.MAX_LEVEL)
                    record.spells.put(spell, new Entry(1, level, "", "", List.of(), new CompoundTag()));
            }
        }
        CompoundTag abandonedTag = tag.getCompound("abandoned");
        for (String spell : abandonedTag.getAllKeys()) {
            CompoundTag range = abandonedTag.getCompound(spell);
            record.abandoned.put(spell, new int[]{range.getInt("min"), range.getInt("max")});
        }
        return record;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return save(tag);
    }

    public CompoundTag save(CompoundTag tag) {
        // Start from what was read, so keys and per-spell data a later build wrote survive.
        for (String key : raw.getAllKeys()) tag.put(key, raw.get(key).copy());
        CompoundTag spellsTag = new CompoundTag();
        CompoundTag before = raw.getCompound("spells");
        for (String key : before.getAllKeys()) if (!spells.containsKey(key)) spellsTag.put(key, before.get(key).copy());
        spells.forEach((spell, entry) -> {
            CompoundTag out = entry.raw().copy();
            out.putInt("min", entry.min());
            out.putInt("max", entry.max());
            out.putString("catalog", entry.catalog());
            out.putString("version", entry.version());
            ListTag sources = new ListTag();
            for (String source : entry.sources()) sources.add(StringTag.valueOf(source));
            out.put("sources", sources);
            spellsTag.put(spell, out);
        });
        tag.put("spells", spellsTag);
        if (!abandoned.isEmpty()) {
            CompoundTag abandonedTag = tag.getCompound("abandoned");
            abandoned.forEach((spell, range) -> {
                CompoundTag out = new CompoundTag();
                out.putInt("min", range[0]);
                out.putInt("max", range[1]);
                abandonedTag.put(spell, out);
            });
            tag.put("abandoned", abandonedTag);
        }
        tag.putInt("schema", Math.max(SCHEMA, raw.getInt("schema")));
        return tag;
    }

    // ---- The check ------------------------------------------------------------------------------

    /** Identities recorded here (and not abandoned) that {@code catalog} does not register, per spell and contiguous range. */
    public List<Missing> missingFrom(ImportedGlyphCatalog catalog) {
        List<Missing> missing = new ArrayList<>();
        spells.forEach((spell, entry) -> {
            ResourceLocation id = ResourceLocation.tryParse(spell);
            if (id == null) return;
            CatalogEntry now = catalog.entry(id);
            int low = now == null ? 0 : now.minLevel(), high = now == null ? 0 : now.registeredMax();
            int[] dropped = abandoned.get(spell);
            int from = -1;
            for (int level = entry.min(); level <= entry.max() + 1; level++) {
                boolean lost = level <= entry.max() && (high == 0 || level < low || level > high)
                    && (dropped == null || level < dropped[0] || level > dropped[1]);
                if (lost && from < 0) from = level;
                if (!lost && from >= 0) {
                    missing.add(new Missing(id, from, level - 1, entry.catalog(), entry.version(), entry.sources()));
                    from = -1;
                }
            }
        });
        return missing;
    }

    /** Record every identity {@code catalog} registers, with its provenance; never forgets one. Returns whether anything changed. */
    public boolean absorb(ImportedGlyphCatalog catalog) {
        boolean changed = false;
        for (CatalogEntry entry : catalog.entries()) {
            ResourceLocation spell = entry.spellId();
            int low = entry.minLevel(), high = entry.registeredMax();
            Entry before = spells.get(spell.toString());
            List<String> sources = new ArrayList<>(before == null ? List.of() : before.sources());
            for (String source : entry.sources()) if (!sources.contains(source)) sources.add(source);
            while (sources.size() > MAX_SOURCES) sources.remove(0);
            String catalogName = entry.catalog();
            String version = entry.version();
            // A dormant retained copy keeps the provenance of the package that actually registered the spell.
            boolean retainedOnly = entry.sources().stream().allMatch(s -> s.startsWith(RetainedCatalogs.SOURCE_PREFIX));
            if (before != null && retainedOnly) {
                catalogName = before.catalog();
                version = before.version();
            }
            Entry after = new Entry(before == null ? low : Math.min(before.min(), low), before == null ? high : Math.max(before.max(), high),
                catalogName, version, sources, before == null ? new CompoundTag() : before.raw());
            if (!after.equals(before)) {
                spells.put(spell.toString(), after);
                changed = true;
            }
        }
        return changed;
    }

    /** Record {@code missing} as deliberately abandoned, so they no longer refuse a world start. */
    void abandon(List<Missing> missing) {
        for (Missing lost : missing) {
            int[] range = abandoned.get(lost.spell().toString());
            abandoned.put(lost.spell().toString(), range == null ? new int[]{lost.fromLevel(), lost.toLevel()}
                : new int[]{Math.min(range[0], lost.fromLevel()), Math.max(range[1], lost.toLevel())});
        }
    }

    // ---- Server lifecycle -------------------------------------------------------------------------

    /**
     * Before the world's levels, chunks or players are loaded: refuse a world that recorded identities
     * the catalog does not register, unless the operator accepted the loss.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME + ".dat");
        if (!Files.isRegularFile(file)) return;
        GlyphIdentityRecord record;
        try {
            record = load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"));
        } catch (IOException | RuntimeException unreadable) {
            throw new MissingIdentitiesException("Ars 'n' Spells cannot read this world's imported-glyph identity record " + file
                + " (" + unreadable + "). The world was not loaded, so nothing in it changed. Restore the file from a backup and start again.");
        }
        List<Missing> missing = record.missingFrom(ImportedGlyphCatalog.get());
        lastMissing = List.copyOf(missing);
        if (missing.isEmpty()) return;
        String report = report(missing);
        if (Boolean.getBoolean(ACCEPT_MISSING_PROPERTY)) {
            LOGGER.error("{}\nStarting anyway because {}=true: these glyphs are dropped from spells, knowledge and inventories when they "
                + "are next saved, and this world stops reporting them.", report, ACCEPT_MISSING_PROPERTY);
            return;
        }
        LOGGER.error(report);
        throw new MissingIdentitiesException(report);
    }

    static String report(List<Missing> missing) {
        int total = missing.stream().mapToInt(Missing::count).sum();
        StringBuilder text = new StringBuilder("Ars 'n' Spells refused to start this world: it has used ").append(total)
            .append(" imported-glyph identities that are not registered now, and loading it would drop those glyphs from spells, knowledge, "
                + "inventories and chunks when they are next saved. The world was not loaded, so nothing in it changed.\nMissing:");
        for (int i = 0; i < Math.min(missing.size(), 20); i++) text.append("\n  ").append(missing.get(i).describe());
        if (missing.size() > 20) text.append("\n  ... and ").append(missing.size() - 20).append(" more spells");
        text.append("\nTo fix it, reinstall the catalog package that registered them (an addon's catalog-only jar in mods/, or its file in config/")
            .append(com.otectus.arsnspells.glyph.catalog.CatalogManifests.CONFIG_DIRECTORY).append("/), or restore its retained copy in config/")
            .append(RetainedCatalogs.DIRECTORY)
            .append("/, then start again. To drop these glyphs for good, start once with -D").append(ACCEPT_MISSING_PROPERTY).append("=true.");
        return text.toString();
    }

    /**
     * After the world loaded: record the identities registered now (and, after an accepted loss, the
     * abandoned ones), for the next start's check and for diagnostics.
     */
    public static List<Missing> check(MinecraftServer server, ImportedGlyphCatalog catalog) {
        GlyphIdentityRecord record = server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
        List<Missing> missing = record.missingFrom(catalog);
        lastMissing = List.copyOf(missing);
        boolean changed = false;
        if (!missing.isEmpty()) {
            // Only reachable with the operator's acceptance (the start was otherwise refused) or for a record written during this run.
            LOGGER.error("{} imported-glyph identities this world used are not registered (first: {}); recorded as abandoned.",
                missing.stream().mapToInt(Missing::count).sum(), missing.get(0).describe());
            record.abandon(missing);
            changed = true;
        }
        if (record.absorb(catalog) || changed) record.setDirty();
        return missing;
    }

    /** What the last check found missing, as part ids, for diagnostics. */
    public static List<String> lastMissing() {
        List<String> ids = new ArrayList<>();
        for (Missing lost : lastMissing)
            for (int level = lost.fromLevel(); level <= lost.toLevel() && ids.size() < 4096; level++)
                ids.add(new ImportedGlyphKey(lost.spell(), level).partId().toString());
        return ids;
    }

    /** The recorded ranges, for diagnostics and tests: spell to {min, max}. */
    Map<String, int[]> ranges() {
        Map<String, int[]> out = new LinkedHashMap<>();
        spells.forEach((spell, entry) -> out.put(spell, new int[]{entry.min(), entry.max()}));
        return out;
    }

    /** A world start refused because recorded identities are missing; carries the actionable report. */
    public static final class MissingIdentitiesException extends RuntimeException {
        public MissingIdentitiesException(String message) {
            super(message);
        }
    }
}
