package com.otectus.arsnspells.network;

import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.contract.RequestAdmission;
import com.otectus.arsnspells.icons.IconCatalog;
import com.otectus.arsnspells.inscription.LoomInscription;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.util.SchoolMappings;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread admission and execution shared by transport and native inventory regression tests. */
public final class LoomRequestHandler {
    public static final String STALE = "stale_request", REJECTED = "request_rejected";
    private LoomRequestHandler() {}
    public static String execute(ServerPlayer sender, LoomRequestContext request, int action,
                                 String name, String nature, String icon) {
        if (sender == null || !sender.isAlive() || sender.isSpectator() || sender.isSleeping()
            || !(sender.containerMenu instanceof SpellLoomMenu menu) || menu.getBlockEntity() == null
            || !menu.stillValid(sender)) return null;
        if (NetworkRequestGuard.admit(sender, request.request()) != RequestAdmission.Result.ACCEPTED) return REJECTED;
        if (!request.matches(menu.containerId, menu.sessionId(), LoomInventoryRevision.of(menu), SchoolMappings.get().digest())) return STALE;
        if (action != 1 && action != 2) return LoomInscription.plan(menu.getBlockEntity()).reasonCode();
        String cleanName = name == null ? "" : name.trim();
        if (!com.otectus.arsnspells.util.PayloadBudget.name(cleanName)) return REJECTED;
        String cleanNature = IconCatalog.BACKGROUNDS.contains(nature) ? nature : "";
        String cleanIcon = IconCatalog.canonical(icon);
        String reason = LoomInscription.apply(menu.getBlockEntity(), action == 2, cleanName, cleanNature, cleanIcon == null ? "" : cleanIcon);
        if (InscriptionPlan.REASON_OK.equals(reason)) com.otectus.arsnspells.util.AdvancementUtil.grant(sender, "transcribe_spell");
        return reason;
    }
}
