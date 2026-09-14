package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.spell.irons.ArsCrossProxySpell;
import com.otectus.arsnspells.spell.irons.IronsInscriptionPolicy;
import com.otectus.arsnspells.spell.irons.IronsTableBindHandler;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side router for the Inscription Table, and the one that matters.
 *
 * <p>Three jobs, all at the head of Iron's own methods so ANS decides before Iron's reads
 * anything:
 * <ol>
 *   <li><b>Crash guard.</b> A malformed scroll hits unguarded {@code ISpellContainer.get(...)}
 *       dereferences on the <b>server thread</b> in both {@code clickMenuButton} and
 *       {@code doInscription} — a crash any player can trigger on a dedicated server, so a
 *       client-side guard alone is not sufficient.</li>
 *   <li><b>Carrier bind.</b> A well-formed ANS carrier has a valid but intentionally empty
 *       container, so Iron's would inscribe {@code SpellRegistry.none()} into the book and
 *       {@code shrink(1)} the scroll. Instead the click is routed to
 *       {@link IronsTableBindHandler}, which binds the Ars payload properly. This is what
 *       makes the table a first-class bind route alongside the ritual and the command.</li>
 *   <li><b>Extraction guard.</b> {@code setupResultSlot} offers the selected book spell for
 *       extraction onto a fresh scroll. Doing that to an ANS proxy entry would hand the
 *       player a scroll holding a proxy with no sidecar payload — a spell that resolves to
 *       nothing — and strand the real entry on the book.</li>
 * </ol>
 *
 * <p>The verdict itself lives in {@link IronsInscriptionPolicy} so this and the client screen
 * mixin cannot disagree.
 *
 * <p><b>Iron's-gated</b> via {@code ArsNSpellsMixinPlugin}. No new mixin class: this is the
 * same target the 3.2.x crash guard already used.
 */
@Mixin(value = InscriptionTableMenu.class, remap = false)
public abstract class MixinInscriptionTableMenu {

    /** Iron's own accessor for the scroll slot; stable from 3.15.0 through 3.16.3. */
    @Shadow
    public abstract Slot getScrollSlot();

    /** Iron's selected wheel index. Only read, never written. */
    @Shadow
    private int selectedSpellIndex;

    /** The book being edited. Shadowed for the extraction guard's container read. */
    @Shadow
    @Final
    private Slot spellBookSlot;

    /** Backs the result slot; clearing it is how the offered scroll is withdrawn. */
    @Shadow
    @Final
    protected ResultContainer resultContainer;

    /**
     * {@code clickMenuButton} is inherited from vanilla {@code AbstractContainerMenu}, so it
     * needs remapping even though the mixin target itself does not.
     */
    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true,
            remap = true, require = 0)
    private void arsnspells$routeMenuButton(Player player, int id,
                                            CallbackInfoReturnable<Boolean> cir) {
        // id >= 0 selects a spell slot and touches no scroll container; only the inscribe
        // action (id < 0) reaches the bind decision.
        if (id >= 0) {
            return;
        }
        IronsInscriptionPolicy.Verdict verdict = arsnspells$verdict();
        if (verdict == IronsInscriptionPolicy.Verdict.ALLOW) {
            return;
        }
        if (verdict.isRejection()) {
            // Returning false leaves the menu untouched and the scroll unconsumed.
            cir.setReturnValue(false);
            arsnspells$notify(player, verdict.messageKey());
            return;
        }
        // BIND_CARRIER: ANS owns this click. Iron's own path is skipped entirely, including
        // its selectedSpellIndex precondition, which is meaningless for a carrier.
        if (player instanceof ServerPlayer serverPlayer
            && (Object) this instanceof InscriptionTableMenu menu) {
            IronsTableBindHandler.handle(serverPlayer, menu);
        }
        cir.setReturnValue(true);
    }

    /**
     * Belt-and-braces: {@code doInscription} is public, so another mod could reach it without
     * going through {@code clickMenuButton}, and Iron's own inscription must never run for a
     * carrier or a malformed scroll.
     */
    @Inject(method = "doInscription", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$guardDoInscription(int selectedIndex, CallbackInfo ci) {
        if (IronsInscriptionPolicy.evaluate(arsnspells$scrollSlotItem())
            != IronsInscriptionPolicy.Verdict.ALLOW) {
            ci.cancel();
        }
    }

    /**
     * Withdraw the extraction offer for ANS proxy entries.
     *
     * <p>Iron's method is its own, so {@code remap = false}. Both sides run the menu, so
     * client and server agree on the empty result slot without any packet of ours.
     *
     * <p>Iron's builds its result {@code Slot} on {@code resultContainer} at container index
     * 2, but {@code ResultContainer} is one stack wide and ignores the index on both
     * {@code getItem} and {@code setItem}, so clearing index 0 clears the offer.
     */
    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void arsnspells$guardResultSlot(CallbackInfo ci) {
        if (!arsnspells$selectedIsProxy()) {
            return;
        }
        if (resultContainer != null) {
            resultContainer.setItem(0, ItemStack.EMPTY);
        }
        ci.cancel();
    }

    private boolean arsnspells$selectedIsProxy() {
        if (selectedSpellIndex < 0 || spellBookSlot == null) {
            return false;
        }
        try {
            ItemStack book = spellBookSlot.getItem();
            if (book.isEmpty() || !ISpellContainer.isSpellContainer(book)) {
                return false;
            }
            ISpellContainer container = ISpellContainer.getOrCreate(book);
            SpellData data = container.getSpellAtIndex(selectedSpellIndex);
            return data != null && data.getSpell() instanceof ArsCrossProxySpell;
        } catch (Throwable ignored) {
            // An unreadable container is Iron's problem to report, not a reason to throw out
            // of a slot refresh; leave the offer to Iron's own code.
            return false;
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

    /** Chat only ever goes to the server player; the client menu must stay silent. */
    private void arsnspells$notify(Player player, String messageKey) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                Component.translatable(messageKey).withStyle(ChatFormatting.RED), false);
        }
    }
}
