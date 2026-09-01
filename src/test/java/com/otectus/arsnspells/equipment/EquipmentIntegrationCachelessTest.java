package com.otectus.arsnspells.equipment;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-014 - the equipment bonus path must not keep a per-player cache.
 *
 * <p><b>Port divergence, and the stronger form of the fix.</b> The 1.20.1 line scanned every
 * equipment slot per player and memoised the result in a map, which had to be made a
 * {@code ConcurrentHashMap} after it threw {@code ConcurrentModificationException} on
 * long-running servers. This port reads the aggregate {@code PerkAttributes} value off the
 * player instead, which is both simpler and strictly more correct - it captures perk- and
 * curio-applied bonuses the per-item scan missed - and needs no cache at all. No cache, no
 * concurrency hazard to guard.
 *
 * <p>This test pins the cachelessness, because reintroducing a cache would reintroduce the
 * bug class the ticket was filed for.
 */
class EquipmentIntegrationCachelessTest {

    private static String source() throws IOException {
        return Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java"));
    }

    @Test
    void noPerPlayerCacheField() throws IOException {
        String src = source();
        assertFalse(src.contains("equipmentCache"),
            "the per-player equipment cache is gone by design (ANS-HIGH-014); the aggregate "
                + "attribute read needs no memoisation");
        assertFalse(src.contains("new HashMap<>()"),
            "a plain HashMap here is the exact shape that threw CME on long-running servers");
    }

    @Test
    void readsTheAggregateAttributeInstead() throws IOException {
        String src = source();
        assertTrue(src.contains("PerkAttributes.MAX_MANA")
                && src.contains("PerkAttributes.MANA_REGEN_BONUS"),
            "the bonus must come from the aggregate player attributes, which is what makes "
                + "the cache unnecessary and catches curio/perk sources the scan missed");
    }
}
