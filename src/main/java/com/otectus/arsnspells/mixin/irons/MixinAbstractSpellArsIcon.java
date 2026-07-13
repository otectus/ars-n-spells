package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.irons.ArsCrossProxySpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: substitutes the per-spell custom name and nature-themed icon for
 * Ars cross-cast proxy spells in Iron's native spell wheel / bar / tooltips. The
 * data is read from the casting book's cross-cast sidecar component, keyed by
 * the proxy's pool id.
 *
 * <p>Both injects use {@code require = 0}: these are cosmetic overrides, so a
 * signature change in a future Iron's version should degrade to the static
 * fallback rather than crash the client. (On Iron's 1.21.1
 * {@code getSpellIconResource} is {@code final} — injection still applies; the
 * un-mixed fallback resolves to {@code textures/gui/spell_icons/ars_cross_k.png},
 * which we also ship.)
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinAbstractSpellArsIcon {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arsnspells$displayName(Player player, CallbackInfoReturnable<MutableComponent> cir) {
        if (!((Object) this instanceof ArsCrossProxySpell proxy)) {
            return;
        }
        CrossModSpell entry = arsnspells$entry(player, proxy.getPoolId());
        if (entry != null && entry.customName().isPresent() && !entry.customName().get().isEmpty()) {
            cir.setReturnValue(Component.literal(entry.customName().get()));
        }
    }

    @Inject(method = "getSpellIconResource", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arsnspells$icon(CallbackInfoReturnable<ResourceLocation> cir) {
        if (!((Object) this instanceof ArsCrossProxySpell proxy)) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        CrossModSpell entry = player == null ? null : arsnspells$entry(player, proxy.getPoolId());
        // Player-chosen icon symbol wins; nature is the fallback. The symbol is
        // whitelisted against ICON_SYMBOLS so unknown component data can't resolve
        // to a missing texture (purple checkerboard in the wheel).
        String symbol = entry == null ? "" : entry.iconSymbol().orElse("");
        String nature = entry == null ? "" : entry.nature().orElse("");
        String path;
        if (CrossModSpellComponents.ICON_SYMBOLS.contains(symbol)) {
            path = "textures/gui/icons/spell/icon_" + symbol + ".png";
        } else if (nature != null && nature.matches("[a-z_]{1,32}")) {
            path = "textures/gui/icons/spell/nature_" + nature + ".png";
        } else {
            path = "textures/gui/icons/spell/ars_cross_default.png";
        }
        cir.setReturnValue(ResourceLocation.fromNamespaceAndPath("ars_n_spells", path));
    }

    private static CrossModSpell arsnspells$entry(Player player, int poolId) {
        if (player == null) {
            return null;
        }
        CrossModSpell fromMain = arsnspells$entryFrom(player.getMainHandItem(), poolId);
        return fromMain != null ? fromMain : arsnspells$entryFrom(player.getOffhandItem(), poolId);
    }

    private static CrossModSpell arsnspells$entryFrom(ItemStack stack, int poolId) {
        if (stack == null || stack.isEmpty()
            || !IronsBookBindingUtil.isIronsSpellBook(stack)
            || !CrossModSpellComponents.has(stack)) {
            return null;
        }
        return CrossModSpellComponents.findEntryByProxyPoolId(stack, poolId).orElse(null);
    }
}
