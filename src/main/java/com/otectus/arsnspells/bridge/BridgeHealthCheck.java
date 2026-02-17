package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.util.ManaUtil;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BridgeHealthCheck {
    private static final Logger LOGGER = LogManager.getLogger();

    public static boolean isIronSpellsHealthy(Player player) {
        try {
            // Functional Logic: Verification of Iron's API responsiveness
            return MagicData.getPlayerMagicData(player) != null;
        } catch (Throwable e) {
            LOGGER.error("Ars 'n' Spells: Iron's Spells API is unstable. Integration suspended for this session.");
            return false;
        }
    }

    public static boolean isArsNouveauHealthy(Player player) {
        try {
            return ManaUtil.getNativeMana(player).isPresent();
        } catch (Throwable e) {
            LOGGER.error("Ars 'n' Spells: Ars Nouveau API is unstable.");
            return false;
        }
    }
}