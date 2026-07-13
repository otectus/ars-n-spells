package com.otectus.arsnspells.network;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.rituals.InscriptionInputs;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Client-to-server request from the Spell Loom screen to inscribe the source slot's
 * Ars spell — with the chosen display name, nature and icon — onto the blank Iron's
 * scroll in the scroll slot, placing the carrier in the output slot.
 *
 * <p>Server-authoritative: the client only sends the cosmetic choices. The server
 * re-reads the block entity's slots (never trusting client item state), validates,
 * and performs the mutation. Play payload handlers run on the main thread, so no
 * explicit enqueue is needed.
 */
public record SpellLoomExportPayload(String name, String nature, String iconSymbol)
    implements CustomPacketPayload {

    public static final int MAX_NAME = 40;

    public static final Type<SpellLoomExportPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "spell_loom_export"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellLoomExportPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SpellLoomExportPayload::name,
            ByteBufCodecs.STRING_UTF8, SpellLoomExportPayload::nature,
            ByteBufCodecs.STRING_UTF8, SpellLoomExportPayload::iconSymbol,
            SpellLoomExportPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SpellLoomExportPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sender)
            || !(sender.containerMenu instanceof SpellLoomMenu menu)) {
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

        String cleanName = payload.name() == null ? "" : payload.name().trim();
        if (cleanName.length() > MAX_NAME) {
            cleanName = cleanName.substring(0, MAX_NAME);
        }
        // Whitelist the cosmetic keys: anything outside the shipped sets is
        // dropped (empty = "use defaults"), so a hand-crafted packet cannot
        // stamp data that later resolves to a missing wheel texture.
        String cleanNature = CrossModSpellComponents.NATURE_KEYS.contains(payload.nature())
            ? payload.nature() : "";
        String cleanIcon = CrossModSpellComponents.ICON_SYMBOLS.contains(payload.iconSymbol())
            ? payload.iconSymbol() : "";
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
            spell.get(), cleanName, cleanNature, cleanIcon);
        if (carrier.isEmpty()) {
            sender.displayClientMessage(
                Component.translatable("ars_n_spells.spell_loom.error.failed"), true);
            return;
        }

        items.extractItem(SpellLoomBlockEntity.SLOT_SOURCE, 1, false);
        items.extractItem(SpellLoomBlockEntity.SLOT_SCROLL, 1, false);
        items.setStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT, carrier);
        be.setChanged();
    }
}
