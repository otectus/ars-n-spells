package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.AnsQuotes;
import com.otectus.arsnspells.casting.AttemptLedgerService;
import com.otectus.arsnspells.casting.CarrierIdentity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.network.CrossCastRequestPayload;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.util.AdvancementUtil;
import com.otectus.arsnspells.util.ArsSpellIntegrity;
import com.otectus.arsnspells.util.CrossCastTrace;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-cast item right-click flow + Ars spell-cost interception.
 *
 * <p>On right-click of an item carrying a {@link CrossModSpellList} component:
 * <ul>
 *   <li>Sneak-right-click cycles the selected inscription forward.</li>
 *   <li>Normal right-click casts the selected inscription via either
 *       {@link SpellResolver} (Ars) or {@link AbstractSpell#attemptInitiateCast}
 *       (Iron's).</li>
 * </ul>
 *
 * <p>For Ars casts: deserialises the embedded {@link Spell} via
 * {@link Spell#CODEC} against {@link NbtOps} (the cross-cast NBT shape was
 * preserved through the data-component port). For Iron's casts: looks the
 * spell up by id in {@link SpellRegistry#REGISTRY} and dispatches with the
 * {@link CastSource} that matches the carrier's own item kind - never the one
 * serialized onto the item (audit V02).
 *
 * <p>The {@link SpellCostCalcEvent} hook applies the cross-cast cost
 * multiplier ({@link AnsConfig#CROSS_CAST_COST_MULTIPLIER}) on Ars-side casts
 * routed through {@link CrossCastContext}. Iron's-side multiplier is applied
 * by {@link CrossCastIronsHandler} on {@code SpellOnCastEvent}.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public class CrossCastingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossCastingHandler.class);

    // TTL for cross-cast context entries lives in CrossCastContext.DEFAULT_TTL_TICKS.

    // -------------------------------------------------------------------------
    //  Static API consumed by the inscribe / uninscribe rituals (unchanged).
    // -------------------------------------------------------------------------

    /** Inscribe an Ars Nouveau spell onto a stack. */
    public static void addCrossModSpell(ItemStack stack, Spell spell) {
        if (spell == null) return;
        CompoundTag arsTag = encodeArsSpell(spell);
        CrossModSpellComponents.addCrossModSpell(
            stack,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID,
            1,
            CrossSpellType.ARS_NOUVEAU,
            arsTag
        );
    }

    /**
     * Serialise a {@link Spell} via its MapCodec into the CompoundTag shape the
     * cross-cast component stores. Shared by inscription, export, and tooltip
     * paths so the payload format never diverges.
     */
    public static CompoundTag encodeArsSpell(Spell spell) {
        if (spell == null) return null;
        var encoded = Spell.CODEC.codec().encodeStart(NbtOps.INSTANCE, spell).result().orElse(null);
        return (encoded instanceof CompoundTag c) ? c : null;
    }

    /** Inscribe a foreign-mod spell by id + level + type. */
    public static void addCrossModSpell(ItemStack stack, ResourceLocation spellId, int spellLevel,
                                        CrossSpellType type) {
        addCrossModSpell(stack, spellId, spellLevel, type, null);
    }

    public static void addCrossModSpell(ItemStack stack, ResourceLocation spellId, int spellLevel,
                                        CrossSpellType type, CompoundTag arsSpellTag) {
        CrossModSpellComponents.addCrossModSpell(stack, spellId, spellLevel, type, arsSpellTag);
    }

    /**
     * Strip every inscription artifact from a stack (audit V20).
     *
     * <p>This is the public, user-facing entry point, so it routes to the full removal -
     * {@link IronsBookBindingUtil#removeAllArsEntries} - not to
     * {@link CrossModSpellComponents#clearPayloadOnly}. It used to be the latter, which is
     * how the name came to promise more than the body delivered: native wheel proxy slots,
     * the export marker and the schema stamp all survived it.
     *
     * <p>{@code IronsBookBindingUtil} declares no Iron's types, so naming it here is safe on
     * this class - which is an {@code @EventBusSubscriber} and therefore has every declared
     * method's signature resolved at registration.
     */
    public static void clearCrossModSpells(ItemStack stack) {
        IronsBookBindingUtil.removeAllArsEntries(stack);
    }

    // -------------------------------------------------------------------------
    //  Event handlers
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        InteractionHand hand = event.getHand();
        // Only process main hand to avoid double-casting.
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        // Payload gate: cross-cast availability is decoupled from mana unification.
        // Disabled mode still permits inscribed items to cast; only mana sharing is
        // suppressed there.
        if (!CrossModSpellComponents.has(stack)) {
            return;
        }
        // 3.0.0: Iron's spellbooks cast their bound Ars entries through Iron's own
        // right-click flow (native wheel -> ars_cross proxy spell -> castArsSpell).
        // Hijacking the click here would suppress Iron's native casting entirely.
        // isIronsSpellBook returns false when Iron's is absent, so this gate is safe
        // on Iron's-less installs.
        if (IronsBookBindingUtil.isIronsSpellBook(stack)) {
            return;
        }
        Player player = event.getEntity();

        if (player.level().isClientSide()) {
            // Client: announce intent via payload, then cancel local use prediction so
            // vanilla item-use animations and side-effects do not race with the
            // server-authoritative cast. The client resolves NOTHING itself.
            UUID clientAttempt = UUID.randomUUID();
            CrossCastRequestPayload.Action action = player.isCrouching()
                ? CrossCastRequestPayload.Action.CYCLE
                : CrossCastRequestPayload.Action.CAST;
            int clientIndex = CrossModSpellComponents.get(stack).normalizedIndex();
            PacketHandler.sendToServer(
                new CrossCastRequestPayload(hand, action, clientIndex, clientAttempt));
            CrossCastTrace.log(clientAttempt, player, CrossCastTrace.Side.C,
                CrossCastTrace.Stage.REQUEST_SENT,
                "hand", hand, "action", action, "index", clientIndex);
            event.setCanceled(true);
            return;
        }

        // Server-side: suppress vanilla right-click. The payload path executes the
        // cast; this cancel only stops native item-use running alongside it. When the
        // client cancels before sending the vanilla use packet this branch never
        // fires — it is defensive coverage for automation / NPC use / future
        // server-side triggers.
        event.setCanceled(true);
    }

    /**
     * Server-authoritative cross-cast entry point. Invoked exclusively from
     * {@link CrossCastRequestPayload#handleOnServer}. Re-reads the stack from the
     * sender's hand (no client trust), validates the payload, then dispatches to the
     * upstream runtime.
     *
     * @return true if the cast was attempted (handed off to upstream), false if the
     *         payload was empty, invalid, or rejected before handoff
     */
    public static boolean serverHandleCast(ServerPlayer player, ItemStack item, InteractionHand hand,
                                           CrossCastRequestPayload.Action action, UUID attemptId) {
        if (player == null || item == null || item.isEmpty()) {
            return false;
        }
        CrossModSpellList list = CrossModSpellComponents.get(item);
        if (list.isEmpty()) {
            return false;
        }

        int index = list.normalizedIndex();

        if (action == CrossCastRequestPayload.Action.CYCLE) {
            if (list.size() <= 1) {
                return false;
            }
            int nextIndex = (index + 1) % list.size();
            CrossModSpellComponents.setSelectedIndex(item, nextIndex);
            player.displayClientMessage(
                Component.translatable("arsnspells.crosscast.selected", nextIndex + 1, list.size())
                    .withStyle(ChatFormatting.AQUA),
                true);
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.CYCLE_APPLIED,
                "from", index, "to", nextIndex, "size", list.size());
            return true;
        }

        CrossModSpell entry = list.spells().get(index);
        CrossCastValidator.ValidationResult vr =
            CrossCastValidator.validate(entry, index, list.size());
        if (!vr.ok()) {
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.DESCRIPTOR_REJECTED,
                "reason", vr.reasonKey(), "index", index);
            player.displayClientMessage(
                Component.translatable(vr.reasonKey()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.DESCRIPTOR_VALIDATED,
            "type", entry.typeName(), "index", index, "spellId", entry.spellId());

        boolean castOk;
        if (CrossSpellType.ARS_NOUVEAU.name().equals(entry.typeName())) {
            castOk = castArsSpell(player, item, entry, attemptId);
        } else if (CrossSpellType.IRONS_SPELLBOOKS.name().equals(entry.typeName())) {
            castOk = castIronsSpell(player, item, entry, attemptId);
        } else {
            return false;
        }
        if (castOk) {
            AdvancementUtil.grant(player, "first_cross_cast");
        }
        return castOk;
    }

    /**
     * ANS-CRIT-004: runs at HIGHEST so the cross-cast multiplier applies to the unmodified
     * base cost, before any other listener rewrites {@code event.currentCost}. At default
     * priority a listener that zeroes the cost first turns the documented 1.25x premium into
     * 0x1.25 - which is how the 1.20.1 line lost the premium entirely for ring wearers.
     *
     * <p><b>Audit V01: this handler never debits.</b> Ars 5.13.1 builds a <em>fresh</em> cost
     * event on every query - {@code getResolveCost()} posts {@code SpellCostCalcEvent.Pre} and
     * {@code getExpendedCost()} posts {@code SpellCostCalcEvent.Post}, and this listener sees
     * both - so a cost query is asked and answered many times per cast. The old code guarded
     * with {@code entry.tryMarkMultiplierApplied()}, a once-per-<em>attempt</em> latch, which
     * suppressed every later event instead of pricing it: the second query returned the
     * unmultiplied base cost. Worse, the first query also pre-paid the Iron's leg, so merely
     * <em>asking</em> what a spell cost moved mana.
     *
     * <p>The fix is idempotence per <em>event</em>, not per attempt. The attempt's quote is the
     * answer, and applying an immutable quote to a freshly-constructed event object is
     * naturally repeatable: ten queries return the same price and move nothing. Payment happens
     * once, at the verified native boundary in {@code MixinSpellResolverMana}.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onArsSpellCost(SpellCostCalcEvent event) {
        if (event.context == null) {
            return;
        }
        if (!(event.context.getUnwrappedCaster() instanceof Player player)) {
            return;
        }
        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry == null || entry.type != CrossSpellType.ARS_NOUVEAU) {
            return;
        }

        int baseEventCost = Math.max(0, event.currentCost);

        // The open attempt for this carrier already holds a quote. Reuse it, so every query
        // during one cast answers identically even if the config changed mid-cast.
        CostQuote quote = AttemptLedgerService.findOpen(player, entry.carrierIdentity)
            .map(CastAttempt::quote)
            .orElse(null);
        CostRules rules = AnsQuotes.rules(player);
        if (quote == null) {
            // No open attempt: a native-wheel proxy cast, or a query outside the cross-cast
            // request path. Quote fresh; still charge nothing.
            quote = AnsQuotes.quote(baseEventCost, ResourceUnit.ARS_MANA,
                CarrierPolicy.REUSABLE_BOOK_SEMANTICS, rules);
        }

        // Stamp the priced legs onto the staging entry so the Iron's-side handler and the
        // payment boundary read the same numbers. These are values, not payments.
        entry.arsCost = AnsQuotes.legAsFloat(quote, ResourceUnit.ARS_MANA);
        entry.issCost = AnsQuotes.legAsFloat(quote, ResourceUnit.IRONS_MANA);
        entry.costsReady = true;

        event.currentCost = AnsQuotes.legAsInt(quote, ResourceUnit.ARS_MANA, rules);

        CrossCastTrace.log(entry.attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.ARS_COST_APPLIED,
            "mode", rules.modeName(), "base", baseEventCost, "final", event.currentCost,
            "issLeg", entry.issCost, "event", event.getClass().getSimpleName());
        logDebug("Ars cross-cast quote applied: base={} ars={} iss={} (query only, nothing charged)",
            baseEventCost, entry.arsCost, entry.issCost);
    }

    private static void logDebug(String message, Object... args) {
        if (AnsConfig.debugEnabled()) {
            LOGGER.info(message, args);
        }
    }

    /**
     * Defence in depth, not the success signal (audit V03).
     *
     * <p>{@code castArsSpell} now reads {@code SpellResolver.onCast}'s own boolean, which is
     * already false when {@code postEvent()} came back cancelled. This observer exists so a
     * cancellation that arrives by some other route still settles the attempt promptly rather
     * than waiting for the TTL sweep. {@code receiveCanceled = true} is required: without it a
     * listener that cancels never reaches us at all, which is why this used to miss the very
     * cancellations it was written for.
     */
    @SubscribeEvent(receiveCanceled = true)
    public static void onArsSpellCastFailed(SpellCastEvent event) {
        if (!event.isCanceled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry == null || entry.type != CrossSpellType.ARS_NOUVEAU) {
            return;
        }
        // Release is one-shot in the ledger, so settling here and again in castArsSpell's
        // finally credits the player exactly once.
        AttemptLedgerService.findOpen(player, entry.carrierIdentity)
            .ifPresent(attempt -> AttemptLedgerService.cancel(attempt, player));
        refundPrepaidIronsShare(player);
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        // ServerPlayer, not Player: CrossCastContext is server-side state keyed by UUID, and
        // on an integrated server both logical sides share the map. Ticking it for the client
        // player evicted the server player's own in-flight context using client game time.
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        CrossCastContext.cleanupExpired(player, player.level().getGameTime());
        // Audit V01 leak guard: a recast or long cast whose finish boundary never fired would
        // otherwise hold its reservation against the player forever.
        AttemptLedgerService.sweep(player.getServer(), player.level().getGameTime());
    }

    // -------------------------------------------------------------------------
    //  Cast invocation paths
    // -------------------------------------------------------------------------

    /**
     * Cast the Ars Nouveau entry. Deserialises the embedded spell via the
     * Ars 5.x {@link Spell#CODEC}, builds a {@link SpellContext} via
     * {@link SpellContext#fromEntity(Spell, net.minecraft.world.entity.LivingEntity, ItemStack)},
     * and invokes {@link SpellResolver#onCast(ItemStack, net.minecraft.world.level.Level)}.
     *
     * <p>Public so the native-wheel proxy spells
     * ({@code spell.irons.ArsCrossProxySpell}) can delegate their server-side
     * {@code onCast} into the exact same cost/multiplier/context pipeline.
     */
    public static boolean castArsSpell(Player player, ItemStack stack, CrossModSpell entry) {
        return castArsSpell(player, stack, entry, null);
    }

    /**
     * As {@link #castArsSpell(Player, ItemStack, CrossModSpell)}, carrying the trace
     * attempt id minted at payload receipt so the resolve stages can be correlated with
     * the request that started them. The proxy-spell path passes {@code null}: a
     * native-wheel cast has no cross-cast request behind it.
     */
    public static boolean castArsSpell(Player player, ItemStack stack, CrossModSpell entry, UUID attemptId) {
        Optional<CompoundTag> tag = entry.arsSpellTag();
        if (tag.isEmpty()) {
            return false;
        }
        // Checked BEFORE decoding, because decoding is where the evidence is lost: Ars 5.x
        // substitutes EffectBreak for any glyph whose mod is gone, so the decoded Spell is the
        // right length, passes isValid(), casts, and does something the player never built.
        List<String> missingGlyphs = ArsSpellIntegrity.missingGlyphIds(tag.get());
        if (!missingGlyphs.isEmpty()) {
            LOGGER.warn("Cross-mod Ars spell references {} glyph(s) that are no longer registered: {}",
                missingGlyphs.size(), missingGlyphs);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                    Component.translatable("message.ars_n_spells.crosscast.invalid.missing_glyphs",
                        ArsSpellIntegrity.describeMissing(missingGlyphs))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        // Same pre-decode discipline for glyphs that exist but must not leave their own caster
        // (see ModTags.CROSS_CAST_BLACKLIST). CrossCastValidator already refuses these on the
        // request path; this guards the proxy-spell path, which reaches castArsSpell directly.
        List<String> blacklistedGlyphs = ArsSpellIntegrity.blacklistedGlyphIds(tag.get());
        if (!blacklistedGlyphs.isEmpty()) {
            LOGGER.warn("Cross-mod Ars spell uses {} glyph(s) tagged cross_cast_blacklist: {}",
                blacklistedGlyphs.size(), blacklistedGlyphs);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                    Component.translatable("message.ars_n_spells.crosscast.invalid.blacklisted_glyphs.detail",
                        ArsSpellIntegrity.describeMissing(blacklistedGlyphs))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        Spell spell = decodeArsSpell(tag.get());
        if (spell == null || spell.size() == 0) {
            return false;
        }
        if (player.level().isClientSide()) {
            // Mark on server side only — the right-click event fires on both sides.
            return true;
        }
        // Audit V03: the attempt is opened, quoted and reserved BEFORE the upstream cast, and
        // settled in a finally. The old code opened a staging context, called onCast, threw the
        // return value away and reported true unconditionally - so a cast Ars refused, or a
        // downstream listener cancelled, still counted as a success and still left the pre-paid
        // Iron's share drained.
        CastAttempt attempt = null;
        boolean success = false;
        try {
            CrossCastContext.begin(player, CrossSpellType.ARS_NOUVEAU, player.level().getGameTime());
            CrossCastContext.Entry ctxEntry = CrossCastContext.peek(player);
            String carrierIdentity = CarrierIdentity.identityOf(stack);
            if (ctxEntry != null) {
                ctxEntry.spellId = entry.spellId().toString();
                ctxEntry.attemptId = attemptId;
                ctxEntry.carrierIdentity = carrierIdentity;
            }

            SpellContext context = SpellContext.fromEntity(spell, player, stack);
            SpellResolver resolver = new SpellResolver(context);

            // Quote once, from one config snapshot, before anything is asked or charged. Every
            // later cost query during this cast answers from this quote (audit V01).
            CostRules rules = AnsQuotes.rules(player);
            CostQuote quote = AnsQuotes.quote(resolver.getResolveCost(), ResourceUnit.ARS_MANA,
                CarrierIdentity.policyOf(stack), rules);
            attempt = AttemptLedgerService.open(player, carrierIdentity,
                CarrierIdentity.payloadRevision(stack), quote, player.level().getGameTime());

            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.RESOURCE_CHECK, "spell", entry.spellId());
            if (!resolver.canCast(player)) {
                return false;
            }

            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.UPSTREAM_CAST_ENTER, "spell", entry.spellId());
            // Ars 5.13.1.1400: SpellResolver.onCast(ItemStack, Level) returns boolean -
            // descriptor (Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Z.
            // It returns CastResolveType.wasSuccess, and false outright when its own canCast()
            // fails or when postEvent() comes back cancelled. That is the authoritative success
            // signal; the SpellCastEvent observer below is defence in depth, not the primary.
            success = resolver.onCast(stack, player.level());
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.UPSTREAM_CAST_EXIT, "spell", entry.spellId(),
                "success", success);
            return success;
        } catch (Throwable t) {
            LOGGER.warn("Ars cross-cast failed for {}: {}", entry.spellId(), t.toString());
            success = false;
            return false;
        } finally {
            // One settlement point for every exit path - normal, denied, thrown. Release is
            // idempotent, so a cancel observer that already settled this attempt makes the call
            // below a no-op rather than a second refund.
            if (success) {
                AttemptLedgerService.complete(attempt);
            } else {
                AttemptLedgerService.fail(attempt, player);
                refundPrepaidIronsShare(player);
            }
        }
    }

    /**
     * ANS-HIGH-030: drain the staging context and compensate any Iron's leg the payment boundary
     * actually paid, matching the BridgeManager rollback contract (ANS-CRIT-003).
     *
     * <p>Audit V01 retired the cost-calc prepayment, so in the normal case the ledger has already
     * released the reservation and {@code issPaid} is zero. This remains as the compensating path
     * for a leg paid outside the ledger - and, like the ledger's own release, it is one-shot,
     * because {@code take()} drains the entry.
     */
    private static void refundPrepaidIronsShare(Player player) {
        CrossCastContext.Entry entry = CrossCastContext.take(player);
        if (entry != null && entry.issPaid > 0.0f) {
            var issBridge = BridgeManager.getNativeIronsBridge();
            if (issBridge != null) {
                issBridge.addMana(player, entry.issPaid);
                logDebug("Refunded pre-paid Iron's share after failed Ars cross-cast: {}", entry.issPaid);
            } else {
                LOGGER.warn("Cross-cast failed after Iron's mana was consumed but no "
                    + "Iron's adapter is available to refund {} mana", entry.issPaid);
            }
        }
    }

    /**
     * Cast the Iron's entry. Looks up the {@link AbstractSpell} from
     * {@link SpellRegistry#REGISTRY}, then dispatches {@link AbstractSpell#attemptInitiateCast}.
     *
     * <p><b>Audit V02.</b> The {@link CastSource} used to come from the item's own serialized
     * cross-cast data, defaulting to {@link CastSource#SCROLL}. In the pinned Iron's 3.16.3 that
     * default is not neutral: {@code CastSource.consumesMana()} and
     * {@code CastSource.respectsCooldown()} both return {@code false} for {@code SCROLL},
     * {@code AbstractSpell.castSpell} only subtracts the event cost when the source consumes
     * mana, and {@code canBeCastedBy} skips the book/sword cooldown check for it and rejects
     * recast spells outright. A serialized value therefore decided, for free, whether the cast
     * cost anything at all.
     *
     * <p>The source is now derived from the carrier's item kind through {@link CarrierIdentity}
     * and mapped to a native {@code CastSource} by
     * {@link com.otectus.arsnspells.spell.irons.IronsCastSourceAdapter}, and the serialized value
     * is ignored for billing. Costs and attempt identity are established <em>before</em>
     * {@code attemptInitiateCast}, so the ledger already holds this cast's price by the time
     * Iron's asks for it.
     */
    private static boolean castIronsSpell(Player player, ItemStack stack, CrossModSpell entry,
                                          java.util.UUID attemptId) {
        if (!IronsCompat.isLoaded()) {
            return false;
        }
        AbstractSpell spell;
        try {
            spell = SpellRegistry.REGISTRY.get(entry.spellId());
        } catch (Throwable t) {
            LOGGER.warn("Iron's spell registry lookup failed for {}: {}", entry.spellId(), t.toString());
            return false;
        }
        if (spell == null) {
            return false;
        }
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.DESCRIPTOR_VALIDATED,
            "leg", "IRONS", "spellId", entry.spellId(), "level", entry.level());
        if (player.level().isClientSide()) {
            return true;
        }

        // Iron's 1.21.1-3.16.3: AbstractSpell.getLevelFor(int, LivingEntity) is the pinned
        // accessor for a spell's effective level - it folds in the Curios level bonus and posts
        // ModifySpellLevelEvent. max(1, stored) skipped both, so a cross-cast was priced and
        // resolved at a different level than the same spell cast natively.
        int level;
        try {
            level = Math.max(spell.getMinLevel(), spell.getLevelFor(entry.level(), player));
        } catch (Throwable notAvailable) {
            level = Math.max(1, entry.level());
        }

        CarrierPolicy carrier = CarrierIdentity.policyOf(stack);
        CastSource source = com.otectus.arsnspells.spell.irons.IronsCastSourceAdapter.forCarrier(carrier);

        CastAttempt attempt = null;
        boolean success = false;
        try {
            // Costs and identity BEFORE attemptInitiateCast: Iron's charges inside that call,
            // and an attempt opened afterwards has nothing to say about a payment already made.
            CostRules rules = AnsQuotes.rules(player);
            CostQuote quote = AnsQuotes.quote(spell.getManaCost(level), ResourceUnit.IRONS_MANA,
                carrier, rules);
            String carrierIdentity = CarrierIdentity.identityOf(stack);

            CrossCastContext.begin(player, CrossSpellType.IRONS_SPELLBOOKS, player.level().getGameTime());
            CrossCastContext.Entry ctxEntry = CrossCastContext.peek(player);
            if (ctxEntry != null) {
                ctxEntry.spellId = entry.spellId().toString();
                ctxEntry.attemptId = attemptId;
                ctxEntry.carrierIdentity = carrierIdentity;
                ctxEntry.arsCost = AnsQuotes.legAsFloat(quote, ResourceUnit.ARS_MANA);
                ctxEntry.issCost = AnsQuotes.legAsFloat(quote, ResourceUnit.IRONS_MANA);
                ctxEntry.costsReady = true;
            }
            attempt = AttemptLedgerService.open(player, carrierIdentity,
                CarrierIdentity.payloadRevision(stack), quote, player.level().getGameTime());

            // attemptInitiateCast(ItemStack, int level, Level, Player, CastSource, boolean consumeMana, String slotTag)
            success = spell.attemptInitiateCast(stack, level, player.level(), player, source, true, "");
            return success;
        } catch (Throwable t) {
            LOGGER.warn("Iron's cross-cast failed for {}: {}", entry.spellId(), t.toString());
            success = false;
            return false;
        } finally {
            if (success) {
                // A long or recast Iron's cast stays open past this return; the ledger keeps it
                // keyed by attempt id until the native finish boundary or the TTL sweep.
                AttemptLedgerService.commit(attempt);
                AttemptLedgerService.complete(attempt);
            } else {
                AttemptLedgerService.fail(attempt, player);
                CrossCastContext.clear(player);
            }
        }
    }

    /** Codec-decode a cross-cast Ars payload back into a {@link Spell}; null on failure. */
    public static Spell decodeArsSpell(CompoundTag tag) {
        try {
            var result = Spell.CODEC.codec().parse(NbtOps.INSTANCE, tag);
            return result.result().orElse(null);
        } catch (Throwable t) {
            LOGGER.warn("Failed to decode Ars cross-spell from tag: {}", t.toString());
            return null;
        }
    }
}
