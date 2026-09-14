package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

/** A request describes exactly one open menu and one complete, observed inventory revision. */
public record LoomRequestContext(int menuId, UUID session, UUID request, String revision, String mappingDigest) {
    public LoomRequestContext {
        if (menuId < 0 || session == null || request == null || revision == null || mappingDigest == null
            || revision.length() > 64 || mappingDigest.length() > 64)
            throw new IllegalArgumentException("Invalid Loom request context");
    }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menuId); buffer.writeUUID(session); buffer.writeUUID(request);
        buffer.writeUtf(revision, 64); buffer.writeUtf(mappingDigest, 64);
    }
    public static LoomRequestContext read(FriendlyByteBuf buffer) {
        return new LoomRequestContext(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(64));
    }
    public boolean matches(int currentMenu, UUID currentSession, String currentRevision, String currentMapping) {
        return menuId == currentMenu && session.equals(currentSession) && revision.length() == 64
            && revision.equals(currentRevision) && mappingDigest.equals(currentMapping);
    }
}
