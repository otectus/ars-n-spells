package com.otectus.arsnspells.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the mana-layer matchers. They are pure {@code (namespace, path)} string
 * predicates, so they are testable without a Minecraft bootstrap.
 *
 * <p><b>Why these exist.</b> The 1.21.1 port replaced the 1.20.1 matching — namespace equals
 * plus {@code path.contains("mana")} — with exact equality against two hardcoded ids,
 * {@code ars_nouveau:mana_bar} and {@code irons_spellbooks:mana_bar}. Neither mod uses either
 * id, so nothing was ever cancelled and both mana bars rendered at once in every mode. The
 * real ids, read out of the shipped jars:
 *
 * <ul>
 *   <li>Ars Nouveau 5.13.1 registers {@code ars_nouveau:mana_hud}
 *       ({@code client.registry.ClientHandler}, via {@code ArsNouveau.prefix}).</li>
 *   <li>Iron's Spellbooks 1.21.1-3.16.3 registers {@code irons_spellbooks:mana_overlay}
 *       ({@code registries.OverlayRegistry}, via {@code IronsSpellbooks.id}).</li>
 * </ul>
 *
 * <p>Iron's also <em>renamed</em> its bar across the port — it was {@code player_mana_bar} on
 * 1.20.1 — which is exactly the churn the {@code contains} match absorbs and an exact-id list
 * does not. Both generations are asserted below so a rename in either direction keeps matching.
 */
class ManaBarControllerOverlayMatchTest {

    @Test
    void isManaOverlay_matchesTheRealIdsBothModsRegisterOn1_21_1() {
        assertTrue(ManaBarController.isManaOverlay("ars_nouveau", "mana_hud"),
            "Ars Nouveau 5.13.1 registers ars_nouveau:mana_hud");
        assertTrue(ManaBarController.isManaOverlay("irons_spellbooks", "mana_overlay"),
            "Iron's 1.21.1-3.16.3 registers irons_spellbooks:mana_overlay");
    }

    @Test
    void isManaOverlay_stillMatchesTheOlderIdsThoseModsUsed() {
        // Iron's used this on 1.20.1. Matching it too is what makes the predicate survive an
        // upstream rename instead of silently going quiet, which is how this bug happened.
        assertTrue(ManaBarController.isManaOverlay("irons_spellbooks", "player_mana_bar"));
        assertTrue(ManaBarController.isManaOverlay("ars_nouveau", "mana"));
    }

    @Test
    void isManaOverlay_rejectsForeignNamespacesAndNonManaLayers() {
        assertFalse(ManaBarController.isManaOverlay("minecraft", "mana"),
            "third-party layers must not be touched even if the path mentions mana");
        assertFalse(ManaBarController.isManaOverlay("irons_spellbooks", "cast_bar"),
            "non-mana layers in our namespaces must be left alone");
        assertFalse(ManaBarController.isManaOverlay("irons_spellbooks", "spell_wheel"),
            "Iron's other real layers must be left alone");
        assertFalse(ManaBarController.isManaOverlay("ars_nouveau", "spell_hud"),
            "Ars's other real layers must be left alone");
    }

    @Test
    void perSourceMatchers_areNamespaceScoped() {
        assertTrue(ManaBarController.isIronsManaOverlay("irons_spellbooks", "mana_overlay"));
        assertFalse(ManaBarController.isIronsManaOverlay("ars_nouveau", "mana_hud"),
            "the Iron's matcher must not match the Ars layer");

        assertTrue(ManaBarController.isArsManaOverlay("ars_nouveau", "mana_hud"));
        assertFalse(ManaBarController.isArsManaOverlay("irons_spellbooks", "mana_overlay"),
            "the Ars matcher must not match the Iron's layer");
    }
}
