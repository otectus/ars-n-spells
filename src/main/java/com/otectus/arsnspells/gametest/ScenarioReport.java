package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Counts which GameTest scenarios actually <em>executed</em> and which merely reported success
 * because an optional mod was absent, and prints that split once as the server stops.
 *
 * <p><b>Why this exists (audit V26).</b> GameTest has no notion of a skipped test: a scenario that
 * returns {@code helper.succeed()} because {@code irons_spellbooks} is missing is indistinguishable
 * in the log from one that drove the whole integration. An absent-profile run therefore reported
 * "71 tests passed" while zero cross-mod scenarios had run, and CI read that as a pass. The pass
 * count cannot tell the truth on its own, so CI reads this line instead.
 *
 * <p>Recording happens from {@link OptionalModGate}, which is called from GameTest batches running
 * on several threads, hence the concurrent map. A test name maps either to {@link #EXECUTED} or to
 * the id of the mod whose absence skipped it; a skip always wins over an execute, so a scenario
 * gated on two mods where only one is present counts as skipped.
 *
 * <p>The FORGE bus carries {@link ServerStoppingEvent}. Nothing is printed when no scenario was
 * recorded, so a production server never sees this line.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class ScenarioReport {

    /** The literal CI greps for. Changing it breaks {@code .github/workflows/ci.yml}. */
    public static final String PREFIX = "ANS-GAMETEST executed=";

    private static final String EXECUTED = "";

    private static final Map<String, String> OUTCOMES = new ConcurrentHashMap<>();
    private static final AtomicBoolean EMITTED = new AtomicBoolean(false);

    private ScenarioReport() {
    }

    /** Record that {@code testName} genuinely ran its integration path. Never downgrades a skip. */
    static void executed(String testName) {
        OUTCOMES.putIfAbsent(testName, EXECUTED);
    }

    /** Record that {@code testName} was skipped because {@code modId} is not loaded. */
    static void skipped(String modId, String testName) {
        OUTCOMES.put(testName, modId);
    }

    /** The one honest line, e.g. {@code ANS-GAMETEST executed=12 skipped=39 byMod={ars_zero=6}}. */
    static String render() {
        int executed = 0;
        int skipped = 0;
        Map<String, Integer> byMod = new TreeMap<>();
        for (String outcome : OUTCOMES.values()) {
            if (EXECUTED.equals(outcome)) {
                executed++;
            } else {
                skipped++;
                byMod.merge(outcome, 1, Integer::sum);
            }
        }
        StringBuilder mods = new StringBuilder();
        byMod.forEach((mod, count) -> {
            if (mods.length() > 0) {
                mods.append(", ");
            }
            mods.append(mod).append('=').append(count);
        });
        return PREFIX + executed + " skipped=" + skipped + " byMod={" + mods + "}";
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (OUTCOMES.isEmpty() || !EMITTED.compareAndSet(false, true)) {
            return;
        }
        ArsNSpells.LOGGER.info(render());
    }
}
