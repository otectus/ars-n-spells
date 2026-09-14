package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.spell.irons.IronsInscriptionPolicy;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side half of the Inscription Table guard - the reported NPE. Stops the screen
 * dereferencing a scroll Iron's cannot read before the packet is ever sent.
 *
 * <p>A well-formed ANS carrier is <em>not</em> cancelled any more: as of 3.3.3 the click is
 * what asks the server to bind, and javap confirms the screen is safe for it - Iron's
 * dead-store SpellData fetch tolerates {@code SpellData.EMPTY}, and the inscribe button is
 * active for any {@code Scroll}. Only the two malformed-scroll verdicts still stop the click.
 *
 * <p>The server-side twin is {@link MixinInscriptionTableMenu}; both read the same verdict
 * from {@link IronsInscriptionPolicy}.
 */
@Mixin(value = io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen.class,
       remap = false)
public abstract class MixinInscriptionTableScreen {

    private static final Logger ARSNSPELLS$LOGGER =
        LoggerFactory.getLogger(MixinInscriptionTableScreen.class);

    /** One ERROR per session is enough; this runs on a click handler. */
    private static final AtomicBoolean ARSNSPELLS$WARNED = new AtomicBoolean();

    @Inject(method = "onInscription", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$guardInscription(CallbackInfo ci) {
        IronsInscriptionPolicy.Verdict verdict =
            IronsInscriptionPolicy.evaluate(arsnspells$scrollSlotItem());
        // ALLOW and BIND_CARRIER both let the click through: the first is Iron's own
        // inscription, the second is the packet the server-side router turns into a bind.
        if (!verdict.isRejection()) {
            return;
        }
        ci.cancel();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                Component.translatable(verdict.messageKey()).withStyle(ChatFormatting.RED), false);
        }
    }

    /**
     * Read the scroll slot via the screen's own menu.
     *
     * <p>{@code InscriptionTableScreen extends AbstractContainerScreen<InscriptionTableMenu>}
     * in 3.15.0 through 3.16.3, so the pattern match always succeeds today. It is written as a
     * test rather than a cast so a future hierarchy change degrades to "no guard" instead of
     * throwing a second exception out of a click handler - but it logs loudly when that
     * happens, because a silently disabled guard is a crash nobody will connect to this code.
     */
    private ItemStack arsnspells$scrollSlotItem() {
        Object self = this;
        if (self instanceof AbstractContainerScreen<?> screen
            && screen.getMenu() instanceof InscriptionTableMenu table) {
            Slot slot = table.getScrollSlot();
            return slot == null ? ItemStack.EMPTY : slot.getItem();
        }
        if (ARSNSPELLS$WARNED.compareAndSet(false, true)) {
            ARSNSPELLS$LOGGER.error("Could not read the Inscription Table scroll slot; the ANS "
                + "client-side crash guard is inactive for this session. Iron's screen/menu "
                + "hierarchy has changed. Malformed scrolls may crash this screen - the "
                + "server-side guard still applies.");
        }
        return ItemStack.EMPTY;
    }
}
