package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client answer to a {@link SpellLoomExportPacket}: the reason code the inscription
 * planner produced, so the Spell Loom screen can say <em>why</em> an input was refused instead
 * of only greying a button (audit V18).
 *
 * <p>The reason code is a short stable string from
 * {@link com.otectus.arsnspells.contract.InscriptionPlan} or from
 * {@link com.otectus.arsnspells.inscription.LoomInscription}. It travels here, on the wire, and
 * is never written to an item stack -- no NBT schema moves and no saved scroll changes shape.
 *
 * <p>{@code preview} distinguishes an answer to a preview request, which the screen shows as
 * "this is what would happen", from an answer to a real attempt.
 */
public class SpellLoomResultPacket {
    /** Reason codes are short fixed literals; the bound only guards a hostile sender. */
    private static final int MAX_REASON = 48;

    private final String reasonCode;
    private final boolean preview;

    public SpellLoomResultPacket(String reasonCode, boolean preview) {
        this.reasonCode = reasonCode == null ? "" : reasonCode;
        this.preview = preview;
    }

    public SpellLoomResultPacket(FriendlyByteBuf buf) {
        this.reasonCode = buf.readUtf(MAX_REASON);
        this.preview = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(reasonCode, MAX_REASON);
        buf.writeBoolean(preview);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx == null) {
            return;
        }
        ctx.enqueueWork(() ->
            // Dist-guarded like every sibling S2C packet: PLAY_TO_CLIENT registration already
            // blocks server-side delivery, and this keeps the client-only reference off the
            // dedicated server's verification path.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.otectus.arsnspells.client.screen.SpellLoomScreen.acceptResult(
                    reasonCode, preview)));
        ctx.setPacketHandled(true);
    }
}
