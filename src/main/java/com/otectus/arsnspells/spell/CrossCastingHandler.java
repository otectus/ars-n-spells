package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.IronsCompat;
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
 * spell up by id in {@link SpellRegistry#REGISTRY} and dispatches with
 * {@link CastSource#SCROLL} semantics (instant cast, no charge animation,
 * respects mana checks).
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

    /** Strip every inscription artifact from a stack. */
    public static void clearCrossModSpells(ItemStack stack) {
        CrossModSpellComponents.clear(stack);
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
        // ANS-HIGH-004: atomic check-and-mark, not read-then-write. This event can fire more
        // than once per resolve (preview vs. actual deduction), and the non-atomic version
        // let two overlapping cross-casts both pass the check and both apply the premium.
        if (!entry.tryMarkMultiplierApplied()) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        boolean unified = BridgeManager.isUnificationEnabled();
        float multiplier = (float) Math.max(0.0, AnsConfig.CROSS_CAST_COST_MULTIPLIER.get());
        int baseEventCost = Math.max(0, event.currentCost);
        // Apply the cross-cast multiplier to the Ars-computed base cost first;
        // the SEPARATE-mode dual-cost split (below) then operates on the
        // already-multiplied total, matching the Iron's-side accounting where
        // the multiplier is applied before the split.
        int totalCost = Math.max(0, Math.round(baseEventCost * multiplier));

        if (unified && mode == ManaUnificationMode.SEPARATE) {
            // Same normalised split BridgeManager spends through, so the amount stamped on
            // the context entry is the amount the tail consume charges.
            double[] split = AnsConfig.dualCostSplit();
            float arsCost = (float) (totalCost * split[0]);
            float issCost = (float) (totalCost * split[1]
                * AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());

            entry.arsCost = arsCost;
            entry.issCost = issCost;
            entry.costsReady = true;

            if (!player.isCreative() && issCost > 0.0f) {
                var issBridge = BridgeManager.getSecondaryBridge();
                float issMana = issBridge != null ? issBridge.getMana(player) : 0.0f;
                if (issMana < issCost) {
                    entry.blocked = true;
                    event.currentCost = Integer.MAX_VALUE;
                    CrossCastContext.clear(player);
                    logDebug("Insufficient Iron mana for cross-cast: need {}, have {}", issCost, issMana);
                    return;
                }
                // ANS-CRIT-002: pre-consume Iron's atomically with the Ars cost-calc.
                // The previous design deferred the Iron's-side consume to the @TAIL of
                // MixinSpellResolverMana, but the TAIL silently swallowed consume failures,
                // letting Ars mana drain one-way. Consuming here makes the dual-cost
                // atomic with the sufficiency check above, and the TAIL is now a no-op
                // for entries that have already paid (issCost = 0).
                if (issBridge != null && !issBridge.consumeMana(player, issCost)) {
                    entry.blocked = true;
                    event.currentCost = Integer.MAX_VALUE;
                    CrossCastContext.clear(player);
                    logDebug("Iron mana consume failed for cross-cast: need {}, have {}", issCost, issMana);
                    return;
                }
                // ANS-HIGH-030: remember what was pre-paid so castArsSpell can
                // refund it if the Ars leg fails (insufficient Ars mana or a
                // downstream cancel). issCost must still go to 0 for the TAIL
                // mixin's already-paid contract (ANS-CRIT-002).
                entry.issPaid = issCost;
                entry.issCost = 0.0f;
            }

            event.currentCost = Math.max(0, Math.round(arsCost));
            CrossCastTrace.log(entry.attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.ARS_COST_APPLIED,
                "mode", "SEPARATE", "unified", true, "base", baseEventCost,
                "final", event.currentCost, "issSecondary", issCost);
            logDebug("Ars cross-cast (SEPARATE): base={} multiplier={} total={} ars={} iss={}",
                baseEventCost, multiplier, totalCost, arsCost, issCost);
            return;
        }

        // Non-SEPARATE (or unified=false): Ars deducts the full multiplied
        // cost from its own pool. The multiplier is the only adjustment we make.
        event.currentCost = totalCost;
        CrossCastTrace.log(entry.attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.ARS_COST_APPLIED,
            "mode", mode, "unified", unified, "base", baseEventCost, "final", totalCost);
        logDebug("[CrossCasting] Ars cross-cast cost multiplier applied: x{} -> {}", multiplier, event.currentCost);
    }

    private static void logDebug(String message, Object... args) {
        if (AnsConfig.debugEnabled()) {
            LOGGER.info(message, args);
        }
    }

    @SubscribeEvent
    public static void onArsSpellCastFailed(SpellCastEvent event) {
        // Defensive cleanup: if Ars cancels the cast entirely (e.g., validation
        // failure), drop the staged context — refunding any pre-paid Iron's
        // share (ANS-HIGH-030) — so we don't leak it into the next cast.
        if (event.isCanceled() && event.getEntity() instanceof Player player) {
            CrossCastContext.Entry entry = CrossCastContext.peek(player);
            if (entry != null && entry.type == CrossSpellType.ARS_NOUVEAU) {
                refundPrepaidIronsShare(player);
            }
        }
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
        try {
            CrossCastContext.begin(player, CrossSpellType.ARS_NOUVEAU, player.level().getGameTime());
            CrossCastContext.Entry ctxEntry = CrossCastContext.peek(player);
            if (ctxEntry != null) {
                ctxEntry.spellId = entry.spellId().toString();
                ctxEntry.attemptId = attemptId;
            }
            SpellContext context = SpellContext.fromEntity(spell, player, stack);
            SpellResolver resolver = new SpellResolver(context);
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.RESOURCE_CHECK, "spell", entry.spellId());
            if (resolver.canCast(player)) {
                CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                    CrossCastTrace.Stage.UPSTREAM_CAST_ENTER, "spell", entry.spellId());
                resolver.onCast(stack, player.level());
                CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                    CrossCastTrace.Stage.UPSTREAM_CAST_EXIT, "spell", entry.spellId());
                return true;
            }
            refundPrepaidIronsShare(player);
            return false;
        } catch (Throwable t) {
            LOGGER.warn("Ars cross-cast failed for {}: {}", entry.spellId(), t.toString());
            refundPrepaidIronsShare(player);
            return false;
        }
    }

    /**
     * ANS-HIGH-030: SEPARATE mode pre-pays the Iron's share during cost-calc
     * ({@code onArsSpellCost}). If the Ars leg then fails, compensate — matching
     * the BridgeManager rollback contract (ANS-CRIT-003). {@code take()} drains
     * the entry atomically; blocked paths that already cleared it consumed
     * nothing, so a missing entry means nothing to refund.
     */
    private static void refundPrepaidIronsShare(Player player) {
        CrossCastContext.Entry entry = CrossCastContext.take(player);
        if (entry != null && entry.issPaid > 0.0f) {
            var issBridge = BridgeManager.getSecondaryBridge();
            if (issBridge != null) {
                issBridge.addMana(player, entry.issPaid);
                logDebug("Refunded pre-paid Iron's share after failed Ars cross-cast: {}", entry.issPaid);
            } else {
                LOGGER.warn("Cross-cast failed after Iron's mana was consumed but no "
                    + "secondary bridge is available to refund {} mana", entry.issPaid);
            }
        }
    }

    /**
     * Cast the Iron's entry. Looks up the {@link AbstractSpell} from
     * {@link SpellRegistry#REGISTRY}, then dispatches
     * {@link AbstractSpell#attemptInitiateCast} with the embedded
     * {@link CastSource} (defaults to SCROLL semantics: instant, mana-checked).
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
        int level = Math.max(1, entry.level());
        CastSource source = parseCastSource(entry.castSource()).orElse(CastSource.SCROLL);

        try {
            CrossCastContext.begin(player, CrossSpellType.IRONS_SPELLBOOKS, player.level().getGameTime());
            CrossCastContext.Entry ctxEntry = CrossCastContext.peek(player);
            if (ctxEntry != null) {
                ctxEntry.spellId = entry.spellId().toString();
            }
            // attemptInitiateCast(ItemStack, int level, Level, Player, CastSource, boolean consumeMana, String slotTag)
            boolean ok = spell.attemptInitiateCast(stack, level, player.level(), player, source, true, "");
            if (!ok) {
                CrossCastContext.clear(player);
            }
            return ok;
        } catch (Throwable t) {
            LOGGER.warn("Iron's cross-cast failed for {}: {}", entry.spellId(), t.toString());
            CrossCastContext.clear(player);
            return false;
        }
    }

    private static Optional<CastSource> parseCastSource(Optional<String> s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        try {
            return Optional.of(CastSource.valueOf(s.get()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
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
