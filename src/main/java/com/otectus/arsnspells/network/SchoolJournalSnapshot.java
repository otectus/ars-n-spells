package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;

/** A bounded, immutable own-player journal view captured on the server command thread. */
public record SchoolJournalSnapshot(String mappingDigest, double perCast, double cap,
                                    int totalSchools, List<Row> rows) {
    public static final int MAX_ROWS = 64;
    public record Row(String school, int affinity, int casts, double applied, String attribute) {
        public Row {
            if (school == null || school.length() > 256 || attribute == null || attribute.length() > 256
                || affinity < 0 || affinity > 100 || casts < 0 || !Double.isFinite(applied))
                throw new IllegalArgumentException("Invalid school journal row");
        }
    }
    public SchoolJournalSnapshot {
        rows = List.copyOf(rows);
        if (mappingDigest == null || mappingDigest.length() > 64 || rows.size() > MAX_ROWS
            || totalSchools < rows.size() || !Double.isFinite(perCast) || !Double.isFinite(cap))
            throw new IllegalArgumentException("Invalid school journal snapshot");
    }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(mappingDigest, 64); buffer.writeDouble(perCast); buffer.writeDouble(cap);
        buffer.writeVarInt(totalSchools); buffer.writeVarInt(rows.size());
        for (Row row : rows) {
            buffer.writeUtf(row.school(), 256); buffer.writeVarInt(row.affinity()); buffer.writeVarInt(row.casts());
            buffer.writeDouble(row.applied()); buffer.writeUtf(row.attribute(), 256);
        }
    }
    public static SchoolJournalSnapshot read(FriendlyByteBuf buffer) {
        String digest = buffer.readUtf(64); double perCast = buffer.readDouble(), cap = buffer.readDouble();
        int total = buffer.readVarInt(), count = buffer.readVarInt();
        if (count < 0 || count > MAX_ROWS || total < count) throw new IllegalArgumentException("Journal count out of bounds");
        List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) rows.add(new Row(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), buffer.readUtf(256)));
        return new SchoolJournalSnapshot(digest, perCast, cap, total, rows);
    }
}
