package com.otectus.arsnspells.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the shared {@code ans-contract-fixtures} against this repository's copy of
 * {@code com.otectus.arsnspells.contract}.
 *
 * <p>This is the parity mechanism, not a convenience. The contract package exists in two
 * repositories and the JSON under {@code src/test/resources/ans-contract-fixtures} is
 * byte-identical in both, so a behavioural difference between the Forge and NeoForge copies
 * fails here rather than reaching a player. {@code contract-manifest.txt} plus
 * {@code contract_parity.py} guard the files themselves; this test guards what they do.
 *
 * <p>Bootstrap-free: the contract has no platform imports, so nothing here needs a mod loading
 * context, a config spec, or a {@code Player}.
 */
class ContractFixtureTest {

    private static final double TOLERANCE = 1.0e-9d;

    private static JsonArray cases(String fixture) {
        String path = "/ans-contract-fixtures/" + fixture;
        try (InputStream in = ContractFixtureTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing fixture " + path);
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return root.getAsJsonArray("cases");
        } catch (Exception e) {
            throw new IllegalStateException("could not read fixture " + path, e);
        }
    }

    private static Stream<DynamicTest> replay(String fixture, java.util.function.Consumer<JsonObject> body) {
        JsonArray array = cases(fixture);
        List<DynamicTest> tests = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject c = element.getAsJsonObject();
            tests.add(DynamicTest.dynamicTest(c.get("name").getAsString(), () -> body.accept(c)));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> quoteFixtures() {
        return replay("quotes.json", c -> {
            JsonObject r = c.getAsJsonObject("rules");
            CostRules rules = CostRules.of(
                r.get("modeName").getAsString(),
                r.get("arsToIronsRate").getAsDouble(),
                r.get("ironsToArsRate").getAsDouble(),
                r.get("rawArsShare").getAsDouble(),
                r.get("rawIronsShare").getAsDouble(),
                ConversionKind.valueOf(r.get("conversionKind").getAsString()),
                r.get("crossCastMultiplier").getAsDouble(),
                RoundingRule.valueOf(r.get("rounding").getAsString()),
                r.get("generation").getAsInt());

            assertEquals(c.get("expectedSharesNormalized").getAsBoolean(), rules.sharesNormalized(),
                "sharesNormalized");
            assertEquals(c.get("expectedArsShare").getAsDouble(), rules.arsShare(), TOLERANCE, "arsShare");
            assertEquals(c.get("expectedIronsShare").getAsDouble(), rules.ironsShare(), TOLERANCE, "ironsShare");

            JsonObject o = c.getAsJsonObject("origin");
            ResourceAmount origin = new ResourceAmount(
                ResourceUnit.valueOf(o.get("unit").getAsString()), o.get("amount").getAsDouble());
            CarrierPolicy carrier = CarrierPolicy.valueOf(c.get("carrier").getAsString());

            CostQuote quote = StandardQuotePolicy.INSTANCE.quote(origin, rules, carrier,
                c.get("nativeArsMax").getAsDouble(), c.get("nativeIronsMax").getAsDouble());

            assertEquals(c.get("expectedGeneration").getAsInt(), quote.rulesGeneration(), "rulesGeneration");
            JsonArray expectedLegs = c.getAsJsonArray("expectedLegs");
            assertEquals(expectedLegs.size(), quote.legs().size(), "leg count");
            for (int i = 0; i < expectedLegs.size(); i++) {
                JsonObject expected = expectedLegs.get(i).getAsJsonObject();
                ResourceAmount actual = quote.legs().get(i);
                assertEquals(ResourceUnit.valueOf(expected.get("unit").getAsString()), actual.unit(),
                    "leg " + i + " unit");
                assertEquals(expected.get("amount").getAsDouble(), actual.amount(), TOLERANCE,
                    "leg " + i + " amount");
            }
            // The breakdown must explain the legs, not decorate them.
            assertFalse(quote.breakdown().isEmpty(), "breakdown must not be empty");
        });
    }

    @TestFactory
    Stream<DynamicTest> routingFixtures() {
        return replay("routing.json", c -> {
            boolean ironsPresent = c.get("ironsPresent").getAsBoolean();
            ModeRoutingSnapshot snapshot = new ModeRoutingSnapshot(
                c.get("requestedMode").getAsString(),
                c.get("requestedMode").getAsString(),
                ironsPresent,
                ResourceUnit.valueOf(c.get("authoritativeUnit").getAsString()),
                c.get("nativeArsAdapterId").getAsString(),
                Optional.of(c.get("nativeIronsAdapterId").getAsString()),
                c.get("generation").getAsInt());

            assertEquals(c.get("expectedEffectiveMode").getAsString(), snapshot.effectiveMode(),
                "effectiveMode");
            assertEquals(ResourceUnit.valueOf(c.get("expectedAuthoritativeUnit").getAsString()),
                snapshot.authoritativeUnit(), "authoritativeUnit");
            assertEquals(c.get("expectedNativeIronsAdapterPresent").getAsBoolean(),
                snapshot.nativeIronsAdapterId().isPresent(),
                "a snapshot taken without Iron's must expose no Iron's adapter");

            assertRoute(c.getAsJsonObject("expectedNativeArsSpend"), snapshot.routeNativeArsSpend(),
                "routeNativeArsSpend");
            assertRoute(c.getAsJsonObject("expectedCrossCast"), snapshot.routeCrossCast(),
                "routeCrossCast");

            JsonElement ironsExpectation = c.get("expectedNativeIronsSpend");
            if (ironsExpectation.isJsonNull()) {
                assertThrows(IllegalStateException.class, snapshot::routeNativeIronsSpend,
                    "no Iron's route may exist when Iron's is absent");
            } else {
                assertRoute(ironsExpectation.getAsJsonObject(), snapshot.routeNativeIronsSpend(),
                    "routeNativeIronsSpend");
            }
        });
    }

    private static void assertRoute(JsonObject expected, BillingRoute actual, String label) {
        assertEquals(ResourceUnit.valueOf(expected.get("payingUnit").getAsString()), actual.payingUnit(),
            label + " payingUnit");
        assertEquals(expected.get("nativeAlsoDebits").getAsBoolean(), actual.nativeAlsoDebits(),
            label + " nativeAlsoDebits");
        assertEquals(BillingRoute.Direction.valueOf(expected.get("direction").getAsString()),
            actual.direction(), label + " direction");
    }

    @TestFactory
    Stream<DynamicTest> inscriptionFixtures() {
        return replay("inscription.json", c -> {
            final InscriptionSourceKind source =
                InscriptionSourceKind.valueOf(c.get("sourceKind").getAsString());
            final InscriptionSourceKind target =
                InscriptionSourceKind.valueOf(c.get("targetKind").getAsString());
            final boolean empty = c.get("targetIsNativelyEmpty").getAsBoolean();
            final int count = c.get("sourceStackCount").getAsInt();

            InscriptionView view = new InscriptionView() {
                @Override
                public InscriptionSourceKind sourceKind() {
                    return source;
                }

                @Override
                public InscriptionSourceKind targetKind() {
                    return target;
                }

                @Override
                public boolean targetIsNativelyEmpty() {
                    return empty;
                }

                @Override
                public int sourceStackCount() {
                    return count;
                }
            };

            InscriptionPlan plan = InscriptionPlanner.plan(view);
            assertEquals(c.get("expectedConsumedUnits").getAsInt(), plan.consumedUnits(), "consumedUnits");
            assertEquals(c.get("expectedOutputCount").getAsInt(), plan.outputCount(), "outputCount");
            assertEquals(c.get("expectedReasonCode").getAsString(), plan.reasonCode(), "reasonCode");

            if (source.isReusable()) {
                assertEquals(0, plan.consumedUnits(), "a reusable source is never consumed");
            }
            if (target == InscriptionSourceKind.FILLED_SCROLL) {
                assertEquals(InscriptionPlan.REASON_NOT_BLANK, plan.reasonCode(),
                    "a filled scroll is never treated as blank");
            }
        });
    }

    /** A pool that clamps like a real one, so a partial drain is a partial drain. */
    private static final class FakeAccess implements ResourceAccess {
        private final Map<ResourceUnit, Double> balances = new EnumMap<>(ResourceUnit.class);

        @Override
        public double current(UUID player, ResourceUnit unit) {
            return balances.getOrDefault(unit, 0.0d);
        }

        @Override
        public double max(UUID player, ResourceUnit unit) {
            return Double.MAX_VALUE;
        }

        @Override
        public double debit(UUID player, ResourceUnit unit, double amount) {
            double have = current(player, unit);
            double moved = Math.min(have, amount);
            balances.put(unit, have - moved);
            return moved;
        }

        @Override
        public double credit(UUID player, ResourceUnit unit, double amount) {
            balances.put(unit, current(player, unit) + amount);
            return amount;
        }
    }

    @TestFactory
    Stream<DynamicTest> attemptLifecycleFixtures() {
        return replay("attempts.json", c -> {
            FakeAccess access = new FakeAccess();
            JsonObject start = c.getAsJsonObject("startBalances");
            for (String unit : start.keySet()) {
                access.balances.put(ResourceUnit.valueOf(unit), start.get(unit).getAsDouble());
            }

            List<ResourceAmount> legs = new ArrayList<>();
            for (JsonElement e : c.getAsJsonArray("legs")) {
                JsonObject l = e.getAsJsonObject();
                legs.add(new ResourceAmount(
                    ResourceUnit.valueOf(l.get("unit").getAsString()), l.get("amount").getAsDouble()));
            }

            CostQuote quote = new CostQuote(legs.get(0), List.of(), legs, 7);
            AttemptLedger ledger = new AttemptLedger();
            UUID player = UUID.nameUUIDFromBytes("ans-fixture-player".getBytes(StandardCharsets.UTF_8));
            CastAttempt attempt = ledger.open(player, "carrier:fixture", 1, quote, 0L);

            JsonArray ops = c.getAsJsonArray("ops");
            int throwAt = c.get("expectThrowAtOpIndex").getAsInt();
            for (int i = 0; i < ops.size(); i++) {
                final String op = ops.get(i).getAsString();
                if (i == throwAt) {
                    assertThrows(IllegalStateException.class, () -> run(op, ledger, attempt, access),
                        "op " + i + " (" + op + ") must be rejected");
                    break;
                }
                run(op, ledger, attempt, access);
            }
            if (throwAt < 0) {
                assertTrue(ops.size() > 0, "a trace must do something");
            }

            assertEquals(AttemptState.valueOf(c.get("expectedFinalState").getAsString()), attempt.state(),
                "final state");
            assertEquals(c.get("expectedOpenCount").getAsInt(), ledger.openCount(), "open attempts left");

            JsonArray expectedReserved = c.getAsJsonArray("expectedReservedLegs");
            assertEquals(expectedReserved.size(), attempt.reservedLegs().size(), "reserved leg count");
            for (int i = 0; i < expectedReserved.size(); i++) {
                JsonObject expected = expectedReserved.get(i).getAsJsonObject();
                ResourceAmount actual = attempt.reservedLegs().get(i);
                assertEquals(ResourceUnit.valueOf(expected.get("unit").getAsString()), actual.unit(),
                    "reserved leg " + i + " unit");
                assertEquals(expected.get("amount").getAsDouble(), actual.amount(), TOLERANCE,
                    "reserved leg " + i + " amount");
            }

            JsonObject expectedBalances = c.getAsJsonObject("expectedBalances");
            for (String unit : expectedBalances.keySet()) {
                ResourceUnit u = ResourceUnit.valueOf(unit);
                assertEquals(expectedBalances.get(unit).getAsDouble(), access.current(player, u), TOLERANCE,
                    "balance in " + unit + " (a double release must not refund twice)");
            }
        });
    }

    private static void run(String op, AttemptLedger ledger, CastAttempt attempt, ResourceAccess access) {
        if ("validate".equals(op)) {
            attempt.validate();
        } else if ("markQuoted".equals(op)) {
            attempt.markQuoted();
        } else if ("reserve".equals(op)) {
            ledger.reserve(attempt, access);
        } else if ("commit".equals(op)) {
            ledger.commit(attempt);
        } else if ("complete".equals(op)) {
            ledger.complete(attempt);
        } else if ("fail".equals(op)) {
            ledger.fail(attempt, access);
        } else if ("cancel".equals(op)) {
            ledger.cancel(attempt, access);
        } else if ("expire".equals(op)) {
            ledger.expireOlderThan(1000L, 10L, access);
        } else {
            throw new IllegalArgumentException("unknown fixture op: " + op);
        }
    }
}
