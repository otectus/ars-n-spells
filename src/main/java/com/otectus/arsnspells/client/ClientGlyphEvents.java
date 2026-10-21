package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Client lifecycle for imported glyphs: forget the server's glyph settings on logout, and give
 * every glyph item the {@code ars_n_spells:dormant} model property its item model switches on to
 * show the ANS fallback art instead of the Iron's spell icon when the glyph is dormant.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, value = Dist.CLIENT)
public final class ClientGlyphEvents {

    public static final ResourceLocation DORMANT_PROPERTY = ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "dormant");

    private ClientGlyphEvents() {
    }

    /**
     * The Glyph Press and its plate in Ars's own Worn Notebook (the documentation system of Ars
     * 5.13), under Crafting, with their recipe pages. Imported glyphs get Ars's generated glyph
     * pages like any registered spell part.
     */
    @SubscribeEvent
    public static void onDocumentation(com.hollingsworth.arsnouveau.api.documentation.ReloadDocumentationEvent.AddEntries event) {
        com.hollingsworth.arsnouveau.setup.registry.Documentation.addBasicItem(
            com.otectus.arsnspells.registry.ModBlocksRegistry.GLYPH_PRESS_ITEM.get(),
            com.hollingsworth.arsnouveau.api.registry.DocumentationRegistry.CRAFTING);
        com.hollingsworth.arsnouveau.setup.registry.Documentation.addBasicItem(
            com.otectus.arsnspells.registry.ModItemsRegistry.blankGlyphPlate().get(),
            com.hollingsworth.arsnouveau.api.registry.DocumentationRegistry.CRAFTING);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GlyphBehavior.clearClient();
    }

    /** Called from client setup (main thread). One shared function for every variant. */
    public static void registerItemProperties() {
        ImportedGlyphRegistration.effects().values().forEach(effect -> ItemProperties.register(effect.getGlyph(), DORMANT_PROPERTY,
            (stack, level, entity, seed) -> effect.behavior().available() ? 0.0f : 1.0f));
    }
}
