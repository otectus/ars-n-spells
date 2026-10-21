package com.otectus.arsnspells.glyph.behavior;

import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One immutable generation of imported-glyph behaviour: what every catalogued variant may do
 * right now, under the current server settings, data packs and Iron's spell config.
 *
 * <p>Built in full, validated, then published atomically ({@link GlyphBehavior}); nothing reads a
 * half-built snapshot. Its {@link #digest()} is separate from the catalog hash: the catalog fixes
 * identity at startup, a snapshot only changes availability and tuning. Admitted casts and running
 * Press jobs keep the generation they started under and re-check it at their next boundary.
 */
public final class GlyphBehaviorSnapshot {

    /** Live facts about one Iron's spell, read from the loaded registry and spell config. */
    public record LiveSpell(boolean registered, boolean enabled, int maxLevel, List<String> rarityByLevel) {
        @Nullable
        public String rarity(int level) {
            int index = level - 1;
            return index >= 0 && index < rarityByLevel.size() ? rarityByLevel.get(index) : null;
        }
    }

    /** What the snapshot builder may ask about the live Iron's state. */
    public interface LiveIrons {
        boolean present();

        @Nullable
        LiveSpell spell(ResourceLocation spellId);

        /**
         * Whether Iron's has built its spell config for this server. Iron's builds it on the first
         * data pack sync (a player joining, or {@code /reload}); before that, its getters answer
         * with fallbacks (every spell enabled, maximum level 1, Common), not the server's values.
         */
        default boolean configReady() {
            return true;
        }
    }

    /** Server settings the snapshot captures, read once per build. */
    public record Settings(boolean glyphsEnabled, boolean pressEnabled, String manaBonusPolicy,
                           int maxImportedEffects, int maxForeignEmissions, int maxActiveSessions,
                           int pressSourceCapacity) {
        public static final Settings DEFAULTS = new Settings(true, true, "per_spell", 4, 16, 8, 4000);
    }

    private final int generation;
    private final String catalogHash;
    private final Settings settings;
    private final Map<ResourceLocation, VariantBehavior> variants;
    private final Map<ResourceLocation, Boolean> selfOnly;
    private final List<String> mappingErrors;
    private final Map<ResourceLocation, String> mappingWinners;
    private final boolean ironsConfigPending;
    private final String digest;

    private GlyphBehaviorSnapshot(int generation, String catalogHash, Settings settings,
                                  Map<ResourceLocation, VariantBehavior> variants, Map<ResourceLocation, Boolean> selfOnly,
                                  List<String> mappingErrors, Map<ResourceLocation, String> mappingWinners,
                                  boolean ironsConfigPending) {
        this(generation, catalogHash, settings, variants, selfOnly, mappingErrors, mappingWinners, ironsConfigPending, null);
    }

    /** {@code serverDigest}: a client copy keeps the server's digest, so requests compare like with like. */
    private GlyphBehaviorSnapshot(int generation, String catalogHash, Settings settings,
                                  Map<ResourceLocation, VariantBehavior> variants, Map<ResourceLocation, Boolean> selfOnly,
                                  List<String> mappingErrors, Map<ResourceLocation, String> mappingWinners,
                                  boolean ironsConfigPending, @Nullable String serverDigest) {
        this.generation = generation;
        this.catalogHash = catalogHash;
        this.settings = settings;
        this.variants = Collections.unmodifiableMap(variants);
        this.selfOnly = Collections.unmodifiableMap(selfOnly);
        this.mappingErrors = List.copyOf(mappingErrors);
        this.mappingWinners = Collections.unmodifiableMap(mappingWinners);
        this.ironsConfigPending = ironsConfigPending;
        this.digest = serverDigest != null ? serverDigest : computeDigest();
    }

    /** Every variant {@link Dormancy#NOT_SYNCED}: what a client knows before the server tells it. */
    public static GlyphBehaviorSnapshot unsynced(ImportedGlyphCatalog catalog) {
        Map<ResourceLocation, VariantBehavior> variants = new LinkedHashMap<>();
        for (ImportedGlyphKey key : catalog.keys()) {
            CatalogEntry entry = catalog.entry(key.ironSpellId());
            variants.put(key.partId(), VariantBehavior.defaults(Dormancy.NOT_SYNCED, entry.defaultTierFor(key.baseLevel())));
        }
        return new GlyphBehaviorSnapshot(0, catalog.hash(), Settings.DEFAULTS, variants, Map.of(), List.of(), Map.of(), false);
    }

    /**
     * Build a snapshot. Pure: everything it reads is passed in, so it is unit-tested directly.
     *
     * <p>While Iron's spell config is not built yet ({@link LiveIrons#configReady()}), each spell's
     * enabled state, maximum level and rarity come from the catalog (Iron's defaults for the pinned
     * version) instead of Iron's unbuilt fallbacks, and the snapshot is marked
     * {@link #ironsConfigPending() pending}: nothing may be decided finally on it.
     */
    public static GlyphBehaviorSnapshot build(int generation, ImportedGlyphCatalog catalog, GlyphMappings.Result mappings,
                                              Settings settings, LiveIrons live) {
        boolean pending = live.present() && !live.configReady();
        Map<ResourceLocation, VariantBehavior> variants = new LinkedHashMap<>();
        Map<ResourceLocation, Boolean> selfOnly = new LinkedHashMap<>();
        for (CatalogEntry entry : catalog.entries()) {
            GlyphMappings.SpellMapping mapping = mappings.mappings().get(entry.spellId());
            if (mapping != null && mapping.adapter() != null && mapping.adapter().toString().equals("ars_n_spells:self")
                    && !entry.adapter().toString().equals("ars_n_spells:self")) {
                selfOnly.put(entry.spellId(), true);
            }
            LiveSpell liveSpell = live.present() ? live.spell(entry.spellId()) : null;
            if (pending) {
                liveSpell = new LiveSpell(liveSpell != null && liveSpell.registered(), entry.enabledByDefault(),
                    entry.maxLevel(), entry.rarity());
            }
            for (int level = entry.minLevel(); level <= entry.maxLevel(); level++) {
                if (!entry.supported()) continue;
                ImportedGlyphKey key = new ImportedGlyphKey(entry.spellId(), level);
                Dormancy dormancy = dormancy(entry, level, mapping, mappings, settings, live, liveSpell);
                int tier = defaultTier(entry, level, liveSpell);
                VariantBehavior base = VariantBehavior.defaults(dormancy, tier);
                GlyphMappings.LevelOverride defaults = mapping == null ? GlyphMappings.LevelOverride.NONE : mapping.defaults();
                GlyphMappings.LevelOverride specific = mapping == null ? GlyphMappings.LevelOverride.NONE
                    : mapping.levels().getOrDefault(level, GlyphMappings.LevelOverride.NONE);
                int effectiveTier = first(specific.tier(), defaults.tier(), base.tier());
                VariantBehavior byTier = VariantBehavior.defaults(dormancy, effectiveTier);
                variants.put(key.partId(), new VariantBehavior(dormancy, effectiveTier,
                    first(specific.catalystCount(), defaults.catalystCount(), byTier.pressGems()),
                    first(specific.sourceCost(), defaults.sourceCost(), byTier.pressSource()),
                    first(specific.durationTicks(), defaults.durationTicks(), byTier.pressTicks()),
                    firstString(specific.catalystTag(), defaults.catalystTag(), byTier.catalyst())));
            }
        }
        return new GlyphBehaviorSnapshot(generation, catalog.hash(), settings, variants, selfOnly,
            mappings.errors(), mappings.winners(), pending);
    }

    private static Dormancy dormancy(CatalogEntry entry, int level, @Nullable GlyphMappings.SpellMapping mapping,
                                     GlyphMappings.Result mappings, Settings settings, LiveIrons live,
                                     @Nullable LiveSpell liveSpell) {
        if (!entry.supported()) return Dormancy.UNSUPPORTED;
        if (!settings.glyphsEnabled()) return Dormancy.FEATURE_DISABLED;
        if (!live.present()) return Dormancy.IRONS_ABSENT;
        if (liveSpell == null || !liveSpell.registered()) return Dormancy.SPELL_NOT_REGISTERED;
        if (!liveSpell.enabled()) return Dormancy.SPELL_DISABLED_BY_IRONS;
        if (level > liveSpell.maxLevel()) return Dormancy.LEVEL_ABOVE_SERVER_MAX;
        if (mappings.failedClosed().contains(entry.spellId())) return Dormancy.DISABLED_BY_PACK;
        if (mappings.policy().refusal(entry.spellId(), entry.school()) != null) return Dormancy.DISABLED_BY_PACK;
        if (mapping != null) {
            if (Boolean.FALSE.equals(mapping.enabled())) return Dormancy.DISABLED_BY_PACK;
            GlyphMappings.LevelOverride specific = mapping.levels().get(level);
            if (specific != null && Boolean.FALSE.equals(specific.enabled())) return Dormancy.DISABLED_BY_PACK;
            if ((specific == null || specific.enabled() == null) && Boolean.FALSE.equals(mapping.defaults().enabled())) {
                return Dormancy.DISABLED_BY_PACK;
            }
        }
        return Dormancy.AVAILABLE;
    }

    /** Tier from Iron's effective rarity at that level (live when Iron's is loaded), else the catalog default. */
    private static int defaultTier(CatalogEntry entry, int level, @Nullable LiveSpell liveSpell) {
        String rarity = liveSpell == null ? null : liveSpell.rarity(level);
        if (rarity == null) return entry.defaultTierFor(level);
        return switch (rarity) {
            case "COMMON" -> 1;
            case "UNCOMMON", "RARE" -> 2;
            default -> 3;
        };
    }

    private static int first(@Nullable Integer a, @Nullable Integer b, int fallback) {
        return a != null ? a : b != null ? b : fallback;
    }

    private static String firstString(@Nullable String a, @Nullable String b, String fallback) {
        return a != null ? a : b != null ? b : fallback;
    }

    private String computeDigest() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((catalogHash + "|" + settings + "\n").getBytes(StandardCharsets.UTF_8));
            for (var e : variants.entrySet()) {
                md.update((e.getKey() + "=" + e.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            for (var e : selfOnly.entrySet()) md.update(("self:" + e.getKey() + "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ---- Queries ----

    public int generation() {
        return generation;
    }

    public String digest() {
        return digest;
    }

    public String catalogHash() {
        return catalogHash;
    }

    public Settings settings() {
        return settings;
    }

    /**
     * Built before Iron's spell config was (server side only; a client copy is never pending).
     * Its Iron's facts are catalog defaults, so a Press job waits rather than finishing or
     * cancelling on it.
     */
    public boolean ironsConfigPending() {
        return ironsConfigPending;
    }

    public VariantBehavior variant(ImportedGlyphKey key) {
        VariantBehavior v = variants.get(key.partId());
        return v != null ? v : VariantBehavior.defaults(Dormancy.UNSUPPORTED, 3);
    }

    /** Whether a pack restricted this spell to Self, the always-safe native adapter. */
    public boolean selfOnly(ResourceLocation spellId) {
        return selfOnly.getOrDefault(spellId, false);
    }

    public List<String> mappingErrors() {
        return mappingErrors;
    }

    public Map<ResourceLocation, String> mappingWinners() {
        return mappingWinners;
    }

    public int variantCount() {
        return variants.size();
    }

    // ---- Wire format (bounded: a few bytes per variant, in catalog key order) ----

    public void write(FriendlyByteBuf buf, ImportedGlyphCatalog catalog) {
        buf.writeVarInt(generation);
        buf.writeUtf(catalogHash, 64);
        buf.writeUtf(digest, 64);
        buf.writeBoolean(settings.glyphsEnabled());
        buf.writeBoolean(settings.pressEnabled());
        buf.writeUtf(settings.manaBonusPolicy(), 32);
        buf.writeVarInt(settings.maxImportedEffects());
        buf.writeVarInt(settings.maxForeignEmissions());
        buf.writeVarInt(settings.maxActiveSessions());
        buf.writeVarInt(settings.pressSourceCapacity());
        List<String> catalysts = new ArrayList<>();
        List<ImportedGlyphKey> keys = catalog.keys();
        for (ImportedGlyphKey key : keys) {
            String tag = variant(key).catalyst();
            if (!catalysts.contains(tag)) catalysts.add(tag);
        }
        buf.writeVarInt(catalysts.size());
        catalysts.forEach(tag -> buf.writeUtf(tag, 256));
        buf.writeVarInt(keys.size());
        for (ImportedGlyphKey key : keys) {
            VariantBehavior v = variant(key);
            buf.writeByte(v.dormancy().ordinal());
            buf.writeByte(v.tier());
            buf.writeByte(v.pressGems());
            buf.writeVarInt(v.pressSource());
            buf.writeVarInt(v.pressTicks());
            buf.writeVarInt(catalysts.indexOf(v.catalyst()));
        }
        buf.writeVarInt(selfOnly.size());
        selfOnly.keySet().forEach(buf::writeResourceLocation);
    }

    /** Read a snapshot written by {@link #write}; refuses anything larger than the local catalog. */
    public static GlyphBehaviorSnapshot read(FriendlyByteBuf buf, ImportedGlyphCatalog catalog) {
        int generation = buf.readVarInt();
        String hash = buf.readUtf(64);
        String serverDigest = buf.readUtf(64);
        Settings settings = new Settings(buf.readBoolean(), buf.readBoolean(), buf.readUtf(32),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        int catalystCount = buf.readVarInt();
        if (catalystCount < 0 || catalystCount > 256) throw new IllegalArgumentException("catalyst table too large");
        List<String> catalysts = new ArrayList<>();
        for (int i = 0; i < catalystCount; i++) catalysts.add(buf.readUtf(256));
        int count = buf.readVarInt();
        List<ImportedGlyphKey> keys = catalog.keys();
        if (count < 0 || count > ImportedGlyphCatalog.MAX_VARIANTS) throw new IllegalArgumentException("variant table too large");
        Map<ResourceLocation, VariantBehavior> variants = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Dormancy dormancy = Dormancy.byOrdinal(buf.readByte());
            int tier = buf.readByte();
            int gems = buf.readByte();
            int source = buf.readVarInt();
            int ticks = buf.readVarInt();
            int catalyst = buf.readVarInt();
            if (i < keys.size()) {
                variants.put(keys.get(i).partId(), new VariantBehavior(dormancy, tier, gems, source, ticks,
                    catalyst >= 0 && catalyst < catalysts.size() ? catalysts.get(catalyst) : VariantBehavior.DEFAULT_CATALYST));
            }
        }
        int selfCount = buf.readVarInt();
        if (selfCount < 0 || selfCount > ImportedGlyphCatalog.MAX_SPELLS) throw new IllegalArgumentException("self-only table too large");
        Map<ResourceLocation, Boolean> selfOnly = new LinkedHashMap<>();
        for (int i = 0; i < selfCount; i++) selfOnly.put(buf.readResourceLocation(), true);
        return new GlyphBehaviorSnapshot(generation, hash, settings, variants, selfOnly, List.of(), Map.of(), false, serverDigest);
    }

    /** Every catalyst item tag a variant names (the Press's catalyst slot accepts their items). */
    public java.util.Set<String> catalystTags() {
        java.util.Set<String> tags = catalystTags;
        if (tags == null) {
            java.util.Set<String> collected = new java.util.TreeSet<>();
            collected.add(VariantBehavior.DEFAULT_CATALYST);
            for (VariantBehavior variant : variants.values()) collected.add(variant.catalyst());
            tags = catalystTags = java.util.Set.copyOf(collected);
        }
        return tags;
    }

    private volatile java.util.Set<String> catalystTags;

    /** Spells whose variants are all dormant for the same reason, for diagnostics. */
    public Map<Dormancy, Integer> dormancyCounts() {
        Map<Dormancy, Integer> counts = new LinkedHashMap<>();
        for (VariantBehavior v : variants.values()) counts.merge(v.dormancy(), 1, Integer::sum);
        return counts;
    }

    public Set<ResourceLocation> variantIds() {
        return variants.keySet();
    }
}
