package com.otectus.arsnspells.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Wire round-trip for the Loom request, including the requested school and the set-school action. */
class SpellLoomExportPacketTest {

    private static LoomRequestContext context() {
        return new LoomRequestContext(3, UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), "b".repeat(64));
    }

    private static SpellLoomExportPacket roundTrip(SpellLoomExportPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.toBytes(buffer);
        return new SpellLoomExportPacket(buffer);
    }

    @Test
    void schoolAndAction_roundTrip() {
        SpellLoomExportPacket sent = new SpellLoomExportPacket("Bolt", "fire", "spell/heal",
            "irons_spellbooks:ice", SpellLoomExportPacket.ACTION_INSCRIBE, context());
        SpellLoomExportPacket received = roundTrip(sent);
        assertEquals(sent, received);
        assertEquals("irons_spellbooks:ice", received.school());
    }

    @Test
    void setSchoolAction_isPreserved_andUnknownActionsDegradeToPreview() {
        assertEquals(SpellLoomExportPacket.ACTION_SET_SCHOOL, roundTrip(new SpellLoomExportPacket("", "", "",
            "auto", SpellLoomExportPacket.ACTION_SET_SCHOOL, context())).action());
        assertEquals(SpellLoomExportPacket.ACTION_PREVIEW,
            new SpellLoomExportPacket("", "", "", "", 99, context()).action());
    }

    @Test
    void automatic_isAnEmptySchool() {
        SpellLoomExportPacket received = roundTrip(new SpellLoomExportPacket("", "", "", null,
            SpellLoomExportPacket.ACTION_PREVIEW, context()));
        assertEquals("", received.school());
    }

    @Test
    void overlongSchool_isRejectedAtDecode() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(""); buffer.writeUtf(""); buffer.writeUtf("");
        buffer.writeUtf("x:" + "y".repeat(SpellLoomExportPacket.MAX_SCHOOL_LENGTH));
        assertThrows(RuntimeException.class, () -> new SpellLoomExportPacket(buffer),
            "a school longer than any valid id must not be decoded");
    }
}
