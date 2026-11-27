package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.client.gui.buttons.CraftingButton;
import com.otectus.arsnspells.client.glyph.BrowserPlatform;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Shows live pending/dormant state on actual stored recipe cells in both Forge editors. */
@Mixin(value = CraftingButton.class, remap = false)
public abstract class MixinCraftingButtonImportedWarning {
    @Shadow private AbstractSpellPart abstractSpellPart;

    @Inject(method = "getTooltip", at = @At("TAIL"))
    private void ans$importedAvailability(List<Component> tooltip, CallbackInfo ci) {
        if (!(abstractSpellPart instanceof ImportedIronEffect imported)) return;
        Component warning;
        if (BrowserPlatform.pending()) {
            warning = Component.translatable("ars_n_spells.browser.waiting").withStyle(ChatFormatting.YELLOW);
        } else {
            GlyphAvailability.Status status = GlyphAvailability.status(imported.definition());
            if (status.available()) return;
            warning = status.message(imported.definition()).copy().withStyle(ChatFormatting.RED);
        }
        if (tooltip.stream().noneMatch(line -> line.getString().equals(warning.getString()))) tooltip.add(warning);
    }
}
