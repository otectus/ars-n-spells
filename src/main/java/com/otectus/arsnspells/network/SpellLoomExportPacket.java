package com.otectus.arsnspells.network;

import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.inscription.LoomInscription;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server request from the Spell Loom screen: preview an inscription, perform one, or
 * perform an explicit conversion of an already-filled scroll.
 *
 * <p>Server-authoritative: the client only sends the cosmetic choices and which of the three
 * actions it wants. The server re-reads the block entity's slots (never trusting client item
 * state) and hands the whole decision to {@link LoomInscription}, which plans through
 * {@link com.otectus.arsnspells.contract.InscriptionPlanner} and only then moves anything.
 *
 * <p><b>Preview moves nothing</b> -- it calls the pure planner and replies with the reason code.
 * <b>Convert is a separate action</b>, never reachable from the ordinary Inscribe button, so a
 * scroll that already holds a spell can only be overwritten by a player who asked for exactly
 * that after seeing the preview say it is not blank.
 *
 * <p>The reply is a {@link SpellLoomResultPacket} carrying the planner's reason code, which is
 * what lets the screen state why an input was refused. Adding the action field and the reply is
 * why {@code PacketHandler.PROTOCOL_VERSION} moved to 4.
 */
public class SpellLoomExportPacket {
    private static final int MAX_NAME = 40;

    /** Plan only and report; touch nothing. */
    public static final int ACTION_PREVIEW = 0;
    /** Inscribe onto a blank target. */
    public static final int ACTION_INSCRIBE = 1;
    /** Blank an already-filled target and inscribe onto it, by explicit player choice. */
    public static final int ACTION_CONVERT = 2;

    private final String name;
    private final String nature;
    private final String iconSymbol;
    private final int action;

    public SpellLoomExportPacket(String name, String nature, String iconSymbol, int action) {
        this.name = name == null ? "" : name;
        this.nature = nature == null ? "" : nature;
        this.iconSymbol = iconSymbol == null ? "" : iconSymbol;
        this.action = clampAction(action);
    }

    public SpellLoomExportPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf(MAX_NAME);
        this.nature = buf.readUtf(64);
        this.iconSymbol = buf.readUtf(64);
        // Anything outside the three known actions degrades to the harmless one rather than
        // being trusted: a hand-crafted packet must not be able to reach the destructive route
        // by sending a value nobody thought to reject.
        this.action = clampAction(buf.readVarInt());
    }

    private static int clampAction(int raw) {
        return (raw == ACTION_INSCRIBE || raw == ACTION_CONVERT) ? raw : ACTION_PREVIEW;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(name, MAX_NAME);
        buf.writeUtf(nature, 64);
        buf.writeUtf(iconSymbol, 64);
        buf.writeVarInt(action);
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

            if (action == ACTION_PREVIEW) {
                InscriptionPlan plan = LoomInscription.plan(be);
                PacketHandler.sendToClient(
                    new SpellLoomResultPacket(plan.reasonCode(), true), sender);
                return;
            }

            String cleanName = name.trim();
            if (cleanName.length() > MAX_NAME) {
                cleanName = cleanName.substring(0, MAX_NAME);
            }
            // Whitelist the cosmetic keys: anything outside the shipped sets is
            // dropped (empty = "use defaults"), so a hand-crafted packet cannot
            // stamp NBT that later resolves to a missing wheel texture.
            String cleanNature = com.otectus.arsnspells.spell.CrossCastNbt.NATURE_KEYS
                .contains(nature) ? nature : "";
            String cleanIcon = com.otectus.arsnspells.spell.CrossCastNbt.ICON_SYMBOLS
                .contains(iconSymbol) ? iconSymbol : "";

            String reason = LoomInscription.apply(
                be, action == ACTION_CONVERT, cleanName, cleanNature, cleanIcon);
            PacketHandler.sendToClient(new SpellLoomResultPacket(reason, false), sender);
            if (InscriptionPlan.REASON_OK.equals(reason)) {
                com.otectus.arsnspells.util.AdvancementUtil.grant(sender, "transcribe_spell");
            }
        });
        ctx.setPacketHandled(true);
    }
}
