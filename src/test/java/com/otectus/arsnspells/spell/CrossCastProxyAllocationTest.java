package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the native-proxy sidecar additions: pool-id allocation, metadata
 * round-trip, entry lookup, and the per-book ceiling math. CompoundTag-only and
 * Bootstrap-free, mirroring {@link CrossCastNbtRoundTripTest}.
 */
class CrossCastProxyAllocationTest {

    private static CompoundTag ars(String body) {
        CompoundTag t = new CompoundTag();
        t.putString("recipe", body);
        return t;
    }

    @Test
    void metadataRoundTrips_andOmitsDefaults() {
        CompoundTag book = new CompoundTag();
        int idx = CrossCastNbt.addArsEntryWithMetaToTag(book,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, ars("heal"),
            3, "My Spell", "fire", "flame");
        assertEquals(0, idx);

        CompoundTag entry = book.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND)
            .getCompound(0);
        assertEquals(3, entry.getInt(CrossCastNbt.TAG_PROXY_POOL_ID));
        assertEquals("My Spell", entry.getString(CrossCastNbt.TAG_CUSTOM_NAME));
        assertEquals("fire", entry.getString(CrossCastNbt.TAG_NATURE));
        assertEquals("flame", entry.getString(CrossCastNbt.TAG_ICON_SYMBOL));
        assertEquals(CrossSpellType.ARS_NOUVEAU.name(), entry.getString(CrossCastNbt.TAG_SPELL_TYPE));

        // Defaults (no pool id, blank meta) are not written.
        CompoundTag book2 = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(book2,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, ars("x"),
            CrossCastNbt.NO_PROXY_POOL_ID, null, "", null);
        CompoundTag bare = book2.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND)
            .getCompound(0);
        assertFalse(bare.contains(CrossCastNbt.TAG_PROXY_POOL_ID));
        assertFalse(bare.contains(CrossCastNbt.TAG_CUSTOM_NAME));
        assertFalse(bare.contains(CrossCastNbt.TAG_NATURE));
        assertFalse(bare.contains(CrossCastNbt.TAG_ICON_SYMBOL));
    }

    @Test
    void allocatePicksSmallestFree_thenReportsFull() {
        CompoundTag book = new CompoundTag();
        // Three entries claiming ids 1, 3, 4 -> smallest free is 2.
        CrossCastNbt.addArsEntryWithMetaToTag(book, null, 1, ars("a"), 1, null, null, null);
        CrossCastNbt.addArsEntryWithMetaToTag(book, null, 1, ars("b"), 3, null, null, null);
        CrossCastNbt.addArsEntryWithMetaToTag(book, null, 1, ars("c"), 4, null, null, null);

        Set<Integer> used = CrossCastNbt.usedProxyPoolIds(book);
        assertTrue(used.containsAll(Set.of(1, 3, 4)));
        assertEquals(2, CrossCastNbt.allocateProxyPoolId(book, CrossCastNbt.PROXY_POOL_SIZE));

        // A book full to a small ceiling reports NO_PROXY_POOL_ID.
        CompoundTag full = new CompoundTag();
        for (int k = 1; k <= 2; k++) {
            CrossCastNbt.addArsEntryWithMetaToTag(full, null, 1, ars("e" + k), k, null, null, null);
        }
        assertEquals(CrossCastNbt.NO_PROXY_POOL_ID, CrossCastNbt.allocateProxyPoolId(full, 2));
        assertEquals(3, CrossCastNbt.allocateProxyPoolId(full, 4));
    }

    @Test
    void findEntryByProxyPoolId_returnsMatchOrNull() {
        CompoundTag book = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(book, null, 1, ars("a"), 1, "one", null, null);
        CrossCastNbt.addArsEntryWithMetaToTag(book, null, 1, ars("b"), 5, "five", null, null);

        CompoundTag five = CrossCastNbt.findEntryByProxyPoolId(book, 5);
        assertNotNull(five);
        assertEquals("five", five.getString(CrossCastNbt.TAG_CUSTOM_NAME));
        assertNull(CrossCastNbt.findEntryByProxyPoolId(book, 2));
        assertEquals(2, CrossCastNbt.countArsEntries(book));
    }

    @Test
    void listOrderIsPreservedAcrossEntries() {
        CompoundTag book = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(book, null, 1, ars("first"), 1, null, null, null);
        CrossCastNbt.addArsEntryWithMetaToTag(book, null, 1, ars("second"), 2, null, null, null);
        ListTag list = book.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        assertEquals("first", list.getCompound(0).getCompound(CrossCastNbt.TAG_ARS_SPELL).getString("recipe"));
        assertEquals("second", list.getCompound(1).getCompound(CrossCastNbt.TAG_ARS_SPELL).getString("recipe"));
    }

    @Test
    void effectiveProxyCeiling_treatsNegativeAsPoolSize() {
        assertEquals(CrossCastNbt.PROXY_POOL_SIZE, IronsBookBindingUtil.effectiveProxyCeiling(-1));
        assertEquals(3, IronsBookBindingUtil.effectiveProxyCeiling(3));
        // A cap above the pool size is clamped to the pool size.
        assertEquals(CrossCastNbt.PROXY_POOL_SIZE,
            IronsBookBindingUtil.effectiveProxyCeiling(CrossCastNbt.PROXY_POOL_SIZE + 50));
    }
}
