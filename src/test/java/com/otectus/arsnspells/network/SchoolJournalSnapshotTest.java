package com.otectus.arsnspells.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SchoolJournalSnapshotTest {
    @Test void snapshotRoundTripPreservesCustomNamespacesCountsAndActualBonus() {
        List<SchoolJournalSnapshot.Row> rows = new ArrayList<>(List.of(
            new SchoolJournalSnapshot.Row("custom:fire", 17, 832, 0.125, "custom:power")));
        var snapshot = new SchoolJournalSnapshot("abc", 0.001, 0.25, 1, rows);
        rows.clear();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { snapshot.write(buffer); assertEquals(snapshot, SchoolJournalSnapshot.read(buffer)); }
        finally { buffer.release(); }
        assertEquals(1, snapshot.rows().size());
    }
    @Test void countBudgetIsCheckedBeforeAnyRowAllocationOrRead() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("abc"); buffer.writeDouble(0.001); buffer.writeDouble(0.25);
            buffer.writeVarInt(Integer.MAX_VALUE); buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> SchoolJournalSnapshot.read(buffer));
        } finally { buffer.release(); }
    }
}
