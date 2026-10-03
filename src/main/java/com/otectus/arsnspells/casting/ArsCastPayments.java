package com.otectus.arsnspells.casting;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.AlternativeResourceAccess;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.*;
import com.otectus.arsnspells.spell.CrossCastContext;
import com.otectus.arsnspells.spell.CrossSpellType;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Quotes belong to one native context; payments begin only after the cancellable cast event. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class ArsCastPayments {
    private static final Map<SpellContext, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<SpellContext, ResourceAmount> ALTERNATIVES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<SpellContext, Player> RESERVED = new IdentityHashMap<>();
    private ArsCastPayments() {}

    private static final class Plan {
        final UUID id = UUID.randomUUID();
        final CostQuote quote;
        final ResourceAmount alternative;
        final PaymentOpenFailurePolicy policy;
        final CompatibilityStatus compatibility;
        CastAttempt attempt;
        ResourceAccess access;
        boolean prepared;
        boolean committed;
        boolean alternativeReserved;
        boolean deathPenalty;
        List<ResourceAmount> reserved = List.of();
        final boolean deathOnInsufficientLP;
        Plan(Player player, CostQuote quote, ResourceAmount alternative) {
            this.quote = quote;
            this.alternative = alternative;
            this.policy = AnsConfig.getPaymentOpenFailurePolicy();
            this.deathOnInsufficientLP = AnsConfig.DEATH_ON_INSUFFICIENT_LP.get();
            this.access = alternative == null ? null : access(player, alternative.unit());
            this.compatibility = alternative == null ? null : status(alternative.unit());
        }
    }

    public static void alternative(SpellContext context, ResourceUnit unit, int amount) {
        ALTERNATIVES.putIfAbsent(context, new ResourceAmount(unit, Math.max(0, amount)));
    }

    public static void quoteEvent(SpellCostCalcEvent event) {
        if (event.context == null || !(event.context.getUnwrappedCaster() instanceof Player player)
                || player.level().isClientSide()) return;
        var crossContext = CrossCastContext.peek(player);
        boolean cross = crossContext != null && crossContext.type == CrossSpellType.ARS_NOUVEAU;
        if (!cross && !BridgeManager.isUnificationEnabled() && !ALTERNATIVES.containsKey(event.context)) return;
        Plan plan = PLANS.get(event.context);
        if (plan == null) {
            var carrier = cross ? CarrierPolicy.REUSABLE_BOOK_SEMANTICS : CarrierPolicy.NATIVE_ONLY;
            CostQuote quote = QuoteService.quote(player, ResourceUnit.ARS_MANA, Math.max(0, event.currentCost),
                QuoteService.currentRules(), carrier);
            plan = new Plan(player, quote, ALTERNATIVES.get(event.context));
            PLANS.put(event.context, plan);
        }
        // Ars's integer event remains in Ars spell units. The authoritative split lives
        // in the typed quote; enoughMana and spend read it directly instead of re-converting.
        if (plan.alternative != null) {
            event.currentCost = 0;
        } else if (cross) {
            double multiplier = plan.quote.breakdown().stream()
                .filter(modifier -> modifier.id().equals("cross_cast_multiplier"))
                .mapToDouble(QuoteModifier::factorOrDelta).findFirst().orElse(1);
            event.currentCost = (int) Math.min(Integer.MAX_VALUE, Math.round(plan.quote.origin().amount() * multiplier));
        }
    }

    public static boolean handles(SpellContext context) { return context != null && PLANS.containsKey(context); }
    public static boolean isCrossCast(SpellContext context) {
        Plan plan = PLANS.get(context);
        return plan != null && plan.quote.breakdown().stream().anyMatch(modifier -> modifier.id().equals("cross_cast_multiplier"));
    }

    public static boolean canAfford(Player player, SpellResolver resolver) {
        int nativeCost = resolver.getResolveCost();
        Plan plan = PLANS.get(resolver.spellContext);
        if (player.isCreative()) return true;
        if (plan == null) return BridgeManager.getNativeArsBridge().getMana(player) >= nativeCost;
        if (plan.prepared) return true;
        if (plan.alternative == null) return BridgeManager.canAffordQuote(player, plan.quote);
        ResourceAccess access = plan.access;
        return plan.policy == PaymentOpenFailurePolicy.LEGACY_OPEN
            || (plan.compatibility.isUsable() && access != null
                && (access.current(player.getUUID(), plan.alternative.unit()) >= plan.alternative.amount()
                    || (plan.deathOnInsufficientLP && plan.alternative.unit() == ResourceUnit.LP)))
            || (plan.policy == PaymentOpenFailurePolicy.NATIVE_FALLBACK && BridgeManager.canAffordQuote(player, plan.quote));
    }

    public static boolean prepare(Player player, SpellContext context) {
        if (!PaymentRecovery.available() || AlternativePayment.pending(player.getUUID()) || CastLedger.blocksPayment(player)) return false;
        Plan plan = PLANS.get(context);
        if (plan == null || player.isCreative() || plan.prepared) return true;
        if (plan.alternative != null) {
            CompatibilityStatus status = plan.compatibility;
            var result = AlternativePayment.reserve(plan.id, player.getUUID(), plan.alternative.unit(),
                plan.alternative.amount(), plan.access, status, plan.policy);
            plan.prepared = result.allowsCast();
            if (plan.prepared) {
                plan.alternativeReserved = true;
                plan.reserved = amounts(plan.alternative.unit(), result.leg() == null ? 0 : result.leg().reserved());
                RESERVED.put(context, player);
                return true;
            }
            if (result.leg() != null) return false; // Incomplete alternative compensation cannot authorize a fallback.
            if (status.isUsable() && plan.deathOnInsufficientLP && plan.alternative.unit() == ResourceUnit.LP) {
                plan.prepared = true;
                plan.deathPenalty = true;
                RESERVED.put(context, player);
                return true;
            }
            if (plan.policy != PaymentOpenFailurePolicy.NATIVE_FALLBACK) return false;
        }
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
        if (plan.alternativeReserved) {
            double paid = AlternativePayment.commit(plan.id);
            if (plan.alternative.unit() == ResourceUnit.LP && AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.ars_n_spells.lp.consumed", Math.round(paid)), true);
            }
        }
        else if (plan.attempt != null) CastLedger.commitAndComplete(plan.attempt);
        plan.committed = true;
        if (plan.deathPenalty) applyDeathPenalty(player);
        return true;
    }

    public static void finish(SpellContext context) {
        Plan plan = PLANS.remove(context);
        RESERVED.remove(context);
        ALTERNATIVES.remove(context);
        if (plan == null || plan.committed) return;
        if (plan.alternativeReserved) AlternativePayment.release(plan.id, plan.access);
        else if (plan.attempt != null && !plan.attempt.state().isTerminal()) CastLedger.cancel(plan.attempt, plan.access);
    }

    private static List<ResourceAmount> amounts(ResourceUnit unit, double amount) {
        return amount > 0 ? List.of(new ResourceAmount(unit, amount)) : List.of();
    }

    private static ResourceAccess access(Player player, ResourceUnit unit) {
        java.util.function.Function<UUID, Player> resolver = id -> id.equals(player.getUUID()) ? player : null;
        return unit == ResourceUnit.LP ? AlternativeResourceAccess.lp(resolver) : AlternativeResourceAccess.aura(resolver);
    }

    private static CompatibilityStatus status(ResourceUnit unit) {
        return unit == ResourceUnit.LP ? AlternativeResourceAccess.lpStatus() : AlternativeResourceAccess.auraStatus();
    }

    /** Preserve the explicit legacy death option, only after an accepted successful cast. */
    public static void applyDeathPenalty(Player player) {
        if (player.getServer() != null) player.getServer().tell(new net.minecraft.server.TickTask(0,
            () -> player.hurt(player.damageSources().magic(), Float.MAX_VALUE)));
    }

    /** Native Ars entry methods are synchronous. Anything still reserved at END threw before return. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void releaseAbortedCalls(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            for (SpellContext context : List.copyOf(RESERVED.keySet())) finish(context);
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        for (var entry : List.copyOf(RESERVED.entrySet())) {
            if (entry.getValue().getUUID().equals(event.getEntity().getUUID())) finish(entry.getKey());
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stop(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (SpellContext context : List.copyOf(RESERVED.keySet())) finish(context);
        PLANS.clear();
        ALTERNATIVES.clear();
    }
}
