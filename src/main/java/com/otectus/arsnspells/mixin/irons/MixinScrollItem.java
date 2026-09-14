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

/** Reject stale proxy scrolls before native initiation. Full scroll mana pricing is captured
 * by IronsCastPayments and committed at the same before-effects boundary as native books. */
@Mixin(value = io.redspace.ironsspellbooks.item.Scroll.class, remap = false)
public class MixinScrollItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinScrollItem.class);

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 1)
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

    }
}
