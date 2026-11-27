package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.spell.irons.IronsTableExtraction;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Inscription Table's result slot (Iron's anonymous {@code InscriptionTableMenu$5} in 1.21.1-3.16.3),
 * for an ANS extraction (3.4.1 review UNBIND-01). The NeoForge counterpart of the Forge build's mixin.
 *
 * <p>Every way of taking the result (click, shift-click, a number key, throwing it, collecting with a
 * double click) asks {@link #mayPickup} first and ends in {@code onTake}. So an offered carrier can only
 * be taken while the book is still exactly the book it was built from, and taking it removes exactly
 * that entry and its proxy slot, verified, instead of Iron's own removal by wheel index. A full
 * inventory leaves the result in place and removes nothing; closing the menu takes nothing. Iron's own
 * spells are untouched: without a staged ANS offer both methods behave natively.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu$5", remap = false)
public abstract class MixinInscriptionResultSlot extends Slot {
    private MixinInscriptionResultSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    /** The anonymous slot's enclosing menu ({@code this$0}), read reflectively: the field is synthetic. */
    @Unique
    private InscriptionTableMenu arsnspells$menu() {
        try {
            java.lang.reflect.Field outer = getClass().getDeclaredField("this$0");
            outer.setAccessible(true);
            return (InscriptionTableMenu) outer.get(this);
        } catch (ReflectiveOperationException | ClassCastException unavailable) {
            return null;
        }
    }

    @Override
    public boolean mayPickup(Player player) {
        InscriptionTableMenu menu = arsnspells$menu();
        IronsTableExtraction.Preview preview = menu == null ? null : IronsTableExtraction.staged(menu);
        if (preview == null || !ItemStack.matches(preview.result(), getItem())) return super.mayPickup(player);
        return preview.ok() && IronsTableExtraction.fresh(preview, menu.getSpellBookSlot().getItem()) && super.mayPickup(player);
    }

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true, require = 1)
    private void arsnspells$commitExtraction(Player player, ItemStack taken, CallbackInfo ci) {
        InscriptionTableMenu menu = arsnspells$menu();
        IronsTableExtraction.Preview preview = menu == null ? null : IronsTableExtraction.staged(menu);
        // An offer is staged only while the result slot holds an ANS carrier; Iron's own offers clear
        // it. The taken stack is not compared: a shift-click passes the emptied slot stack.
        if (preview == null || !preview.ok()) return;
        IronsTableExtraction.stage(menu, null);
        Slot bookSlot = menu.getSpellBookSlot();
        ItemStack updated = IronsTableExtraction.commit(preview, bookSlot.getItem());
        // Iron's own removal-by-index never runs for an ANS carrier, committed or not.
        ci.cancel();
        if (updated != null) bookSlot.set(updated);
        // The wheel index no longer names the taken entry: clear the selection so nothing else is offered.
        menu.setSelectedSpell(-1);
        setChanged();
    }
}
