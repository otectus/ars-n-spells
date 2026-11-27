package com.otectus.arsnspells.client.glyph;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphLiveSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;

public final class BrowserPlatform {
    private BrowserPlatform() {}
    public static long generation() { return GlyphBehavior.generation(); }
    public static boolean pending() {
        var state = GlyphBehavior.live().readiness();
        return state == GlyphLiveSnapshot.Readiness.NOT_SYNCED || state == GlyphLiveSnapshot.Readiness.PENDING;
    }
    public static boolean enabled() {
        var live = GlyphBehavior.live();
        return live.readiness() == GlyphLiveSnapshot.Readiness.READY
            && (live.received() ? live.glyphsEnabled() : AnsConfig.ironGlyphsEnabled());
    }
    public static boolean available(ImportedIronEffect effect) {
        return !pending() && GlyphAvailability.status(effect.definition()).available();
    }
    public static String sourceName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(mod -> mod.getModInfo().getDisplayName()).orElse(namespace);
    }
    public static BrowserModel.Variant variant(ImportedIronEffect effect) {
        var def = effect.definition();
        var status = GlyphAvailability.status(def);
        String name = Component.translatableWithFallback("spell." + def.spell().getNamespace() + "." + def.spell().getPath(), def.spellName()).getString();
        String reason = status.available() ? null : status.message(def).getString();
        if (status.reason() == GlyphAvailability.Reason.LEVEL_BELOW_MINIMUM)
            reason = Component.translatable(status.reason().key(), effect.displayName(), GlyphBehavior.facts(def).minLevel()).getString();
        var facts = GlyphBehavior.live().facts(def.spell());
        String school = facts == null || !facts.registered() || facts.school() == null ? "" : facts.school().toString();
        return new BrowserModel.Variant(def.glyphId().toString(), def.spell().toString(), def.level(), name, effect.displayName().getString(),
            sourceName(def.spell().getNamespace()), school,
            GlyphAvailability.tier(def), reason, GlyphBehavior.requiresLearning(def));
    }
    public static Path favoritesPath() { return FMLPaths.CONFIGDIR.get().resolve("ars_n_spells/glyph_browser.json"); }
    public static void push(Screen screen) { Minecraft.getInstance().pushGuiLayer(screen); }
    public static void pop() { Minecraft.getInstance().popGuiLayer(); }
}
