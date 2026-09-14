package com.otectus.arsnspells.network;

import com.otectus.arsnspells.data.SchoolMappingLoader;
import com.otectus.arsnspells.util.SchoolMappings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Server-authoritative semantic mapping snapshot. No client or optional-mod types in codec. */
public final class SchoolMappingsSyncPacket {
    private final Map<String, List<String>> glyphs;
    private final Map<String, List<String>> ars;
    private final Map<String, String> provenance;
    private final String digest;

    public SchoolMappingsSyncPacket(SchoolMappings snapshot) {
        glyphs = snapshot.glyphKeys(); ars = snapshot.arsKeys();
        provenance = snapshot.provenance(); digest = snapshot.digest();
    }
    public SchoolMappingsSyncPacket(FriendlyByteBuf buffer) {
        glyphs = readMap(buffer); ars = readMap(buffer);
        int count = bounded(buffer.readVarInt(), SchoolMappingLoader.MAX_ENTRIES * 2);
        Map<String, String> sources = new TreeMap<>();
        for (int i = 0; i < count; i++) sources.put(buffer.readUtf(272), buffer.readUtf(272));
        provenance = Map.copyOf(sources); digest = buffer.readUtf(64);
    }
    private static int bounded(int count, int maximum) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("School snapshot count out of bounds");
        return count;
    }
    private static Map<String, List<String>> readMap(FriendlyByteBuf buffer) {
        int count = bounded(buffer.readVarInt(), SchoolMappingLoader.MAX_ENTRIES + 64);
        Map<String, List<String>> entries = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf(256);
            int members = bounded(buffer.readVarInt(), SchoolMappingLoader.MAX_MEMBERSHIPS);
            List<String> schools = new ArrayList<>();
            for (int j = 0; j < members; j++) schools.add(buffer.readUtf(256));
            entries.put(id, List.copyOf(schools));
        }
        return Map.copyOf(entries);
    }
    private static void writeMap(FriendlyByteBuf buffer, Map<String, List<String>> entries) {
        buffer.writeVarInt(entries.size());
        entries.forEach((id, schools) -> {
            buffer.writeUtf(id, 256); buffer.writeVarInt(schools.size());
            schools.forEach(school -> buffer.writeUtf(school, 256));
        });
    }
    public void toBytes(FriendlyByteBuf buffer) {
        writeMap(buffer, glyphs); writeMap(buffer, ars);
        buffer.writeVarInt(provenance.size());
        provenance.forEach((id, source) -> { buffer.writeUtf(id, 272); buffer.writeUtf(source, 272); });
        buffer.writeUtf(digest, 64);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context == null) return;
        context.enqueueWork(() -> SchoolMappings.acceptSnapshot(glyphs, ars, provenance, digest));
        context.setPacketHandled(true);
    }
}
