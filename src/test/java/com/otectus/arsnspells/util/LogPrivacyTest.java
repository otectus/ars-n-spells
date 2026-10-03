package com.otectus.arsnspells.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Diagnostics players paste publicly carry a stable token, never the account UUID. */
class LogPrivacyTest {
    @Test
    void tokenIsStableDistinctAndHidesTheUuid() {
        UUID one = UUID.fromString("4bb1bb62-edfd-411b-a878-35b6b6d5573e");
        UUID two = UUID.fromString("4bb1bb62-edfd-411b-a878-35b6b6d5573f");
        String token = LogPrivacy.token(one);
        assertEquals(token, LogPrivacy.token(one), "one player's lines must correlate");
        assertNotEquals(token, LogPrivacy.token(two));
        assertTrue(token.matches("p-[0-9a-f]{8}"), token);
        for (String part : one.toString().split("-")) {
            assertFalse(token.contains(part), "no fragment of the UUID may appear: " + part);
        }
        assertEquals("p-none", LogPrivacy.token(null));
    }
}
