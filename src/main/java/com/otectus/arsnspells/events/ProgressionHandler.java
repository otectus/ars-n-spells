package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Cross-mod progression: casting Ars spells builds persistent per-school experience
 * that provides transient attribute bonuses to Iron's spell power.
 */
public class ProgressionHandler {
    /** ResourceLocation-keyed AttributeModifier id (1.21.1 NeoForge replaced UUID-keyed modifiers). */
    private static final ResourceLocation ELEMENT_XP_ID =
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "progression_element_xp");

    @SubscribeEvent
    public void onArsSpellCast(SpellCastEvent event) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            String school = SpellAnalysis.analyze(event.spell).dominantSchool();
            if (!"generic".equals(school)) {
                ProgressionData data = player.getData(AttachmentTypes.PROGRESSION.get());
                data.incrementCastCount(school);
                applyTransientBonus(player, school, data.getBonusForSchool(school));
            }
        }
    }

    /** Reapply transient bonuses on login from persisted data. */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapplyAll(player);
        }
    }

    /**
     * Reapply every per-school transient attribute bonus from the player's
     * persisted cast counts. Transient modifiers do not survive a new Player
     * entity, so this runs on login (here) and on respawn / dimension change
     * (via {@link CapabilityResyncHandler}). Idempotent — each apply removes
     * then re-adds its modifier.
     */
    public static void reapplyAll(ServerPlayer player) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        ProgressionData data = player.getData(AttachmentTypes.PROGRESSION.get());
        data.getAllCastCounts().forEach((school, count) -> {
            double bonus = data.getBonusForSchool(school);
            if (bonus > 0) {
                applyTransientBonus(player, school, bonus);
            }
        });
    }

    private static void applyTransientBonus(ServerPlayer player, String school, double bonus) {
        String attrName = school + "_spell_power";
        var attributeHolder = BuiltInRegistries.ATTRIBUTE.getHolder(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", attrName)).orElse(null);
        if (attributeHolder == null) return;

        AttributeInstance instance = player.getAttribute(attributeHolder);
        if (instance == null) return;

        instance.removeModifier(ELEMENT_XP_ID);
        if (bonus > 0) {
            instance.addTransientModifier(new AttributeModifier(
                ELEMENT_XP_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
