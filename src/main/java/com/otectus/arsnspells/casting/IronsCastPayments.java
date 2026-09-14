package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
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
    private static final Map<MagicData, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private record Plan(String spellId, CostRules rules, CarrierPolicy carrier, CostQuote quote, double arsMax, double ironsMax, boolean charge) {}

    private static Plan quote(Player player, AbstractSpell spell, int level, CastSource source, MagicData data) {
        var context = CrossCastContext.peek(player);
        var carrier = context != null && context.type == CrossSpellType.IRONS_SPELLBOOKS
            && spell.getSpellId().equals(context.spellId) ? CarrierPolicy.REUSABLE_BOOK_SEMANTICS : CarrierPolicy.NATIVE_ONLY;
        var rules = QuoteService.currentRules();
        int cost = spell.getManaCost(level);
        double arsMax = BridgeManager.getNativeArsBridge().getMaxMana(player);
        double ironsMax = BridgeManager.getNativeIronsBridge().getMaxMana(player);
        CostQuote mana = StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.IRONS_MANA, cost),
            rules, carrier, arsMax, ironsMax);
        return new Plan(spell.getSpellId(), rules, carrier, mana, arsMax, ironsMax, shouldCharge(player, spell, source, data));
    }

    public static boolean shouldCharge(Player player, AbstractSpell spell, CastSource source, MagicData data) {
        boolean pricedSource = source.consumesMana() || (source == CastSource.SCROLL && "full".equals(AnsConfig.SCROLL_COST_MODE.get()));
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
        if (!affordable(player, plan)) { event.setCanceled(true); return; }
        PLANS.put(data, plan);
    }

    /** Called after every native cost modifier ran, before native payment, cooldown, or effects. */
    public static boolean commit(Player player, AbstractSpell spell, int level, CastSource source,
                                 MagicData data, SpellOnCastEvent event) {
        Plan plan = PLANS.get(data);
        if (plan == null || !plan.spellId.equals(spell.getSpellId())) plan = quote(player, spell, level, source, data);
        if (!plan.charge) return true;
        CostQuote finalQuote = plan.quote;
        if (event.getManaCost() != spell.getManaCost(level)) {
            // Upstream cost-event adjustments are the final pricing boundary. Mode and
            // configured conversion remain the initiation snapshot, never live-onCast values.
            finalQuote = StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.IRONS_MANA, Math.max(0, event.getManaCost())),
                plan.rules, plan.carrier, plan.arsMax, plan.ironsMax);
        }
        NativePayment.Result payment = NativePayment.settle(player, finalQuote);
        if (!payment.paid()) return false;
        event.setManaCost(0);
        return true;
    }

    public static void clear(MagicData data) { PLANS.remove(data); }
    @SubscribeEvent public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        clear(MagicData.getPlayerMagicData(event.getEntity()));
    }
    @SubscribeEvent public void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { PLANS.clear(); }
    public static boolean isCrossCast(Player player) {
        Plan plan = PLANS.get(MagicData.getPlayerMagicData(player));
        return plan != null && plan.carrier != CarrierPolicy.NATIVE_ONLY;
    }
}
