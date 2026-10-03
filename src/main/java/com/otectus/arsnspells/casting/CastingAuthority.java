package com.otectus.arsnspells.casting;

import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.contract.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Compatibility facade; native cast lifecycles own their immutable quote and payment. */
public final class CastingAuthority {
    private CastingAuthority() {}
    public static boolean canCastArsSpell(Player player, SpellResolver resolver) {
        if (player == null || resolver == null) return false;
        return ArsCastPayments.canAfford(player, resolver);
    }
    public static boolean canCastIronsSpell(Player player, int manaCost) {
        if (player == null) return false;
        return player.isCreative() || BridgeManager.canAffordQuote(player,
            QuoteService.quote(player, ResourceUnit.IRONS_MANA, manaCost, QuoteService.currentRules(), CarrierPolicy.NATIVE_ONLY));
    }
    public static boolean consumeIronsSpellMana(Player player, int manaCost) {
        if (player == null) return false;
        return player.isCreative() || BridgeManager.consumeQuote(player,
            QuoteService.quote(player, ResourceUnit.IRONS_MANA, manaCost, QuoteService.currentRules(), CarrierPolicy.NATIVE_ONLY));
    }
    public static float effectiveArsCost(int baseCost) {
        return effectiveCost(ResourceUnit.ARS_MANA, baseCost, QuoteService.currentRules());
    }
    public static float effectiveIronsCost(int baseCost) {
        return effectiveCost(ResourceUnit.IRONS_MANA, baseCost, QuoteService.currentRules());
    }
    /** Flat-policy scalar compatibility API. Runtime callers use the typed player-aware quote. */
    public static float effectiveCost(ResourceUnit origin, int baseCost, CostRules rules) {
        return (float) QuoteService.quoteNativeCast(origin, baseCost, rules).legs().stream()
            .mapToDouble(ResourceAmount::amount).sum();
    }
    public static void sendDenialMessage(Player player, String reason) {
        if (player != null && !player.level().isClientSide()) player.displayClientMessage(Component.literal(reason), true);
    }
}
