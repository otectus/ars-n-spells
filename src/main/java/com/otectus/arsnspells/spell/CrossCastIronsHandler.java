package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.BridgeRouting;
import com.otectus.arsnspells.casting.AnsQuotes;
import com.otectus.arsnspells.casting.AttemptLedgerService;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.util.CrossCastTrace;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prices the Iron's Spellbooks side of a cast.
 *
 * <p>Audit V05: this handler used to hold its own copy of the conversion arithmetic - a
 * pool-aware {@code effectiveIronToArsRate(player)} multiplied in with {@code Math.round} - while
 * {@link com.otectus.arsnspells.casting.CastingAuthority#effectiveIronsCost} validated the same
 * cast against the <em>raw</em> config rate. Check and charge therefore differed by the ratio of
 * the two mana pools. Both now read one {@link CostQuote}, built from one {@link CostRules}
 * snapshot taken once per event, by {@link com.otectus.arsnspells.contract.StandardQuotePolicy}.
 */
public class CrossCastIronsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossCastIronsHandler.class);

    @SubscribeEvent
    public void onIronsSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        // One routing read for the whole event (audit V04). Reading the mode and the
        // unification flag separately let a mode switch land between them.
        BridgeRouting routing = BridgeManager.routing();
        boolean unified = BridgeManager.isUnificationEnabled()
            && (routing == null || routing.snapshot().isUnified());
        boolean dualCost = unified && routing != null && routing.snapshot().isDualCost();

        // An ANS proxy's own Iron's-side cost is always 0 and must stay 0: the delegated
        // Ars cast charges the real cost through onArsSpellCost. Applying the cross-cast
        // multiplier or the conversion to the proxy would bill the player twice for one
        // cast. The context entry is still drained below so lifecycle cleanup is unchanged.
        boolean isProxy = CrossModSpellComponents.isArsCrossProxyId(event.getSpellId());

        // One config snapshot for this cast, taken with the player so the Iron's-to-Ars rate is
        // the same pool-aware value the pre-cast check used.
        CostRules rules = AnsQuotes.rules(player);

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry != null && entry.type == CrossSpellType.IRONS_SPELLBOOKS) {
            // Different spell than the one we tagged: stale or interleaved cast.
            // Drop the entry and let the cast proceed without our adjustments.
            if (entry.spellId != null && !entry.spellId.equals(event.getSpellId())) {
                CrossCastContext.clear(player);
                return;
            }

            if (isProxy) {
                return;
            }

            int baseEventCost = event.getManaCost();

            if (dualCost) {
                // Cost was precomputed (multiplier already applied) in
                // CrossCastingHandler.castIronsSpell. Use it as-is so the
                // multiplier is applied exactly once.
                int issCost = Math.max(0, Math.round(entry.issCost));
                // Audit V02: do not zero this leg unless the ledger has already committed it.
                // Zeroing an uncommitted leg is how a SEPARATE cross-cast became free - the
                // event cost went to nothing and nothing else ever charged it.
                if (issCost == 0 && !AttemptLedgerService.hasCommittedLeg(
                        player, ResourceUnit.IRONS_MANA)) {
                    issCost = baseEventCost;
                }
                event.setManaCost(issCost);
                if (!player.isCreative() && entry.arsCost > 0.0f) {
                    BridgeManager.getNativeArsBridge().consumeMana(player, entry.arsCost);
                }
            } else {
                // Non-dual (or unified=false): Iron's computed the cost itself. Price the leg
                // once - the cross-cast multiplier, then the conversion the mode calls for.
                CostQuote quote = AnsQuotes.quote(baseEventCost, ResourceUnit.IRONS_MANA,
                    CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules);
                event.setManaCost(AnsQuotes.legAsInt(quote, ResourceUnit.IRONS_MANA, rules));
            }

            CrossCastTrace.log(entry.attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.IRON_COST_APPLIED,
                "spell", event.getSpellId(), "mode", rules.modeName(), "unified", unified,
                "base", baseEventCost, "final", event.getManaCost());
            if (AnsConfig.debugEnabled()) {
                LOGGER.info(
                    "[CrossCasting] [DEBUG] Iron's cross-cast spell={} mode={} unified={} baseEventCost={} finalCost={}",
                    event.getSpellId(), rules.modeName(), unified, baseEventCost, event.getManaCost());
            }

            // Clear after applying so a duplicate event fire (or a stale entry surviving
            // beyond this cast) cannot apply the multiplier twice.
            CrossCastContext.clear(player);
            return;
        }

        if (isProxy) {
            return;
        }

        // Normal Iron's cast (not via cross-cast pipeline). The cross-cast multiplier does not
        // apply here, so the leg is priced as NATIVE_ONLY; only the mode's own conversion runs.
        if (unified) {
            CostQuote quote = AnsQuotes.quote(event.getManaCost(), ResourceUnit.IRONS_MANA,
                CarrierPolicy.NATIVE_ONLY, rules);
            event.setManaCost(AnsQuotes.legAsInt(quote, ResourceUnit.IRONS_MANA, rules));
        }
    }
}
