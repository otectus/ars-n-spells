package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.ISpellCaster;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.network.CrossCastRequestPacket;
import com.otectus.arsnspells.network.PacketHandler;
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
            PacketHandler.sendToServer(new CrossCastRequestPacket(hand, action, clientIndex, clientAttempt));
            CrossCastTrace.log(clientAttempt, player, CrossCastTrace.Side.C,
                CrossCastTrace.Stage.REQUEST_SENT,
                "hand", hand, "action", action, "index", clientIndex);
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
        if (player == null || item == null || item.isEmpty()) {
            return false;
        }
        if (!item.hasTag() || !item.getTag().contains(TAG_CROSS_MOD_SPELLS)) {
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
            Component msg = Component.literal("Cross spell selected: " + (nextIndex + 1)
                + "/" + spellList.size());
            player.displayClientMessage(msg, true);
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
            player.displayClientMessage(Component.translatable(vr.reasonKey()), true);
            return false;
        }
        CrossSpellType type = vr.resolvedType();
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.DESCRIPTOR_VALIDATED,
            "type", type, "index", index, "spellId", vr.spellId());

        switch (type) {
            case ARS_NOUVEAU:
                return castArsSpell(player, item, hand, spellData, attemptId);
            case IRONS_SPELLBOOKS:
                return castIronsSpell(player, item, spellData, attemptId);
            default:
                return false;
        }
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
        CompoundTag arsSpellTag = spellData.getCompound(TAG_ARS_SPELL);
        if (arsSpellTag.isEmpty()) {
            LOGGER.warn("Cross-mod Ars spell missing ars_spell tag: {}", spellData);
            return false;
        }

        Spell spell = Spell.fromTag(arsSpellTag);
        ISpellCaster caster = new SpellCaster(item);

        // Always mark the cast so onArsSpellCost can apply the cross-cast cost
        // multiplier (and, in SEPARATE mode, the dual-cost split). The
        // attemptId threads through CrossCastContext for trace correlation.
        CrossCastContext.beginWithAttempt(player, CrossSpellType.ARS_NOUVEAU,
            player.level().getGameTime(), attemptId);

        // ANS-MED-002: wrap upstream cast in try/finally so the CrossCastContext entry
        // is cleared even if caster.castSpell throws. Without this, an exception in
        // any link of the Ars cast chain left a stale entry in ACTIVE_CASTS for up to
        // the TTL window, contaminating the next cost-calc fire.
        boolean success = false;
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.UPSTREAM_CAST_ENTER, "runtime", "ARS");
        try {
            InteractionResultHolder<ItemStack> result = caster.castSpell(player.level(), player, hand, null, spell);
            success = result.getResult().consumesAction();
            return success;
        } finally {
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.UPSTREAM_CAST_EXIT, "runtime", "ARS", "success", success);
            if (!success) {
                CrossCastContext.clear(player);
            }
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

        int spellLevel = Math.max(1, spellData.getInt(TAG_SPELL_LEVEL));
        int castLevel = spell.getLevelFor(spellLevel, player);
        io.redspace.ironsspellbooks.api.spells.CastSource source =
            parseCastSource(spellData, io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);

        float multiplier = (float) Math.max(0.0, AnsConfig.CROSS_CAST_COST_MULTIPLIER.get());
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        boolean unified = BridgeManager.isUnificationEnabled();
        if (unified && mode == ManaUnificationMode.SEPARATE) {
            float baseCost = spell.getManaCost(castLevel);
            // Apply the cross-cast multiplier once, on the base cost, before the
            // SEPARATE-mode dual-cost split. The handler in CrossCastIronsHandler
            // reads entry.issCost as-is to avoid a second application at event time.
            float totalCost = baseCost * multiplier;
            float arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get().floatValue();
            float issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get().floatValue();
            float arsCost = (float) (totalCost * arsPercent * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get());
            float issCost = totalCost * issPercent;

            if (!player.isCreative() && arsCost > 0.0f) {
                float arsMana = BridgeManager.getBridge().getMana(player);
                if (arsMana < arsCost) {
                    logDebug("Insufficient Ars mana for cross-cast: need {}, have {}", arsCost, arsMana);
                    return false;
                }
            }

            logDebug("Iron's cross-cast (SEPARATE) {}: base={} multiplier={} total={} ars={} iss={}",
                spellId, baseCost, multiplier, totalCost, arsCost, issCost);

            CrossCastContext.begin(player, CrossSpellType.IRONS_SPELLBOOKS,
                player.level().getGameTime(), arsCost, issCost, spell.getSpellId(), attemptId);

            // ANS-MED-002: try/finally for context cleanup on exception.
            boolean success = false;
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.UPSTREAM_CAST_ENTER,
                "runtime", "IRON", "spell", spellId, "mode", mode, "unified", unified);
            try {
                success = CrossCastContext.withManaCheckOverride(player, issPercent,
                    () -> spell.attemptInitiateCast(item, castLevel, player.level(), player, source, true, CROSS_CAST_SLOT));
                return success;
            } finally {
                CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                    CrossCastTrace.Stage.UPSTREAM_CAST_EXIT, "runtime", "IRON", "success", success);
                if (!success) {
                    CrossCastContext.clear(player);
                }
            }
        }

        // Non-SEPARATE (including unified=false): Iron's owns the cost
        // calculation. The CrossCastIronsHandler applies the multiplier when
        // SpellOnCastEvent fires; ARS_PRIMARY currency conversion only runs
        // when unification is enabled.
        // ANS-MED-002: try/finally for context cleanup on exception.
        CrossCastContext.begin(player, CrossSpellType.IRONS_SPELLBOOKS,
            player.level().getGameTime(), 0.0f, 0.0f, spell.getSpellId(), attemptId);
        boolean success = false;
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.UPSTREAM_CAST_ENTER,
            "runtime", "IRON", "spell", spellId, "mode", mode, "unified", unified);
        try {
            success = spell.attemptInitiateCast(item, castLevel, player.level(), player, source, true, CROSS_CAST_SLOT);
            return success;
        } finally {
            CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.UPSTREAM_CAST_EXIT, "runtime", "IRON", "success", success);
            if (!success) {
                CrossCastContext.clear(player);
            }
        }
    }

    private static io.redspace.ironsspellbooks.api.spells.CastSource parseCastSource(
        CompoundTag spellData,
        io.redspace.ironsspellbooks.api.spells.CastSource fallback) {

        String sourceName = spellData.getString(TAG_CAST_SOURCE);
        if (sourceName.isEmpty()) {
            return fallback;
        }
        try {
            return io.redspace.ironsspellbooks.api.spells.CastSource.valueOf(sourceName);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /**
     * ANS-CRIT-004: runs at HIGHEST so the cross-cast multiplier applies to the
     * unmodified base cost, BEFORE CursedRingHandler / VirtueRingHandler zero out
     * event.currentCost to stamp pending LP/aura. Without this, ring wearers paid
     * zero cross-cast overhead — the documented 1.25× premium silently became 0×1.25.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onArsSpellCost(SpellCostCalcEvent event) {
        LivingEntity caster = event.context != null ? event.context.getUnwrappedCaster() : null;
        if (!(caster instanceof Player player)) {
            return;
        }

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry == null || entry.type != CrossSpellType.ARS_NOUVEAU) {
            return;
        }

        // ANS-HIGH-004: atomic check-and-mark. The Ars cost-calc event can fire more
        // than once during a resolve (preview vs. actual deduction). compareAndSet
        // ensures exactly one caller applies the multiplier even under overlapping
        // cross-casts on different threads.
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
            float arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get().floatValue();
            float issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get().floatValue();
            float arsCost = totalCost * arsPercent;
            float issCost = (float) (totalCost * issPercent * AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());

            entry.arsCost = arsCost;
            entry.issCost = issCost;
            entry.costsReady = true;
            // ANS-HIGH-004: multiplierApplied was already set atomically at the top of
            // this handler via tryMarkMultiplierApplied. No further write needed here.

            if (!player.isCreative() && issCost > 0.0f) {
                IManaBridge issBridge = BridgeManager.getSecondaryBridge();
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
        // cost from its own pool. The multiplier is the only adjustment we
        // make. multiplierApplied was set atomically at the top.
        event.currentCost = totalCost;
        CrossCastTrace.log(entry.attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.ARS_COST_APPLIED,
            "mode", mode, "unified", unified, "base", baseEventCost, "final", totalCost);
        logDebug("Ars cross-cast ({}, unified={}): base={} multiplier={} total={}",
            mode, unified, baseEventCost, multiplier, totalCost);
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
     * Strip every cross-mod inscription artifact from an item, including the
     * cycle index. The result is bit-identical to a never-inscribed stack.
     */
    public static void clearCrossModSpells(ItemStack stack) {
        CrossCastNbt.clearCrossModSpells(stack);
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
