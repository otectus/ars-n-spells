package com.otectus.arsnspells.glyph.behavior;

import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
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
import java.util.function.Function;

/**
 * One immutable generation of live Iron's facts for every catalogued spell, as the server sees
 * them. Built in full and published at once ({@link GlyphBehavior}); a remote client receives a
 * bounded copy, so its editor, tooltips and recipe viewer decide exactly as the server does.
 *
 * <p>{@link Readiness} keeps "not known yet" apart from "unavailable". Iron's builds its spell
 * configuration on the first datapack sync of a server (the first login or {@code /reload}); before
 * that its getters answer with built-in fallbacks (every spell at maximum level 1). A snapshot built
 * then is {@link Readiness#PENDING}: it carries the catalog's defaults and nothing final (cancelling
 * a restored Press job, say) may be decided on it. A remote client is {@link Readiness#NOT_SYNCED}
 * until the server's copy arrives.
 */
public final class GlyphLiveSnapshot {
    public enum Readiness {
        /** Built from Iron's authoritative configuration. */
        READY,
        /** Server side, before Iron's built its configuration: catalog defaults, provisional. */
        PENDING,
        /** A remote client that has not received the server's facts yet. */
        NOT_SYNCED,
        /** Iron's is not installed: there are no live facts. */
        ABSENT
    }

    /** What the builder may ask about the live Iron's state. */
    public interface LiveIrons {
        boolean present();

        /** Live facts for {@code spell}, querying rarity only for levels {@code 1..registeredMax}. */
        LiveSpellFacts facts(ResourceLocation spell, int registeredMax);
    }

    public static final int MAX_SPELLS = ImportedGlyphCatalog.MAX_SPELLS;
    /** Iron's has five rarities; anything far beyond that is not a snapshot this build wrote. */
    static final int MAX_RARITIES = 32;

    private final Map<ResourceLocation, LiveSpellFacts> facts;
    private final Readiness readiness;
    private final String catalogDigest;
    private final String digest;
    /** The server's {@code enable_iron_glyphs} when this copy was built (3.4.1 review SYNC-01). */
    private final boolean glyphsEnabled;
    /** Decoded from the server's copy: a remote client's view, whose feature flag is the server's. */
    private final boolean received;

    private GlyphLiveSnapshot(Map<ResourceLocation, LiveSpellFacts> facts, Readiness readiness, String catalogDigest,
                              @Nullable String digest) {
        this(facts, readiness, catalogDigest, digest, true, false);
    }

    private GlyphLiveSnapshot(Map<ResourceLocation, LiveSpellFacts> facts, Readiness readiness, String catalogDigest,
                              @Nullable String digest, boolean glyphsEnabled, boolean received) {
        this.facts = Collections.unmodifiableMap(facts);
        this.readiness = readiness;
        this.catalogDigest = catalogDigest;
        this.glyphsEnabled = glyphsEnabled;
        this.received = received;
        this.digest = digest != null ? digest : computeDigest();
    }

    /** The same facts with the server's feature flag, which is part of what a client must agree on. */
    public GlyphLiveSnapshot withGlyphsEnabled(boolean enabled) {
        return enabled == glyphsEnabled ? this : new GlyphLiveSnapshot(new LinkedHashMap<>(facts), readiness, catalogDigest, null, enabled, received);
    }

    public boolean glyphsEnabled() {
        return glyphsEnabled;
    }

    /** Whether this is a remote client's copy of the server's snapshot. */
    public boolean received() {
        return received;
    }

    /** Every spell at its catalog defaults, marked {@code readiness}. */
    public static GlyphLiveSnapshot defaults(ImportedGlyphCatalog catalog, Readiness readiness) {
        Map<ResourceLocation, LiveSpellFacts> facts = new LinkedHashMap<>();
        for (ResourceLocation spell : catalog.spells()) facts.put(spell, catalogFacts(catalog.levelsOf(spell)));
        return new GlyphLiveSnapshot(facts, readiness, catalog.identityDigest(), null);
    }

    /**
     * Build from the live state. While Iron's configuration is not ready, the catalog's defaults
     * stand in for Iron's fallbacks and the snapshot is {@link Readiness#PENDING}.
     */
    public static GlyphLiveSnapshot build(ImportedGlyphCatalog catalog, LiveIrons live, boolean configReady) {
        if (!live.present()) return defaults(catalog, Readiness.ABSENT);
        Map<ResourceLocation, LiveSpellFacts> facts = new LinkedHashMap<>();
        for (ResourceLocation spell : catalog.spells()) {
            List<ImportedGlyphDefinition> levels = catalog.levelsOf(spell);
            int registeredMax = levels.isEmpty() ? 0 : levels.get(levels.size() - 1).level();
            LiveSpellFacts read = live.facts(spell, registeredMax);
            if (!configReady) {
                // Registered-ness is real before the config is built; the rest is a fallback.
                LiveSpellFacts defaults = catalogFacts(levels);
                read = new LiveSpellFacts(read != null && read.registered(), defaults.enabled(), defaults.minLevel(), defaults.maxLevel(),
                    defaults.school(), defaults.requiresLearning(), defaults.rarities());
            }
            facts.put(spell, read == null ? LiveSpellFacts.missing(levels.isEmpty() ? spell : levels.get(0).school()) : read);
        }
        return new GlyphLiveSnapshot(facts, configReady ? Readiness.READY : Readiness.PENDING, catalog.identityDigest(), null);
    }

    private static LiveSpellFacts catalogFacts(List<ImportedGlyphDefinition> levels) {
        if (levels.isEmpty()) return LiveSpellFacts.missing(new ResourceLocation("irons_spellbooks", "none"));
        ImportedGlyphDefinition first = levels.get(0);
        // Rarities are listed from level 1; a level below the first registered one takes that level's rarity.
        List<String> rarities = new ArrayList<>(levels.size() + first.minLevel() - 1);
        for (int level = 1; level < first.minLevel(); level++) rarities.add(first.defaultRarity());
        for (ImportedGlyphDefinition level : levels) rarities.add(level.defaultRarity());
        return new LiveSpellFacts(true, first.enabledByDefault(), first.minLevel(), first.maxLevel(), first.school(), first.requiresLearning(),
            rarities);
    }

    @Nullable
    public LiveSpellFacts facts(ResourceLocation spell) {
        return facts.get(spell);
    }

    public Map<ResourceLocation, LiveSpellFacts> all() {
        return facts;
    }

    public Readiness readiness() {
        return readiness;
    }

    /** True only for facts read from Iron's authoritative configuration (or synced from a server that had them). */
    public boolean authoritative() {
        return readiness == Readiness.READY;
    }

    public String catalogDigest() {
        return catalogDigest;
    }

    public String digest() {
        return digest;
    }

    private String computeDigest() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(("ans-iron-live/v2|" + catalogDigest + "|" + readiness + "|glyphs=" + glyphsEnabled + "\n").getBytes(StandardCharsets.UTF_8));
            for (var entry : facts.entrySet()) sha.update((entry.getKey() + "=" + entry.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ---- Wire format: bounded, in the catalog's spell order, tables for schools and rarities ----

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(catalogDigest, 64);
        buffer.writeUtf(digest, 64);
        buffer.writeByte(readiness.ordinal());
        buffer.writeBoolean(glyphsEnabled);
        List<String> schools = new ArrayList<>(), rarities = new ArrayList<>();
        for (LiveSpellFacts fact : facts.values()) {
            String school = fact.school().toString();
            if (!schools.contains(school)) schools.add(school);
            for (String rarity : fact.rarities()) if (!rarities.contains(rarity)) rarities.add(rarity);
        }
        // 3.4.1 review SYNC-01: the writer holds itself to the reader's bounds, so a snapshot the server
        // accepted is one every client can decode.
        if (schools.size() > Math.max(1, facts.size()) || rarities.size() > MAX_RARITIES)
            throw new IllegalStateException("live snapshot tables exceed their bounds: " + schools.size() + " schools, " + rarities.size() + " rarities");
        writeTable(buffer, schools);
        writeTable(buffer, rarities);
        buffer.writeVarInt(facts.size());
        for (var entry : facts.entrySet()) {
            LiveSpellFacts fact = entry.getValue();
            buffer.writeResourceLocation(entry.getKey());
            buffer.writeByte((fact.registered() ? 1 : 0) | (fact.enabled() ? 2 : 0) | (fact.requiresLearning() ? 4 : 0));
            buffer.writeVarInt(fact.maxLevel());
            buffer.writeVarInt(fact.minLevel());
            buffer.writeVarInt(schools.indexOf(fact.school().toString()));
            buffer.writeVarInt(fact.rarities().size());
            for (String rarity : fact.rarities()) buffer.writeByte(rarities.indexOf(rarity));
        }
    }

    /**
     * Read a copy written by {@link #write}. Refused when it describes a different catalog, a spell it
     * does not know or lists twice, more levels than a spell registered, an out-of-range table entry,
     * fewer spells than the catalog holds (3.4.1 review SYNC-01: a snapshot is complete or refused, never
     * filled in with catalog defaults), or content that does not reproduce the digest it carries.
     */
    public static GlyphLiveSnapshot read(FriendlyByteBuf buffer, ImportedGlyphCatalog catalog) {
        String catalogDigest = buffer.readUtf(64);
        String digest = buffer.readUtf(64);
        int readinessOrdinal = buffer.readUnsignedByte();
        Readiness[] values = Readiness.values();
        if (readinessOrdinal >= values.length) throw new IllegalArgumentException("live snapshot readiness out of range: " + readinessOrdinal);
        boolean glyphsEnabled = buffer.readBoolean();
        if (!catalogDigest.equals(catalog.identityDigest()))
            throw new IllegalArgumentException("live snapshot is for catalog " + catalogDigest.substring(0, Math.min(16, catalogDigest.length()))
                + ", this client has " + catalog.identityDigest().substring(0, 16));
        List<String> schools = readTable(buffer, Math.max(1, catalog.spells().size())), rarities = readTable(buffer, MAX_RARITIES);
        int count = buffer.readVarInt();
        if (count != catalog.spells().size())
            throw new IllegalArgumentException("live snapshot lists " + count + " spells; the catalog has " + catalog.spells().size());
        Map<ResourceLocation, LiveSpellFacts> facts = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation spell = buffer.readResourceLocation();
            List<ImportedGlyphDefinition> levels = catalog.levelsOf(spell);
            if (levels.isEmpty()) throw new IllegalArgumentException("live snapshot names uncatalogued spell " + spell);
            int flags = buffer.readUnsignedByte();
            int max = buffer.readVarInt();
            int min = buffer.readVarInt();
            int school = buffer.readVarInt();
            int rarityCount = buffer.readVarInt();
            int registeredMax = levels.get(levels.size() - 1).level();
            if (max < 0 || min < 1 || min > ImportedGlyphKey.MAX_LEVEL || school < 0 || school >= schools.size() || rarityCount < 0
                    || rarityCount > registeredMax)
                throw new IllegalArgumentException("live snapshot entry for " + spell + " is out of range");
            List<String> levelRarities = new ArrayList<>(rarityCount);
            for (int j = 0; j < rarityCount; j++) {
                int index = buffer.readUnsignedByte();
                if (index >= rarities.size()) throw new IllegalArgumentException("live snapshot rarity index out of range");
                levelRarities.add(rarities.get(index));
            }
            ResourceLocation schoolId = ResourceLocation.tryParse(schools.get(school));
            if (schoolId == null) throw new IllegalArgumentException("live snapshot school is not an id");
            if ((flags & ~7) != 0) throw new IllegalArgumentException("live snapshot flags out of range for " + spell + ": " + flags);
            if (facts.put(spell, new LiveSpellFacts((flags & 1) != 0, (flags & 2) != 0, min, max, schoolId, (flags & 4) != 0, levelRarities)) != null)
                throw new IllegalArgumentException("live snapshot lists " + spell + " twice");
        }
        GlyphLiveSnapshot decoded = new GlyphLiveSnapshot(facts, values[readinessOrdinal], catalogDigest, null, glyphsEnabled, true);
        if (!decoded.digest().equals(digest))
            throw new IllegalArgumentException("live snapshot content does not reproduce its digest " + digest.substring(0, Math.min(12, digest.length())));
        return decoded;
    }

    private static void writeTable(FriendlyByteBuf buffer, List<String> table) {
        buffer.writeVarInt(table.size());
        table.forEach(entry -> buffer.writeUtf(entry, 256));
    }

    private static List<String> readTable(FriendlyByteBuf buffer, int max) {
        int size = buffer.readVarInt();
        if (size < 0 || size > max) throw new IllegalArgumentException("live snapshot table too large: " + size + " (at most " + max + ")");
        List<String> table = new ArrayList<>(size);
        for (int i = 0; i < size; i++) table.add(buffer.readUtf(256));
        return table;
    }

    /** Test seam: a snapshot with explicit facts. */
    public static GlyphLiveSnapshot of(ImportedGlyphCatalog catalog, Readiness readiness, Function<ResourceLocation, LiveSpellFacts> facts) {
        Map<ResourceLocation, LiveSpellFacts> map = new LinkedHashMap<>();
        for (ResourceLocation spell : catalog.spells()) map.put(spell, facts.apply(spell));
        return new GlyphLiveSnapshot(map, readiness, catalog.identityDigest(), null);
    }
}
