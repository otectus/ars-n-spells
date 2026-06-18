package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.progression.ProgressionAttributes;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Locale;

/**
 * Iron's-side mirror of {@link ProgressionHandler}. When a player casts an Iron's
 * Spellbooks spell, increment the matching school's count in {@link ProgressionData}
 * and (re-)apply the {@link ProgressionAttributes} bonus.
 *
 * <p>Iron's and Ars share storage by school name. Iron's school IDs look like
 * {@code irons_spellbooks:fire}; we use the path component ({@code "fire"}) so
 * both handlers index into the same map and the {@code <school>_spell_power}
 * attribute applied by either side targets the same Iron's attribute.
 *
 * <p>Iron's-only: must only be registered when Iron's is loaded.
 */
public class IronsProgressionHandler {

    @SubscribeEvent
    public void onIronsSpellCast(SpellOnCastEvent event) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        if (event.getEntity() == null || event.getSchoolType() == null) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation schoolId = event.getSchoolType().getId();
        if (schoolId == null) {
            return;
        }
        String school = schoolId.getPath().toLowerCase(Locale.ROOT);
        if (school.isEmpty()) {
            return;
        }
        ProgressionData data = player.getData(AttachmentTypes.PROGRESSION.get());
        data.incrementCastCount(school);
        ProgressionAttributes.applyTransientBonus(player, school, data.getBonusForSchool(school));
    }
}
