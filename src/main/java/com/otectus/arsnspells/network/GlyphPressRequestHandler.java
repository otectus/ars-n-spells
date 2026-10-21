package com.otectus.arsnspells.network;

import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.contract.RequestAdmission;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Server-thread admission and execution of Glyph Press actions, shared by the payload and the
 * GameTests. A request names one open menu session, the exact inventory it saw and the glyph
 * behaviour digest it was planned under; a duplicate request id, a replay, a stale view or a
 * closed menu does nothing. The server re-derives the spell, level, costs and result from the
 * current inputs: nothing the client sends is trusted beyond which button was pressed.
 */
public final class GlyphPressRequestHandler {
    public static final String STALE = "ars_n_spells.glyph_press.request.stale";
    public static final String REJECTED = "ars_n_spells.glyph_press.request.rejected";
    public static final String CANCELLED = "ars_n_spells.glyph_press.cancel.player";

    private GlyphPressRequestHandler() {
    }

    /** The current request revision of {@code menu}: a digest of all four slots. */
    public static String revision(GlyphPressMenu menu) {
        GlyphPressBlockEntity press = menu.press();
        if (press == null || press.getLevel() == null || menu.slots.size() < GlyphPressBlockEntity.SLOT_COUNT) return "";
        return LoomInventoryRevision.digest(List.of(menu.getSlot(0).getItem(), menu.getSlot(1).getItem(),
            menu.getSlot(2).getItem(), menu.getSlot(3).getItem()), press.getLevel().registryAccess());
    }

    /** Run one action; returns the translation key of the outcome, or null when nothing applies to this sender. */
    public static String execute(ServerPlayer sender, LoomRequestContext request, int action) {
        if (sender == null || !sender.isAlive() || sender.isSpectator() || sender.isSleeping()
            || !(sender.containerMenu instanceof GlyphPressMenu menu) || menu.press() == null || !menu.stillValid(sender)) return null;
        if (NetworkRequestGuard.admit(sender, request.request()) != RequestAdmission.Result.ACCEPTED) return REJECTED;
        if (!request.matches(menu.containerId, menu.sessionId(), revision(menu), GlyphBehavior.current().digest())) return STALE;
        GlyphPressBlockEntity press = menu.press();
        if (action == GlyphPressActionPayload.ACTION_CANCEL) {
            if (press.job() == null) return GlyphPressPlan.Reason.INSERT_SCROLL.translationKey();
            press.cancel(CANCELLED);
            return CANCELLED;
        }
        return press.inscribe(sender).translationKey();
    }
}
