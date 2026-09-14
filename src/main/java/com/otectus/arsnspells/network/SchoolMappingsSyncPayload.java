package com.otectus.arsnspells.network;

import com.otectus.arsnspells.data.SchoolMappingLoader;
import com.otectus.arsnspells.util.SchoolMappings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;
import java.util.function.Supplier;

/** Server-authoritative semantic mapping snapshot. No client or optional-mod types in codec. */
public final class SchoolMappingsSyncPayload implements CustomPacketPayload {
    public static final Type<SchoolMappingsSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "school_mappings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SchoolMappingsSyncPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> payload.toBytes(buffer), SchoolMappingsSyncPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private final Map<String, List<String>> glyphs;
    private final Map<String, List<String>> ars;
    private final Map<String, String> provenance;
    private final String digest;

    public SchoolMappingsSyncPayload(SchoolMappings snapshot) {
        glyphs = snapshot.glyphKeys(); ars = snapshot.arsKeys();
        provenance = snapshot.provenance(); digest = snapshot.digest();
    }
    public SchoolMappingsSyncPayload(FriendlyByteBuf buffer) {
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
    public static void handleOnClient(SchoolMappingsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SchoolMappings.acceptSnapshot(payload.glyphs, payload.ars, payload.provenance, payload.digest));
    }
}
