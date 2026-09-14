package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LpSourcePolicyTest {
    @Test void healthOnlyIgnoresAnInstalledAndFundedSoulNetwork() {
        assertEquals(LpSourcePolicy.Source.HEALTH, LpSourcePolicy.select("HEALTH_ONLY", true, 100000, 100));
        assertEquals(190, LpSourcePolicy.available("HEALTH_ONLY", true, 100000, 190));
    }
    @Test void priorityChoosesBloodOnlyWhenItCanCoverTheEntireQuote() {
        assertEquals(LpSourcePolicy.Source.BLOOD_MAGIC, LpSourcePolicy.select("BLOOD_MAGIC_PRIORITY", true, 100, 100));
        assertEquals(LpSourcePolicy.Source.HEALTH, LpSourcePolicy.select("BLOOD_MAGIC_PRIORITY", true, 99, 100));
        assertEquals(90, LpSourcePolicy.available("BLOOD_MAGIC_PRIORITY", true, 60, 90));
    }
    @Test void bloodOnlyNeverSubstitutesHealthWhenTheAdapterIsAbsent() {
        assertEquals(LpSourcePolicy.Source.UNAVAILABLE, LpSourcePolicy.select("BLOOD_MAGIC_ONLY", false, 0, 100));
        assertEquals(0, LpSourcePolicy.available("BLOOD_MAGIC_ONLY", false, 0, 190));
        assertEquals(LpSourcePolicy.Source.BLOOD_MAGIC, LpSourcePolicy.select("BLOOD_MAGIC_ONLY", true, 50, 100));
    }
}
