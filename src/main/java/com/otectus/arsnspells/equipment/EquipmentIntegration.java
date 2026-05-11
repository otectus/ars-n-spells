package com.otectus.arsnspells.equipment;

import net.minecraft.world.entity.player.Player;

/**
 * STUB — EquipmentIntegration in 1.20.1 read AN's {@code IManaEquipment}
 * implementations across the player's armor + curio slots, scaled the
 * results, and translated the deltas into Iron's {@code MAX_MANA} /
 * {@code MANA_REGEN} attribute modifiers. NeoForge 1.21.1's
 * {@code AttributeModifier} ctor signature changed (UUID → ResourceLocation),
 * AN 5.x reshaped {@code IManaEquipment}, and curios changed slot iteration.
 *
 * Until Phase 11: armor / curios with mana boosts have no cross-mod
 * effect. Players relying on Ars armor enchants for Iron's mana scaling
 * see only the native AN side until restored.
 */
public class EquipmentIntegration {
    private EquipmentIntegration() {}

    public static void recomputeFor(Player player) {
        // TODO(Phase 11): re-add IManaEquipment scan + Iron's attribute modifiers.
    }

    public static void clearAll(Player player) {
        // TODO(Phase 11)
    }
}
