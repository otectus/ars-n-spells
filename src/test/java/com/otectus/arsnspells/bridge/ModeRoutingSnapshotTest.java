package com.otectus.arsnspells.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.BillingRoute;
import com.otectus.arsnspells.contract.ModeRoutingSnapshot;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V04 - {@link BridgeManager} must answer "what mode, is Iron's here, which pool pays?"
 * from one published {@link ModeRoutingSnapshot}, not from three separately-volatile fields.
 *
 * <p>Two concrete defects are pinned here:
 *
 * <ul>
 *   <li><b>DISABLED nulled the Iron's adapter.</b> The old {@code initializeBridges} set
 *       {@code secondaryBridge = null} in DISABLED even with Iron's loaded, so a native Iron's
 *       spend fell through to {@code activeBridge} - the Ars pool. "No integration" means
 *       Iron's pays out of its own pool, not that the Iron's pool stops existing.</li>
 *   <li><b>SEPARATE without Iron's left a stale secondary.</b> That branch logged a warning and
 *       rewrote {@code currentMode}, but never cleared {@code secondaryBridge}, so a route
 *       could still name a pool that had just been found absent.</li>
 * </ul>
 *
 * <p>Bootstrap-free: {@link BridgeRouting} is built directly from fake adapters, so nothing
 * here needs a real bridge, a config spec, or a {@code Player}. The spend paths are reached
 * with a {@code null} player, which the fakes ignore.
 */
class ModeRoutingSnapshotTest {

    /** Records what it was asked to do so a test can tell which pool was actually billed. */
    private static final class RecordingBridge implements IManaBridge {
        private final String type;
        float balance = 1000.0f;
        float consumed;

        RecordingBridge(String type) {
            this.type = type;
        }

        @Override public float getMana(Player player) { return balance; }
        @Override public void setMana(Player player, float amount) { balance = amount; }
        @Override public void addMana(Player player, float amount) { balance += amount; }

        @Override
        public boolean consumeMana(Player player, float amount) {
            if (balance < amount) {
                return false;
            }
            balance -= amount;
            consumed += amount;
            return true;
        }

        @Override public float getMaxMana(Player player) { return 1000.0f; }
        @Override public String getBridgeType() { return type; }
    }

    private RecordingBridge ars;
    private RecordingBridge irons;

    private BridgeRouting install(ManaUnificationMode requested, boolean ironsLoaded) {
        ars = new RecordingBridge("ARS_NATIVE");
        irons = new RecordingBridge("IRONS_SPELLS");
        BridgeRouting routing =
            BridgeRouting.build(requested, ironsLoaded, ars, ironsLoaded ? irons : null, 1);
        BridgeManager.testSetRouting(routing);
        return routing;
    }

    @AfterEach
    void clearRouting() {
        // The routing field is static and shared with every other BridgeManager test.
        BridgeManager.testSetRouting(null);
    }

    // ------------------------------------------------------------------
    //  The two audit scenarios
    // ------------------------------------------------------------------

    @Test
    void disabledWithIronsLoaded_routesANativeIronsSpendToTheIronsPool() {
        BridgeRouting routing = install(ManaUnificationMode.DISABLED, true);

        assertNotNull(routing.nativeIrons(),
            "the Iron's adapter must exist in DISABLED whenever Iron's is loaded - nulling it "
                + "is what sent a native Iron's spend to the Ars pool");
        assertSame(ResourceUnit.IRONS_MANA,
            routing.snapshot().routeNativeIronsSpend().payingUnit(),
            "a native Iron's spend is billed to Iron's even with integration off");

        assertTrue(BridgeManager.consumeManaForMode(null, 40.0f, ResourceUnit.IRONS_MANA),
            "the Iron's pool holds 1000, so a 40 spend must succeed");
        assertEquals(40.0f, irons.consumed, 1.0e-6f,
            "the Iron's pool must be the one debited");
        assertEquals(0.0f, ars.consumed, 1.0e-6f,
            "the Ars pool must not be touched by a native Iron's spend");
    }

    @Test
    void separateWithoutIrons_exposesNoStaleSecondary() {
        BridgeRouting routing = install(ManaUnificationMode.SEPARATE, false);

        assertNull(routing.nativeIrons(),
            "no Iron's adapter may survive the absent-Iron's fallback");
        assertNull(BridgeManager.getSecondaryBridge(),
            "SEPARATE without Iron's must expose no secondary bridge at all - the old branch "
                + "warned, rewrote the mode, and left the stale reference in place");
        assertFalse(routing.snapshot().ironsPresent(), "Iron's is absent in this scenario");
        assertTrue(routing.snapshot().nativeIronsAdapterId().isEmpty(),
            "the snapshot must not name an Iron's adapter that does not exist");
        assertEquals(ModeRoutingSnapshot.DISABLED, routing.snapshot().effectiveMode(),
            "with Iron's absent there is no effective unified mode");
        assertFalse(routing.snapshot().isDualCost(),
            "a dual-cost route needs two pools; there is only one here");
    }

    // ------------------------------------------------------------------
    //  One snapshot, not three fields
    // ------------------------------------------------------------------

    @Test
    void ironsAdapterIsPopulatedInEveryMode_whenIronsIsLoaded() {
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            BridgeRouting routing = install(mode, true);
            assertNotNull(routing.nativeIrons(),
                mode + " must still carry the named Iron's adapter when Iron's is loaded");
            assertSame(irons, routing.adapterFor(ResourceUnit.IRONS_MANA),
                mode + " must resolve IRONS_MANA to the Iron's adapter by name, not by slot");
            assertSame(ars, routing.adapterFor(ResourceUnit.ARS_MANA),
                mode + " must resolve ARS_MANA to the Ars adapter by name, not by slot");
        }
    }

    @Test
    void getCurrentModeAndIsUnificationEnabled_readTheSnapshot() {
        install(ManaUnificationMode.HYBRID, true);
        assertSame(ManaUnificationMode.HYBRID, BridgeManager.getCurrentMode(),
            "getCurrentMode() is a thin read of the published snapshot");

        install(ManaUnificationMode.HYBRID, false);
        assertSame(ManaUnificationMode.ARS_PRIMARY, BridgeManager.getCurrentMode(),
            "HYBRID without Iron's falls back, and the fallback is decided once, in build()");
    }

    // ------------------------------------------------------------------
    //  Fixture mirror
    // ------------------------------------------------------------------

    /**
     * Every case in the shared routing fixture, replayed through the loader's own
     * {@link BridgeRouting#build}. {@link com.otectus.arsnspells.contract.ContractFixtureTest}
     * proves the contract computes the right routes; this proves the loader hands it the right
     * inputs, which is where the two repositories previously drifted.
     */
    @TestFactory
    Stream<DynamicTest> loaderReproducesTheRoutingFixture() {
        JsonArray array = fixtureCases();
        List<DynamicTest> tests = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject c = element.getAsJsonObject();
            tests.add(DynamicTest.dynamicTest(c.get("name").getAsString(), () -> {
                ManaUnificationMode requested =
                    ManaUnificationMode.fromString(c.get("requestedMode").getAsString());
                boolean ironsPresent = c.get("ironsPresent").getAsBoolean();
                ModeRoutingSnapshot snapshot = BridgeRouting.build(
                    requested, ironsPresent,
                    new RecordingBridge("ARS_NATIVE"),
                    ironsPresent ? new RecordingBridge("IRONS_SPELLS") : null,
                    c.get("generation").getAsInt()).snapshot();

                assertEquals(c.get("expectedEffectiveMode").getAsString(),
                    snapshot.effectiveMode(), "effective mode");
                assertEquals(c.get("expectedAuthoritativeUnit").getAsString(),
                    snapshot.authoritativeUnit().name(), "authoritative unit");
                assertEquals(c.get("expectedNativeIronsAdapterPresent").getAsBoolean(),
                    snapshot.nativeIronsAdapterId().isPresent(), "Iron's adapter presence");
                assertEquals(BridgeRouting.ARS_ADAPTER_ID, snapshot.nativeArsAdapterId(),
                    "the Ars adapter is named, and its id is the fixture's");

                assertRoute(c.getAsJsonObject("expectedNativeArsSpend"),
                    snapshot.routeNativeArsSpend(), "native Ars spend");
                assertRoute(c.getAsJsonObject("expectedCrossCast"),
                    snapshot.routeCrossCast(), "cross-cast");
                if (c.get("expectedNativeIronsSpend").isJsonNull()) {
                    assertFalse(snapshot.ironsPresent(),
                        "a fixture with no Iron's route must have no Iron's");
                } else {
                    assertRoute(c.getAsJsonObject("expectedNativeIronsSpend"),
                        snapshot.routeNativeIronsSpend(), "native Iron's spend");
                }
            }));
        }
        return tests.stream();
    }

    private static void assertRoute(JsonObject expected, BillingRoute actual, String what) {
        assertEquals(expected.get("payingUnit").getAsString(), actual.payingUnit().name(),
            what + ": paying unit");
        assertEquals(expected.get("nativeAlsoDebits").getAsBoolean(), actual.nativeAlsoDebits(),
            what + ": nativeAlsoDebits");
        assertEquals(expected.get("direction").getAsString(), actual.direction().name(),
            what + ": direction");
    }

    private static JsonArray fixtureCases() {
        String path = "/ans-contract-fixtures/routing.json";
        try (InputStream in = ModeRoutingSnapshotTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing fixture " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("cases");
        } catch (Exception e) {
            throw new IllegalStateException("could not read fixture " + path, e);
        }
    }
}
