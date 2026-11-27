package com.otectus.arsnspells.client.glyph;

import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.extensions.IMinecraftExtension;

import java.nio.file.Path;

/** NeoForge bindings for the loader-neutral browser controller and screens. */
public final class BrowserPlatform {
    private static GlyphBehaviorSnapshot enabledSnapshot;
    private static boolean enabledAtGeneration;

    private BrowserPlatform() {}

    public static boolean available(ImportedIronEffect effect) {
        return !pending() && effect != null && effect.behavior().available();
    }

    public static long generation() {
        return GlyphBehavior.current().generation();
    }

    public static boolean pending() {
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        return snapshot.generation() <= 0 || snapshot.ironsConfigPending();
    }

    public static boolean enabled() {
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        if (pending() || !snapshot.settings().glyphsEnabled() || !ModList.get().isLoaded("irons_spellbooks")) return false;
        if (enabledSnapshot != snapshot) {
            enabledSnapshot = snapshot;
            enabledAtGeneration = com.otectus.arsnspells.glyph.ImportedGlyphRegistration.effects().values().stream()
                .anyMatch(effect -> snapshot.variant(effect.key()).available());
        }
        return enabledAtGeneration;
    }

    public static BrowserModel.Variant variant(ImportedIronEffect effect) {
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        VariantBehavior behavior = snapshot.variant(effect.key());
        var facts = snapshot.spell(effect.key().ironSpellId());
        String reason = behavior.available() ? null : dormantReason(effect, snapshot, behavior);
        // The unsynced snapshot contains catalog defaults so gameplay code has a bounded fallback,
        // but the browser must not present that default as the world's effective school.
        boolean hasLiveSpell = switch (behavior.dormancy()) {
            case IRONS_ABSENT, SPELL_NOT_REGISTERED, PACKAGE_MISSING, NOT_SYNCED -> false;
            default -> !pending() && facts != null && facts.school() != null;
        };
        String school = hasLiveSpell ? facts.school().toString() : "";
        boolean learning = facts != null ? facts.requiresLearning() : effect.entry().requiresLearning();
        return new BrowserModel.Variant(
            effect.getRegistryName().toString(), effect.key().ironSpellId().toString(), effect.key().baseLevel(),
            effect.spellName().getString(), effect.getLocaleName(), sourceName(effect.key().ironSpellId().getNamespace()),
            school, behavior.tier(), reason, learning);
    }

    private static String dormantReason(ImportedIronEffect effect, GlyphBehaviorSnapshot snapshot, VariantBehavior behavior) {
        Dormancy dormancy = behavior.dormancy();
        String source = sourceName(effect.key().ironSpellId().getNamespace());
        String spell = effect.spellName().getString();
        return switch (dormancy) {
            case IRONS_ABSENT -> Component.translatable("ars_n_spells.browser.reason.source_absent", source, spell).getString();
            case SPELL_NOT_REGISTERED ->
                Component.translatable("ars_n_spells.browser.reason.spell_missing", spell, source).getString();
            case LEVEL_ABOVE_SERVER_MAX -> {
                var facts = snapshot.spell(effect.key().ironSpellId());
                int maximum = facts == null ? effect.entry().maxLevel() : facts.liveMax();
                yield Component.translatable("ars_n_spells.browser.reason.above_max", effect.key().baseLevel(), maximum).getString();
            }
            case LEVEL_BELOW_SERVER_MIN -> {
                var facts = snapshot.spell(effect.key().ironSpellId());
                int minimum = facts == null ? effect.entry().minLevel() : facts.liveMin();
                yield Component.translatable("ars_n_spells.browser.reason.below_min", effect.key().baseLevel(), minimum).getString();
            }
            default -> Component.translatable(dormancy.translationKey()).getString();
        };
    }

    public static String sourceName(String namespace) {
        return ModList.get().getModContainerById(namespace)
            .map(container -> container.getModInfo().getDisplayName())
            .orElse(namespace);
    }

    public static Path favoritesPath() {
        return FMLPaths.CONFIGDIR.get().resolve("ars_n_spells").resolve("glyph_browser.json");
    }

    public static void push(Screen screen) {
        ((IMinecraftExtension) Minecraft.getInstance()).pushGuiLayer(screen);
    }

    public static void pop() {
        ((IMinecraftExtension) Minecraft.getInstance()).popGuiLayer();
    }

}
