package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Folds Iron's Spellbooks {@code MANA_REGEN} and {@code MAX_MANA}
 * attributes into Ars Nouveau's mana calculations when the active mode is
 * ARS_PRIMARY (Ars is the source of truth and should inherit Iron's
 * gear-driven bonuses). All cross-system regen translation goes through
 * {@link ManaRegenBridge} so the unit mismatch (Iron's % of pool vs. Ars
 * absolute mana/sec) is handled centrally.
 *
 * <p>Other modes are no-ops here: ISS_PRIMARY runs Iron's natively, HYBRID
 * shares the pool through {@code MixinManaCapability}, and SEPARATE keeps
 * the pools independent.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class ArsManaCalcHandler {

    private ArsManaCalcHandler() {}

    @SubscribeEvent
    public static void onManaRegenCalc(ManaRegenCalcEvent event) {
        if (!shouldFoldIron(event.getEntity())) {
            return;
        }
        Player player = (Player) event.getEntity();
        double ironsRegen;
        try {
            ironsRegen = player.getAttributeValue(AttributeRegistry.MANA_REGEN);
        } catch (Throwable t) {
            return;
        }
        if (ironsRegen <= 0.0) {
            return;
        }
        double absAdd = ManaRegenBridge.convertIronsToArs(ironsRegen, player);
        if (absAdd != 0.0) {
            event.setRegen(event.getRegen() + absAdd);
        }
    }

    @SubscribeEvent
    public static void onMaxManaCalc(MaxManaCalcEvent event) {
        if (!shouldFoldIron(event.getEntity())) {
            return;
        }
        Player player = (Player) event.getEntity();
        double ironsMax;
        try {
            ironsMax = player.getAttributeValue(AttributeRegistry.MAX_MANA);
        } catch (Throwable t) {
            return;
        }
        if (ironsMax <= 0.0) {
            return;
        }
        // Iron's MAX_MANA is an absolute pool size; fold it directly into Ars max.
        event.setMax(event.getMax() + (int) Math.round(ironsMax));
    }

    private static boolean shouldFoldIron(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof Player)) return false;
        if (!IronsCompat.isLoaded()) return false;
        if (!BridgeManager.isUnificationEnabled()) return false;
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        return mode == ManaUnificationMode.ARS_PRIMARY;
    }
}
