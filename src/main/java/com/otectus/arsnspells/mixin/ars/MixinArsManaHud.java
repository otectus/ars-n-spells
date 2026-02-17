package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.client.gui.GuiManaHUD;
import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide Ars Nouveau mana HUD when Iron's Spellbooks is the primary mana system.
 * This ensures only one mana bar is visible based on the configured mana mode.
 */
@Mixin(value = GuiManaHUD.class, remap = false)
public abstract class MixinArsManaHud {

    /**
     * Cancel Ars Nouveau mana HUD rendering when Iron's Spellbooks is primary.
     * Uses require = 0 for optional mixin (graceful degradation if class changes).
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arsnspells$hideArsHud(GuiGraphics graphics, float partialTicks, CallbackInfo ci) {
        try {
            // Only hide if mana unification is enabled
            if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
                return; // Let both bars show if unification is disabled
            }
            
            // Get current mana mode
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            
            // Hide Ars mana bar if Iron's Spellbooks is primary
            if (mode == ManaUnificationMode.ISS_PRIMARY) {
                ci.cancel();
                return;
            }

            // In HYBRID mode, hide Ars bar if user prefers Iron's bar
            if (mode == ManaUnificationMode.HYBRID) {
                if ("irons".equalsIgnoreCase(AnsConfig.HYBRID_MANA_BAR.get())) {
                    ci.cancel();
                    return;
                }
            }

            // In SEPARATE mode, show both bars (no cancellation)
            // In ARS_PRIMARY mode, show Ars bar (no cancellation)
            // In HYBRID mode with ars preference, show Ars bar (no cancellation)
            // In DISABLED mode, show both bars (no cancellation)
        } catch (Exception e) {
            // Fail silently to prevent crashes
        }
    }
}
