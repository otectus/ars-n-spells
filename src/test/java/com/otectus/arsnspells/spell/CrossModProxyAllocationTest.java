package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.0.0 native-wheel proxy pool — allocation/lookup semantics over
 * {@link CrossModSpellComponents}' pure list-level helpers, plus the
 * backward-compatibility contract for entries written before 3.0.x.
 * Bootstrap-free (codec via {@link NbtOps}).
 */
class CrossModProxyAllocationTest {

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    private static CrossModSpellList withEntry(CrossModSpellList list, String body, int poolId) {
        return CrossModSpellComponents.withArsEntry(list,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, arsPayload(body),
            poolId, "Name " + body, "fire", "spark");
    }

    @Test
    void allocation_findsSmallestFreeId() {
        CrossModSpellList list = CrossModSpellList.EMPTY;
        list = withEntry(list, "a", 1);
        list = withEntry(list, "b", 3);
        assertEquals(2, CrossModSpellComponents.allocateProxyPoolId(list, 8),
            "smallest free id in [1,8] must be 2 when 1 and 3 are taken");
    }

    @Test
    void allocation_exhaustsAtPoolCeiling() {
        CrossModSpellList list = CrossModSpellList.EMPTY;
        for (int k = 1; k <= 4; k++) {
            list = withEntry(list, "spell" + k, k);
        }
        assertEquals(CrossModSpellComponents.NO_PROXY_POOL_ID,
            CrossModSpellComponents.allocateProxyPoolId(list, 4),
            "a full pool must report NO_PROXY_POOL_ID");
        assertEquals(5, CrossModSpellComponents.allocateProxyPoolId(list, 8),
            "raising the ceiling frees the next id");
    }

    @Test
    void usedIds_ignoreEntriesWithoutPool() {
        CrossModSpellList list = CrossModSpellList.EMPTY;
        list = withEntry(list, "a", 2);
        list = CrossModSpellComponents.withArsEntry(list, null, 1, arsPayload("legacy"),
            CrossModSpellComponents.NO_PROXY_POOL_ID, null, null, null);
        assertEquals(Set.of(2), CrossModSpellComponents.usedProxyPoolIds(list));
    }

    @Test
    void findByPoolId_returnsTheMatchingEntry() {
        CrossModSpellList list = CrossModSpellList.EMPTY;
        list = withEntry(list, "a", 1);
        list = withEntry(list, "b", 2);
        Optional<CrossModSpell> found = CrossModSpellComponents.findEntryByProxyPoolId(list, 2);
        assertTrue(found.isPresent());
        assertEquals("b", found.get().arsSpellTag().orElseThrow().getString("recipe"));
        assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(list, 5).isEmpty());
        assertTrue(CrossModSpellComponents.findEntryByProxyPoolId(list,
            CrossModSpellComponents.NO_PROXY_POOL_ID).isEmpty(),
            "the NO_PROXY_POOL_ID sentinel must never match an entry");
    }

    @Test
    void countArsEntries_countsOnlyPayloadCarriers() {
        CrossModSpellList list = CrossModSpellList.EMPTY;
        list = withEntry(list, "a", 1);
        List<CrossModSpell> spells = new java.util.ArrayList<>(list.spells());
        spells.add(new CrossModSpell(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fireball"),
            2, CrossSpellType.IRONS_SPELLBOOKS.name(), Optional.empty(), Optional.of("SPELLBOOK")));
        CrossModSpellList mixed = new CrossModSpellList(List.copyOf(spells), 0);
        assertEquals(1, CrossModSpellComponents.countArsEntries(mixed));
    }

    @Test
    void legacyEntryWithoutProxyFields_decodesWithDefaults() {
        // Hand-build the NBT shape a 2.6.1-era component produced (no
        // proxy_pool_id / custom_name / nature / icon_symbol keys) and decode it
        // with the extended codec: the save-compat promise of the 3.0.x port.
        CompoundTag entry = new CompoundTag();
        entry.putString("spell_id", "ars_nouveau:spell");
        entry.putInt("spell_level", 1);
        entry.putString("spell_type", CrossSpellType.ARS_NOUVEAU.name());
        entry.put("ars_spell", arsPayload("legacy"));

        CrossModSpell decoded = CrossModSpell.CODEC.parse(NbtOps.INSTANCE, entry).getOrThrow();
        assertEquals(CrossModSpellComponents.NO_PROXY_POOL_ID, decoded.proxyPoolId());
        assertFalse(decoded.hasProxyPool());
        assertTrue(decoded.customName().isEmpty());
        assertTrue(decoded.nature().isEmpty());
        assertTrue(decoded.iconSymbol().isEmpty());
        assertEquals("legacy", decoded.arsSpellTag().orElseThrow().getString("recipe"));
    }

    @Test
    void defaultProxyFields_areOmittedOnEncode() {
        // Entries without proxy metadata must encode byte-identically to the
        // 2.6.1 shape (optionalFieldOf omits defaults), so downgrades and mixed
        // lists stay clean.
        CrossModSpell legacyShaped = new CrossModSpell(
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1,
            CrossSpellType.ARS_NOUVEAU.name(),
            Optional.of(arsPayload("x")), Optional.empty());
        Tag encoded = CrossModSpell.CODEC.encodeStart(NbtOps.INSTANCE, legacyShaped).getOrThrow();
        CompoundTag c = (CompoundTag) encoded;
        assertFalse(c.contains("proxy_pool_id"), "default pool id must be omitted");
        assertFalse(c.contains("custom_name"));
        assertFalse(c.contains("nature"));
        assertFalse(c.contains("icon_symbol"));
    }

    @Test
    void proxyMetadata_roundTripsThroughCodec() {
        CrossModSpellList list = withEntry(CrossModSpellList.EMPTY, "a", 3);
        Tag encoded = CrossModSpellList.CODEC.encodeStart(NbtOps.INSTANCE, list).getOrThrow();
        CrossModSpellList decoded = CrossModSpellList.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        CrossModSpell d = decoded.spells().get(0);
        assertEquals(3, d.proxyPoolId());
        assertEquals(Optional.of("Name a"), d.customName());
        assertEquals(Optional.of("fire"), d.nature());
        assertEquals(Optional.of("spark"), d.iconSymbol());
    }

    @Test
    void whitelists_matchShippedIconSet() {
        // The nature/icon whitelists pair 1:1 with shipped textures and lang
        // keys; pool size pairs with the registered ars_cross_1..N proxies.
        assertEquals(8, CrossModSpellComponents.NATURE_KEYS.size());
        assertEquals(8, CrossModSpellComponents.ICON_SYMBOLS.size());
        assertEquals(8, CrossModSpellComponents.PROXY_POOL_SIZE);
    }
}
