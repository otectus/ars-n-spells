package com.otectus.arsnspells.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.otectus.arsnspells.affinity.AffinityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Per-player affinity levels keyed by the full school id string (e.g.
 * {@code "irons_spellbooks:fire"}, {@code "cataclysm_spellbooks:abyssal"}).
 *
 * <p><b>2.5.0:</b> re-keyed from the old 16-value {@code AffinityType} enum to a
 * {@code Map<String,Integer>} (mirroring {@link ProgressionData}) so every Iron's
 * addon school is tracked, not just the hardcoded set. Full ids — not paths —
 * keep addon schools that share a path distinct (e.g. {@code somakespells:aqua}
 * vs {@code traveloptics:aqua}).
 *
 * <p><b>Persistence is versioned with a one-way migration.</b> The disk shape is
 * a record {@code {schema_version, levels}}. A pre-2.5.0 save is a bare
 * {@code Map<String,Integer>} with enum-name keys ({@code "FIRE"}); it has no
 * {@code schema_version} field, so the current record codec fails and the legacy
 * codec takes over, remapping each enum name to its canonical id via
 * {@link AffinityType#legacyKeyToId(String)} (the seven category buckets map to
 * {@code null} and are dropped). Every encode writes the current record, so a
 * world is migrated the first time it saves — idempotent, no world scan. The
 * nine elemental tracks are renamed, never recomputed, so no player loses
 * progress.
 */
public class AffinityData {

    /** Bump when the {@code levels} key convention or record fields change. */
    public static final int SCHEMA_VERSION = 2;

    /**
     * Current shape: {@code {schema_version:int, levels:{id->level},
     * decay_remainders:{id->double}}}.
     *
     * <p>{@code decay_remainders} is optional so a v1 save (which predates the
     * fractional-decay residual) decodes with an empty accumulator instead of
     * failing over to the legacy bare-map codec.
     */
    private static final Codec<AffinityData> CURRENT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        // schema_version stays strict: its absence is the signal that this is a legacy save,
        // and the Either below depends on the current codec failing so the fallback runs.
        Codec.INT.fieldOf("schema_version").forGetter(d -> SCHEMA_VERSION),
        // The two payload fields are optional-with-default so that one malformed field costs
        // only that field. As strict fields, a single bad entry failed the record, the legacy
        // fallback failed too, and NeoForge's AttachmentHolder.deserializeAttachments catches
        // the resulting error and SILENTLY DROPS the whole attachment - so a garbled decay
        // remainder erased every school's affinity with nothing in the log.
        // orElse, not just optionalFieldOf: optionalFieldOf covers an ABSENT field, but a
        // field that is present and malformed still fails the record. orElse is the piece
        // that turns a decode error into the default.
        Codec.unboundedMap(Codec.STRING, Codec.INT).orElseGet(Map::of)
            .optionalFieldOf("levels", Map.of())
            .forGetter(AffinityData::getAllLevels),
        // Optional-of, not a shared default instance: a default built once at class-init is
        // handed to every player that lacks the field, and DecayAccumulator is mutable.
        DecayAccumulator.CODEC.orElseGet(DecayAccumulator::new)
            .optionalFieldOf("decay_remainders")
            .forGetter(d -> Optional.of(d.getDecayAccumulator())),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("unresolved_legacy", Map.of())
            .forGetter(d -> d.unresolvedLegacy)
    ).apply(inst, AffinityData::fromCurrent));

    /** Legacy shape: a bare {@code Map<enumName->level>} written by 2.0.x and earlier. */
    private static final Codec<AffinityData> LEGACY_CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(AffinityData::fromLegacy, AffinityData::getAllLevels);

    /**
     * Decodes the current record first; on failure (no {@code schema_version}
     * field) falls back to the legacy bare-map migration. Always encodes the
     * current record, so saves migrate forward automatically.
     */
    public static final Codec<AffinityData> CODEC = Codec.either(CURRENT_CODEC, LEGACY_CODEC).xmap(
        either -> either.map(Function.identity(), Function.identity()),
        Either::left
    );

    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, Integer> unresolvedLegacy = new HashMap<>();
    public Map<String, Integer> unresolvedLegacy() { return Map.copyOf(unresolvedLegacy); }

    /**
     * Sub-point decay carried between intervals. Without it the decay handler's
     * integer floor strips a flat 1 point per interval, roughly 20x the
     * documented proportional rate (audit D1).
     */
    private final DecayAccumulator decay = new DecayAccumulator();

    private static int clamp(Integer level) {
        if (level == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, level));
    }

    private static AffinityData fromCurrent(int version, Map<String, Integer> levels,
                                            Optional<DecayAccumulator> decay, Map<String, Integer> unresolved) {
        // version is read for forward-compat; v1 levels are already canonical ids.
        AffinityData d = new AffinityData();
        levels.forEach((k, v) -> {
            if (k != null && !k.isEmpty()) {
                d.levels.merge(k, clamp(v), Math::max);
            }
        });
        d.unresolvedLegacy.putAll(unresolved);
        decay.ifPresent(acc -> acc.snapshot().forEach((k, r) -> d.decay.accrue(k, r)));
        return d;
    }

    private static AffinityData fromLegacy(Map<String, Integer> raw) {
        AffinityData d = new AffinityData();
        raw.forEach((k, v) -> {
            if (k == null) {
                return;
            }
            String canonical = k.indexOf(':') >= 0 ? k : AffinityType.legacyKeyToId(k);
            if (canonical != null) {
                d.levels.merge(canonical, clamp(v), Math::max);
            }
            else d.unresolvedLegacy.put(k, clamp(v)); // archival, never an attribute identity
        });
        return d;
    }

    public int getLevel(String schoolKey) {
        return levels.getOrDefault(schoolKey, 0);
    }

    public void setLevel(String schoolKey, int level) {
        levels.put(schoolKey, clamp(level));
    }

    public void addLevel(String schoolKey, int amount) {
        setLevel(schoolKey, getLevel(schoolKey) + amount);
    }

    /** A copy of every tracked school's level. Safe to iterate while mutating the original. */
    public Map<String, Integer> getAllLevels() {
        return Map.copyOf(levels);
    }

    /** A replacement snapshot includes deletions; an empty disabled snapshot clears the mirror. */
    public void replaceLevels(Map<String, Integer> replacement) {
        levels.clear(); replacement.forEach(this::setLevel);
    }

    /**
     * The live fractional-decay carrier. Mutated in place by the decay handler;
     * persisted as part of this attachment so residuals survive relogs.
     */
    public DecayAccumulator getDecayAccumulator() {
        return decay;
    }
}
