package com.otectus.arsnspells.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Thread-safe config value cache to reduce file system access.
 * Caches config values for a short duration to prevent excessive file reads.
 */
public class ConfigCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCache.class);
    
    private static final Map<String, CachedValue<?>> cache = new ConcurrentHashMap<>();
    private static final long DEFAULT_CACHE_DURATION_MS = 5000; // 5 seconds
    private static long globalLastUpdate = 0;
    
    /**
     * Get a cached config value or compute it if not cached/expired
     * 
     * @param key Unique key for this config value
     * @param supplier Function to get the actual config value
     * @return The cached or freshly computed value
     */
    public static <T> T get(String key, Supplier<T> supplier) {
        return get(key, supplier, DEFAULT_CACHE_DURATION_MS);
    }
    
    /**
     * Get a cached config value with custom cache duration
     * 
     * @param key Unique key for this config value
     * @param supplier Function to get the actual config value
     * @param cacheDurationMs How long to cache this value
     * @return The cached or freshly computed value
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> supplier, long cacheDurationMs) {
        long now = System.currentTimeMillis();
        
        // Check if we have a cached value
        CachedValue<?> cached = cache.get(key);
        
        if (cached != null && !cached.isExpired(now)) {
            // Return cached value
            return (T) cached.value;
        }
        
        // Compute new value
        T value = supplier.get();
        
        // Cache it
        cache.put(key, new CachedValue<>(value, now, cacheDurationMs));
        
        return value;
    }
    
    /**
     * Invalidate a specific cached value
     */
    public static void invalidate(String key) {
        cache.remove(key);
        LOGGER.debug("Invalidated cache for key: {}", key);
    }
    
    /**
     * Invalidate all cached values
     */
    public static void invalidateAll() {
        int size = cache.size();
        cache.clear();
        globalLastUpdate = System.currentTimeMillis();
        LOGGER.info("Invalidated all cached config values ({})", size);
    }
    
    /**
     * Get cache statistics
     */
    public static CacheStats getStats() {
        return new CacheStats(
            cache.size(),
            cache.values().stream().filter(v -> !v.isExpired(System.currentTimeMillis())).count()
        );
    }
    
    /**
     * Cached value wrapper
     */
    private static class CachedValue<T> {
        final T value;
        final long timestamp;
        final long duration;
        
        CachedValue(T value, long timestamp, long duration) {
            this.value = value;
            this.timestamp = timestamp;
            this.duration = duration;
        }
        
        boolean isExpired(long now) {
            return (now - timestamp) > duration;
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int totalEntries;
        public final long validEntries;
        
        CacheStats(int totalEntries, long validEntries) {
            this.totalEntries = totalEntries;
            this.validEntries = validEntries;
        }
        
        @Override
        public String toString() {
            return String.format("Cache: %d total, %d valid", totalEntries, validEntries);
        }
    }
}
