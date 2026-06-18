package com.otectus.arsnspells.spell;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bootstrap-free round-trip tests for the new data-component-backed
 * cross-cast inscription storage. Replaces the deleted
 * {@code CrossCastNbtRoundTripTest} that targeted root-NBT keys under
 * Forge 1.20.1.
 *
 * Uses {@link NbtOps#INSTANCE} so the Codec path is exercised without
 * requiring Minecraft's full {@code Bootstrap} dance. The StreamCodec
 * path is intentionally not exercised here; it requires
 * {@code RegistryFriendlyByteBuf}, which needs a live registry access
 * and is verified in PayloadStreamCodecTest under a runClient.
 */
class CrossModSpellListRoundTripTest {

    private static CrossModSpell sampleIrons(String spellPath, int level) {
        return new CrossModSpell(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", spellPath),
            level,
            CrossSpellType.IRONS_SPELLBOOKS.name(),
            Optional.empty(),
            Optional.of("SPELLBOOK")
        );
    }

    private static CrossModSpell sampleArs(String spellPath, int level, CompoundTag arsTag) {
        return new CrossModSpell(
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", spellPath),
            level,
            CrossSpellType.ARS_NOUVEAU.name(),
            Optional.of(arsTag),
            Optional.empty()
        );
    }

    @Test
    void emptyListRoundTrips() {
        CrossModSpellList list = CrossModSpellList.EMPTY;
        Tag encoded = CrossModSpellList.CODEC.encodeStart(NbtOps.INSTANCE, list).getOrThrow();
        CrossModSpellList decoded = CrossModSpellList.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(list.size(), decoded.size());
        assertEquals(list.selectedIndex(), decoded.selectedIndex());
        assertTrue(decoded.isEmpty());
    }

    @Test
    void singleIronsEntryRoundTrips() {
        CrossModSpellList list = new CrossModSpellList(List.of(sampleIrons("fireball", 3)), 0);
        Tag encoded = CrossModSpellList.CODEC.encodeStart(NbtOps.INSTANCE, list).getOrThrow();
        CrossModSpellList decoded = CrossModSpellList.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(1, decoded.size());
        CrossModSpell d = decoded.spells().get(0);
        assertEquals("irons_spellbooks", d.spellId().getNamespace());
        assertEquals("fireball", d.spellId().getPath());
        assertEquals(3, d.level());
        assertEquals(CrossSpellType.IRONS_SPELLBOOKS.name(), d.typeName());
        assertEquals(Optional.of("SPELLBOOK"), d.castSource());
        assertEquals(Optional.empty(), d.arsSpellTag());
    }

    @Test
    void arsEntryPreservesEmbeddedTag() {
        CompoundTag arsTag = new CompoundTag();
        arsTag.putString("name", "Test Spell");
        arsTag.putInt("cost", 42);
        CrossModSpellList list = new CrossModSpellList(List.of(sampleArs("lightning", 1, arsTag)), 0);
        Tag encoded = CrossModSpellList.CODEC.encodeStart(NbtOps.INSTANCE, list).getOrThrow();
        CrossModSpellList decoded = CrossModSpellList.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        CrossModSpell d = decoded.spells().get(0);
        assertTrue(d.arsSpellTag().isPresent());
        CompoundTag decodedTag = d.arsSpellTag().get();
        assertEquals("Test Spell", decodedTag.getString("name"));
        assertEquals(42, decodedTag.getInt("cost"));
    }

    @Test
    void multipleEntriesAndSelectedIndexSurvive() {
        CrossModSpellList list = new CrossModSpellList(
            List.of(sampleIrons("fireball", 2), sampleIrons("magic_missile", 5), sampleIrons("ice_spike", 1)),
            2
        );
        Tag encoded = CrossModSpellList.CODEC.encodeStart(NbtOps.INSTANCE, list).getOrThrow();
        CrossModSpellList decoded = CrossModSpellList.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(3, decoded.size());
        assertEquals(2, decoded.selectedIndex());
        assertEquals("magic_missile", decoded.spells().get(1).spellId().getPath());
        assertEquals("ice_spike", decoded.spells().get(2).spellId().getPath());
    }

    @Test
    void normalizedIndexClampsOutOfBounds() {
        CrossModSpellList negative = new CrossModSpellList(List.of(sampleIrons("fireball", 1)), -3);
        CrossModSpellList tooLarge = new CrossModSpellList(List.of(sampleIrons("fireball", 1)), 99);
        assertEquals(0, negative.normalizedIndex());
        assertEquals(0, tooLarge.normalizedIndex());
    }

    @Test
    void emptyListNormalizedIndexIsZero() {
        assertEquals(0, CrossModSpellList.EMPTY.normalizedIndex());
        assertEquals(0, new CrossModSpellList(List.of(), 42).normalizedIndex());
    }

    @Test
    void invalidEncodedDataFailsCleanly() {
        // A bare string is not a valid CrossModSpellList — codec must reject without throwing.
        DataResult<CrossModSpellList> result = CrossModSpellList.CODEC.parse(NbtOps.INSTANCE,
            net.minecraft.nbt.StringTag.valueOf("garbage"));
        assertTrue(result.error().isPresent());
        assertNotNull(result.error().get().message());
    }
}
