package com.otectus.arsnspells.client;

import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Client-only: whether the local player already knows an imported glyph. */
public final class ImportedGlyphClientTooltip {
    private ImportedGlyphClientTooltip() {}

    public static void appendKnowledge(ImportedIronEffect effect, List<Component> tooltip) {
        if (Minecraft.getInstance().player == null) return;
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(Minecraft.getInstance().player).orElse(null);
        if (cap == null) return;
        tooltip.add(cap.knowsGlyph(effect)
            ? Component.translatable("tooltip.ars_nouveau.glyph_known").withStyle(ChatFormatting.DARK_GREEN)
            : Component.translatable("tooltip.ars_nouveau.glyph_unknown").withStyle(ChatFormatting.DARK_RED));
    }
}
