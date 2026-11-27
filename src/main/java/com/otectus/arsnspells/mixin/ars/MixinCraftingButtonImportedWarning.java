package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.client.gui.buttons.CraftingButton;
import com.otectus.arsnspells.client.glyph.BrowserPlatform;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Existing recipes remain exact and explain imported glyph state even when browsing is hidden. */
@Mixin(CraftingButton.class)
public abstract class MixinCraftingButtonImportedWarning {
    @Shadow protected AbstractSpellPart abstractSpellPart;

    @Inject(method = "getTooltip", at = @At("TAIL"))
    private void arsnspells$appendImportedState(List<Component> tooltip, CallbackInfo ci) {
        if (!(abstractSpellPart instanceof ImportedIronEffect effect)) return;
        if (BrowserPlatform.pending()) {
            tooltip.add(Component.translatable("ars_n_spells.browser.waiting").withStyle(ChatFormatting.GOLD));
        } else if (!BrowserPlatform.available(effect)) {
            tooltip.add(Component.literal(BrowserPlatform.variant(effect).reason()).withStyle(ChatFormatting.RED));
        }
    }
}
