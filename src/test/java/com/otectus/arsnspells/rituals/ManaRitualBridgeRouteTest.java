package com.otectus.arsnspells.rituals;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-002 — verifies that {@link ManaInfusionRitual} and
 * {@link ManaWellRitual} now route mana grants through
 * {@code BridgeManager.getBridge().addMana(...)} instead of importing
 * Iron's {@code MagicData} directly.
 */
class ManaRitualBridgeRouteTest {

    @Test
    void manaInfusionRitual_routesThroughBridgeManager() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java"));
        assertFalse(src.contains("import io.redspace.ironsspellbooks.api.magic.MagicData"),
            "ManaInfusionRitual must not import Iron's MagicData directly");
        assertFalse(src.contains("MagicData.getPlayerMagicData"),
            "ManaInfusionRitual must not call MagicData.getPlayerMagicData directly");
        assertTrue(src.contains("BridgeManager.getBridge().addMana"),
            "ManaInfusionRitual must route the mana grant through BridgeManager.addMana");
    }

    @Test
    void manaWellRitual_routesThroughBridgeManager() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/rituals/ManaWellRitual.java"));
        assertFalse(src.contains("import io.redspace.ironsspellbooks.api.magic.MagicData"),
            "ManaWellRitual must not import Iron's MagicData directly");
        assertFalse(src.contains("MagicData.getPlayerMagicData"),
            "ManaWellRitual must not call MagicData.getPlayerMagicData directly");
        assertTrue(src.contains("BridgeManager.getBridge().addMana"),
            "ManaWellRitual must route the mana grant through BridgeManager.addMana");
    }
}
