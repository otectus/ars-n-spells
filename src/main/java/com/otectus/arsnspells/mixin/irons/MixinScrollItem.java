package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.compat.ScrollLPTracker;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyHiding;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
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
 *   <li><b>full</b>: Scrolls cost the same as casting the spell normally.</li>
 *   <li><b>lp_only</b>: Scrolls are mana-free but LP is still consumed for Cursed Ring wearers.</li>
 *   <li><b>free</b>: No resource cost, but LP from Cursed Ring still applies.</li>
 * </ul>
 *
 * <p>In every mode, a Cursed Ring wearer (with {@code enable_lp_system} on) pays LP
 * <em>instead of</em> mana, never both — the same substitution normal casting performs via
 * {@code IronsLPHandler}'s {@code event.setManaCost(0)}.
 */
@Mixin(value = io.redspace.ironsspellbooks.item.Scroll.class, remap = false)
public class MixinScrollItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinScrollItem.class);

    /**
     * {@code use} is inherited from vanilla {@code Item}, so it needs remapping even
     * though the mixin target itself does not — same situation as
     * {@code MixinInscriptionTableMenu.clickMenuButton}. Without {@code remap = true}
     * the annotation processor emits no refmap entry, and at runtime Mixin looks for a
     * literal {@code use} on a class whose method has been reobfuscated to
     * {@code m_7203_}. It silently finds nothing, {@code require = 0} swallows the
     * miss, and scrolls cast with no cost at all in every non-dev environment.
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = true, require = 0)
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
        } catch (Throwable e) {
            // Throwable, not Exception: ISpellContainer.get bottoms out in
            // DataResult.getOrThrow, and a linkage error from an Iron's version skew would
            // otherwise escape into Scroll.use.
            LOGGER.debug("Could not read spell data from scroll: {}", e.getMessage());
            return;
        }
        if (spellData == null) {
            return;
        }

        AbstractSpell spell = spellData.getSpell();
        int spellLevel = spellData.getLevel();
        // `spell == null` never happens: an empty or out-of-range slot yields SpellData.EMPTY,
        // whose getSpell() is a real SpellRegistry.none(). Testing for null therefore let the
        // whole cost/LP path below run against NoneSpell for any scroll with an empty
        // container — which is exactly what an ANS carrier is, since its payload is an Ars
        // spell in the sidecar rather than a native slot. Test for the none spell instead.
        // (Same oversight the 3.1.0 fix documented for Iron's own doInscription.)
        if (spell == null || spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) {
            return;
        }

        // Loot debris from before ArsCrossProxySpell.allowLooting() existed: a scroll carrying
        // an ars_cross_* proxy, with no spellbook behind it, so casting it can only ever reach
        // the "no carrier" failure. Blank it back to a plain scroll and refuse the use. The
        // container is already decoded above, so recognising it costs nothing, and this sits
        // ahead of the cost block so a dud can never stage anything in ScrollLPTracker.
        if (ArsCrossProxyRegistry.poolIdOf(spell.getSpellResource()) >= 0) {
            ArsCrossProxyHiding.neutralizeStrayProxyScroll(stack);
            player.displayClientMessage(
                Component.translatable("arsnspells.crosscast.proxy.stray_scroll"), true);
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            return;
        }

        String scrollMode = AnsConfig.SCROLL_COST_MODE.get().toLowerCase();
        int manaCost = spell.getManaCost(spellLevel);

        // --- Cursed Ring LP path (always applies regardless of scroll_cost_mode) ---
        //
        // LP REPLACES mana here; it is not additive. That is deliberate and matches every
        // other LP path in the mod: IronsLPHandler.onIronsSpellCast calls event.setManaCost(0)
        // for ring wearers, and MixinSpellResolverMana cancels expendMana outright. The
        // scroll_cost_mode=full documentation ("consume mana and LP just like normal casting")
        // means "the same rules normal casting uses", and under those rules a Cursed Ring
        // wearer pays LP instead of mana. Charging both here would make scrolls uniquely
        // double-priced for ring wearers.
        //
        // Audit F13: honor the LP system's master toggle. Every other LP participant gates on
        // ENABLE_LP_SYSTEM ("When disabled, spells use normal mana even with Cursed Ring
        // equipped"); scrolls must not keep charging LP when it is off.
        if (SanctifiedLegacyCompat.isCursedRingCostPathActive(player)) {
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
                        ScrollLPTracker.stage(player.getUUID(), lpCost, true, level.getGameTime());
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
                ScrollLPTracker.stage(player.getUUID(), lpCost, false, level.getGameTime());
                // Scroll proceeds; LP commits at RETURN if Iron's accepts the use. Returning
                // here is what makes LP replace mana rather than add to it — see the note above.
                return;
            }
            // manaCost == 0: nothing to convert to LP, so fall through. The mana block below
            // is also a no-op for a zero-cost spell, so no currency is charged either way.
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
            ScrollLPTracker.stage(player.getUUID(), 0, false, manaCost, level.getGameTime());
        }
    }

    /**
     * Commit (or kill) the staged LP cost based on whether Iron's actually accepted the use.
     * Runs after the original {@code use} method completes.
     */
    @Inject(method = "use", at = @At("RETURN"), remap = true, require = 0)
    private void arsnspells$commitScrollCost(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide()) {
            return;
        }

        InteractionResultHolder<ItemStack> result = cir.getReturnValue();
        boolean castSucceeded = result != null && result.getResult().consumesAction();

        // Aura commit removed alongside the HEAD-side aura intercept — see the note
        // above. Only the Cursed-Ring / LP commit path remains.

        ScrollLPTracker.Entry pending = ScrollLPTracker.take(player.getUUID(), level.getGameTime());
        if (pending == null) {
            return;
        }

        if (!castSucceeded) {
            // Scroll didn't actually cast (cooldown, target invalid, etc.). Don't pay anything.
            LOGGER.debug("Scroll cast did not consume action; skipping LP commit for {}",
                player.getName().getString());
            return;
        }

        // ANS-MED-043: mana entry staged by "full" scroll cost mode. Under current staging an
        // entry carries EITHER mana (no ring) OR LP (ring wearer, LP replaces mana), never
        // both. This commits each independently rather than returning after the first, so if
        // a future change ever does stage both, the second is charged instead of silently
        // dropped — the failure mode here should be "charged correctly", not "charged once".
        if (pending.manaCost > 0.0f) {
            boolean consumed = com.otectus.arsnspells.casting.CastingAuthority
                .consumeIronsSpellMana(player, Math.round(pending.manaCost));
            if (!consumed) {
                LOGGER.warn("Scroll mana commit failed for {} despite successful validation; spell already cast",
                    player.getName().getString());
            }
            if (pending.lpCost <= 0 && !pending.deathMode) {
                return;
            }
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

        if (pending.lpCost <= 0) {
            // Mana-only entry already committed above; nothing left to charge.
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
