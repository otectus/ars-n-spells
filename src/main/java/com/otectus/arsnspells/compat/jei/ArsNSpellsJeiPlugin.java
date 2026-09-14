package com.otectus.arsnspells.compat.jei;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.IronsCompat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional, client-only JEI integration whose sole job is to keep ANS's internal
 * {@code ars_cross_*} proxy spells out of the recipe viewer.
 *
 * <p><b>Why a plugin is needed at all.</b> Most of the pollution is handled upstream and
 * without JEI: the proxies declare {@code allowCrafting = false} (so Iron's Scroll Forge and
 * its generated JEI recipes skip them), and ANS strips generated proxy scrolls out of creative
 * tab contents (so JEI, EMI, and the creative menu all lose the ghost ingredients, since they
 * source their item lists from tabs). What none of that reaches is Iron's Arcane Anvil
 * category, which enumerates {@code SpellRegistry.getEnabledSpells()} directly and consults no
 * flag ANS controls. Those recipes have to be hidden through JEI itself.
 *
 * <p><b>Never required.</b> The class is annotated {@link JeiPlugin} and is only ever loaded by
 * JEI's own plugin scanner, so ANS boots identically without JEI installed. It additionally
 * self-disables when Iron's is absent, since the proxies do not exist then.
 *
 * <p><b>EMI.</b> EMI's JEI-compatibility layer consumes JEI plugins, so hiding here also hides
 * in EMI. The creative-tab filtering covers EMI's native item list either way.
 *
 * <p><b>Failure is non-fatal.</b> Hiding reaches into Iron's non-API JEI classes, which could
 * change shape in a future Iron's release. Any failure is caught and logged once; the worst
 * outcome is that the proxy recipes stay visible, never a broken JEI or a crash.
 */
@JeiPlugin
public class ArsNSpellsJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsNSpellsJeiPlugin.class);

    private static final ResourceLocation UID =
        new ResourceLocation(ArsNSpells.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!IronsCompat.isLoaded()) {
            // No Iron's means no proxy spells and no Iron's recipe categories to filter.
            return;
        }
        try {
            int hidden = IronsProxyRecipeHider.hideProxyRecipes(runtime);
            if (hidden > 0) {
                LOGGER.debug("Hid {} Iron's Arcane Anvil recipe(s) generated from ANS proxy spells", hidden);
            }
        } catch (Throwable t) {
            // Deliberately broad: a LinkageError from an Iron's JEI refactor must not take
            // JEI down with it. Ghost recipes are a cosmetic problem; a broken recipe viewer
            // is not.
            LOGGER.warn("Could not hide ANS proxy recipes from JEI; ars_cross_* entries may be "
                + "visible in the Arcane Anvil category. This is cosmetic and safe to ignore.", t);
        }
    }
}
