package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.contract.RequestAdmission;
import net.minecraft.network.chat.Component;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Client-to-server payload announcing a cross-cast intent. The server is the
 * sole authority over cast execution: the client only signals intent with this
 * payload, then cancels the local interaction to suppress vanilla use prediction.
 *
 * <p>The {@code clientAttemptId} is generated client-side at the moment of input
 * and is logged alongside the server's own attempt UUID for correlated cross-side
 * traces. The server never trusts {@code clientSelectedIndex} — it is carried for
 * the trace only; {@link CrossCastingHandler#serverHandleCast} re-reads the held
 * stack and re-resolves the payload at receipt.
 */
public record CrossCastRequestPayload(InteractionHand hand,
                                      Action action,
                                      int clientSelectedIndex,
                                      UUID clientAttemptId,
                                      String carrierFingerprint) implements CustomPacketPayload {

    public enum Action { CAST, CYCLE }

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public static final Type<CrossCastRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "cross_cast_request"));

    /**
     * Hand-written rather than composed: {@code ByteBufCodecs} has no enum codec,
     * and {@link RegistryFriendlyByteBuf} still inherits the 1.20.1-era
     * {@code writeEnum}/{@code readEnum}/{@code writeUUID} helpers, so the wire
     * shape is byte-identical to the SimpleChannel packet this replaces.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CrossCastRequestPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, value) -> {
                buf.writeEnum(value.hand());
                buf.writeEnum(value.action());
                buf.writeVarInt(value.clientSelectedIndex());
                buf.writeUUID(value.clientAttemptId());
                buf.writeUtf(value.carrierFingerprint(), 64);
            },
            buf -> new CrossCastRequestPayload(
                buf.readEnum(InteractionHand.class),
                buf.readEnum(Action.class),
                buf.readVarInt(),
                buf.readUUID(),
                buf.readUtf(64)
            )
        );

    public CrossCastRequestPayload {
        clientAttemptId = clientAttemptId != null ? clientAttemptId : NIL_UUID;
        carrierFingerprint = carrierFingerprint == null ? "" : carrierFingerprint;
    }

    public CrossCastRequestPayload(InteractionHand hand, Action action, int selected, UUID attempt) {
        this(hand, action, selected, attempt, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Runs on the server main thread (NeoForge payload handlers do by default, so
     * there is no {@code enqueueWork} wrapper as there was under SimpleChannel).
     */
    public static void handleOnServer(CrossCastRequestPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sender) || payload.hand() != InteractionHand.MAIN_HAND
            || payload.action() == null || !sender.isAlive() || sender.isSpectator() || sender.isSleeping()
            || sender.containerMenu != sender.inventoryMenu) {
            return;
        }
        if (NetworkRequestGuard.admit(sender, payload.clientAttemptId()) != RequestAdmission.Result.ACCEPTED) return;
        ItemStack stack = sender.getItemInHand(payload.hand());
        if (payload.carrierFingerprint().isEmpty()
            || !payload.carrierFingerprint().equals(CarrierFingerprint.of(stack))) {
            sender.displayClientMessage(Component.translatable("arsnspells.crosscast.stale_carrier"), true);
            sender.inventoryMenu.broadcastChanges();
            return;
        }
        UUID serverAttemptId = UUID.randomUUID();

        CrossCastTrace.log(serverAttemptId, sender, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.REQUEST_RECEIVED,
            "hand", payload.hand(),
            "action", payload.action(),
            "clientIndex", payload.clientSelectedIndex(),
            "clientAttempt", payload.clientAttemptId(),
            "item", stack.getItem());

        CrossCastingHandler.serverHandleCast(sender, stack, payload.hand(), payload.action(), serverAttemptId);
    }
}
