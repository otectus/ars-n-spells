package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts the optional-mod scenarios {@link OptionalModGate} let through against the ones it
 * skipped, and emits a single machine-readable line when the GameTest server shuts down.
 *
 * <p>The line is the CI success signal that "N required tests passed" cannot be, because the
 * harness counts a mod-absent self-skip as a pass:
 *
 * <pre>
 *   ANS-GAMETEST executed=35 skipped=2 byMod={irons_spellbooks=2}
 * </pre>
 *
 * <p><b>What the numbers cover.</b> Only gate-visible scenarios - the tests that depend on an
 * optional mod. Unconditional tests are not counted here at all: they run in every profile and
 * the harness's own pass count already speaks for them. So {@code executed} answers "how many
 * integrations did this profile actually exercise", which is exactly the question the absent
 * profile was previously answering with a lie.
 *
 * <p>The {@code ANS-GAMETEST executed=} prefix is grepped by {@code .github/workflows/ci.yml};
 * do not reword it.
 *
 * <p>Registered on the game bus only when the GameTest namespace property is set, so a normal
 * client or server install never sees this line.
 */
public final class ScenarioReport {

    /** The literal CI greps for. Keep it stable. */
    public static final String PREFIX = "ANS-GAMETEST executed=";

    private static final AtomicInteger EXECUTED = new AtomicInteger();
    private static final AtomicInteger SKIPPED = new AtomicInteger();
    private static final Map<String, AtomicInteger> BY_MOD = new ConcurrentHashMap<>();
    private static final List<String> SKIPPED_NAMES = new CopyOnWriteArrayList<>();
    private static final List<String> EXECUTED_NAMES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean EMITTED = new AtomicBoolean();

    private ScenarioReport() {}

    /**
     * Subscribe the report to the game bus when this process is a GameTest run.
     *
     * <p>Called from the mod constructor. {@code neoforge.enabledGameTestNamespaces} is the
     * property {@code build.gradle}'s {@code gameTestServer} run sets, and it is the only
     * thing that distinguishes a test server from a real one this early.
     */
    public static void register() {
        String namespaces = System.getProperty("neoforge.enabledGameTestNamespaces");
        if (namespaces == null || namespaces.isBlank()) {
            return;
        }
        NeoForge.EVENT_BUS.register(ScenarioReport.class);
    }

    /** Record that {@code testName} really exercised its optional-mod integration. */
    static void executed(String testName) {
        EXECUTED.incrementAndGet();
        EXECUTED_NAMES.add(testName);
    }

    /** Record that {@code testName} was skipped over {@code modId}'s presence state. */
    static void skipped(String modId, String testName) {
        SKIPPED.incrementAndGet();
        BY_MOD.computeIfAbsent(modId, id -> new AtomicInteger()).incrementAndGet();
        SKIPPED_NAMES.add(testName + " (" + modId + ")");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        emit();
    }

    /**
     * Emit the report line, once. {@code ServerStoppingEvent} can be seen more than once
     * across an integrated-server lifetime, and a second line would double the count CI reads.
     */
    private static void emit() {
        if (!EMITTED.compareAndSet(false, true)) {
            return;
        }
        StringJoiner byMod = new StringJoiner(", ", "{", "}");
        new TreeMap<>(BY_MOD).forEach((modId, count) -> byMod.add(modId + "=" + count.get()));
        ArsNSpells.LOGGER.info("{}{} skipped={} byMod={}",
            PREFIX, EXECUTED.get(), SKIPPED.get(), byMod);
        ArsNSpells.LOGGER.debug("ANS-GAMETEST executed scenarios: {}", EXECUTED_NAMES);
        ArsNSpells.LOGGER.debug("ANS-GAMETEST skipped scenarios: {}", SKIPPED_NAMES);
    }
}
