package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlasphemySchoolsTest {
    @Test void canonicalSchoolsNeverMatchUnrelatedNamespacesOrSubstrings() {
        assertEquals("fire", BlasphemySchools.canonical("fire"));
        assertEquals("fire", BlasphemySchools.canonical("irons_spellbooks:fire"));
        assertNull(BlasphemySchools.canonical("unrelated:fire"));
        assertNull(BlasphemySchools.canonical("firework"));
        assertNull(BlasphemySchools.canonical(null));
    }
}
