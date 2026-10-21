package com.otectus.arsnspells.network;

import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.contract.RequestAdmission;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-thread admission and execution of one Glyph Press request. Shared by the packet and the
 * GameTests, so both take the exact same path.
 */
public final class GlyphPressRequestHandler {
    public static final int ACTION_START = 1, ACTION_CANCEL = 2;
    public static final String STALE = "stale_request", REJECTED = "request_rejected";

    private GlyphPressRequestHandler() {}

    /** @return a reason code for the client, or null when there is no open press menu to answer */
    public static String execute(ServerPlayer sender, LoomRequestContext request, int action) {
        if (sender == null || !sender.isAlive() || sender.isSpectator() || sender.isSleeping()
            || !(sender.containerMenu instanceof GlyphPressMenu menu) || menu.getBlockEntity() == null
            || !menu.stillValid(sender)) return null;
        if (NetworkRequestGuard.admit(sender, request.request()) != RequestAdmission.Result.ACCEPTED) return REJECTED;
        if (!request.matches(menu.containerId, menu.sessionId(), GlyphPressRevision.of(menu), GlyphMappingSnapshot.current().digest()))
            return STALE;
        GlyphPressBlockEntity press = menu.getBlockEntity();
        return switch (action) {
            case ACTION_START -> press.start(sender);
            case ACTION_CANCEL -> press.cancel("player");
            default -> REJECTED;
        };
    }
}
