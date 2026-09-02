package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * ANS-HIGH-002: routes the mana grant through {@link BridgeManager} instead of
 * importing {@code io.redspace.ironsspellbooks.api.magic.MagicData} directly.
 * The previous design imported MagicData at the class level; even though
 * registration is gated by Iron's-loaded, the top-level import made the class
 * brittle — any future reference to the type (reflection, diagnostic scan,
 * IDE inspection feature) would NoClassDefFoundError on Iron's-less servers.
 * Going through the bridge also makes the ritual useful on Ars-only setups.
 */
public class ManaInfusionRitual extends AnsRitual {
    /** Registry path shared by the ritual id, its tablet item, and its assets. */
    public static final String REGISTRY_PATH = "mana_infusion";

    @Override
    public void onEnd() {
        if (this.getWorld() == null || this.getWorld().isClientSide()) {
            return;
        }
        // Was its own nearest-player-within-8 search. Now shared with the feedback path, so the
        // mana goes to the player who lit the brazier rather than to whoever happens to be closest
        // when it finishes -- the ritual burns for a few seconds, which is long enough for those
        // to be different people on a busy server.
        Player player = recipient();
        if (player == null) {
            return;
        }
        BridgeManager.getBridge().addMana(player,
            AnsConfig.RITUAL_MANA_INFUSION_AMOUNT.get().floatValue());
    }

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", REGISTRY_PATH);
    }
}
