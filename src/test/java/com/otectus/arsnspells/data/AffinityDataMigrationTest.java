package com.otectus.arsnspells.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    void legacyCategoryBucketsAndUnknownsAreDropped() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("FIRE", 5);       // migrates
        legacy.putInt("OFFENSIVE", 9);  // category bucket -> dropped
        legacy.putInt("HYBRID", 4);     // source bucket   -> dropped
        legacy.putInt("BOGUS", 7);      // unknown          -> dropped

        AffinityData decoded = AffinityData.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();

        assertEquals(5, decoded.getLevel("irons_spellbooks:fire"));
        // Only the one real elemental survives; buckets/unknowns gone.
        assertEquals(1, decoded.getAllLevels().size());
        assertFalse(decoded.getAllLevels().containsKey("OFFENSIVE"));
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
}
