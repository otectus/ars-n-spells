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

    /**
     * Live facts about one Iron's spell, read through Iron's own getters (so Iron's precedence of
     * per-spell files, datapacks, the global config and configuration events applies). The maximum
     * is a scalar; {@code rarityByLevel} covers only the catalog's registered levels, never every
     * level up to an arbitrarily large configured maximum.
     *
     * @param school           the spell's effective school (configurable in Iron's)
     * @param requiresLearning whether Iron's requires it to be learned now (follows the school)
     */
    public record LiveSpell(boolean registered, boolean enabled, int maxLevel, List<String> rarityByLevel,
                            @Nullable ResourceLocation school, boolean requiresLearning, int minLevel) {
        public LiveSpell(boolean registered, boolean enabled, int maxLevel, List<String> rarityByLevel) {
            this(registered, enabled, maxLevel, rarityByLevel, null, false, 1);
        }

        public LiveSpell(boolean registered, boolean enabled, int maxLevel, List<String> rarityByLevel,
                         @Nullable ResourceLocation school, boolean requiresLearning) {
            this(registered, enabled, maxLevel, rarityByLevel, school, requiresLearning, 1);
        }

        @Nullable
        public String rarity(int level) {
            int index = level - 1;
            return index >= 0 && index < rarityByLevel.size() ? rarityByLevel.get(index) : null;
        }
    }

    /**
     * The effective, per-spell facts every consumer needs (augment limits, admission, the Press
     * planner, explanations), synced to clients: Iron's live maximum, school and learning rule.
     */
    public record SpellFacts(int liveMin, int liveMax, ResourceLocation school, boolean requiresLearning) {
        /** Compatibility constructor for callers whose fixtures predate the synchronized minimum. */
        public SpellFacts(int liveMax, ResourceLocation school, boolean requiresLearning) {
            this(1, liveMax, school, requiresLearning);
        }
    }

    /** What the snapshot builder may ask about the live Iron's state. */
    public interface LiveIrons {
        boolean present();

        /** Live facts, with rarity for levels {@code 1..registeredMax} only; null when Iron's is absent. */
        @Nullable
        LiveSpell spell(ResourceLocation spellId, int registeredMax);

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
    private final Map<ResourceLocation, SpellFacts> spells;
    private final List<String> mappingErrors;
    private final Map<ResourceLocation, String> mappingWinners;
    private final boolean ironsConfigPending;
    private final String digest;

    private GlyphBehaviorSnapshot(int generation, String catalogHash, Settings settings,
                                  Map<ResourceLocation, VariantBehavior> variants, Map<ResourceLocation, Boolean> selfOnly,
                                  Map<ResourceLocation, SpellFacts> spells,
                                  List<String> mappingErrors, Map<ResourceLocation, String> mappingWinners,
                                  boolean ironsConfigPending) {
        this(generation, catalogHash, settings, variants, selfOnly, spells, mappingErrors, mappingWinners, ironsConfigPending, null);
    }

    /** {@code serverDigest}: a client copy keeps the server's digest, so requests compare like with like. */
    private GlyphBehaviorSnapshot(int generation, String catalogHash, Settings settings,
                                  Map<ResourceLocation, VariantBehavior> variants, Map<ResourceLocation, Boolean> selfOnly,
                                  Map<ResourceLocation, SpellFacts> spells,
                                  List<String> mappingErrors, Map<ResourceLocation, String> mappingWinners,
                                  boolean ironsConfigPending, @Nullable String serverDigest) {
        this.generation = generation;
        this.catalogHash = catalogHash;
        this.settings = settings;
        this.variants = Collections.unmodifiableMap(variants);
        this.selfOnly = Collections.unmodifiableMap(selfOnly);
        this.spells = Collections.unmodifiableMap(spells);
        this.mappingErrors = List.copyOf(mappingErrors);
        this.mappingWinners = Collections.unmodifiableMap(mappingWinners);
        this.ironsConfigPending = ironsConfigPending;
        this.digest = serverDigest != null ? serverDigest : computeDigest();
    }

    private static volatile GlyphBehaviorSnapshot unsyncedCache;

    /**
     * Every variant {@link Dormancy#NOT_SYNCED}: what a client knows before the server tells it.
     * Built once per catalog and reused, so editor and tooltip queries before the first sync cost a
     * lookup, not a whole-catalog rebuild each.
     */
    public static GlyphBehaviorSnapshot unsynced(ImportedGlyphCatalog catalog) {
        GlyphBehaviorSnapshot cached = unsyncedCache;
        if (cached != null && cached.catalogHash.equals(catalog.hash())) return cached;
        Map<ResourceLocation, VariantBehavior> variants = new LinkedHashMap<>();
        Map<ResourceLocation, SpellFacts> spells = new LinkedHashMap<>();
        for (ImportedGlyphKey key : catalog.keys()) {
            CatalogEntry entry = catalog.entry(key.ironSpellId());
            variants.put(key.partId(), VariantBehavior.defaults(Dormancy.NOT_SYNCED, entry.defaultTierFor(key.baseLevel())));
        }
        for (CatalogEntry entry : catalog.entries())
            spells.put(entry.spellId(), new SpellFacts(entry.minLevel(), entry.maxLevel(), entry.school(), entry.requiresLearning()));
        GlyphBehaviorSnapshot built = new GlyphBehaviorSnapshot(0, catalog.hash(), Settings.DEFAULTS, variants, Map.of(), spells,
            List.of(), Map.of(), false);
        unsyncedCache = built;
        return built;
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
        Map<ResourceLocation, SpellFacts> spells = new LinkedHashMap<>();
        for (CatalogEntry entry : catalog.entries()) {
            GlyphMappings.SpellMapping mapping = mappings.mappings().get(entry.spellId());
            if (mapping != null && mapping.selfOnly() && !entry.adapter().toString().equals(GlyphMappings.SELF_ADAPTER)) {
                selfOnly.put(entry.spellId(), true);
            }
            LiveSpell liveSpell = live.present() ? live.spell(entry.spellId(), entry.registeredMax()) : null;
            if (pending) {
                // LiveSpell lists rarity from level 1; the catalog lists it from its first registered level.
                List<String> fromOne = new java.util.ArrayList<>(entry.registeredMax());
                for (int level = 1; level <= entry.registeredMax(); level++) fromOne.add(entry.rarityFor(Math.max(level, entry.minLevel())));
                liveSpell = new LiveSpell(liveSpell != null && liveSpell.registered(), entry.enabledByDefault(),
                    entry.maxLevel(), fromOne, entry.school(), entry.requiresLearning(), entry.minLevel());
            }
            ResourceLocation school = liveSpell != null && liveSpell.school() != null ? liveSpell.school() : entry.school();
            spells.put(entry.spellId(), new SpellFacts(liveSpell != null && liveSpell.registered() ? liveSpell.minLevel() : entry.minLevel(),
                liveSpell != null && liveSpell.registered() ? liveSpell.maxLevel() : entry.maxLevel(),
                school, liveSpell != null && liveSpell.school() != null ? liveSpell.requiresLearning() : entry.requiresLearning()));
            for (int level = entry.minLevel(); level <= entry.registeredMax(); level++) {
                ImportedGlyphKey key = new ImportedGlyphKey(entry.spellId(), level);
                Dormancy dormancy = dormancy(entry, level, mapping, mappings, settings, live, liveSpell, school);
                int tier = defaultTier(entry, level, liveSpell);
                VariantBehavior base = tierTerms(dormancy, tier, mappings);
                GlyphMappings.LevelOverride defaults = mapping == null ? GlyphMappings.LevelOverride.NONE : mapping.defaults();
                GlyphMappings.LevelOverride specific = mapping == null ? GlyphMappings.LevelOverride.NONE
                    : mapping.levels().getOrDefault(level, GlyphMappings.LevelOverride.NONE);
                int effectiveTier = first(specific.tier(), defaults.tier(), base.tier());
                VariantBehavior byTier = tierTerms(dormancy, effectiveTier, mappings);
                variants.put(key.partId(), new VariantBehavior(dormancy, effectiveTier,
                    first(specific.catalystCount(), defaults.catalystCount(), byTier.pressGems()),
                    first(specific.sourceCost(), defaults.sourceCost(), byTier.pressSource()),
                    first(specific.durationTicks(), defaults.durationTicks(), byTier.pressTicks()),
                    firstString(specific.catalystTag(), defaults.catalystTag(), byTier.catalyst())));
            }
        }
        return new GlyphBehaviorSnapshot(generation, catalog.hash(), settings, variants, selfOnly, spells,
            mappings.errors(), mappings.winners(), pending);
    }

    /** A tier's Press terms: a {@code press_tiers} mapping when one overrides it, else the shipped terms. */
    static VariantBehavior tierTerms(Dormancy dormancy, int tier, GlyphMappings.Result mappings) {
        VariantBehavior shipped = VariantBehavior.defaults(dormancy, tier);
        GlyphMappings.TierTerms override = mappings.tiers().get(shipped.tier());
        return override == null ? shipped : new VariantBehavior(dormancy, shipped.tier(), override.catalystCount(),
            override.sourceCost(), override.durationTicks(), override.catalystTag());
    }

    private static Dormancy dormancy(CatalogEntry entry, int level, @Nullable GlyphMappings.SpellMapping mapping,
                                     GlyphMappings.Result mappings, Settings settings, LiveIrons live,
                                     @Nullable LiveSpell liveSpell, ResourceLocation school) {
        if (entry.retired())
            return entry.sourceCatalog().startsWith(com.otectus.arsnspells.glyph.catalog.RetainedCatalogs.SOURCE_PREFIX)
                ? Dormancy.PACKAGE_MISSING : Dormancy.RETIRED;
        if (!entry.supported()) return Dormancy.UNSUPPORTED;
        if (!entry.executable()) return Dormancy.ADAPTER_TOO_NEW;
        if (!settings.glyphsEnabled()) return Dormancy.FEATURE_DISABLED;
        if (!live.present()) return Dormancy.IRONS_ABSENT;
        if (liveSpell == null || !liveSpell.registered()) return Dormancy.SPELL_NOT_REGISTERED;
        if (!liveSpell.enabled()) return Dormancy.SPELL_DISABLED_BY_IRONS;
        if (level > liveSpell.maxLevel()) return Dormancy.LEVEL_ABOVE_SERVER_MAX;
        if (level < liveSpell.minLevel()) return Dormancy.LEVEL_BELOW_SERVER_MIN;
        if (mappings.failedClosed().contains(entry.spellId())) return Dormancy.DISABLED_BY_PACK;
        // The spell's effective school decides policy, not the one it was catalogued with.
        if (mappings.policy().refusal(entry.spellId(), school) != null) return Dormancy.DISABLED_BY_PACK;
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
            for (var e : spells.entrySet()) md.update(("spell:" + e.getKey() + "=" + e.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
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

    /** The spell's effective facts, or null when the catalog does not know it. */
    @Nullable
    public SpellFacts spell(ResourceLocation spellId) {
        return spells.get(spellId);
    }

    /** Iron's live maximum for the spell (the catalog default before the server says otherwise). */
    public int liveMax(CatalogEntry entry) {
        SpellFacts facts = spells.get(entry.spellId());
        return facts == null ? entry.maxLevel() : facts.liveMax();
    }

    public Map<ResourceLocation, SpellFacts> spells() {
        return spells;
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
        // 3.4.1: per-spell effective facts, in catalog order, with a school table.
        List<CatalogEntry> entries = catalog.entries();
        List<String> schools = new ArrayList<>();
        for (CatalogEntry entry : entries) {
            SpellFacts facts = spells.get(entry.spellId());
            String school = (facts == null ? entry.school() : facts.school()).toString();
            if (!schools.contains(school)) schools.add(school);
        }
        buf.writeVarInt(schools.size());
        schools.forEach(school -> buf.writeUtf(school, 256));
        buf.writeVarInt(entries.size());
        for (CatalogEntry entry : entries) {
            SpellFacts facts = spells.get(entry.spellId());
            if (facts == null) facts = new SpellFacts(entry.minLevel(), entry.maxLevel(), entry.school(), entry.requiresLearning());
            buf.writeVarInt(Math.max(1, facts.liveMin()));
            buf.writeVarInt(Math.max(0, facts.liveMax()));
            buf.writeVarInt(schools.indexOf(facts.school().toString()));
            buf.writeBoolean(facts.requiresLearning());
        }
    }

    /**
     * Read a snapshot written by {@link #write}. The variants are positional (catalog key order), so
     * they are applied only when the server's catalog hash is this client's: otherwise the rest is
     * skipped and an empty snapshot carrying the server's hash is returned, which the receiver refuses
     * with a readable disconnect.
     *
     * <p>3.4.1 review SYNC-01: a snapshot is complete or it is refused. It must list exactly one entry
     * per catalogued variant and per catalogued spell, every table index and value must be in range,
     * and a self-only rule must name a catalogued spell once. Nothing is defaulted for a missing or
     * malformed entry: the caller keeps its last accepted snapshot instead.
     */
    public static GlyphBehaviorSnapshot read(FriendlyByteBuf buf, ImportedGlyphCatalog catalog) {
        int generation = buf.readVarInt();
        String hash = buf.readUtf(64);
        String serverDigest = buf.readUtf(64);
        if (!hash.equals(catalog.hash())) {
            buf.skipBytes(buf.readableBytes());
            return new GlyphBehaviorSnapshot(generation, hash, Settings.DEFAULTS, Map.of(), Map.of(), Map.of(), List.of(), Map.of(), false, serverDigest);
        }
        Settings settings = new Settings(buf.readBoolean(), buf.readBoolean(), buf.readUtf(32),
            nonNegative(buf.readVarInt(), "max imported effects"), nonNegative(buf.readVarInt(), "max foreign emissions"),
            nonNegative(buf.readVarInt(), "max active sessions"), nonNegative(buf.readVarInt(), "press source capacity"));
        List<ImportedGlyphKey> keys = catalog.keys();
        int catalystCount = buf.readVarInt();
        if (catalystCount < 0 || catalystCount > Math.max(1, keys.size())) throw new IllegalArgumentException("catalyst table too large: " + catalystCount);
        List<String> catalysts = new ArrayList<>();
        for (int i = 0; i < catalystCount; i++) catalysts.add(buf.readUtf(256));
        int count = buf.readVarInt();
        if (count != keys.size()) throw new IllegalArgumentException("variant table lists " + count + " variants; the catalog has " + keys.size());
        Map<ResourceLocation, VariantBehavior> variants = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int ordinal = buf.readByte();
            if (ordinal < 0 || ordinal >= Dormancy.values().length) throw new IllegalArgumentException("dormancy out of range: " + ordinal);
            Dormancy dormancy = Dormancy.byOrdinal(ordinal);
            int tier = buf.readByte();
            int gems = buf.readByte();
            int source = nonNegative(buf.readVarInt(), "press source");
            int ticks = nonNegative(buf.readVarInt(), "press ticks");
            int catalyst = buf.readVarInt();
            if (tier < 1 || tier > 3 || gems < 0) throw new IllegalArgumentException("variant terms out of range: tier " + tier + ", gems " + gems);
            if (catalyst < 0 || catalyst >= catalysts.size()) throw new IllegalArgumentException("catalyst index out of range: " + catalyst);
            variants.put(keys.get(i).partId(), new VariantBehavior(dormancy, tier, gems, source, ticks, catalysts.get(catalyst)));
        }
        int selfCount = buf.readVarInt();
        if (selfCount < 0 || selfCount > catalog.entries().size()) throw new IllegalArgumentException("self-only table too large: " + selfCount);
        Map<ResourceLocation, Boolean> selfOnly = new LinkedHashMap<>();
        for (int i = 0; i < selfCount; i++) {
            ResourceLocation spell = buf.readResourceLocation();
            if (catalog.entry(spell) == null || selfOnly.put(spell, true) != null)
                throw new IllegalArgumentException("self-only rule for an unknown or repeated spell: " + spell);
        }
        int schoolCount = buf.readVarInt();
        // One school per spell at most: the writer's table is bounded by the catalog, and so is this one.
        if (schoolCount < 0 || schoolCount > Math.max(1, catalog.entries().size())) throw new IllegalArgumentException("school table too large: " + schoolCount);
        List<ResourceLocation> schools = new ArrayList<>();
        for (int i = 0; i < schoolCount; i++) schools.add(ResourceLocation.parse(buf.readUtf(256)));
        int spellCount = buf.readVarInt();
        List<CatalogEntry> entries = catalog.entries();
        if (spellCount != entries.size()) throw new IllegalArgumentException("spell table does not match the catalog");
        Map<ResourceLocation, SpellFacts> spells = new LinkedHashMap<>();
        for (CatalogEntry entry : entries) {
            int min = positive(buf.readVarInt(), "live minimum");
            int max = nonNegative(buf.readVarInt(), "live maximum");
            int school = buf.readVarInt();
            boolean learning = buf.readBoolean();
            if (school < 0 || school >= schools.size()) throw new IllegalArgumentException("school index out of range");
            if (max < min) throw new IllegalArgumentException("live maximum " + max + " is below minimum " + min);
            spells.put(entry.spellId(), new SpellFacts(min, max, schools.get(school), learning));
        }
        return new GlyphBehaviorSnapshot(generation, hash, settings, variants, selfOnly, spells, List.of(), Map.of(), false, serverDigest);
    }

    private static int nonNegative(int value, String what) {
        if (value < 0) throw new IllegalArgumentException(what + " out of range: " + value);
        return value;
    }

    private static int positive(int value, String what) {
        if (value < 1) throw new IllegalArgumentException(what + " out of range: " + value);
        return value;
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
