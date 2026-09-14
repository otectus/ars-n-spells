package com.otectus.arsnspells.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread token bucket and short replay window; no world or upstream API dependency. */
public final class RequestAdmission {
    public enum Result { ACCEPTED, DUPLICATE, RATE_LIMITED, INVALID }

    private static final long WINDOW_NANOS = 60_000_000_000L;
    private static final int MAX_RECENT_REQUESTS = 2048;
    private final Map<UUID, Long> recent = new LinkedHashMap<>();
    private double tokens;
    private long lastRefill;
    private boolean initialized;

    public Result admit(UUID request, long now, int perSecond, int burst) {
        if (request == null || request.equals(new UUID(0, 0)) || perSecond < 1 || burst < 1) {
            return Result.INVALID;
        }
        recent.entrySet().removeIf(entry -> now - entry.getValue() >= WINDOW_NANOS);
        if (recent.containsKey(request)) return Result.DUPLICATE;
        if (!initialized) {
            tokens = burst;
            lastRefill = now;
            initialized = true;
        } else {
            long elapsed = Math.max(0, now - lastRefill);
            tokens = Math.min(burst, tokens + elapsed / 1_000_000_000.0 * perSecond);
            lastRefill = now;
        }
        if (tokens < 1 || recent.size() >= MAX_RECENT_REQUESTS) return Result.RATE_LIMITED;
        tokens--;
        recent.put(request, now);
        return Result.ACCEPTED;
    }

    public int rememberedRequests() { return recent.size(); }
}
