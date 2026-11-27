package com.otectus.arsnspells.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.4.1 review CONFIG-01: who may change world settings, and which values a request may carry. */
class ConfigAuthorityTest {
    @Test
    void operatorsMayAlways() {
        assertTrue(ConfigAuthority.decide(true, true, false), "a dedicated-server operator");
        assertTrue(ConfigAuthority.decide(true, false, false), "an operator on an opened-to-LAN world");
    }

    @Test
    void theSingleplayerOwnerMayWithoutCheats() {
        assertTrue(ConfigAuthority.decide(false, false, true), "the owner of an integrated server, permission level 0");
    }

    @Test
    void guestsAndNonOperatorsMayNot() {
        assertFalse(ConfigAuthority.decide(false, false, false), "a LAN guest is not the owner");
        assertFalse(ConfigAuthority.decide(false, true, false), "a non-op on a dedicated server");
        assertFalse(ConfigAuthority.decide(false, true, true), "a dedicated server has no owner, whatever its profile check says");
    }

    @Test
    void requestValuesAreValidatedByType() {
        var mode = AnsSettings.setting(AnsSettings.MODE);
        assertTrue("hybrid".equals(AnsSettings.parse(mode, "hybrid")));
        assertTrue("ars_primary".equals(AnsSettings.parse(mode, "ARS_PRIMARY")), "mode names are case-insensitive");
        assertNull(AnsSettings.parse(mode, "bogus"));
        var toggle = AnsSettings.setting("debug_mode");
        assertTrue(Boolean.TRUE.equals(AnsSettings.parse(toggle, "true")));
        assertNull(AnsSettings.parse(toggle, "yes"), "booleans are exactly true or false");
        var ticks = AnsSettings.setting("inscribed_ars_default_cooldown_ticks");
        assertTrue(Integer.valueOf(40).equals(AnsSettings.parse(ticks, "40")));
        assertNull(AnsSettings.parse(ticks, "12001"), "the config's own range");
        assertNull(AnsSettings.parse(ticks, "1.5"));
        assertNull(AnsSettings.setting("enable_lp_system"), "only the screen's settings can be requested");
    }
}
