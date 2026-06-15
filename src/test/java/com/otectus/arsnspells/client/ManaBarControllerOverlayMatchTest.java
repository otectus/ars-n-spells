package com.otectus.arsnspells.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the mana-overlay matchers (OPT-009). They are pure
 * {@code (namespace, path)} string predicates, so they are testable without a
 * Minecraft bootstrap, and they pin the namespace-scoped matching that replaced
 * the old substring scan of the full {@code "namespace:path"} string.
 */
class ManaBarControllerOverlayMatchTest {

    @Test
    void isManaOverlay_matchesArsAndIronsManaBars() {
        assertTrue(ManaBarController.isManaOverlay("irons_spellbooks", "player_mana_bar"));
        assertTrue(ManaBarController.isManaOverlay("ars_nouveau", "mana_hud"));
    }

    @Test
    void isManaOverlay_rejectsForeignNamespacesAndNonManaBars() {
        assertFalse(ManaBarController.isManaOverlay("minecraft", "mana"),
            "third-party overlays must not be touched even if the path mentions mana");
        assertFalse(ManaBarController.isManaOverlay("irons_spellbooks", "cooldown_bar"),
            "non-mana overlays in our namespaces must be left alone");
    }

    @Test
    void perSourceMatchers_areNamespaceScoped() {
        assertTrue(ManaBarController.isIronsManaOverlay("irons_spellbooks", "player_mana_bar"));
        assertFalse(ManaBarController.isIronsManaOverlay("ars_nouveau", "mana"),
            "the Iron's matcher must not match the Ars overlay");

        assertTrue(ManaBarController.isArsManaOverlay("ars_nouveau", "mana"));
        assertFalse(ManaBarController.isArsManaOverlay("irons_spellbooks", "mana"),
            "the Ars matcher must not match the Iron's overlay");
    }
}
