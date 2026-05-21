package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-015 — verifies that {@link RegenSynergyHandler#sourceJarCacheMap} is
 * a {@link ConcurrentHashMap} (was {@code HashMap}).
 */
class RegenSynergyHandlerConcurrencyTest {

    @Test
    void sourceJarCacheMap_isConcurrentHashMap() throws Exception {
        Field f = RegenSynergyHandler.class.getDeclaredField("sourceJarCacheMap");
        f.setAccessible(true);
        Object value = f.get(null);
        assertNotNull(value);
        assertTrue(value instanceof ConcurrentHashMap,
            "sourceJarCacheMap must be a ConcurrentHashMap (ANS-HIGH-015); was " + value.getClass().getName());
    }
}
