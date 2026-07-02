package com.otectus.arsnspells.network;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.rituals.InscriptionInputs;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client-to-server request from the Spell Loom screen to inscribe the source slot's
 * Ars spell — with the chosen display name, nature and icon — onto the blank Iron's
 * scroll in the scroll slot, placing the carrier in the output slot.
 *
 * <p>Server-authoritative: the client only sends the cosmetic choices. The server
 * re-reads the block entity's slots (never trusting client item state), validates,
 * and performs the mutation.
 */
public class SpellLoomExportPacket {
    private static final int MAX_NAME = 40;

    private final String name;
    private final String nature;
    private final String iconSymbol;
    private final int iconColor;

    public SpellLoomExportPacket(String name, String nature, String iconSymbol, int iconColor) {
        this.name = name == null ? "" : name;
        this.nature = nature == null ? "" : nature;
        this.iconSymbol = iconSymbol == null ? "" : iconSymbol;
        this.iconColor = iconColor;
    }

    public SpellLoomExportPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf(MAX_NAME);
        this.nature = buf.readUtf(64);
        this.iconSymbol = buf.readUtf(64);
        this.iconColor = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(name, MAX_NAME);
        buf.writeUtf(nature, 64);
        buf.writeUtf(iconSymbol, 64);
        buf.writeInt(iconColor);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx == null) {
            return;
        }
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !(sender.containerMenu instanceof SpellLoomMenu menu)) {
                return;
            }
            SpellLoomBlockEntity be = menu.getBlockEntity();
            if (be == null || !menu.stillValid(sender)) {
                return;
            }
            ItemStackHandler items = be.getItems();
            ItemStack source = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE);
            ItemStack scroll = items.getStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL);
            ItemStack output = items.getStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT);

            if (!output.isEmpty()) {
                return; // output occupied; player must clear it first
            }
            if (!IronsCompat.isLoaded()) {
                sender.displayClientMessage(
                    Component.translatable("ars_n_spells.spell_loom.error.irons_missing"), true);
                return;
            }
            // A blank Iron's scroll: the real scroll item, not already a carrier.
            if (!IronsBookBindingUtil.isIronsScroll(scroll) || InscriptionInputs.isInscribed(scroll)) {
                sender.displayClientMessage(
                    Component.translatable("ars_n_spells.spell_loom.error.no_scroll"), true);
                return;
            }
            Optional<Spell> spell = ArsSpellExportUtil.extractArsSpell(source);
            if (spell.isEmpty()) {
                sender.displayClientMessage(
                    Component.translatable("ars_n_spells.spell_loom.error.no_source"), true);
                return;
            }

            String cleanName = name.trim();
            if (cleanName.length() > MAX_NAME) {
                cleanName = cleanName.substring(0, MAX_NAME);
            }
            ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
                spell.get(), cleanName, nature, iconSymbol, iconColor);
            if (carrier.isEmpty()) {
                sender.displayClientMessage(
                    Component.translatable("ars_n_spells.spell_loom.error.failed"), true);
                return;
            }

            items.extractItem(SpellLoomBlockEntity.SLOT_SOURCE, 1, false);
            items.extractItem(SpellLoomBlockEntity.SLOT_SCROLL, 1, false);
            items.setStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT, carrier);
            be.setChanged();
        });
        ctx.setPacketHandled(true);
    }
}
