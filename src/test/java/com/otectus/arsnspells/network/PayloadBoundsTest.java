package com.otectus.arsnspells.network;

import com.otectus.arsnspells.cooldown.CooldownCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `ANS-MED-016` / `ANS-MED-017` — every payload must bound what it accepts off the wire.
 *
 * <p>The SimpleChannel → {@code CustomPacketPayload} rewrite carried the wire *format* across
 * but not the hardening: the ported payloads read unbounded strings and applied no range checks
 * at all. These tests pin the value clamps, which live in each record's compact constructor and
 * so are testable without a {@code ByteBuf}. The string length caps they pair with are enforced
 * by {@code ByteBufCodecs.stringUtf8(n)} in each {@code STREAM_CODEC}; a bare
 * {@code STRING_UTF8} defaults to {@code Short.MAX_VALUE}, i.e. 32 KB allocated per field.
 */
class PayloadBoundsTest {

    // ---- ResonanceSyncPayload: the value becomes a damage multiplier ----

    @Test
    void resonance_rejectsNaN() {
        assertEquals(1.0f, new ResonanceSyncPayload(Float.NaN).resonance(), 1.0e-6f,
            "NaN must fall back to neutral: it is not just out of range, it defeats every "
                + "Math.min-style cap downstream including spell_power_cap");
    }

    @Test
    void resonance_rejectsInfinity() {
        assertEquals(1.0f, new ResonanceSyncPayload(Float.POSITIVE_INFINITY).resonance(), 1.0e-6f);
        assertEquals(1.0f, new ResonanceSyncPayload(Float.NEGATIVE_INFINITY).resonance(), 1.0e-6f);
    }

    @Test
    void resonance_rejectsNegative() {
        assertEquals(1.0f, new ResonanceSyncPayload(-3.0f).resonance(), 1.0e-6f,
            "a negative multiplier would heal the target");
    }

    @Test
    void resonance_capsAbsurdValues() {
        assertTrue(new ResonanceSyncPayload(1e9f).resonance() <= 100.0f,
            "an absurd value must be capped, not passed through");
    }

    @Test
    void resonance_passesLegitimateValuesUnchanged() {
        assertEquals(1.2f, new ResonanceSyncPayload(1.2f).resonance(), 1.0e-6f,
            "the clamp must not disturb a value in the normal operating range");
    }

    // ---- AffinitySyncPayload: level is defined on [0,100] ----

    @Test
    void affinity_clampsLevelIntoRange() {
        assertEquals(100, new AffinitySyncPayload("irons_spellbooks:fire", 9999).level());
        assertEquals(0, new AffinitySyncPayload("irons_spellbooks:fire", -50).level());
        assertEquals(0, new AffinitySyncPayload("irons_spellbooks:fire", Integer.MIN_VALUE).level());
        assertEquals(100, new AffinitySyncPayload("irons_spellbooks:fire", Integer.MAX_VALUE).level());
    }

    @Test
    void affinity_passesLegitimateLevelsUnchanged() {
        assertEquals(37, new AffinitySyncPayload("irons_spellbooks:fire", 37).level());
    }

    @Test
    void affinity_toleratesANullId() {
        assertEquals("", new AffinitySyncPayload(null, 1).typeName(),
            "a null id must not become an NPE on the client");
    }

    // ---- CooldownSyncPayload: an unbounded end-tick is an unclearable cooldown ----

    @Test
    void cooldown_capsTimestamp() {
        long capped = new CooldownSyncPayload("OFFENSIVE", Long.MAX_VALUE).timestamp();
        assertTrue(capped <= 1_000_000_000_000L,
            "Long.MAX_VALUE would put the category on cooldown effectively forever, with no "
                + "way for the player to clear it; got " + capped);
    }

    @Test
    void cooldown_clampsNegativeTimestampToZero() {
        assertEquals(0L, new CooldownSyncPayload("OFFENSIVE", -1L).timestamp());
    }

    @Test
    void cooldown_passesLegitimateTimestampsUnchanged() {
        assertEquals(123456L, new CooldownSyncPayload("OFFENSIVE", 123456L).timestamp());
    }

    @Test
    void cooldown_categoryConvenienceConstructorRoundTrips() {
        CooldownSyncPayload p = new CooldownSyncPayload(CooldownCategory.OFFENSIVE, 500L);
        assertEquals(CooldownCategory.OFFENSIVE.name(), p.categoryName());
        assertEquals(500L, p.timestamp());
    }

    // ---- AffinityBulkSyncPayload: one packet carrying the whole map ----

    @Test
    void affinityBulk_clampsEveryLevel() {
        var p = new AffinityBulkSyncPayload(new java.util.LinkedHashMap<>(java.util.Map.of(
            "irons_spellbooks:fire", 5,
            "irons_spellbooks:ice", 9999,
            "irons_spellbooks:holy", -40)));
        assertEquals(5, p.levels().get("irons_spellbooks:fire"));
        assertEquals(100, p.levels().get("irons_spellbooks:ice"));
        assertEquals(0, p.levels().get("irons_spellbooks:holy"));
    }

    @Test
    void affinityBulk_dropsNullAndEmptyKeys() {
        var raw = new java.util.LinkedHashMap<String, Integer>();
        raw.put("irons_spellbooks:fire", 3);
        raw.put("", 4);
        raw.put(null, 5);
        raw.put("irons_spellbooks:ice", null);
        var p = new AffinityBulkSyncPayload(raw);
        assertEquals(1, p.levels().size(),
            "only the one well-formed entry may survive");
        assertEquals(3, p.levels().get("irons_spellbooks:fire"));
    }

    @Test
    void affinityBulk_boundsTheSchoolCount() {
        var raw = new java.util.LinkedHashMap<String, Integer>();
        for (int i = 0; i < 1000; i++) {
            raw.put("addon:school_" + i, 1);
        }
        assertTrue(new AffinityBulkSyncPayload(raw).levels().size() <= 256,
            "the school map must be bounded: the whole point of keying by id is that the "
                + "count is open-ended, so the wire needs a ceiling");
    }

    @Test
    void affinityBulk_tolerablesANullMap() {
        assertTrue(new AffinityBulkSyncPayload(null).levels().isEmpty());
    }
}
