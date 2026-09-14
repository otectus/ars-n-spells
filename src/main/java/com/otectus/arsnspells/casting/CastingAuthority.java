package com.otectus.arsnspells.casting;

import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.contract.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Pure affordability checks over the same final paying-unit quote used for expenditure. */
public final class CastingAuthority {
    private CastingAuthority() {}
    public static boolean canCast(Player player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }
    public static boolean canCastIronsSpell(Player player) { return canCast(player); }
    public static boolean canCastArsSpell(Player player, SpellResolver resolver) {
        if (!canCast(player) || resolver == null) return false;
        int cost = resolver.getResolveCost();
        return ArsCastPayments.handles(resolver.spellContext)
            ? ArsCastPayments.canAfford(player, resolver) : affordable(player, ResourceUnit.ARS_MANA, cost);
    }
    public static boolean canCastIronsSpell(Player player, int cost) {
        return canCast(player) && affordable(player, ResourceUnit.IRONS_MANA, cost);
    }
    private static boolean affordable(Player player, ResourceUnit origin, int cost) {
        if (player.isCreative() || cost <= 0) return true;
        CostQuote quote = QuoteService.quote(player, origin, cost, QuoteService.currentRules(), CarrierPolicy.NATIVE_ONLY);
        for (ResourceAmount leg : quote.legs()) {
            var bridge = BridgeManager.getNativeBridge(leg.unit());
            double available = bridge == null ? 0 : bridge.getMana(player);
            if (available < leg.amount()) {
                if (!player.level().isClientSide()) player.displayClientMessage(Component.translatable(
                    "arsnspells.crosscast.insufficient_resource", leg.unit() == ResourceUnit.ARS_MANA ? "Ars mana" : "Iron's mana",
                    leg.amount(), available), true);
                return false;
            }
        }
        return true;
    }
    public static float effectiveArsCost(int cost) {
        return effectiveCost(ResourceUnit.ARS_MANA, cost, QuoteService.currentRules());
    }
    public static float effectiveIronsCost(int cost) {
        return effectiveCost(ResourceUnit.IRONS_MANA, cost, QuoteService.currentRules());
    }
    public static float effectiveCost(ResourceUnit origin, int cost, CostRules rules) {
        return (float) QuoteService.quoteNativeCast(origin, Math.max(0, cost), rules).legs().stream()
            .mapToDouble(ResourceAmount::amount).sum();
    }
    public static boolean consumeIronsSpellMana(Player player, int cost) {
        if (!canCast(player)) return false;
        return player.isCreative() || cost <= 0 || BridgeManager.consumeQuote(player,
            QuoteService.quote(player, ResourceUnit.IRONS_MANA, cost, QuoteService.currentRules(), CarrierPolicy.NATIVE_ONLY));
    }
    public static void sendDenialMessage(Player player, String reason) {
        if (player != null && !player.level().isClientSide()) player.displayClientMessage(Component.literal(reason), true);
    }
}
