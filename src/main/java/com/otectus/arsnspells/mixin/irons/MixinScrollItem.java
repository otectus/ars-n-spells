package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.events.IronsLPHandler;
import com.otectus.arsnspells.events.LPDeathPrevention;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
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
 * Behavior is controlled by the scroll_cost_mode config:
 * - "full": Scrolls consume mana and LP like normal casting
 * - "lp_only": Scrolls are mana-free but LP is still consumed for Cursed Ring
 * - "free": No resource cost, but LP from Cursed Ring still applies
 */
@Mixin(targets = "io.redspace.ironsspellbooks.item.Scroll", remap = false)
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

        // Extract spell data from scroll
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

        // --- Cursed Ring LP validation (always applies regardless of scroll_cost_mode) ---
        if (SanctifiedLegacyCompat.isAvailable() && SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            if (manaCost > 0) {
                SpellRarity rarity = spell.getRarity(spellLevel);
                int lpCost = SanctifiedLegacyCompat.calculateIronsLPCost(manaCost, spellLevel, rarity.name());

                LOGGER.debug("Scroll LP validation: spell={}, level={}, lpCost={}",
                    spell.getSpellId(), spellLevel, lpCost);

                boolean hasEnough = SanctifiedLegacyCompat.hasEnoughLP(player, lpCost);
                if (!hasEnough) {
                    if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
                        // Death penalty: allow scroll, mark for death
                        LPDeathPrevention.markSpellCast(player);
                        // LP consumption will happen below or in post-cast
                    } else {
                        // Safe mode: cancel scroll use
                        LOGGER.warn("Insufficient LP for scroll - cancelling");
                        cir.setReturnValue(InteractionResultHolder.fail(stack));

                        SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);

                        if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                            player.displayClientMessage(
                                Component.literal(ChatFormatting.RED + "Insufficient LP - Scroll Cancelled"),
                                true);
                        }
                        return;
                    }
                }

                // Mark spell cast for death prevention system
                LPDeathPrevention.markSpellCast(player);

                // Store pending LP cost for consumption after scroll cast
                IronsLPHandler.storePendingScrollLP(player, lpCost, manaCost);

                if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                    player.displayClientMessage(
                        Component.literal(ChatFormatting.GOLD + "Consumed " + lpCost + " LP"),
                        true);
                }

                // Consume LP now (before scroll executes)
                SanctifiedLegacyCompat.consumeLP(player, lpCost);

                // Scroll uses LP instead of mana; allow the scroll to proceed
                return;
            }
        }

        // --- Mana cost validation (based on scroll_cost_mode) ---
        if ("free".equals(scrollMode) || "lp_only".equals(scrollMode)) {
            // No mana cost for scrolls in these modes
            return;
        }

        // "full" mode: validate mana like a normal spell cast
        if (manaCost > 0) {
            boolean canAfford = com.otectus.arsnspells.casting.CastingAuthority.canCastIronsSpell(player, manaCost);
            if (!canAfford) {
                cir.setReturnValue(InteractionResultHolder.fail(stack));
                return;
            }
        }
    }
}
