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

    @Test
    void matchers_neverTouchVanillaOrThirdPartyOverlays() {
        // Third-party HUDs (e.g. Durability Viewer) draw from the Post of the vanilla hotbar
        // and potion/effects overlays; a cancelled Pre suppresses that Post. These ids must
        // never match, in any matcher, regardless of mode.
        String[][] foreign = {
            {"minecraft", "hotbar"},
            {"minecraft", "potion_icons"},
            {"minecraft", "effects"},
            {"minecraft", "experience_bar"},
            {"durabilityviewer", "durability_viewer"},
            {"ars_nouveau", "hotbar"},
            {"irons_spellbooks", "cooldown_bar"},
        };
        for (String[] id : foreign) {
            String label = id[0] + ":" + id[1] + " must never be matched";
            assertFalse(ManaBarController.isManaOverlay(id[0], id[1]), label);
            assertFalse(ManaBarController.isIronsManaOverlay(id[0], id[1]), label);
            assertFalse(ManaBarController.isArsManaOverlay(id[0], id[1]), label);
        }
    }
}
