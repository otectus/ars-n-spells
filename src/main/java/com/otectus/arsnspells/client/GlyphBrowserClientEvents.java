package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.client.glyph.ArsGlyphEditorHost;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Bounded knowledge/generation observation only while a supported editor is the active screen. */
@EventBusSubscriber(modid = ArsNSpells.MODID, value = Dist.CLIENT)
public final class GlyphBrowserClientEvents {
    private static boolean unsupportedReported;
    private static long recipeRevision;

    @SubscribeEvent
    public static void recipesUpdated(net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) {
        recipeRevision++;
    }

    public static long recipeRevision() { return recipeRevision; }

    private GlyphBrowserClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof ArsGlyphEditorHost host && host.browserController() != null) {
            host.browserController().tick();
            return;
        }
        if (!unsupportedReported && screen != null
                && (screen instanceof com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook
                    || screen.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("spellbook"))) {
            unsupportedReported = true;
            ArsNSpells.LOGGER.warn("[GlyphBrowser] unsupported spell editor {}; preserving its native imported-glyph behavior",
                screen.getClass().getName());
        }
    }
}
