package com.otectus.arsnspells.events;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ANS-HIGH-023 - the affinity cast handler must not swallow failures broadly.
 *
 * <p>It used to wrap its packet send in {@code catch (Exception ignored)}, so a sync failure
 * disappeared entirely and the client's affinity display silently drifted from the server's.
 * The port has no catch on that path at all, which is the stronger form; this pins that a
 * broad swallow does not come back.
 */
class AffinityHandlerCatchNarrowTest {

    @Test
    void source_hasNoBroadSwallow() throws IOException {
        String src = Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/events/AffinityHandler.java"));
        assertFalse(src.contains("catch (Exception ignored)"),
            "catch(Exception ignored) hides packet-send failures (ANS-HIGH-023)");
        assertFalse(src.contains("catch (Throwable ignored)"),
            "catch(Throwable ignored) hides packet-send failures (ANS-HIGH-023)");
    }
}
