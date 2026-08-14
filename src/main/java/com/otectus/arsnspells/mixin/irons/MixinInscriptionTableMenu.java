package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.spell.irons.IronsInscriptionPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side half of the Inscription Table guard, and the one that matters.
 *
 * <p>The reported crash was a client NPE, but the same malformed scroll hits two more
 * unguarded {@code ISpellContainer.get(...)} dereferences on the <b>server thread</b>:
 * <pre>
 *   clickMenuButton: SpellData spellData = ISpellContainer.get(scrollStack).getSpellAtIndex(0);
 *   doInscription:   ISpellContainer scrollContainer = ISpellContainer.get(scrollItemStack);
 *                    SpellData scrollSlot = scrollContainer.getSpellAtIndex(0);
 * </pre>
 * On a dedicated server that is a crash any player can trigger with a legacy ANS scroll, so
 * the client guard alone is not sufficient — an old or modified client still sends the packet.
 *
 * <p>{@code clickMenuButton} is guarded rather than only {@code doInscription} because the
 * first NPE happens before {@code doInscription} is ever called.
 *
 * <p>The guard also stops a <em>non</em>-crashing corruption: a well-formed ANS carrier has a
 * valid empty container, so {@code getSpellAtIndex(0)} yields {@code SpellData.EMPTY}, whose
 * {@code getSpell()} is a real {@code SpellRegistry.none()}. {@code doInscription} would
 * happily write that into the book and {@code shrink(1)} the scroll — silently eating the
 * carrier and adding a dud entry.
 *
 * <p><b>Iron's-gated</b> via {@code ArsNSpellsMixinPlugin}.
 */
@Mixin(value = io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu.class,
       remap = false)
public abstract class MixinInscriptionTableMenu {

    /** Iron's own accessor for the scroll slot; stable across 3.15.0 and 3.16.2. */
    @org.spongepowered.asm.mixin.Shadow
    public abstract Slot getScrollSlot();

    /**
     * {@code clickMenuButton} is inherited from vanilla {@code AbstractContainerMenu}, so it
     * needs remapping even though the mixin target itself does not.
     */
    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true,
            remap = true, require = 0)
    private void arsnspells$guardMenuButton(Player player, int id,
                                            CallbackInfoReturnable<Boolean> cir) {
        // id >= 0 selects a spell slot and touches no scroll container; only the
        // inscribe action (id < 0) reaches the unguarded dereference.
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
