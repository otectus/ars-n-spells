package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.*;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.*;
import com.otectus.arsnspells.spell.*;
import io.redspace.ironsspellbooks.api.events.*;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.*;
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
        /** A shortage in {@code unit}; the amounts are reported to the caster, never guessed. */
        public static Outcome shortage(UUID id, ResourceUnit unit, double required, double available) {
            return new Outcome(false, false, false, "refused", NativePayment.refused(id,
                TransactionSnapshot.Reason.INSUFFICIENT_RESOURCE, unit, required, available));
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
                        ResourceAmount alternative, PaymentOpenFailurePolicy failurePolicy,
                        double arsMax, double ironsMax, boolean charge, boolean deathOnInsufficientLP,
                        ResourceAccess alternativeAccess, CompatibilityStatus compatibility, double lpScale, int lpMinimum, double blasphemy) {}

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
        double rarityScale = switch (spell.getRarity(level).name()) {
            case "UNCOMMON" -> AnsConfig.IRONS_LP_UNCOMMON_MULTIPLIER.get();
            case "RARE" -> AnsConfig.IRONS_LP_RARE_MULTIPLIER.get();
            case "EPIC" -> AnsConfig.IRONS_LP_EPIC_MULTIPLIER.get();
            case "LEGENDARY" -> AnsConfig.IRONS_LP_LEGENDARY_MULTIPLIER.get();
            default -> AnsConfig.IRONS_LP_COMMON_MULTIPLIER.get();
        };
        double lpScale = AnsConfig.IRONS_LP_BASE_MULTIPLIER.get() * (1 + level * AnsConfig.IRONS_LP_PER_LEVEL_MULTIPLIER.get()) * rarityScale;
        int minimum = AnsConfig.IRONS_LP_MINIMUM_COST.get();
        double blasphemy = SanctifiedLegacyCompat.getBlasphemyLPMultiplier(player, spell.getSchoolType().getId().toString());
        ResourceAmount alternative = null;
        if (SanctifiedLegacyCompat.isCursedRingCostPathActive(player)) {
            var rarity = spell.getRarity(level);
            int lp = SanctifiedLegacyCompat.calculateIronsLPCost(cost, level, rarity.name());
            lp = (int) Math.max(0, Math.round(lp * SanctifiedLegacyCompat.getBlasphemyLPMultiplier(
                player, spell.getSchoolType().getId().toString())));
            alternative = new ResourceAmount(ResourceUnit.LP, lp);
        }
        return new Plan(spell.getSpellId(), rules, carrier, mana, alternative, AnsConfig.getPaymentOpenFailurePolicy(),
            arsMax, ironsMax, shouldCharge(player, spell, source, data), AnsConfig.DEATH_ON_INSUFFICIENT_LP.get(),
            alternative == null ? null : AlternativeResourceAccess.lp(id -> id.equals(player.getUUID()) ? player : null),
            alternative == null ? null : AlternativeResourceAccess.lpStatus(), lpScale, minimum, blasphemy);
    }

    public static boolean shouldCharge(Player player, AbstractSpell spell, CastSource source, MagicData data) {
        // Native scrolls enter the same commit hook; a successful use is only initiation,
        // so charging Scroll.use RETURN would charge cancelled long casts.
        boolean pricedSource = source.consumesMana() || (source == CastSource.SCROLL
            && (SanctifiedLegacyCompat.isCursedRingCostPathActive(player) || "full".equals(AnsConfig.SCROLL_COST_MODE.get())));
        return pricedSource && !CrossCastNbt.isArsCrossProxyId(spell.getSpellId())
            && !SanctifiedLegacyCompat.isVirtueAuraCostPathActive(player)
            && (!player.isCreative() || io.redspace.ironsspellbooks.config.ServerConfigs.CREATIVE_MANA_COST.get())
            && !data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
    }

    public static boolean canAfford(Player player, AbstractSpell spell, int level, CastSource source, MagicData data) {
        if (!shouldCharge(player, spell, source, data)) return true;
        return affordable(player, quote(player, spell, level, source, data));
    }

    private static boolean affordable(Player player, Plan plan) {
        if (!plan.charge) return true;
        if (plan.alternative == null) return BridgeManager.canAffordQuote(player, plan.quote);
        ResourceAccess access = plan.alternativeAccess;
        return plan.failurePolicy == PaymentOpenFailurePolicy.LEGACY_OPEN
            || (plan.compatibility.isUsable() && access != null
                && (access.current(player.getUUID(), ResourceUnit.LP) >= plan.alternative.amount() || plan.deathOnInsufficientLP))
            || (plan.failurePolicy == PaymentOpenFailurePolicy.NATIVE_FALLBACK && BridgeManager.canAffordQuote(player, plan.quote));
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
     * block: Animus for NeoForge 1.21.1 checks affordability there and may top the pool up from
     * blood magic. Paying first let such a check see the pool already debited and cancel an
     * affordable cast, so the payment is taken at Iron's own write instead ({@link #settle}),
     * which it replaces. A source Iron's never charges (a scroll in "full" cost mode) has no
     * native write and pays here. The LP leg is not in any pool Iron's reads and is still
     * reserved here.
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
        if (plan.alternative != null) {
            UUID attempt = UUID.randomUUID();
            ResourceAccess access = plan.alternativeAccess;
            if (AlternativePayment.pending(player.getUUID())) return Outcome.refused(attempt, TransactionSnapshot.Reason.INCOMPLETE_COMPENSATION);
            double finalLp = event.getManaCost() <= 0 ? 0 : Math.max(0, Math.round(
                Math.max(plan.lpMinimum, Math.round(event.getManaCost() * plan.lpScale)) * plan.blasphemy));
            var payment = AlternativePayment.reserve(attempt, player.getUUID(), ResourceUnit.LP,
                finalLp, access, plan.compatibility, plan.failurePolicy);
            if (payment.allowsCast()) {
                IronsCastLifecycle.current().alternativeId = attempt;
                IronsCastLifecycle.current().alternativeAccess = access;
                // LP pays; Iron's mana write is suppressed, so nothing is taken from the mana pool.
                stateNativeCost(event, null);
                return new Outcome(true, true, plan.carrier != CarrierPolicy.NATIVE_ONLY, "lp:" + payment.outcome(), null, payment);
            }
            if (payment.leg() != null) return Outcome.refused(attempt, TransactionSnapshot.Reason.INCOMPLETE_COMPENSATION);
            if (plan.deathOnInsufficientLP && plan.compatibility.isUsable()) {
                ArsCastPayments.applyDeathPenalty(player);
                stateNativeCost(event, null);
                return new Outcome(true, true, plan.carrier != CarrierPolicy.NATIVE_ONLY, "lp", null);
            }
            if (plan.failurePolicy != PaymentOpenFailurePolicy.NATIVE_FALLBACK) {
                // The LP leg is what failed; reporting the Iron's-mana origin here sent every
                // ring shortage into the logs as an Iron's mana shortage.
                return Outcome.shortage(attempt, ResourceUnit.LP, finalLp, observed(access, player, ResourceUnit.LP));
            }
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
     * listeners agreed on, and an exempt or LP-paid cast takes nothing. Rounded down, so such a
     * check is never stricter than the payment itself.
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

    private static double observed(ResourceAccess access, Player player, ResourceUnit unit) {
        if (access == null) return Double.NaN;
        try { return access.current(player.getUUID(), unit); }
        catch (RuntimeException unavailable) { return Double.NaN; }
    }

    /**
     * Whether ANS, rather than the native ticker's base-cost forecast, decides when this
     * channel runs out of resources. Null leaves the native forecast in place: exempt casts
     * (creative, recasts, proxies, virtue aura) keep Iron's own arithmetic.
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
    @SubscribeEvent public void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        clear(MagicData.getPlayerMagicData(event.getEntity()));
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) IronsCastLifecycle.logout(player);
    }
    @SubscribeEvent public void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        clear(MagicData.getPlayerMagicData(event.getOriginal()));
        clear(MagicData.getPlayerMagicData(event.getEntity()));
        if (event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer old) IronsCastLifecycle.logout(old);
    }
    @SubscribeEvent public void stop(net.minecraftforge.event.server.ServerStoppingEvent event) { PLANS.clear(); IronsCastLifecycle.stop(); }
    public static boolean isCrossCast(Player player) {
        Plan plan = PLANS.get(MagicData.getPlayerMagicData(player));
        return plan != null && plan.carrier != CarrierPolicy.NATIVE_ONLY;
    }
}
