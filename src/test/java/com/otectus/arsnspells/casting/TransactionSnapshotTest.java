package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionSnapshotTest {
    private static TransactionSnapshot value(List<ResourceAmount> quoted) {
        return new TransactionSnapshot(UUID.randomUUID(), ResourceUnit.ARS_MANA, true,
            TransactionSnapshot.Stage.REFUNDED, quoted,
            List.of(new ResourceAmount(ResourceUnit.ARS_MANA, 25), new ResourceAmount(ResourceUnit.IRONS_MANA, 750)),
            List.of(new ResourceAmount(ResourceUnit.ARS_MANA, 25), new ResourceAmount(ResourceUnit.IRONS_MANA, 700)),
            TransactionSnapshot.Reason.RESOURCE_CHANGED, ResourceUnit.IRONS_MANA);
    }
    @Test void duplicateResourcesAndUnboundedAmountsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> value(List.of(
            new ResourceAmount(ResourceUnit.ARS_MANA, 1), new ResourceAmount(ResourceUnit.ARS_MANA, 2))));
        assertThrows(IllegalArgumentException.class, () -> value(List.of(new ResourceAmount(ResourceUnit.LP, Double.MAX_VALUE))));
    }
}
