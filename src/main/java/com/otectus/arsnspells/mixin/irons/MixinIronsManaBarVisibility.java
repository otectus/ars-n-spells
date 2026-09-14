package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.client.ManaBarVisibility;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.config.ClientConfigs;
import io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay;
import io.redspace.ironsspellbooks.item.CastingItem;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Iron's contextual visibility at the precision of its mana packet and HUD. */
@Mixin(value = ManaBarOverlay.class, remap = false)
public abstract class MixinIronsManaBarVisibility {
    // Correct the shared predicate so the mana overlay and Iron's XP-bar handling
    // agree. Cancelling only RenderGuiLayerEvent.Pre would leave XP hidden.
    @Inject(method = "shouldShowManaBar", at = @At("RETURN"), cancellable = true, require = 0)
    private static void arsnspells$hideFullContextualMana(Player player,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || player == null
            || ClientConfigs.MANA_BAR_DISPLAY.get() != ManaBarOverlay.Display.Contextual) {
            return;
        }

        // Match Iron's native held-item rule, including either hand and containers
        // which can cast without being equipped. Equipped-only books do not count.
        boolean holdingCastingItem = player.isHolding(stack -> stack.getItem() instanceof CastingItem
            || (ISpellContainer.isSpellContainer(stack) && !ISpellContainer.get(stack).mustEquip()));
        if (ManaBarVisibility.shouldHideContextualBar(true, holdingCastingItem,
            ClientMagicData.getPlayerMana(), player.getAttributeValue(AttributeRegistry.MAX_MANA))) {
            cir.setReturnValue(false);
        }
    }
}
