package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.contract.*;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Portable policy/ledger invariant. Real native event ownership is exercised by GameTests. */
class CostQuoteRepeatabilityTest {
    private static final class Balances implements ResourceAccess {
        final EnumMap<ResourceUnit, Double> pools = new EnumMap<>(ResourceUnit.class);
        int debits;
        Balances() { pools.put(ResourceUnit.ARS_MANA, 1000d); pools.put(ResourceUnit.IRONS_MANA, 1000d); }
        public double current(UUID id, ResourceUnit unit) { return pools.getOrDefault(unit, 0d); }
        public double max(UUID id, ResourceUnit unit) { return 1000; }
        public double debit(UUID id, ResourceUnit unit, double amount) {
            debits++;
            double moved = Math.min(amount, current(id, unit));
            pools.put(unit, current(id, unit) - moved); return moved;
        }
        public double credit(UUID id, ResourceUnit unit, double amount) {
            pools.put(unit, current(id, unit) + amount); return amount;
        }
    }
    @Test void tenReadsThenOneCommitHasOneEconomicEffect() {
        var rules = CostRules.of("iss_primary", 1, 1, .5, .5, ConversionKind.FLAT_LEGACY, 2, RoundingRule.HALF_UP, 1);
        var balances = new Balances();
        var ledger = new AttemptLedger();
        UUID player = UUID.randomUUID();
        CostQuote quote = null;
        for (int i = 0; i < 10; i++) {
            quote = StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.ARS_MANA, 100), rules,
                CarrierPolicy.REUSABLE_BOOK_SEMANTICS, 1000, 200);
            assertEquals(200, quote.total(ResourceUnit.IRONS_MANA));
            assertEquals(0, quote.total(ResourceUnit.ARS_MANA));
        }
        assertEquals(0, balances.debits);
        assertEquals(0, ledger.openCount());
        var attempt = ledger.open(player, "carrier", 0, quote, 0);
        attempt.validate();
        attempt.markQuoted();
        ledger.reserve(attempt, balances);
        ledger.commit(attempt);
        ledger.complete(attempt);
        ledger.cancel(attempt, balances);
        assertEquals(1, balances.debits);
        assertEquals(800, balances.current(player, ResourceUnit.IRONS_MANA));
        assertEquals(1000, balances.current(player, ResourceUnit.ARS_MANA));
        assertEquals(0, ledger.openCount());
    }
}
