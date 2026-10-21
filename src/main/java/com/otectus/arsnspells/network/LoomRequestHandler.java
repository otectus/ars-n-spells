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
        return execute(sender, request, action, name, nature, icon, "");
    }

    /**
     * Admit and run one Loom request. {@code school} is the client's requested mechanical school
     * ({@code ""} or {@code auto} for Automatic); it is judged here against this server's
     * installed schools and {@code allow_spell_school_override}, never trusted.
     */
    public static String execute(ServerPlayer sender, LoomRequestContext request, int action,
                                 String name, String nature, String icon, String school) {
        if (sender == null || !sender.isAlive() || sender.isSpectator() || sender.isSleeping()
            || !(sender.containerMenu instanceof SpellLoomMenu menu) || menu.getBlockEntity() == null
            || !menu.stillValid(sender)) return null;
        if (NetworkRequestGuard.admit(sender, request.request()) != RequestAdmission.Result.ACCEPTED) return REJECTED;
        if (!request.matches(menu.containerId, menu.sessionId(), LoomInventoryRevision.of(menu), SchoolMappings.get().digest())) return STALE;
        if (action == SpellLoomExportPacket.ACTION_SET_SCHOOL) {
            // In place on the carrier in the target slot; validates the school itself.
            return LoomInscription.applySchoolChange(menu.getBlockEntity(), school);
        }
        if (action != SpellLoomExportPacket.ACTION_INSCRIBE && action != SpellLoomExportPacket.ACTION_CONVERT) {
            // A preview reports a school this server cannot use only where it is what would
            // stop an otherwise possible inscription; a loom missing its inputs says so first.
            String plan = LoomInscription.plan(menu.getBlockEntity()).reasonCode();
            String schoolRefusal = LoomInscription.schoolReason(school);
            boolean inscribable = InscriptionPlan.REASON_OK.equals(plan)
                || InscriptionPlan.REASON_NOT_BLANK.equals(plan)
                || InscriptionPlan.REASON_TARGET_NOT_EMPTY.equals(plan);
            return inscribable && schoolRefusal != null ? schoolRefusal : plan;
        }
        String cleanName = name == null ? "" : name.trim();
        if (!com.otectus.arsnspells.util.PayloadBudget.name(cleanName)) return REJECTED;
        String cleanNature = IconCatalog.BACKGROUNDS.contains(nature) ? nature : "";
        String cleanIcon = IconCatalog.canonical(icon);
        String reason = LoomInscription.apply(menu.getBlockEntity(), action == SpellLoomExportPacket.ACTION_CONVERT,
            cleanName, cleanNature, cleanIcon == null ? "" : cleanIcon, school);
        if (InscriptionPlan.REASON_OK.equals(reason)) com.otectus.arsnspells.util.AdvancementUtil.grant(sender, "transcribe_spell");
        return reason;
    }
}
