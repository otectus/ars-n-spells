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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

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
        // Serialise the Spell via its MapCodec into a CompoundTag for the component payload.
        var encoded = Spell.CODEC.codec().encodeStart(NbtOps.INSTANCE, spell).result().orElse(null);
        CompoundTag arsTag = (encoded instanceof CompoundTag c) ? c : null;
        CrossModSpellComponents.addCrossModSpell(
            stack,
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "spell"),
            1,
            CrossSpellType.ARS_NOUVEAU,
            arsTag
        );
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
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!CrossModSpellComponents.has(stack)) {
            return;
        }
        Player player = event.getEntity();

        CrossModSpellList list = CrossModSpellComponents.get(stack);
        if (list.isEmpty()) {
            return;
        }

        // Sneak-right-click cycles the selected inscription index.
        if (player.isShiftKeyDown() && list.size() > 1) {
            int next = (list.normalizedIndex() + 1) % list.size();
            CrossModSpellComponents.setSelectedIndex(stack, next);
            CrossModSpell entry = list.spells().get(next);
            if (!player.level().isClientSide()) {
                player.displayClientMessage(
                    Component.literal("Selected spell " + (next + 1) + "/" + list.size() + ": " + entry.spellId())
                        .withStyle(ChatFormatting.AQUA),
                    true
                );
            }
            event.setCanceled(true);
            return;
        }

        // Non-shift right-click casts the selected entry.
        CrossModSpell entry = list.spells().get(list.normalizedIndex());
        boolean cast;
        if (CrossSpellType.ARS_NOUVEAU.name().equals(entry.typeName())) {
            cast = castArsSpell(player, stack, entry);
        } else if (CrossSpellType.IRONS_SPELLBOOKS.name().equals(entry.typeName())) {
            cast = castIronsSpell(player, stack, entry);
        } else {
            cast = false;
        }

        if (cast) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onArsSpellCost(SpellCostCalcEvent event) {
        if (event.context == null) {
            return;
        }
        if (!(event.context.getUnwrappedCaster() instanceof Player player)) {
            return;
        }
        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry == null || entry.type != CrossSpellType.ARS_NOUVEAU || entry.multiplierApplied) {
            return;
        }

        double mul = AnsConfig.CROSS_CAST_COST_MULTIPLIER.get();
        if (mul <= 0.0 || Math.abs(mul - 1.0) < 1.0e-4) {
            entry.multiplierApplied = true;
            return;
        }

        int multiplied = (int) Math.max(0, Math.round(event.currentCost * mul));
        event.currentCost = multiplied;
        entry.multiplierApplied = true;

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == ManaUnificationMode.SEPARATE) {
            double arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get();
            double issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
            entry.arsCost = (float) (multiplied * arsPercent);
            entry.issCost = (float) (multiplied * issPercent);
            event.currentCost = (int) Math.round(entry.arsCost);
        }

        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CrossCasting] Ars cross-cast cost multiplier applied: x{} -> {}", mul, event.currentCost);
        }
    }

    @SubscribeEvent
    public static void onArsSpellCastFailed(SpellCastEvent event) {
        // Defensive cleanup: if Ars cancels the cast entirely (e.g., validation
        // failure), drop the staged context so we don't leak it into the next cast.
        if (event.isCanceled() && event.getEntity() instanceof Player player) {
            CrossCastContext.Entry entry = CrossCastContext.peek(player);
            if (entry != null && entry.type == CrossSpellType.ARS_NOUVEAU) {
                CrossCastContext.clear(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) {
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
     */
    private static boolean castArsSpell(Player player, ItemStack stack, CrossModSpell entry) {
        Optional<CompoundTag> tag = entry.arsSpellTag();
        if (tag.isEmpty()) {
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
            }
            SpellContext context = SpellContext.fromEntity(spell, player, stack);
            SpellResolver resolver = new SpellResolver(context);
            if (resolver.canCast(player)) {
                resolver.onCast(stack, player.level());
                return true;
            }
            CrossCastContext.clear(player);
            return false;
        } catch (Throwable t) {
            LOGGER.warn("Ars cross-cast failed for {}: {}", entry.spellId(), t.toString());
            CrossCastContext.clear(player);
            return false;
        }
    }

    /**
     * Cast the Iron's entry. Looks up the {@link AbstractSpell} from
     * {@link SpellRegistry#REGISTRY}, then dispatches
     * {@link AbstractSpell#attemptInitiateCast} with the embedded
     * {@link CastSource} (defaults to SCROLL semantics: instant, mana-checked).
     */
    private static boolean castIronsSpell(Player player, ItemStack stack, CrossModSpell entry) {
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

    private static Spell decodeArsSpell(CompoundTag tag) {
        try {
            var result = Spell.CODEC.codec().parse(NbtOps.INSTANCE, tag);
            return result.result().orElse(null);
        } catch (Throwable t) {
            LOGGER.warn("Failed to decode Ars cross-spell from tag: {}", t.toString());
            return null;
        }
    }
}
