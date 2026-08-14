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
 * Client-side half of the Inscription Table guard.
 *
 * <p>{@code onInscription} does:
 * <pre>
 *   ISpellContainer scrollContainer = ISpellContainer.get(menu.getScrollSlot().getItem());
 *   SpellData scrollSlot = scrollContainer.getSpellAtIndex(0);   // no null check
 * </pre>
 * and {@code ISpellContainer.get} returns null for a scroll with no container NBT (verified
 * identical in Iron's 3.15.0 and 3.16.2). Any ANS carrier created before the export fix
 * therefore crashes this screen — the reported NPE.
 *
 * <p>This cancels before that dereference and tells the player why. The server-side twin
 * ({@link MixinInscriptionTableMenu}) is the one that actually protects the world: a client
 * mixin cannot stop an older or modified client from sending the packet.
 *
 * <p><b>Client-only and Iron's-gated</b> (see {@code ars_n_spells.mixins.json} "client" list
 * and {@code ArsNSpellsMixinPlugin}).
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
     * in both 3.15.0 and 3.16.2, so the pattern match always succeeds today. It is written as
     * a test rather than a cast so a future hierarchy change degrades to "no guard" instead of
     * throwing a second exception out of a click handler — but it logs loudly when that
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
                + "hierarchy has changed. Malformed scrolls may crash this screen — the "
                + "server-side guard still applies.");
        }
        return ItemStack.EMPTY;
    }
}
