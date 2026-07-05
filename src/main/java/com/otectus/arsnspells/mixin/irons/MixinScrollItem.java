package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.compat.ScrollLPTracker;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts Iron's Spellbooks scroll usage to enforce resource costs.
 * Scrolls normally bypass SpellPreCastEvent/SpellOnCastEvent, so without
 * this mixin, scrolls would cast for free (no mana, LP, or aura consumed).
 *
 * <p>Cost handling is transactional: HEAD validates and stages a pending cost
 * via {@link ScrollLPTracker}, RETURN commits or rolls back based on whether
 * Iron's actually accepted the use. This prevents the pre-1.9.0 bug where
 * LP was consumed before the cast and could leave invariants broken if the
 * cast itself failed downstream.
 *
 * <p>State lives in {@link ScrollLPTracker} (a non-mixin package) because
 * Sponge Mixin forbids direct references to inner classes of a mixin.
 *
 * <p>Behavior is controlled by the {@code scroll_cost_mode} config:
 * <ul>
 *   <li><b>full</b>: Scrolls consume mana and LP like normal casting.</li>
 *   <li><b>lp_only</b>: Scrolls are mana-free but LP is still consumed for Cursed Ring wearers.</li>
 *   <li><b>free</b>: No resource cost, but LP from Cursed Ring still applies.</li>
 * </ul>
 */
@Mixin(value = io.redspace.ironsspellbooks.item.Scroll.class, remap = false)
public class MixinScrollItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinScrollItem.class);

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$validateScrollCast(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide()) {
            return;
        }
        if (player.isCreative()) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);

        SpellData spellData = null;
        try {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container != null) {
                spellData = container.getSpellAtIndex(0);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read spell data from scroll: {}", e.getMessage());
            return;
        }
        if (spellData == null) {
            return;
        }

        AbstractSpell spell = spellData.getSpell();
        int spellLevel = spellData.getLevel();
        if (spell == null) {
            return;
        }

        String scrollMode = AnsConfig.SCROLL_COST_MODE.get().toLowerCase();
        int manaCost = spell.getManaCost(spellLevel);

        // --- Cursed Ring LP path (always applies regardless of scroll_cost_mode) ---
        if (SanctifiedLegacyCompat.isAvailable() && SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            if (manaCost > 0) {
                SpellRarity rarity = spell.getRarity(spellLevel);
                if (rarity == null) {
                    LOGGER.warn("Null rarity for scroll spell {} level {} - skipping LP cost", spell.getSpellId(), spellLevel);
                    return;
                }
                int lpCost = SanctifiedLegacyCompat.calculateIronsLPCost(manaCost, spellLevel, rarity.name());

                LOGGER.debug("Scroll LP validation: spell={}, level={}, lpCost={}",
                    spell.getSpellId(), spellLevel, lpCost);

                boolean hasEnough = SanctifiedLegacyCompat.hasEnoughLP(player, lpCost);
                if (!hasEnough) {
                    if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
                        // Death mode: scroll proceeds; RETURN inject will kill the player on success.
                        ScrollLPTracker.stage(player.getUUID(), lpCost, true);
                        return;
                    }

                    // Safe mode: cancel scroll use entirely; no LP consumed.
                    LOGGER.warn("Insufficient LP for scroll - cancelling");
                    cir.setReturnValue(InteractionResultHolder.fail(stack));
                    SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);
                    if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                        player.displayClientMessage(
                            Component.translatable("message.ars_n_spells.lp.scroll_cancelled")
                                .withStyle(ChatFormatting.RED),
                            true);
                    }
                    return;
                }

                // Sufficient LP: stage the commit. Actual consumption happens in RETURN.
                ScrollLPTracker.stage(player.getUUID(), lpCost, false);
                // Scroll proceeds; LP commits at RETURN if Iron's accepts the use.
                return;
            }
        }

        // Virtue Ring aura path removed: Covenant of the Seven's own Iron's-spell
        // integration deducts aura for scroll casts natively. We no longer intercept
        // here — the previous double-payment bug went with the deletion.

        // --- Mana cost validation (based on scroll_cost_mode) ---
        if ("free".equals(scrollMode) || "lp_only".equals(scrollMode)) {
            return;
        }

        // "full" mode: validate mana like a normal spell cast, and stage the
        // consume for RETURN. ANS-MED-043: validation alone charged nothing —
        // Iron's scrolls never deduct mana natively, so "full" mode was
        // documented as costing mana but was actually free.
        if (manaCost > 0) {
            boolean canAfford = com.otectus.arsnspells.casting.CastingAuthority.canCastIronsSpell(player, manaCost);
            if (!canAfford) {
                cir.setReturnValue(InteractionResultHolder.fail(stack));
                return;
            }
            ScrollLPTracker.stage(player.getUUID(), 0, false, manaCost);
        }
    }

    /**
     * Commit (or kill) the staged LP cost based on whether Iron's actually accepted the use.
     * Runs after the original {@code use} method completes.
     */
    @Inject(method = "use", at = @At("RETURN"), require = 0)
    private void arsnspells$commitScrollCost(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide()) {
            return;
        }

        InteractionResultHolder<ItemStack> result = cir.getReturnValue();
        boolean castSucceeded = result != null && result.getResult().consumesAction();

        // Aura commit removed alongside the HEAD-side aura intercept — see the note
        // above. Only the Cursed-Ring / LP commit path remains.

        ScrollLPTracker.Entry pending = ScrollLPTracker.take(player.getUUID());
        if (pending == null) {
            return;
        }

        if (!castSucceeded) {
            // Scroll didn't actually cast (cooldown, target invalid, etc.). Don't pay anything.
            LOGGER.debug("Scroll cast did not consume action; skipping LP commit for {}",
                player.getName().getString());
            return;
        }

        // ANS-MED-043: mana entry staged by "full" scroll cost mode. Distinct from
        // the LP entries (those carry manaCost == 0); commit via the same authority
        // that validated it at HEAD.
        if (pending.manaCost > 0.0f) {
            boolean consumed = com.otectus.arsnspells.casting.CastingAuthority
                .consumeIronsSpellMana(player, Math.round(pending.manaCost));
            if (!consumed) {
                LOGGER.warn("Scroll mana commit failed for {} despite successful validation; spell already cast",
                    player.getName().getString());
            }
            return;
        }

        if (pending.deathMode) {
            // Insufficient LP + death mode: spell proceeded, now collect the death penalty.
            LOGGER.warn("Death penalty for scroll cast with insufficient LP ({} LP required) on {}",
                pending.lpCost, player.getName().getString());
            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                player.displayClientMessage(
                    Component.translatable("message.ars_n_spells.lp.death", pending.lpCost)
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    true);
            }
            player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
            return;
        }

        // Normal commit. Validation already passed; consumption shouldn't fail, but check anyway.
        boolean ok = SanctifiedLegacyCompat.consumeLP(player, pending.lpCost);
        if (!ok) {
            LOGGER.warn("Scroll LP commit failed for {} despite successful validation; spell already cast",
                player.getName().getString());
            return;
        }
        if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
            player.displayClientMessage(
                Component.translatable("message.ars_n_spells.lp.consumed", pending.lpCost)
                    .withStyle(ChatFormatting.GOLD),
                true);
        }
    }
}
