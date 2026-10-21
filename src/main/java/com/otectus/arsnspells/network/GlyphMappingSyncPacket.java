package com.otectus.arsnspells.network;

import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * The bounded client view of the imported-glyph behaviour snapshot: tier terms, per-spell
 * overrides, allow and deny lists and the server's digest. Sent on login and after every datapack
 * reload, so the client's editor, tooltips and recipe viewer agree with the server.
 */
public record GlyphMappingSyncPacket(GlyphMappingSnapshot.ClientView view) {
    /** No list or map may exceed what the catalog itself could need. */
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_LEVELS = 99;

    public GlyphMappingSyncPacket(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private static GlyphMappingSnapshot.ClientView read(FriendlyByteBuf buffer) {
        int tierCount = bounded(buffer.readVarInt(), 3);
        Map<Integer, GlyphMappingSnapshot.PressTerms> tiers = new TreeMap<>();
        for (int i = 0; i < tierCount; i++) tiers.put(buffer.readVarInt(), readTerms(buffer));
        int spellCount = bounded(buffer.readVarInt(), MAX_ENTRIES);
        Map<ResourceLocation, GlyphMappingSnapshot.ClientSpell> spells = new TreeMap<>();
        for (int i = 0; i < spellCount; i++) {
            ResourceLocation spell = buffer.readResourceLocation();
            Boolean enabled = switch (buffer.readByte()) {
                case 1 -> Boolean.TRUE;
                case 0 -> Boolean.FALSE;
                default -> null;
            };
            int tierLevels = bounded(buffer.readVarInt(), MAX_LEVELS);
            Map<Integer, Integer> tierByLevel = new TreeMap<>();
            for (int j = 0; j < tierLevels; j++) {
                int level = buffer.readVarInt();
                int tier = buffer.readVarInt();
                if (tier < 1 || tier > 3) throw new IllegalArgumentException("Imported-glyph tier out of range: " + tier);
                tierByLevel.put(level, tier);
            }
            int termLevels = bounded(buffer.readVarInt(), MAX_LEVELS);
            Map<Integer, GlyphMappingSnapshot.PressTerms> termsByLevel = new TreeMap<>();
            for (int j = 0; j < termLevels; j++) termsByLevel.put(buffer.readVarInt(), readTerms(buffer));
            spells.put(spell, new GlyphMappingSnapshot.ClientSpell(enabled, tierByLevel, termsByLevel));
        }
        List<ResourceLocation> allowSpells = ids(buffer), denySpells = ids(buffer), allowSchools = ids(buffer),
            denySchools = ids(buffer), failed = ids(buffer);
        return new GlyphMappingSnapshot.ClientView(tiers, spells, allowSpells, denySpells, allowSchools, denySchools, failed,
            buffer.readUtf(64), buffer.readVarInt());
    }

    private static GlyphMappingSnapshot.PressTerms readTerms(FriendlyByteBuf buffer) {
        // The record's own constructor rejects out-of-range values, so a malformed view fails to decode.
        return new GlyphMappingSnapshot.PressTerms(buffer.readVarInt(), buffer.readVarInt(), buffer.readResourceLocation(), buffer.readVarInt());
    }

    private static void writeTerms(FriendlyByteBuf buffer, GlyphMappingSnapshot.PressTerms terms) {
        buffer.writeVarInt(terms.sourceCost());
        buffer.writeVarInt(terms.durationTicks());
        buffer.writeResourceLocation(terms.catalystTag());
        buffer.writeVarInt(terms.catalystCount());
    }

    private static List<ResourceLocation> ids(FriendlyByteBuf buffer) {
        int count = bounded(buffer.readVarInt(), MAX_ENTRIES);
        List<ResourceLocation> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ids.add(buffer.readResourceLocation());
        return ids;
    }

    private static int bounded(int count, int max) {
        if (count < 0 || count > max) throw new IllegalArgumentException("Imported-glyph view exceeds its bound: " + count);
        return count;
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(view.tiers().size());
        view.tiers().forEach((tier, terms) -> { buffer.writeVarInt(tier); writeTerms(buffer, terms); });
        buffer.writeVarInt(view.spells().size());
        view.spells().forEach((spell, mapping) -> {
            buffer.writeResourceLocation(spell);
            buffer.writeByte(mapping.enabled() == null ? 2 : mapping.enabled() ? 1 : 0);
            buffer.writeVarInt(mapping.tierByLevel().size());
            mapping.tierByLevel().forEach((level, tier) -> { buffer.writeVarInt(level); buffer.writeVarInt(tier); });
            buffer.writeVarInt(mapping.termsByLevel().size());
            mapping.termsByLevel().forEach((level, terms) -> { buffer.writeVarInt(level); writeTerms(buffer, terms); });
        });
        for (List<ResourceLocation> list : List.of(view.allowSpells(), view.denySpells(), view.allowSchools(), view.denySchools(), view.failedClosed())) {
            buffer.writeVarInt(list.size());
            list.forEach(buffer::writeResourceLocation);
        }
        buffer.writeUtf(view.digest(), 64);
        buffer.writeVarInt(view.generation());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var network = supplier.get();
        if (network == null) return;
        network.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.otectus.arsnspells.client.GlyphClientSync.acceptMapping(view)));
        network.setPacketHandled(true);
    }
}
