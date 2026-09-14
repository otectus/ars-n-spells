package com.otectus.arsnspells.contract;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.otectus.arsnspells.contract.RequestAdmission.Result.*;

class RequestAdmissionTest {
    @Test void retriesAndReorderedDuplicatesNeverSpendAnotherToken() {
        RequestAdmission gate = new RequestAdmission();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertEquals(ACCEPTED, gate.admit(first, 0, 10, 4));
        assertEquals(ACCEPTED, gate.admit(second, 0, 10, 4));
        for (int i = 0; i < 100; i++) assertEquals(DUPLICATE, gate.admit(first, 0, 10, 4));
        assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), 0, 10, 4));
        assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), 0, 10, 4));
        assertEquals(RATE_LIMITED, gate.admit(UUID.randomUUID(), 0, 10, 4));
    }

    @Test void allowsLatencyBurstAndRefillsAtConfiguredRate() {
        RequestAdmission gate = new RequestAdmission();
        for (int i = 0; i < 4; i++) assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), 0, 10, 4));
        assertEquals(RATE_LIMITED, gate.admit(UUID.randomUUID(), 99_000_000, 10, 4));
        assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), 100_000_000, 10, 4));
        assertEquals(RATE_LIMITED, gate.admit(UUID.randomUUID(), 100_000_000, 10, 4));
        assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), 300_000_000, 10, 4));
        assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), 300_000_000, 10, 4));
    }

    @Test void replayWindowExpiresAndMemoryStaysBoundedAtMaximumConfiguration() {
        RequestAdmission gate = new RequestAdmission();
        UUID first = UUID.randomUUID();
        assertEquals(ACCEPTED, gate.admit(first, 0, 20, 20));
        for (long i = 1; i < 100_000; i++) {
            assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), i * 50_000_000, 20, 20));
        }
        assertTrue(gate.rememberedRequests() <= 1201);
        assertEquals(ACCEPTED, gate.admit(first, 5_000_000_000_000L, 20, 20));
    }

    @Test void playerBucketsDoNotInterfereAndInvalidIdsAreRefused() {
        RequestAdmission a = new RequestAdmission(), b = new RequestAdmission();
        UUID id = UUID.randomUUID();
        assertEquals(INVALID, a.admit(new UUID(0, 0), 0, 10, 4));
        assertEquals(INVALID, a.admit(null, 0, 10, 4));
        assertEquals(ACCEPTED, a.admit(id, 0, 10, 4));
        assertEquals(ACCEPTED, b.admit(id, 0, 10, 4));
        assertEquals(1, a.rememberedRequests());
    }

    @Test void liveConfigurationReductionClampsAccumulatedBurst() {
        RequestAdmission gate = new RequestAdmission();
        assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), 0, 20, 20));
        assertEquals(ACCEPTED, gate.admit(UUID.randomUUID(), 0, 1, 1));
        assertEquals(RATE_LIMITED, gate.admit(UUID.randomUUID(), 0, 1, 1));
    }
}
