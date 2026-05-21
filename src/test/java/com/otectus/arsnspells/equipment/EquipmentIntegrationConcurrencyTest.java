package com.otectus.arsnspells.equipment;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-014 — verifies that {@link EquipmentIntegration#equipmentCache} is
 * declared as a {@link ConcurrentHashMap} (was {@link java.util.HashMap}, which
 * could CME on long-running multiplayer servers).
 *
 * <p>This is a structural assertion — testing actual concurrency would require
 * mocking {@code Player} and the Curios API. The structural check pins the type
 * declaration so a future refactor cannot silently regress to {@code HashMap}.
 */
class EquipmentIntegrationConcurrencyTest {

    @Test
    void equipmentCache_isConcurrentHashMap() throws Exception {
        Field f = EquipmentIntegration.class.getDeclaredField("equipmentCache");
        f.setAccessible(true);
        Object value = f.get(null);
        assertNotNull(value, "equipmentCache must be initialised");
        assertTrue(value instanceof ConcurrentHashMap,
            "equipmentCache must be a ConcurrentHashMap (ANS-HIGH-014); was " + value.getClass().getName());
    }

    @Test
    void equipmentCache_acceptsConcurrentPuts() throws Exception {
        Field f = EquipmentIntegration.class.getDeclaredField("equipmentCache");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) f.get(null);
        // Smoke test: just exercises the put/remove path on the actual field instance
        // to confirm it's a Map and tolerates concurrent operations from this thread.
        Object key = new Object();
        map.put(key, "test");
        assertEquals("test", map.get(key));
        map.remove(key);
    }
}
