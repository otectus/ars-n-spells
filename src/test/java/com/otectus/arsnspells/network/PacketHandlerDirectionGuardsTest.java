package com.otectus.arsnspells.network;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-013 - every payload must be registered with an explicit direction, so the bus
 * rejects a mis-directed payload from a hostile or buggy peer rather than running a
 * server-side handler on data a client sent.
 *
 * <p><b>Port divergence.</b> The 1.20.1 line passed
 * {@code Optional.of(NetworkDirection.PLAY_TO_CLIENT)} to a {@code SimpleChannel}
 * registration. NeoForge's {@code PayloadRegistrar} encodes the same guard in the method
 * name - {@code playToClient} / {@code playToServer} - and has no undirected overload that
 * would still compile, so the shape of the assertion changes while the invariant does not.
 */
class PacketHandlerDirectionGuardsTest {

    private static String source() throws IOException {
        return Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/network/PacketHandler.java"));
    }

    /** Payloads that must only ever travel server to client. */
    private static final List<String> S2C = List.of(
        "AffinitySyncPayload", "AffinityBulkSyncPayload", "CooldownSyncPayload",
        "ResonanceSyncPayload");

    /** Payloads that must only ever travel client to server. */
    private static final List<String> C2S = List.of(
        "SpellLoomExportPayload", "CrossCastRequestPayload");

    @Test
    void everyPayloadIsRegisteredInExactlyOneDirection() throws IOException {
        String src = source();
        List<String> wrong = new ArrayList<>();

        for (String payload : S2C) {
            if (!src.contains("playToClient(" + payload + ".TYPE")) {
                wrong.add(payload + " must register with playToClient");
            }
            if (src.contains("playToServer(" + payload + ".TYPE")) {
                wrong.add(payload + " must NOT accept a client-to-server send");
            }
        }
        for (String payload : C2S) {
            if (!src.contains("playToServer(" + payload + ".TYPE")) {
                wrong.add(payload + " must register with playToServer");
            }
            if (src.contains("playToClient(" + payload + ".TYPE")) {
                wrong.add(payload + " must NOT accept a server-to-client send");
            }
        }

        assertTrue(wrong.isEmpty(), "payload direction guards are wrong (ANS-HIGH-013): " + wrong);
    }

    @Test
    void everyRegisteredPayloadIsAccountedForByThisTest() throws IOException {
        // Otherwise a new payload could be added with no direction assertion at all and this
        // file would keep passing while covering less.
        String src = source();
        List<String> registered = new ArrayList<>();
        Matcher m = Pattern.compile("play(?:ToClient|ToServer)\\((\\w+)\\.TYPE").matcher(src);
        while (m.find()) {
            registered.add(m.group(1));
        }
        List<String> expected = new ArrayList<>(S2C);
        expected.addAll(C2S);
        assertEquals(expected.size(), registered.size(),
            "a payload was registered that this test does not cover: " + registered);
        assertTrue(registered.containsAll(expected),
            "expected every known payload to be registered; saw " + registered);
    }

    @Test
    void protocolVersionIsDeclared() throws IOException {
        // A payload set change without a protocol bump lets a mismatched client connect and
        // then desync; the version is what makes negotiation refuse it up front.
        assertTrue(source().contains("PROTOCOL_VERSION"),
            "PacketHandler must declare a protocol version for channel negotiation");
    }
}
