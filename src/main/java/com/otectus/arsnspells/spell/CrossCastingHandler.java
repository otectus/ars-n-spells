package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.ISpellCaster;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.casting.QuoteService;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.network.CrossCastRequestPacket;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.spell.target.InteractionOrigin;
import com.otectus.arsnspells.spell.target.TargetHint;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Handles cross-mod spell casting - allows spells from one mod to be cast using items from another.
 * Examples:
 * - Cast Iron's Spellbooks spells from Ars Nouveau spellbooks
 * - Cast Ars Nouveau glyphs from Iron's Spellbooks items
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class CrossCastingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossCastingHandler.class);

    // NBT tag keys live in CrossCastNbt so the inscribe/uninscribe round-trip
    // is testable without bootstrapping Minecraft. Local aliases keep call
    // sites in this file unchanged.
    private static final String TAG_CROSS_MOD_SPELLS = CrossCastNbt.TAG_CROSS_MOD_SPELLS;
    private static final String TAG_SPELL_ID = CrossCastNbt.TAG_SPELL_ID;
    private static final String TAG_SPELL_LEVEL = CrossCastNbt.TAG_SPELL_LEVEL;
    private static final String TAG_SPELL_TYPE = CrossCastNbt.TAG_SPELL_TYPE;
    private static final String TAG_SPELL_INDEX = CrossCastNbt.TAG_SPELL_INDEX;
    private static final String TAG_ARS_SPELL = CrossCastNbt.TAG_ARS_SPELL;
    private static final String TAG_CAST_SOURCE = CrossCastNbt.TAG_CAST_SOURCE;
    private static final String CROSS_CAST_SLOT = "arsnspells:cross_cast";

    /**
     * Client sends a {@link CrossCastRequestPacket}; server suppresses vanilla
     * use. The cast itself is executed by the packet handler via
     * {@link #serverHandleCast}. This handler exists purely to detect the
     * intent on the client and to suppress vanilla right-click behavior on
     * the server when an inscribed item is in hand.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        InteractionHand hand = event.getHand();

        // Only process main hand to avoid double-casting
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }

        // Payload gate: cross-cast availability is decoupled from mana
        // unification. Disabled mode still permits inscribed items to cast;
        // only mana sharing is suppressed there.
        if (!stack.hasTag() || !stack.getTag().contains(TAG_CROSS_MOD_SPELLS)) {
            return;
        }

        // 3.0.0: an Iron's spellbook surfaces its Ars entries as registered
        // proxy spells in Iron's *native* spell wheel and casts them through
        // Iron's own right-click flow. Hijacking right-click here would suppress
        // native Iron's casting entirely, so we defer to the native flow. Only
        // generic inscribed items (which have no native cast UI) use this path.
        // isIronsSpellBook is registry-id based and returns false when Iron's is
        // absent, so this gate is safe on Iron's-less installs.
        if (IronsBookBindingUtil.isIronsSpellBook(stack)) {
            return;
        }

        if (player.level().isClientSide()) {
            // Client: announce intent via packet, then cancel local use
            // prediction so vanilla item-use animations and side-effects
            // do not race with the server-authoritative cast.
            UUID clientAttempt = UUID.randomUUID();
            CrossCastRequestPacket.Action action = player.isCrouching()
                ? CrossCastRequestPacket.Action.CYCLE
                : CrossCastRequestPacket.Action.CAST;
            int clientIndex = peekSelectedIndex(stack);
            // The client's own crosshair pick at the moment of the click. Only a hint: the
            // server picks for itself with Ars's reach rules and records disagreement.
            TargetHint hint = action == CrossCastRequestPacket.Action.CAST
                ? net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> com.otectus.arsnspells.client.CrossCastClientHints::current)
                : TargetHint.NONE;
            PacketHandler.sendToServer(new CrossCastRequestPacket(hand, action, clientIndex, clientAttempt,
                com.otectus.arsnspells.network.CarrierFingerprint.of(stack), hint));
            CrossCastTrace.log(clientAttempt, player, CrossCastTrace.Side.C,
                CrossCastTrace.Stage.REQUEST_SENT,
                "hand", hand, "action", action, "index", clientIndex, "hint", hint == null ? "none" : hint.kind());
            event.setCanceled(true);
            return;
        }

        // Server-side: suppress vanilla right-click. The packet path executes
        // the cast; this server-side cancel only prevents native item-use
        // from running alongside the cross-cast. Note: when the client cancels
        // the event before sending the vanilla use packet, this branch will
        // not fire at all — it is defensive coverage for edge cases (e.g.
        // automation, NPC use, or future server-side triggers).
        event.setCanceled(true);
    }

    /**
     * Server-authoritative cross-cast entry point. Invoked exclusively from
     * {@link CrossCastRequestPacket#handle}. Re-reads the stack from the
     * sender's hand (no client trust), validates the payload, then dispatches
     * to the upstream runtime.
     *
     * @return true if the cast was attempted (handed off to upstream), false
     *         if the payload was empty, invalid, or rejected before handoff.
     */
    public static boolean serverHandleCast(ServerPlayer player, ItemStack item, InteractionHand hand,
        CrossCastRequestPacket.Action action, UUID attemptId) {
        return serverHandleCast(player, item, hand, action, attemptId, TargetHint.NONE);
    }

    /**
     * {@link #serverHandleCast(ServerPlayer, ItemStack, InteractionHand, CrossCastRequestPacket.Action, UUID)}
     * with the client's aim at the moment of the click. The hint is only compared with the
     * server's own pick (see {@link ArsCastDispatcher}); it never chooses the target.
     */
    public static boolean serverHandleCast(ServerPlayer player, ItemStack item, InteractionHand hand,
        CrossCastRequestPacket.Action action, UUID attemptId, TargetHint hint) {
        if (player == null || item == null || item.isEmpty()) {
            return false;
        }
        if (hand != InteractionHand.MAIN_HAND || action == null || !player.isAlive()
            || player.isSpectator() || player.isSleeping() || player.containerMenu != player.inventoryMenu
            || item != player.getItemInHand(hand) || IronsBookBindingUtil.isIronsSpellBook(item)) {
            return false;
        }
        if (!item.hasTag() || !item.getTag().contains(TAG_CROSS_MOD_SPELLS)) {
            return false;
        }

        if (CrossCastNbt.schemaVersion(item.getTag()) > CrossCastNbt.SCHEMA_VERSION) {
            player.displayClientMessage(Component.translatable("arsnspells.crosscast.invalid.future_schema"), true);
            return false;
        }
        CompoundTag tag = item.getTag();
        ListTag spellList = tag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        if (spellList.isEmpty()) {
            return false;
        }

        int index = getSelectedIndex(tag, spellList.size());

        if (action == CrossCastRequestPacket.Action.CYCLE) {
            int nextIndex = (index + 1) % spellList.size();
            setSelectedIndex(tag, nextIndex);
            player.displayClientMessage(
                Component.translatable("arsnspells.crosscast.selected", nextIndex + 1, spellList.size())
                    .withStyle(net.minecraft.ChatFormatting.AQUA),
                true);
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.CYCLE_APPLIED,
                "from", index, "to", nextIndex, "size", spellList.size());
            return true;
        }

        CompoundTag spellData = spellList.getCompound(index);
        CrossCastValidator.ValidationResult vr =
            CrossCastValidator.validate(player, spellData, index, spellList.size());
        if (!vr.ok()) {
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.DESCRIPTOR_REJECTED,
                "reason", vr.reasonKey(), "index", index);
            player.displayClientMessage(
                Component.translatable(vr.reasonKey()).withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }
        CrossSpellType type = vr.resolvedType();
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.DESCRIPTOR_VALIDATED,
            "type", type, "index", index, "spellId", vr.spellId());

        boolean castOk;
        switch (type) {
            case ARS_NOUVEAU:
                // A generic carrier is held, so it is both the sidecar owner and the casting tool.
                castOk = castArsSpell(player, item, item, hand, spellData, attemptId,
                    InteractionOrigin.ITEM_USE, hint).resolved();
                break;
            case IRONS_SPELLBOOKS:
                castOk = castIronsSpell(player, item, spellData, attemptId);
                break;
            default:
                return false;
        }
        if (castOk) {
            com.otectus.arsnspells.util.AdvancementUtil.grant(player, "first_cross_cast");
        }
        return castOk;
    }

    private static int peekSelectedIndex(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        ListTag list = tag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return 0;
        int idx = tag.getInt(TAG_SPELL_INDEX);
        if (idx < 0 || idx >= list.size()) return 0;
        return idx;
    }

    /**
     * Casts the Ars spell serialized in {@code spellData}'s {@code ars_spell}
     * sub-tag using {@code item} as the casting stack. Public so the Iron's
     * native-proxy spell ({@code spell.irons.ArsCrossProxySpell}) can delegate
     * here from its {@code onCast}, reusing the exact same cross-cast cost,
     * multiplier, scaling and cooldown path as the sidecar right-click cast — no
     * parallel pipeline. Opens a {@link CrossCastContext} so {@code onArsSpellCost}
     * applies the multiplier exactly once.
     */
    public static boolean castArsSpell(Player player, ItemStack item, InteractionHand hand,
        CompoundTag spellData, UUID attemptId) {
        return castArsSpell(player, item, item, hand, spellData, attemptId, InteractionOrigin.SERVER, TargetHint.NONE)
            .resolved();
    }

    /**
     * Cast the Ars entry {@code spellData}, which belongs to {@code carrier}, through
     * {@link ArsCastDispatcher}.
     *
     * <p>{@code carrier} is the stack whose sidecar holds the entry and only names the spell;
     * {@code tool} is what the caster physically used (the held carrier, an Iron's staff or
     * scroll, or the book itself for a hotkey cast) and becomes the Ars context's caster tool.
     * The two differ for an Iron's book cast from the Curios slot or with a staff, and keeping
     * them apart is what stops a staff's or an empty hand's state standing in for the book.
     *
     * @return the dispatcher's result; {@link ArsCastDispatcher.Result#resolved()} is the success
     *         every caller grants advancements and native cooldowns on
     */
    public static ArsCastDispatcher.Result castArsSpell(Player player, ItemStack carrier, ItemStack tool,
        InteractionHand hand, CompoundTag spellData, UUID attemptId, InteractionOrigin origin, TargetHint hint) {
        ArsCastDispatcher.Result refused = new ArsCastDispatcher.Result(ArsCastDispatcher.Outcome.INVALID_SPELL,
            ArsCastDispatcher.Entry.NONE, null, true);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return refused;
        }
        ItemStack item = carrier;
        if (item == null || CrossCastNbt.schemaVersion(item.getTag()) > CrossCastNbt.SCHEMA_VERSION) {
            player.displayClientMessage(Component.translatable("arsnspells.crosscast.invalid.future_schema"), true);
            return refused;
        }
        if (!com.otectus.arsnspells.util.PayloadBudget.tag(spellData)
            || !com.otectus.arsnspells.util.PayloadBudget.name(spellData.getString(CrossCastNbt.TAG_CUSTOM_NAME))
            || !com.otectus.arsnspells.util.PayloadBudget.arsSpell(spellData.getCompound(TAG_ARS_SPELL))) {
            player.displayClientMessage(Component.translatable("arsnspells.crosscast.invalid.payload_budget"), true);
            return refused;
        }
        CompoundTag arsSpellTag = spellData.getCompound(TAG_ARS_SPELL);
        if (arsSpellTag.isEmpty()) {
            LOGGER.warn("Cross-mod Ars spell missing ars_spell tag: {}", spellData);
            return refused;
        }

        // Checked BEFORE deserializing, because deserialization is where the evidence is lost:
        // Spell.fromTag skips glyphs whose mod is no longer installed and hands back a SHORTER
        // recipe that isValid() still accepts. Without this, removing an addon silently turned
        // a bound spell into a different spell — and still charged full price for it.
        java.util.List<String> missingGlyphs =
            com.otectus.arsnspells.util.ArsSpellIntegrity.missingGlyphIds(arsSpellTag);
        if (!missingGlyphs.isEmpty()) {
            LOGGER.warn("Cross-mod Ars spell references {} glyph(s) that are no longer registered: {}",
                missingGlyphs.size(), missingGlyphs);
            player.displayClientMessage(
                Component.translatable("arsnspells.crosscast.invalid.missing_glyphs",
                    com.otectus.arsnspells.util.ArsSpellIntegrity.describeMissing(missingGlyphs))
                    .withStyle(net.minecraft.ChatFormatting.RED),
                true);
            return refused;
        }
        // Same pre-decode discipline for glyphs that exist but must not leave their own caster
        // (see ModTags.CROSS_CAST_BLACKLIST). CrossCastValidator already refuses these on the
        // request path; this guards the proxy-spell path, which reaches castArsSpell directly.
        java.util.List<String> blacklistedGlyphs =
            com.otectus.arsnspells.util.ArsSpellIntegrity.blacklistedGlyphIds(arsSpellTag);
        if (!blacklistedGlyphs.isEmpty()) {
            LOGGER.warn("Cross-mod Ars spell uses {} glyph(s) tagged cross_cast_blacklist: {}",
                blacklistedGlyphs.size(), blacklistedGlyphs);
            player.displayClientMessage(
                Component.translatable("message.ars_n_spells.crosscast.invalid.blacklisted_glyphs.detail",
                    com.otectus.arsnspells.util.ArsSpellIntegrity.describeMissing(blacklistedGlyphs))
                    .withStyle(net.minecraft.ChatFormatting.RED),
                true);
            return refused;
        }

        Spell spell = Spell.fromTag(arsSpellTag);
        // A payload written by a different Ars version deserializes to an empty/invalid
        // recipe. Fail before the context is opened so no resource is spent.
        if (!spell.isValid()) {
            LOGGER.warn("Cross-mod Ars spell deserialized to an invalid/empty recipe: {}", spellData);
            player.displayClientMessage(
                Component.translatable("arsnspells.crosscast.invalid.ars_spell_unreadable"), true);
            return refused;
        }
        // The entry's chosen school, validated now against the installed schools and the server
        // setting; null means Automatic. Both the right-click route and the native-wheel proxy
        // reach Ars only through this method, so this is the one place it is applied. It is
        // written onto the cast's own SpellContext before the resolver exists, so the cost
        // events, SpellCastEvent and every hit see it, and Ars copies it into each child
        // context, projectile and delayed event the cast spawns.
        String explicitSchool = SpellSchoolChoice.effective(CrossCastNbt.storedSchool(spellData));
        ISpellCaster caster = explicitSchool == null ? new SpellCaster(item) : new SpellCaster(item) {
            @Override
            public com.hollingsworth.arsnouveau.api.spell.SpellResolver getSpellResolver(
                    com.hollingsworth.arsnouveau.api.spell.SpellContext context,
                    net.minecraft.world.level.Level level, LivingEntity entity, InteractionHand castHand) {
                SpellSchoolChoice.stamp(context, explicitSchool);
                return super.getSpellResolver(context, level, entity, castHand);
            }
        };

        // Always mark the cast so onArsSpellCost can apply the cross-cast cost
        // multiplier (and, in SEPARATE mode, the dual-cost split). The
        // attemptId threads through CrossCastContext for trace correlation.
        CrossCastContext.beginWithAttempt(player, CrossSpellType.ARS_NOUVEAU,
            player.level().getGameTime(), attemptId);

        // ANS-MED-002: wrap upstream cast in try/finally so the CrossCastContext entry
        // is cleared even if the cast throws. Without this, an exception in any link of
        // the Ars cast chain left a stale entry in ACTIVE_CASTS for up to the TTL window,
        // contaminating the next cost-calc fire.
        ArsCastDispatcher.Result result = refused;
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.UPSTREAM_CAST_ENTER, "runtime", "ARS", "origin", origin);
        try {
            // 3.4.0: the dispatcher replaces Ars's castSpell here so the casting tool, the
            // validated target and the typed outcome are ANS's own; its target rules and
            // resolver entries are Ars's, unchanged. The resolver's own boolean decides success:
            // Ars's caster answers CONSUME even when the resolver refused the cast.
            result = ArsCastDispatcher.dispatch(new ArsCastDispatcher.Request(serverPlayer, caster,
                tool == null ? ItemStack.EMPTY : tool, hand, spell, origin, hint, attemptId));
            return result;
        } finally {
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.UPSTREAM_CAST_EXIT, "runtime", "ARS", "outcome", result.outcome());
            CrossCastContext.clear(player);
        }
    }

    private static boolean castIronsSpell(Player player, ItemStack item, CompoundTag spellData,
        UUID attemptId) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return false;
        }

        ResourceLocation spellId = ResourceLocation.tryParse(spellData.getString(TAG_SPELL_ID));
        if (spellId == null) {
            LOGGER.warn("Cross-mod Iron spell missing spell_id: {}", spellData);
            return false;
        }

        io.redspace.ironsspellbooks.api.spells.AbstractSpell spell =
            io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);

        if (spell == null) {
            LOGGER.warn("Cross-mod Iron spell not found: {}", spellId);
            return false;
        }

        int storedLevel = spellData.getInt(TAG_SPELL_LEVEL);
        if (storedLevel < 1 || storedLevel > spell.getMaxLevel()) return false;
        int castLevel = spell.getLevelFor(storedLevel, player);
        // Generic ANS carriers are reusable. Serialized COMMAND/SCROLL/MOB never bypass
        // native restrictions or payment; real native scrolls keep their own item pathway.
        var source = io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK;
        CrossCastContext.begin(player, CrossSpellType.IRONS_SPELLBOOKS,
            player.level().getGameTime(), 0, 0, spell.getSpellId(), attemptId);
        boolean accepted = false;
        try {
            accepted = spell.attemptInitiateCast(item, castLevel, player.level(), player, source, true, CROSS_CAST_SLOT);
            return accepted;
        } finally {
            CrossCastContext.clear(player);
        }
    }
    /**
     * Cost events already answered, so one event instance gets one stamp.
     *
     * <p>Idempotence is <em>per event</em>, not per attempt. That distinction is the whole
     * of V01: the old {@code tryMarkMultiplierApplied} suppressed every later <em>new</em>
     * event for the attempt, so the first query returned 200 and the second returned 100.
     * Upstream Ars builds a fresh event on every {@code getResolveCost()} call, and both
     * {@code canCast()} and {@code expendMana()} call it, so "once per attempt" was never
     * the right unit. Weak keys: an event that was collected can never be re-posted.
     */
    private static final java.util.Map<Object, Integer> ANSWERED_COST_EVENTS =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Price one Ars cost query. Repeatable, and it never debits.
     *
     * <p>This is the whole body of {@link #onArsSpellCost}; the handler below does nothing
     * but unwrap the event. Kept separate so the repeatability regression can be exercised
     * without a Minecraft bootstrap - a "fresh cost event" is exactly a fresh
     * {@code eventKey} carrying the same {@code baseCost}, which is what upstream produces.
     *
     * <p>The first query for a player and carrier quotes from the policy and opens the
     * attempt that holds the quote. Every later query reads that attempt and answers
     * identically. Nothing here moves a resource: the SEPARATE-mode Iron's share used to be
     * consumed inside this calculation, which made asking the price cost money.
     *
     * @return the Ars-pool leg, in whole units, that the event should report
     */
    public static int quoteArsCostForEvent(Object eventKey, UUID playerId, String carrierIdentity,
                                           CarrierPolicy carrier, int baseCost, CostRules rules,
                                           long gameTick) {
        Integer alreadyAnswered = ANSWERED_COST_EVENTS.get(eventKey);
        if (alreadyAnswered != null) {
            return alreadyAnswered;
        }

        CostQuote quote = CastLedger.findOpen(playerId, carrierIdentity)
            .map(CastAttempt::quote)
            .orElseGet(() -> {
                CostQuote fresh = QuoteService.quote(
                    ResourceUnit.ARS_MANA, Math.max(0, baseCost), rules, carrier);
                // payloadRevision 0: this wave changes no stack NBT, so there is no revision
                // counter to read yet. The field exists for the carrier-edited-mid-cast check
                // a later wave adds.
                CastLedger.open(playerId, carrierIdentity, 0, fresh, gameTick);
                return fresh;
            });

        long rounded = rules.rounding().apply(quote.total(ResourceUnit.ARS_MANA));
        int answer = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, rounded));
        ANSWERED_COST_EVENTS.put(eventKey, answer);
        return answer;
    }

    /**
     * ANS-CRIT-004: runs at HIGHEST so the cross-cast multiplier applies to the
     * unmodified base cost, BEFORE CursedRingHandler / VirtueRingHandler zero out
     * event.currentCost to stamp pending LP/aura. Without this, ring wearers paid
     * zero cross-cast overhead — the documented 1.25× premium silently became 0×1.25.
     *
     * <p>V01: a thin adapter over {@link #quoteArsCostForEvent}. It answers; it does not
     * charge. A native cast's event is left reporting Ars's own base cost - the conversion
     * lives on the attempt's quote and is applied when the reservation is taken, and
     * rewriting the event as well would convert it twice.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onArsSpellCost(SpellCostCalcEvent event) {
        com.otectus.arsnspells.casting.ArsCastPayments.quoteEvent(event);
    }
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player == null || event.player.level().isClientSide()) {
            return;
        }
        CrossCastContext.cleanupExpired(event.player, event.player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CrossCastContext.clear(event.getEntity());
    }

    /**
     * Add a cross-mod spell to an item
     */
    public static void addCrossModSpell(ItemStack stack, ResourceLocation spellId, int spellLevel,
        CrossSpellType type) {
        addCrossModSpell(stack, spellId, spellLevel, type, null);
    }

    /**
     * Add a cross-mod Ars spell to an item using serialized spell NBT
     */
    public static void addCrossModSpell(ItemStack stack, Spell spell) {
        if (spell == null) {
            return;
        }
        addCrossModSpell(stack, new ResourceLocation("ars_nouveau", "spell"), 1,
            CrossSpellType.ARS_NOUVEAU, spell.serialize());
    }

    /**
     * Add a cross-mod spell to an item with optional Ars spell tag.
     * Delegates to {@link CrossCastNbt} so the on-disk shape stays in sync
     * with the uninscribe path and the round-trip test.
     */
    public static void addCrossModSpell(ItemStack stack, ResourceLocation spellId, int spellLevel,
        CrossSpellType type, CompoundTag arsSpellTag) {
        CrossCastNbt.addCrossModSpellToTag(stack.getOrCreateTag(), spellId, spellLevel, type, arsSpellTag);
    }

    /**
     * Strip every cross-mod inscription artifact from an item: native wheel proxy slots,
     * the sidecar entries, the cycle index, and ANS's export marker.
     *
     * <p>Routed through {@link IronsBookBindingUtil#removeAllArsEntries(ItemStack)} rather
     * than {@link CrossCastNbt#clearCrossModSpells(ItemStack)} so this entry point cannot
     * reintroduce the orphan-wheel-slot bug: the raw NBT strip drops the sidecar that holds
     * the pool ids, leaving Iron's native slots behind as selectable no-ops.
     */
    public static void clearCrossModSpells(ItemStack stack) {
        IronsBookBindingUtil.removeAllArsEntries(stack);
    }

    /**
     * Get all cross-mod spells on an item
     */
    public static ListTag getCrossModSpells(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        if (tag.contains(TAG_CROSS_MOD_SPELLS)) {
            return tag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        }

        return new ListTag();
    }

    private static int getSelectedIndex(CompoundTag tag, int size) {
        // ANS-MED-006: read-only. The old version wrote back to NBT when the stored
        // index was out of range, which mutated the stack during what looks like a
        // pure read and produced surprising NBT churn on shared-stack reads.
        if (size <= 0) {
            return 0;
        }
        int index = tag.getInt(TAG_SPELL_INDEX);
        return (index < 0 || index >= size) ? 0 : index;
    }

    private static void setSelectedIndex(CompoundTag tag, int index) {
        if (index < 0) {
            index = 0;
        }
        tag.putInt(TAG_SPELL_INDEX, index);
    }

    /**
     * Log debug message if debug mode is enabled
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CrossCasting] [DEBUG] " + message, args);
        }
    }
}
