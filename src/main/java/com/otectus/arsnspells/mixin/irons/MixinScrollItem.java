package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.casting.CastingAuthority;
import com.otectus.arsnspells.compat.ScrollLPTracker;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyHiding;
import com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
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
 * Intercepts Iron's Spellbooks scroll usage to enforce resource costs. Scrolls
 * normally bypass SpellPreCastEvent/SpellOnCastEvent, so without this mixin they
 * would cast for free.
 *
 * <p>Cost handling is transactional: HEAD validates and stages a pending cost via
 * {@link ScrollLPTracker}, RETURN commits or rolls back based on whether Iron's
 * actually accepted the use. This prevents the pre-1.9.0 bug where the cost was
 * consumed before the cast and could leave invariants broken if the cast failed
 * downstream.
 *
 * <p>State lives in {@link ScrollLPTracker} (a non-mixin package) because Sponge
 * Mixin forbids direct references to inner classes of a mixin — the mixin's bytecode
 * is merged into the target class, so {@code MixinX$Y} would have no resolvable home.
 *
 * <p>Behavior is controlled by the {@code scroll_cost_mode} config:
 * <ul>
 *   <li><b>full</b>: Scrolls cost the same as casting the spell normally.</li>
 *   <li><b>lp_only</b>: Scrolls are mana-free but LP is still consumed for Cursed Ring wearers.</li>
 *   <li><b>free</b>: No resource cost, but LP from Cursed Ring still applies.</li>
 * </ul>
 *
 * <p><b>Covenant of the Seven on 1.21.1.</b> The 1.20.1 build ran a Cursed-Ring LP
 * branch ahead of the mana check, so a ring wearer paid LP <em>instead of</em> mana,
 * never both. Covenant has no 1.21.1 release, so that branch has no consumer and is
 * not carried here; {@code lp_only} consequently behaves as {@code free}. The
 * {@link ScrollLPTracker} entry still carries {@code lpCost}/{@code deathMode} so
 * re-enabling the subsystem does not need a staging-format change. See
 * Covenant of the Seven, which has no 1.21.1 release.
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

        SpellData spellData;
        try {
            ISpellContainer container = ISpellContainer.get(stack);
            spellData = container != null ? container.getSpellAtIndex(0) : null;
        } catch (Throwable e) {
            // Throwable, not Exception: ISpellContainer.get bottoms out in a codec decode,
            // and a linkage error from an Iron's version skew would otherwise escape into
            // Scroll.use and crash the interaction.
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
        // whole cost path below run against NoneSpell for any scroll with an empty container —
        // which is exactly what an ANS carrier is, since its payload is an Ars spell in the
        // sidecar rather than a native slot. Test for the none spell instead. (Same oversight
        // the 3.1.0 fix documented for Iron's own doInscription.)
        if (spell == null || spell == SpellRegistry.none()) {
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
                Component.translatable("message.ars_n_spells.crosscast.proxy.stray_scroll"), true);
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            return;
        }

        String scrollMode = AnsConfig.SCROLL_COST_MODE.get().toLowerCase();
        int manaCost = spell.getManaCost(spellLevel);

        // Cursed-Ring LP path would run here, ahead of the mana check, and return early so
        // LP replaces mana rather than adding to it. Inert on 1.21.1 — see the class javadoc.

        // --- Mana cost validation (based on scroll_cost_mode) ---
        if ("free".equals(scrollMode) || "lp_only".equals(scrollMode)) {
            return;
        }

        // "full" mode: validate mana like a normal spell cast, and stage the consume for
        // RETURN. ANS-MED-043: validation alone charged nothing — Iron's scrolls never
        // deduct mana natively, so "full" mode was documented as costing mana but was
        // actually free.
        if (manaCost > 0) {
            if (!CastingAuthority.canCastIronsSpell(player, manaCost)) {
                cir.setReturnValue(InteractionResultHolder.fail(stack));
                return;
            }
            ScrollLPTracker.stage(player.getUUID(), 0, false, manaCost, level.getGameTime());
        }
    }

    /**
     * Commit the staged cost based on whether Iron's actually accepted the use. Runs
     * after the original {@code use} method completes.
     */
    @Inject(method = "use", at = @At("RETURN"), require = 0)
    private void arsnspells$commitScrollCost(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide()) {
            return;
        }

        InteractionResultHolder<ItemStack> result = cir.getReturnValue();
        boolean castSucceeded = result != null && result.getResult().consumesAction();

        ScrollLPTracker.Entry pending = ScrollLPTracker.take(player.getUUID(), level.getGameTime());
        if (pending == null) {
            return;
        }

        if (!castSucceeded) {
            // Scroll didn't actually cast (cooldown, target invalid, etc.). Don't pay anything.
            LOGGER.debug("Scroll cast did not consume action; skipping cost commit for {}",
                player.getName().getString());
            return;
        }

        if (pending.manaCost > 0.0f) {
            boolean consumed = CastingAuthority.consumeIronsSpellMana(player, Math.round(pending.manaCost));
            if (!consumed) {
                LOGGER.warn("Scroll mana commit failed for {} despite successful validation; spell already cast",
                    player.getName().getString());
            }
        }

        // The LP / death-mode commit branch would follow here, keyed on pending.lpCost and
        // pending.deathMode. Inert on 1.21.1 — see the class javadoc.
    }
}
