package com.otectus.arsnspells.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-013 — verifies that all four S2C packets register with an explicit
 * {@code NetworkDirection.PLAY_TO_CLIENT} direction guard so the bus rejects
 * mis-directed payloads from a hostile or buggy client.
 */
class PacketHandlerDirectionGuardsTest {

    @Test
    void allS2cPackets_haveDirectionGuards() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/network/PacketHandler.java"));
        // Count the number of S2C packet registrations that include the direction guard.
        // 3 S2C packets remain (Resonance, Affinity, Cooldown) after AuraSyncPacket was
        // deleted in the aura-subsystem refactor; CrossCastRequestPacket is the single C2S.
        // The direction marker is Optional.of(NetworkDirection.PLAY_TO_CLIENT).
        long s2cGuards = countOccurrences(src, "Optional.of(NetworkDirection.PLAY_TO_CLIENT)");
        assertTrue(s2cGuards >= 3,
            "All three remaining S2C packets must register with NetworkDirection.PLAY_TO_CLIENT "
                + "(ANS-HIGH-013); found " + s2cGuards + " occurrences");

        // The single C2S packet keeps its PLAY_TO_SERVER guard.
        assertTrue(src.contains("Optional.of(NetworkDirection.PLAY_TO_SERVER)"),
            "CrossCastRequestPacket must still register with PLAY_TO_SERVER");
    }

    @Test
    void sendToClient_hasNullCheck() throws IOException {
        // ANS-LOW-015 piggyback: defensive null-check on player.connection.
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/network/PacketHandler.java"));
        int methodIdx = src.indexOf("public static void sendToClient");
        assertTrue(methodIdx > 0);
        int nextMethodIdx = src.indexOf("public static", methodIdx + 1);
        if (nextMethodIdx < 0) nextMethodIdx = src.length();
        String body = src.substring(methodIdx, nextMethodIdx);
        assertTrue(body.contains("player == null") || body.contains("player.connection == null"),
            "sendToClient must null-check player.connection before dispatch (ANS-LOW-015)");
    }

    private static long countOccurrences(String haystack, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
