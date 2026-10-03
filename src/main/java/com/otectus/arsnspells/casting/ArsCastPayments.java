package com.otectus.arsnspells.casting;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.*;
import com.otectus.arsnspells.spell.CrossCastContext;
import com.otectus.arsnspells.spell.CrossSpellType;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Quotes belong to one native context; payments begin only after the cancellable cast event. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "ars_n_spells")
public final class ArsCastPayments {
    private static final Map<SpellContext, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<SpellContext, Player> RESERVED = new IdentityHashMap<>();
    private ArsCastPayments() {}

    private static final class Plan {
        final UUID id = UUID.randomUUID();
        final CostQuote quote;
        CastAttempt attempt;
        ResourceAccess access;
        boolean prepared;
        boolean committed;
        List<ResourceAmount> reserved = List.of();
        Plan(CostQuote quote) { this.quote = quote; }
    }

    public static void quoteEvent(SpellCostCalcEvent event) {
        if (event.context == null || !(event.context.getUnwrappedCaster() instanceof Player player)
                || player.level().isClientSide()) return;
        var crossContext = CrossCastContext.peek(player);
        boolean cross = crossContext != null && crossContext.type == CrossSpellType.ARS_NOUVEAU;
        if (!cross && !BridgeManager.isUnificationEnabled()) return;
        Plan plan = PLANS.get(event.context);
        if (plan == null) {
            var carrier = cross ? CarrierPolicy.REUSABLE_BOOK_SEMANTICS : CarrierPolicy.NATIVE_ONLY;
            CostQuote quote = QuoteService.quote(player, ResourceUnit.ARS_MANA, Math.max(0, event.currentCost),
                QuoteService.currentRules(), carrier);
            plan = new Plan(quote);
            PLANS.put(event.context, plan);
        }
        // Ars's integer event remains in Ars spell units. The authoritative split lives
        // in the typed quote; enoughMana and spend read it directly instead of re-converting.
        if (cross) {
            double multiplier = plan.quote.breakdown().stream()
                .filter(modifier -> modifier.id().equals("cross_cast_multiplier"))
                .mapToDouble(QuoteModifier::factorOrDelta).findFirst().orElse(1);
            event.currentCost = (int) Math.min(Integer.MAX_VALUE, Math.round(plan.quote.origin().amount() * multiplier));
        }
    }

    public static boolean isCrossCast(SpellContext context) {
        Plan plan = PLANS.get(context);
        return plan != null && plan.quote.breakdown().stream().anyMatch(modifier -> modifier.id().equals("cross_cast_multiplier"));
    }

    public static boolean handles(SpellContext context) { return context != null && PLANS.containsKey(context); }

    public static boolean canAfford(Player player, SpellResolver resolver) {
        int nativeCost = resolver.getResolveCost();
        Plan plan = PLANS.get(resolver.spellContext);
        if (player.isCreative()) return true;
        if (plan == null) return BridgeManager.getNativeArsBridge().getMana(player) >= nativeCost;
        if (plan.prepared) return true;
        return BridgeManager.canAffordQuote(player, plan.quote);
    }

    public static boolean prepare(Player player, SpellContext context) {
        if (!PaymentRecovery.available() || CastLedger.ledger().openFor(player.getUUID()).stream()
                .anyMatch(a -> a.state().isTerminal() && !a.isReleased())) return false;
        Plan plan = PLANS.get(context);
        if (plan == null || player.isCreative() || plan.prepared) return true;
        if (!BridgeManager.canAffordQuote(player, plan.quote)) return false;
        plan.access = CastLedger.forPlayer(player);
        plan.attempt = CastLedger.open(player.getUUID(), "ars-context:" + plan.id, 0, plan.quote,
            player.level().getGameTime());
        List<ResourceAmount> reserved = CastLedger.reserve(plan.attempt, plan.access);
        if (!plan.attempt.paymentAccepted()) {
            CastLedger.fail(plan.attempt, plan.access);
            return false;
        }
        plan.prepared = true;
        plan.reserved = reserved;
        RESERVED.put(context, player);
        return true;
    }

    /** True means ANS owns this exact expenditure, so only this native subtraction is suppressed. */
    public static boolean commit(Player player, SpellContext context) {
        Plan plan = PLANS.get(context);
        if (plan == null) return false;
        if (player.isCreative() || plan.committed) return true;
        if (!plan.prepared) return false;
        CastLedger.commitAndComplete(plan.attempt);
        plan.committed = true;
        return true;
    }

    public static void finish(SpellContext context) {
        Plan plan = PLANS.remove(context);
        RESERVED.remove(context);
        if (plan == null || plan.committed) return;
        if (plan.attempt != null && !plan.attempt.state().isTerminal()) CastLedger.cancel(plan.attempt, plan.access);
    }


    /** A prepared Ars invocation still open at END exited by exception before its RETURN hook. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void releaseAbortedCalls(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (SpellContext context : List.copyOf(RESERVED.keySet())) finish(context);
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        for (var entry : List.copyOf(RESERVED.entrySet())) {
            if (entry.getValue().getUUID().equals(event.getEntity().getUUID())) finish(entry.getKey());
        }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (SpellContext context : List.copyOf(RESERVED.keySet())) finish(context);
        PLANS.clear();
    }
}
