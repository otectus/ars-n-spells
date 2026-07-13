package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the pure decision core of the spellbook-binding step
 * ({@link IronsBookBindingUtil#planAppend}) and the carrier-validation
 * predicate ({@link IronsBookBindingUtil#extractSingleEntry(CrossModSpellList)}).
 * List-level ports of the 1.20.1 {@code IronsBookBindingNbtTest} /
 * {@code SpellbookBindingPredicateTest} tag-level tests; the ItemStack /
 * proxy-slot mirroring halves are covered in-game (TESTING_GUIDE).
 */
class IronsBookBindingPlanTest {

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    private static CrossModSpellList bookWith(String... bodies) {
        CrossModSpellList list = CrossModSpellList.EMPTY;
        int pool = 1;
        for (String body : bodies) {
            list = CrossModSpellComponents.withArsEntry(list,
                CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, arsPayload(body),
                pool++, null, null, null);
        }
        return list;
    }

    // ---- planAppend ----

    @Test
    void planAppend_addsWithSmallestFreePoolId() {
        var plan = IronsBookBindingUtil.planAppend(bookWith("a", "b"), arsPayload("c"), -1);
        assertEquals(IronsBookBindingUtil.AppendResult.ADDED, plan.result());
        assertEquals(3, plan.poolId());
    }

    @Test
    void planAppend_rejectsDuplicatePayload() {
        var plan = IronsBookBindingUtil.planAppend(bookWith("a"), arsPayload("a"), -1);
        assertEquals(IronsBookBindingUtil.AppendResult.DUPLICATE, plan.result());
        assertEquals(CrossModSpellComponents.NO_PROXY_POOL_ID, plan.poolId());
    }

    @Test
    void planAppend_reportsBookFullAtPoolCeiling() {
        String[] eight = {"s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8"};
        var plan = IronsBookBindingUtil.planAppend(bookWith(eight), arsPayload("ninth"), -1);
        assertEquals(IronsBookBindingUtil.AppendResult.BOOK_FULL, plan.result());
    }

    @Test
    void planAppend_respectsConfiguredCap() {
        var plan = IronsBookBindingUtil.planAppend(bookWith("a", "b"), arsPayload("c"), 2);
        assertEquals(IronsBookBindingUtil.AppendResult.BOOK_FULL, plan.result());
    }

    @Test
    void planAppend_failsOnMissingPayload() {
        assertEquals(IronsBookBindingUtil.AppendResult.FAILED,
            IronsBookBindingUtil.planAppend(bookWith("a"), null, -1).result());
        assertEquals(IronsBookBindingUtil.AppendResult.FAILED,
            IronsBookBindingUtil.planAppend(bookWith("a"), new CompoundTag(), -1).result());
    }

    @Test
    void effectiveCeiling_isBoundedByPoolSize() {
        assertEquals(CrossModSpellComponents.PROXY_POOL_SIZE,
            IronsBookBindingUtil.effectiveProxyCeiling(-1), "negative cap means pool-size bound");
        assertEquals(3, IronsBookBindingUtil.effectiveProxyCeiling(3));
        assertEquals(CrossModSpellComponents.PROXY_POOL_SIZE,
            IronsBookBindingUtil.effectiveProxyCeiling(64), "cap can never exceed the pool size");
    }

    // ---- carrier validation (extractSingleEntry) ----

    @Test
    void carrier_requiresExactlyOneEntry() {
        assertTrue(IronsBookBindingUtil.extractSingleEntry(CrossModSpellList.EMPTY).isEmpty());
        assertTrue(IronsBookBindingUtil.extractSingleEntry(bookWith("a")).isPresent());
        assertTrue(IronsBookBindingUtil.extractSingleEntry(bookWith("a", "b")).isEmpty(),
            "an ambiguous multi-entry carrier must be rejected, not guessed at");
    }

    @Test
    void carrier_requiresArsTypeWithNonEmptyPayload() {
        CrossModSpellList ironsOnly = new CrossModSpellList(List.of(new CrossModSpell(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fireball"),
            2, CrossSpellType.IRONS_SPELLBOOKS.name(), Optional.empty(), Optional.of("SPELLBOOK"))), 0);
        assertTrue(IronsBookBindingUtil.extractSingleEntry(ironsOnly).isEmpty(),
            "an Iron's-typed entry is not a valid Ars carrier");

        CrossModSpellList emptyPayload = new CrossModSpellList(List.of(new CrossModSpell(
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU.name(),
            Optional.of(new CompoundTag()), Optional.empty())), 0);
        assertTrue(IronsBookBindingUtil.extractSingleEntry(emptyPayload).isEmpty(),
            "an empty ars_spell payload must be rejected");
    }

    @Test
    void carrier_preservesLoomDisplayMetadata() {
        CrossModSpellList carrier = CrossModSpellComponents.withArsEntry(CrossModSpellList.EMPTY,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, arsPayload("a"),
            CrossModSpellComponents.NO_PROXY_POOL_ID, "My Spell", "ice", "moon");
        Optional<CrossModSpell> entry = IronsBookBindingUtil.extractSingleEntry(carrier);
        assertTrue(entry.isPresent());
        assertEquals(Optional.of("My Spell"), entry.get().customName());
        assertEquals(Optional.of("ice"), entry.get().nature());
        assertEquals(Optional.of("moon"), entry.get().iconSymbol());
    }
}
