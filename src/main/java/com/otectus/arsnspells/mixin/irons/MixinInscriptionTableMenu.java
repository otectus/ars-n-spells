package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.spell.irons.IronsInscriptionPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side half of the Inscription Table guard.
 *
 * <p>Iron's dereferences {@code ISpellContainer.get(...)} unguarded in both
 * {@code clickMenuButton} and {@code doInscription}. A malformed scroll therefore crashes
 * the <em>server</em>, not just the client that clicked - which makes this a remote crash
 * vector on a dedicated server, reachable by any player holding a legacy ANS carrier.
 *
 * <p>The verdict itself lives in {@link IronsInscriptionPolicy} so this and the client
 * screen mixin cannot disagree.
 */
@Mixin(value = io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu.class,
       remap = false)
public abstract class MixinInscriptionTableMenu {

    /** Iron's own accessor for the scroll slot; stable from 3.15.0 through 3.16.3. */
    @Shadow
    public abstract Slot getScrollSlot();

    /**
     * {@code clickMenuButton} is inherited from vanilla {@code AbstractContainerMenu}, so it
     * needs remapping even though the mixin target itself does not.
     */
    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true,
            remap = true, require = 0)
    private void arsnspells$guardMenuButton(Player player, int id,
                                            CallbackInfoReturnable<Boolean> cir) {
        // id >= 0 selects a spell slot and touches no scroll container; only the inscribe
        // action (id < 0) reaches the unguarded dereference.
        if (id >= 0) {
            return;
        }
        IronsInscriptionPolicy.Verdict verdict = arsnspells$verdict();
        if (!verdict.isRejection()) {
            return;
        }
        // Returning false leaves the menu untouched and the scroll unconsumed.
        cir.setReturnValue(false);
        arsnspells$notify(player, verdict);
    }

    /**
     * Belt-and-braces: {@code doInscription} is public, so another mod could reach it without
     * going through {@code clickMenuButton}. Cheap to guard, and it keeps the invariant local
     * to the method that actually mutates the book.
     */
    @Inject(method = "doInscription", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$guardDoInscription(int selectedIndex, CallbackInfo ci) {
        if (IronsInscriptionPolicy.evaluate(arsnspells$scrollSlotItem()).isRejection()) {
            ci.cancel();
        }
    }

    private IronsInscriptionPolicy.Verdict arsnspells$verdict() {
        return IronsInscriptionPolicy.evaluate(arsnspells$scrollSlotItem());
    }

    private ItemStack arsnspells$scrollSlotItem() {
        try {
            Slot slot = getScrollSlot();
            return slot == null ? ItemStack.EMPTY : slot.getItem();
        } catch (Throwable ignored) {
            // EMPTY -> ALLOW -> unmodified Iron's behaviour.
            return ItemStack.EMPTY;
        }
    }

    private void arsnspells$notify(Player player, IronsInscriptionPolicy.Verdict verdict) {
        if (player != null && !player.level().isClientSide()) {
            player.displayClientMessage(
                Component.translatable(verdict.messageKey()).withStyle(ChatFormatting.RED), false);
        }
    }
}
