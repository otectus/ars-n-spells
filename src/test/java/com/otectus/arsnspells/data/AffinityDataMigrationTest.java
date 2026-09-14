package com.otectus.arsnspells.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bootstrap-free tests for {@link AffinityData}'s versioned codec and the
 * 2.0.x -> 2.5.0 legacy migration. Uses {@link NbtOps#INSTANCE} so no Minecraft
 * Bootstrap is required (same approach as the cross-cast round-trip tests).
 */
class AffinityDataMigrationTest {

    @Test
    void currentSchemaRoundTrips() {
        AffinityData d = new AffinityData();
        d.addLevel("irons_spellbooks:fire", 5);
        d.addLevel("cataclysm_spellbooks:abyssal", 12);

        Tag encoded = AffinityData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        AffinityData decoded = AffinityData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(5, decoded.getLevel("irons_spellbooks:fire"));
        assertEquals(12, decoded.getLevel("cataclysm_spellbooks:abyssal"));
    }

    @Test
    void encodeWritesVersionedRecord() {
        AffinityData d = new AffinityData();
        d.addLevel("irons_spellbooks:fire", 1);
        Tag encoded = AffinityData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();

        CompoundTag tag = assertInstanceOf(CompoundTag.class, encoded);
        assertTrue(tag.contains("schema_version"), "encode must stamp schema_version");
        assertTrue(tag.contains("levels"), "encode must write a levels map");
        assertEquals(AffinityData.SCHEMA_VERSION, tag.getInt("schema_version"));
    }

    @Test
    void legacyEnumKeysMigrateToCanonicalIds() {
        // A pre-2.5.0 save: a bare map of AffinityType.name() -> level (no schema_version).
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("FIRE", 5);
        legacy.putInt("ICE", 3);
        legacy.putInt("ELDRITCH", 8);

        AffinityData decoded = AffinityData.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();

        assertEquals(5, decoded.getLevel("irons_spellbooks:fire"));
        assertEquals(3, decoded.getLevel("irons_spellbooks:ice"));
        assertEquals(8, decoded.getLevel("irons_spellbooks:eldritch"));
    }

    @Test
    void legacyCategoryBucketsAndUnknownsAreArchived() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("FIRE", 5);       // migrates
        legacy.putInt("OFFENSIVE", 9);  // archived category bucket
        legacy.putInt("HYBRID", 4);     // archived source bucket
        legacy.putInt("BOGUS", 7);      // archived unknown key

        AffinityData decoded = AffinityData.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();

        assertEquals(5, decoded.getLevel("irons_spellbooks:fire"));
        // Only real elemental data activates bonuses; unresolved keys remain archival.
        assertEquals(1, decoded.getAllLevels().size());
        assertFalse(decoded.getAllLevels().containsKey("OFFENSIVE"));
        assertEquals(9, decoded.unresolvedLegacy().get("OFFENSIVE"));
        AffinityData roundTrip = AffinityData.CODEC.parse(NbtOps.INSTANCE,
            AffinityData.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow()).getOrThrow();
        assertEquals(decoded.unresolvedLegacy(), roundTrip.unresolvedLegacy());
    }

    @Test
    void migratedDataReEncodesAsCurrentRecord() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("FIRE", 5);
        AffinityData decoded = AffinityData.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();

        CompoundTag reEncoded = assertInstanceOf(CompoundTag.class,
            AffinityData.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow());
        // After one save the world is migrated to the versioned shape.
        assertTrue(reEncoded.contains("schema_version"));
        assertEquals(5, reEncoded.getCompound("levels").getInt("irons_spellbooks:fire"));
    }

    @Test
    void levelsAreClampedToZeroHundred() {
        AffinityData d = new AffinityData();
        d.setLevel("x", 250);
        d.setLevel("y", -5);
        assertEquals(100, d.getLevel("x"));
        assertEquals(0, d.getLevel("y"));
    }

    // ---- Malformed-field tolerance ----

    @Test
    void malformedDecayRemaindersDoNotDestroyLevels() {
        // The two payload fields used to be strict, so one bad field failed the whole record,
        // the legacy fallback failed too, and NeoForge silently DROPPED the attachment - every
        // school's affinity gone, nothing in the log.
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", AffinityData.SCHEMA_VERSION);
        CompoundTag levels = new CompoundTag();
        levels.putInt("irons_spellbooks:fire", 7);
        tag.put("levels", levels);
        tag.putString("decay_remainders", "not a map");

        AffinityData decoded = AffinityData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(7, decoded.getLevel("irons_spellbooks:fire"),
            "a malformed decay field must not cost the levels map");
    }

    @Test
    void malformedLevelsStillYieldsUsableData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", AffinityData.SCHEMA_VERSION);
        tag.putString("levels", "not a map");

        AffinityData decoded = AffinityData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(0, decoded.getLevel("irons_spellbooks:fire"));
    }

    @Test
    void decayAccumulatorIsNotSharedBetweenDecodes() {
        // The default used to be one instance built at class-init and handed to every decode
        // that lacked the field. DecayAccumulator is mutable, so two players could share one.
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", AffinityData.SCHEMA_VERSION);
        tag.put("levels", new CompoundTag());

        AffinityData first = AffinityData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        AffinityData second = AffinityData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();

        assertNotSame(first.getDecayAccumulator(), second.getDecayAccumulator(),
            "each decode must get its own mutable accumulator");
    }
}
