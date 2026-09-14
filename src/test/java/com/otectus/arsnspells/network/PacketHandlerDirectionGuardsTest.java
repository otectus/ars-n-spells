package com.otectus.arsnspells.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ANS-HIGH-013 — verifies that every S2C packet registers with an explicit
 * {@code NetworkDirection.PLAY_TO_CLIENT} direction guard so the bus rejects
 * mis-directed payloads from a hostile or buggy client.
 */
class PacketHandlerDirectionGuardsTest {

    @Test
    void allPacketsHaveOneExplicitDirectionAndOnlyApprovedCommandsAcceptClientInput() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/network/PacketHandler.java"));
        Set<String> clientCommands = Set.of("CrossCastRequestPacket", "SpellLoomExportPacket");
        Set<String> requiredSnapshots = Set.of("AffinitySyncPacket", "CooldownSyncPacket",
            "ResonanceSyncPacket", "SchoolMappingsSyncPacket", "SpellLoomResultPacket",
            "JournalSnapshotPacket");
        Matcher registrations = Pattern.compile(
            "INSTANCE\\.registerMessage\\(id\\+\\+,\\s*(\\w+)\\.class,(.*?)\\);", Pattern.DOTALL).matcher(src);
        Map<String, String> directions = new HashMap<>();
        while (registrations.find()) {
            String packet = registrations.group(1);
            Matcher guard = Pattern.compile("Optional\\.of\\(NetworkDirection\\.(PLAY_TO_CLIENT|PLAY_TO_SERVER)\\)")
                .matcher(registrations.group(2));
            assertTrue(guard.find(), packet + " requires an explicit direction guard");
            String direction = guard.group(1);
            assertNull(directions.put(packet, direction), packet + " registered more than once");
            assertEquals(clientCommands.contains(packet) ? "PLAY_TO_SERVER" : "PLAY_TO_CLIENT", direction,
                packet + " accepts an unauthorized direction");
        }
        assertEquals(countOccurrences(src, "INSTANCE.registerMessage("), (long) directions.size(),
            "Every registration must be covered, including a newly added registration shape");
        assertTrue(directions.keySet().containsAll(clientCommands));
        assertTrue(directions.keySet().containsAll(requiredSnapshots));
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
