package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide Iron's Spellbooks mana bar when Ars Nouveau is the primary mana system.
 * This ensures only one mana bar is visible based on the configured mana mode.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay", remap = false)
public abstract class MixinIronsManaBarOverlay {

    /**
     * Cancel Iron's Spellbooks mana bar rendering when Ars Nouveau is primary.
     * Uses require = 0 for optional mixin (graceful degradation if class changes).
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$hideIronsManaBar(
            ForgeGui forgeGui,
            GuiGraphics guiGraphics,
            float partialTick,
            int screenWidth,
            int screenHeight,
            CallbackInfo ci) {
        
        // Only hide if mana unification is enabled
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return; // Let both bars show if unification is disabled
        }
        
        // Get current mana mode
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        
        // Hide Iron's mana bar if Ars Nouveau is primary
        if (mode == ManaUnificationMode.ARS_PRIMARY) {
            ci.cancel();
            return;
        }

        // In HYBRID mode, check which bar the user wants to display
        if (mode == ManaUnificationMode.HYBRID) {
            if (!"irons".equalsIgnoreCase(AnsConfig.HYBRID_MANA_BAR.get())) {
                ci.cancel();
                return;
            }
        }

        // In ISS_PRIMARY and SEPARATE modes, show Iron's bar
        // In DISABLED mode, show both bars
    }
}
