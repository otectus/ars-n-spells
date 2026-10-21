package com.otectus.arsnspells.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Wire round-trip for the Loom request, including the requested school and the set-school action. */
class SpellLoomExportPayloadTest {

    private static LoomRequestContext context() {
        return new LoomRequestContext(3, UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), "b".repeat(64));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static SpellLoomExportPayload roundTrip(SpellLoomExportPayload payload) {
        RegistryFriendlyByteBuf buffer = buffer();
        SpellLoomExportPayload.STREAM_CODEC.encode(buffer, payload);
        return SpellLoomExportPayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void schoolAndAction_roundTrip() {
        SpellLoomExportPayload sent = new SpellLoomExportPayload("Bolt", "fire", "spell/heal",
            "irons_spellbooks:ice", SpellLoomExportPayload.ACTION_INSCRIBE, context());
        SpellLoomExportPayload received = roundTrip(sent);
        assertEquals(sent, received);
        assertEquals("irons_spellbooks:ice", received.school());
    }

    @Test
    void setSchoolAction_isPreserved_andUnknownActionsDegradeToPreview() {
        assertEquals(SpellLoomExportPayload.ACTION_SET_SCHOOL, roundTrip(new SpellLoomExportPayload("", "", "",
            "auto", SpellLoomExportPayload.ACTION_SET_SCHOOL, context())).action());
        assertEquals(SpellLoomExportPayload.ACTION_PREVIEW,
            new SpellLoomExportPayload("", "", "", "", 99, context()).action());
    }

    @Test
    void automatic_isAnEmptySchool() {
        assertEquals("", roundTrip(new SpellLoomExportPayload("", "", "", null,
            SpellLoomExportPayload.ACTION_PREVIEW, context())).school());
    }

    @Test
    void overlongSchool_isRejectedAtDecode() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeUtf(""); buffer.writeUtf(""); buffer.writeUtf("");
        buffer.writeUtf("x:" + "y".repeat(SpellLoomExportPayload.MAX_SCHOOL_LENGTH));
        assertThrows(RuntimeException.class, () -> SpellLoomExportPayload.STREAM_CODEC.decode(buffer),
            "a school longer than any valid id must not be decoded");
    }
}
