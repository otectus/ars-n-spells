package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.*;
import com.otectus.arsnspells.spell.*;
import io.redspace.ironsspellbooks.api.events.*;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.*;
import java.util.*;

/** Native cast-state identity owns pricing across long/channel casts; no initiation-time debit. */
public final class IronsCastPayments {
    public record Outcome(boolean allowed, boolean replaced, boolean cross, String route, NativePayment.Result payment, AlternativePayment.Result alternative) {
        public Outcome(boolean allowed, boolean replaced, boolean cross, String route, NativePayment.Result payment) {
            this(allowed, replaced, cross, route, payment, null);
        }
        public static Outcome refused(UUID id, TransactionSnapshot.Reason reason) {
            return new Outcome(false, false, false, "refused", NativePayment.refused(id, reason, ResourceUnit.IRONS_MANA));
        }
        /** Priced but not yet paid: the payment is taken at Iron's native mana write. */
        static Outcome owed(boolean cross) { return new Outcome(true, true, cross, NATIVE_ROUTE, null); }
        /** Whether this invocation still owes its mana payment. */
        public boolean owed() { return allowed && NATIVE_ROUTE.equals(route) && payment == null; }
    }
    private static final String NATIVE_ROUTE = "native";
    private static Outcome exempt(Plan plan) { return new Outcome(true, true, plan.carrier != CarrierPolicy.NATIVE_ONLY, "exempt", null); }
    private static final Map<MagicData, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private record Plan(String spellId, CostRules rules, CarrierPolicy carrier, CostQuote quote,
                        double arsMax, double ironsMax, boolean charge) {}

    private static Plan quote(Player player, AbstractSpell spell, int level, CastSource source, MagicData data) {
        var context = CrossCastContext.peek(player);
        var carrier = context != null && context.type == CrossSpellType.IRONS_SPELLBOOKS
            && spell.getSpellId().equals(context.spellId) ? CarrierPolicy.REUSABLE_BOOK_SEMANTICS : CarrierPolicy.NATIVE_ONLY;
        var rules = QuoteService.currentRules();
        int cost = spell.getManaCost(level);
        double arsMax = BridgeManager.getNativeArsBridge().transactionMax(player);
        double ironsMax = BridgeManager.getNativeIronsBridge().transactionMax(player);
        CostQuote mana = StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.IRONS_MANA, cost),
            rules, carrier, arsMax, ironsMax);
        return new Plan(spell.getSpellId(), rules, carrier, mana, arsMax, ironsMax,
            shouldCharge(player, spell, source, data));
    }

    public static boolean shouldCharge(Player player, AbstractSpell spell, CastSource source, MagicData data) {
        // Native scrolls enter the same commit hook; a successful use is only initiation,
        // so charging Scroll.use RETURN would charge cancelled long casts.
        boolean pricedSource = source.consumesMana() || (source == CastSource.SCROLL
            && "full".equals(AnsConfig.SCROLL_COST_MODE.get()));
        return pricedSource && !CrossModSpellComponents.isArsCrossProxyId(spell.getSpellId())
            && (!player.isCreative() || io.redspace.ironsspellbooks.config.ServerConfigs.CREATIVE_MANA_COST.get())
            && !data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
    }

    public static boolean canAfford(Player player, AbstractSpell spell, int level, CastSource source, MagicData data) {
        if (!shouldCharge(player, spell, source, data)) return true;
        return affordable(player, quote(player, spell, level, source, data));
    }

    private static boolean affordable(Player player, Plan plan) {
        return !plan.charge || BridgeManager.canAffordQuote(player, plan.quote);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPreCast(SpellPreCastEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide() || event.isCanceled()) return;
        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        MagicData data = MagicData.getPlayerMagicData(player);
        Plan plan = quote(player, spell, event.getSpellLevel(), event.getCastSource(), data);
        // Final event listeners may discount an otherwise unaffordable cast.
        PLANS.put(data, plan);
        IronsCastLifecycle.initiated((net.minecraft.server.level.ServerPlayer) player, spell, event.getSpellLevel(), event.getCastSource());
    }

    /** Direct API callers also latch their plan before the final cost event is dispatched. */
    public static void ensurePlan(Player player, AbstractSpell spell, int level, CastSource source) {
        MagicData data = MagicData.getPlayerMagicData(player);
        PLANS.computeIfAbsent(data, ignored -> quote(player, spell, level, source, data));
    }

    /**
     * Price the invocation once every native cost listener has run. Refusals end it here.
     *
     * <p>A source Iron's charges (a spellbook or a sword) is not paid here. Iron's next runs its
     * own mana block, reading the pool and writing the debit, and other mods act inside that
     * block: Animus checks affordability there and may top the pool up from blood magic. Paying
     * first let such a check see the pool already debited and cancel an affordable cast, so the
     * payment is taken at Iron's own write instead ({@link #settle}), which it replaces. A source
     * Iron's never charges (a scroll in "full" cost mode) has no native write and pays here.
     */
    public static Outcome price(Player player, AbstractSpell spell, int level, CastSource source,
                                MagicData data, SpellOnCastEvent event) {
        TransactionSnapshot.Reason boundaryFailure = IronsCastLifecycle.boundaryFailure();
        if (boundaryFailure != null) return Outcome.refused(UUID.randomUUID(), boundaryFailure);
        if (!PaymentRecovery.available() || CastLedger.blocksPayment(player))
            return Outcome.refused(UUID.randomUUID(), TransactionSnapshot.Reason.INCOMPLETE_COMPENSATION);
        Plan plan = PLANS.get(data);
        if (plan == null || !plan.spellId.equals(spell.getSpellId())) {
            plan = quote(player, spell, level, source, data);
            PLANS.put(data, plan);
        }
        if (event.getManaCost() < 0) return Outcome.refused(UUID.randomUUID(), TransactionSnapshot.Reason.INVALID_COST);
        if (!plan.charge) {
            // Iron's own write, where it runs at all (a proxy), is suppressed for an exempt cast.
            stateNativeCost(event, null);
            return exempt(plan);
        }
        CostQuote finalQuote = plan.quote;
        if (event.getManaCost() != spell.getManaCost(level)) {
            // Upstream cost-event adjustments are the final pricing boundary. Mode and
            // configured conversion remain the initiation snapshot, never live-onCast values.
            finalQuote = StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.IRONS_MANA, Math.max(0, event.getManaCost())),
                plan.rules, plan.carrier, plan.arsMax, plan.ironsMax);
        }
        IronsCastLifecycle.Frame frame = IronsCastLifecycle.current();
        if (frame != null) frame.quote = finalQuote;
        boolean cross = plan.carrier != CarrierPolicy.NATIVE_ONLY;
        if (!source.consumesMana()) return settle(player, cross, finalQuote);
        stateNativeCost(event, finalQuote);
        return Outcome.owed(cross);
    }

    /** Take a priced payment. At Iron's native mana write the payment replaces that write. */
    public static Outcome settle(Player player, boolean cross, CostQuote quote) {
        NativePayment.Result payment = NativePayment.hold(player, quote);
        return new Outcome(payment.paid(), payment.paid(), cross, NATIVE_ROUTE, payment);
    }

    /** An owed payment whose native write another mod skipped: that decision stands and nothing is taken. */
    public static Outcome waived(Outcome owed) {
        return new Outcome(true, true, owed.cross(), "waived", null);
    }

    /**
     * Leave the event stating what ANS will take from the pool Iron's mana block reads.
     *
     * <p>The event has finished dispatching, so no listener sees this; only Iron's mana block and
     * code injected into it read the cost from here on. ANS replaces the native write, but a check
     * inside the block compares {@code MagicData.getMana()} with this cost. After a conversion rate
     * or a dual-cost split, what ANS takes from that pool differs from the Iron's-unit cost the
     * listeners agreed on, and an exempt cast takes nothing. Rounded down, so such a check is
     * never stricter than the payment itself.
     */
    private static void stateNativeCost(SpellOnCastEvent event, CostQuote quote) {
        ResourceUnit read = nativeReadUnit();
        double amount = 0;
        if (quote != null) for (ResourceAmount leg : quote.legs()) if (leg.unit() == read) amount += leg.amount();
        int stated = (int) Math.min(Integer.MAX_VALUE, Math.floor(Math.max(0, amount) + 1.0e-9));
        if (stated != event.getManaCost()) event.setManaCost(stated);
    }

    /** The pool {@code MagicData.getMana()} reads: {@code MixinIronsMagicDataMana} routes it to Ars only in ars_primary. */
    private static ResourceUnit nativeReadUnit() {
        return BridgeManager.isUnificationEnabled() && BridgeManager.getCurrentMode() == ManaUnificationMode.ARS_PRIMARY
            ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
    }

    /**
     * Whether ANS, rather than the native ticker's base-cost forecast, decides when this
     * channel runs out of resources. Null leaves the native forecast in place: exempt casts
     * (creative, recasts, proxies) keep Iron's own arithmetic.
     */
    public static Boolean ownsChannelAffordability(MagicData data) {
        Plan plan = PLANS.get(data);
        if (plan == null || !plan.charge || !plan.spellId.equals(data.getCastingSpellId())) return null;
        return Boolean.TRUE;
    }

    /**
     * Whether every leg of the pulse just paid could be paid once more from what is left.
     * An unreadable pool answers yes: the next pulse settles against the real pool and reports.
     */
    public static boolean affordsAnotherPulse(Player player, CostQuote pulse) {
        ResourceAccess access = CastLedger.forPlayer(player);
        try {
            for (ResourceAmount leg : pulse.legs()) {
                if (leg.amount() <= 0) continue;
                if (access.current(player.getUUID(), leg.unit()) < leg.amount()) return false;
            }
        } catch (RuntimeException unavailable) {
            return true;
        }
        return true;
    }

    public static void clear(MagicData data) { PLANS.remove(data); IronsCastLifecycle.reset(data); }
    @SubscribeEvent public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        clear(MagicData.getPlayerMagicData(event.getEntity()));
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) IronsCastLifecycle.logout(player);
    }
    @SubscribeEvent public void clone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        clear(MagicData.getPlayerMagicData(event.getOriginal()));
        clear(MagicData.getPlayerMagicData(event.getEntity()));
        if (event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer old) IronsCastLifecycle.logout(old);
    }
    @SubscribeEvent public void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { PLANS.clear(); IronsCastLifecycle.stop(); }
    public static boolean isCrossCast(Player player) {
        Plan plan = PLANS.get(MagicData.getPlayerMagicData(player));
        return plan != null && plan.carrier != CarrierPolicy.NATIVE_ONLY;
    }
}
