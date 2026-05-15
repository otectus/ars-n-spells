package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Immutable per-attempt cast context. Threads the attempt UUID, player,
 * descriptor, mode, and cost breakdown through the validator -> cost
 * resolver -> upstream cast pipeline.
 *
 * <p>Introduced in 2.0.0 as the value-type foundation for Phase 3 work.
 * Existing call sites still pass individual parameters; migration onto
 * {@code CastContext} is tracked for 2.1.0.
 *
 * <p>Note: {@link CrossCastContext} is the *runtime* in-flight registry
 * keyed by player UUID; {@code CastContext} is the *value type* describing a
 * single attempt. Different concerns, intentionally distinct types.
 */
public record CastContext(
    UUID attemptId,
    ServerPlayer player,
    InteractionHand hand,
    ItemStack sourceStack,
    SpellDescriptor descriptor,
    ManaUnificationMode mode,
    CrossCastCostResolver.CostBreakdown costs
) {

    public static CastContext begin(UUID attemptId, ServerPlayer player, InteractionHand hand,
        ItemStack sourceStack, SpellDescriptor descriptor, ManaUnificationMode mode) {
        return new CastContext(attemptId, player, hand, sourceStack, descriptor, mode, null);
    }

    public CastContext withCosts(CrossCastCostResolver.CostBreakdown breakdown) {
        return new CastContext(attemptId, player, hand, sourceStack, descriptor, mode, breakdown);
    }
}
