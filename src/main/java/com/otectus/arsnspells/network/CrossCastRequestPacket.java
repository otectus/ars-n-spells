package com.otectus.arsnspells.network;

import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.contract.RequestAdmission;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client-to-server packet announcing a cross-cast intent. The server is the
 * sole authority over cast execution: the client only signals intent via this
 * packet, then cancels the local interaction to suppress vanilla use prediction.
 *
 * <p>The {@code clientAttemptId} is generated client-side at the moment of
 * input and is logged alongside the server's own attempt UUID for correlated
 * cross-side traces. The server never trusts the client's selected index;
 * {@link CrossCastingHandler} re-reads the held stack and re-resolves the
 * payload at packet receipt.
 */
public class CrossCastRequestPacket {

    public enum Action { CAST, CYCLE }

    private final InteractionHand hand;
    private final Action action;
    private final int clientSelectedIndex;
    private final UUID clientAttemptId;
    private final String carrierFingerprint;

    public CrossCastRequestPacket(InteractionHand hand, Action action, int clientSelectedIndex,
        UUID clientAttemptId) {
        this(hand, action, clientSelectedIndex, clientAttemptId, "");
    }

    public CrossCastRequestPacket(InteractionHand hand, Action action, int clientSelectedIndex,
        UUID clientAttemptId, String carrierFingerprint) {
        this.hand = hand;
        this.action = action;
        this.clientSelectedIndex = clientSelectedIndex;
        this.clientAttemptId = clientAttemptId != null ? clientAttemptId : new UUID(0L, 0L);
        this.carrierFingerprint = carrierFingerprint == null ? "" : carrierFingerprint;
    }

    public CrossCastRequestPacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.action = buf.readEnum(Action.class);
        this.clientSelectedIndex = buf.readVarInt();
        this.clientAttemptId = buf.readUUID();
        this.carrierFingerprint = buf.readUtf(64);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeEnum(action);
        buf.writeVarInt(clientSelectedIndex);
        buf.writeUUID(clientAttemptId);
        buf.writeUtf(carrierFingerprint, 64);
    }

    public InteractionHand hand() {
        return hand;
    }

    public Action action() {
        return action;
    }

    public int clientSelectedIndex() {
        return clientSelectedIndex;
    }

    public UUID clientAttemptId() {
        return clientAttemptId;
    }

    public String carrierFingerprint() { return carrierFingerprint; }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx == null) {
            return;
        }
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || hand != InteractionHand.MAIN_HAND || action == null
                || !sender.isAlive() || sender.isSpectator() || sender.isSleeping()
                || sender.containerMenu != sender.inventoryMenu) {
                return;
            }
            RequestAdmission.Result admission = NetworkRequestGuard.admit(sender, clientAttemptId);
            if (admission != RequestAdmission.Result.ACCEPTED) return;
            ItemStack stack = sender.getItemInHand(hand);
            if (carrierFingerprint.isEmpty() || !carrierFingerprint.equals(CarrierFingerprint.of(stack))) {
                sender.displayClientMessage(Component.translatable("arsnspells.crosscast.stale_carrier"), true);
                sender.inventoryMenu.broadcastChanges();
                return;
            }
            UUID serverAttemptId = UUID.randomUUID();

            CrossCastTrace.log(serverAttemptId, sender, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.REQUEST_RECEIVED,
                "hand", hand,
                "action", action,
                "clientIndex", clientSelectedIndex,
                "clientAttempt", clientAttemptId,
                "item", stack.getItem());

            CrossCastingHandler.serverHandleCast(sender, stack, hand, action, serverAttemptId);
        });
        ctx.setPacketHandled(true);
    }
}
