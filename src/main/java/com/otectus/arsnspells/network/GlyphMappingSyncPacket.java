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
public record GlyphMappingSyncPacket(long publication, @org.jetbrains.annotations.Nullable GlyphMappingSnapshot.ClientView view, String rejection) {
    /** No list or map may exceed what the catalog itself could need. */
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_LEVELS = com.otectus.arsnspells.glyph.ImportedGlyphKey.MAX_LEVEL;
    /** A count bound alone does not bound a payload; the whole view must also fit this. */
    static final int MAX_PAYLOAD_BYTES = 1 << 20;

    public GlyphMappingSyncPacket(long publication, GlyphMappingSnapshot.ClientView view) {
        this(publication, view, "");
    }

    /** 3.4.1 review SYNC-01: never throws into the network layer; a malformed view arrives as a rejection. */
    public GlyphMappingSyncPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    private GlyphMappingSyncPacket(GlyphMappingSyncPacket decoded) {
        this(decoded.publication, decoded.view, decoded.rejection);
    }

    private static GlyphMappingSyncPacket decode(FriendlyByteBuf buffer) {
        long publication = -1;
        try {
            if (buffer.readableBytes() > MAX_PAYLOAD_BYTES)
                return reject(buffer, publication, "larger than " + MAX_PAYLOAD_BYTES + " bytes: " + buffer.readableBytes());
            publication = buffer.readVarLong();
            GlyphMappingSnapshot.ClientView view = read(buffer);
            if (buffer.readableBytes() != 0) return reject(buffer, publication, buffer.readableBytes() + " unread bytes");
            return new GlyphMappingSyncPacket(publication, view, "");
        } catch (RuntimeException malformed) {
            return reject(buffer, publication, malformed.toString());
        }
    }

    private static GlyphMappingSyncPacket reject(FriendlyByteBuf buffer, long publication, String why) {
        if (buffer.readableBytes() > 0) buffer.skipBytes(buffer.readableBytes());
        return new GlyphMappingSyncPacket(publication, null, why);
    }

    private static GlyphMappingSnapshot.ClientView read(FriendlyByteBuf buffer) {
        int tierCount = bounded(buffer.readVarInt(), 3);
        Map<Integer, GlyphMappingSnapshot.PressTerms> tiers = new TreeMap<>();
        for (int i = 0; i < tierCount; i++) {
            int tier = buffer.readVarInt();
            if (tier < 1 || tier > 3) throw new IllegalArgumentException("Imported-glyph press tier out of range: " + tier);
            tiers.put(tier, readTerms(buffer));
        }
        int spellCount = bounded(buffer.readVarInt(), MAX_ENTRIES);
        Map<ResourceLocation, GlyphMappingSnapshot.ClientSpell> spells = new TreeMap<>();
        var catalog = com.otectus.arsnspells.glyph.ImportedGlyphCatalog.get();
        for (int i = 0; i < spellCount; i++) {
            ResourceLocation spell = buffer.readResourceLocation();
            // 3.4.1 review SYNC-01: one entry per catalogued spell, never a silent overwrite or an unknown spell.
            if (spells.containsKey(spell) || catalog.levelsOf(spell).isEmpty())
                throw new IllegalArgumentException("Imported-glyph view repeats or does not know spell " + spell);
            Boolean enabled = tristate(buffer.readByte());
            boolean selfOnly = buffer.readBoolean();
            GlyphMappingSnapshot.LevelOverride defaults = readOverride(buffer);
            int levelCount = bounded(buffer.readVarInt(), MAX_LEVELS);
            Map<Integer, GlyphMappingSnapshot.LevelOverride> levels = new TreeMap<>();
            for (int j = 0; j < levelCount; j++) {
                int level = buffer.readVarInt();
                if (level < 1 || level > MAX_LEVELS) throw new IllegalArgumentException("Imported-glyph level out of range: " + level);
                levels.put(level, readOverride(buffer));
            }
            spells.put(spell, new GlyphMappingSnapshot.ClientSpell(enabled, selfOnly, defaults, levels));
        }
        List<ResourceLocation> allowSpells = ids(buffer), denySpells = ids(buffer), allowSchools = ids(buffer),
            denySchools = ids(buffer), failed = ids(buffer);
        return new GlyphMappingSnapshot.ClientView(tiers, spells, allowSpells, denySpells, allowSchools, denySchools, failed,
            buffer.readUtf(64), buffer.readVarInt());
    }

    private static Boolean tristate(byte value) {
        return switch (value) {
            case 1 -> Boolean.TRUE;
            case 0 -> Boolean.FALSE;
            case 2 -> null;
            default -> throw new IllegalArgumentException("Imported-glyph flag out of range: " + value);
        };
    }

    private static byte tristate(Boolean value) {
        return (byte) (value == null ? 2 : value ? 1 : 0);
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

    /** One presence bit per optional field, then the present values. */
    private static GlyphMappingSnapshot.LevelOverride readOverride(FriendlyByteBuf buffer) {
        int present = buffer.readUnsignedByte();
        if ((present & ~0x3F) != 0) throw new IllegalArgumentException("Imported-glyph override flags out of range: " + present);
        Boolean enabled = (present & 1) != 0 ? buffer.readBoolean() : null;
        Integer tier = (present & 2) != 0 ? buffer.readVarInt() : null;
        Integer source = (present & 4) != 0 ? buffer.readVarInt() : null;
        Integer ticks = (present & 8) != 0 ? buffer.readVarInt() : null;
        ResourceLocation tag = (present & 16) != 0 ? buffer.readResourceLocation() : null;
        Integer count = (present & 32) != 0 ? buffer.readVarInt() : null;
        return new GlyphMappingSnapshot.LevelOverride(enabled, tier, source, ticks, tag, count);
    }

    private static void writeOverride(FriendlyByteBuf buffer, GlyphMappingSnapshot.LevelOverride o) {
        int present = (o.enabled() != null ? 1 : 0) | (o.tier() != null ? 2 : 0) | (o.sourceCost() != null ? 4 : 0)
            | (o.durationTicks() != null ? 8 : 0) | (o.catalystTag() != null ? 16 : 0) | (o.catalystCount() != null ? 32 : 0);
        buffer.writeByte(present);
        if (o.enabled() != null) buffer.writeBoolean(o.enabled());
        if (o.tier() != null) buffer.writeVarInt(o.tier());
        if (o.sourceCost() != null) buffer.writeVarInt(o.sourceCost());
        if (o.durationTicks() != null) buffer.writeVarInt(o.durationTicks());
        if (o.catalystTag() != null) buffer.writeResourceLocation(o.catalystTag());
        if (o.catalystCount() != null) buffer.writeVarInt(o.catalystCount());
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
        buffer.writeVarLong(publication);
        if (view == null) throw new IllegalStateException("a refused view is never sent");
        buffer.writeVarInt(view.tiers().size());
        view.tiers().forEach((tier, terms) -> { buffer.writeVarInt(tier); writeTerms(buffer, terms); });
        buffer.writeVarInt(view.spells().size());
        view.spells().forEach((spell, mapping) -> {
            buffer.writeResourceLocation(spell);
            buffer.writeByte(tristate(mapping.enabled()));
            buffer.writeBoolean(mapping.selfOnly());
            writeOverride(buffer, mapping.defaults());
            buffer.writeVarInt(mapping.levels().size());
            mapping.levels().forEach((level, override) -> { buffer.writeVarInt(level); writeOverride(buffer, override); });
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
            com.otectus.arsnspells.client.GlyphClientSync.acceptMapping(this)));
        network.setPacketHandled(true);
    }
}
