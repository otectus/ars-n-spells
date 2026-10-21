package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the server's imported-glyph behaviour snapshot, so the client's spellbook
 * GUI, tooltips, the Glyph Press screen and JEI agree with what the server will allow.
 *
 * <p>Bounded by the catalog size (a few bytes per variant). It also carries the server's catalog
 * hash; a client whose startup catalog differs disconnects with a readable message instead of
 * misreading spells.
 */
public record ImportedGlyphSyncPayload(GlyphBehaviorSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<ImportedGlyphSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "imported_glyph_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportedGlyphSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> value.snapshot().write(buf, ImportedGlyphCatalog.get()),
        buf -> new ImportedGlyphSyncPayload(GlyphBehaviorSnapshot.read(buf, ImportedGlyphCatalog.get())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(ImportedGlyphSyncPayload payload, IPayloadContext context) {
        String local = ImportedGlyphCatalog.get().hash();
        if (!local.equals(payload.snapshot().catalogHash())) {
            context.disconnect(net.minecraft.network.chat.Component.translatable("ars_n_spells.glyph.catalog_mismatch",
                payload.snapshot().catalogHash().substring(0, 12), local.substring(0, 12)));
            return;
        }
        com.otectus.arsnspells.glyph.behavior.GlyphBehavior.receiveClient(payload.snapshot());
        com.otectus.arsnspells.glyph.recipe.GlyphPressRecipes.behaviourChanged();
    }
}
