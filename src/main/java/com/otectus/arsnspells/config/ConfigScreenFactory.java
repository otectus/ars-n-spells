package com.otectus.arsnspells.config;

import net.minecraft.client.gui.screens.Screen;

/**
 * STUB — the in-game config screen depended on Forge's
 * {@code ConfigScreenHandler.ConfigScreenFactory} extension point, which
 * NeoForge 1.21.1 removed in favour of {@code IConfigScreenFactory}
 * registered on {@link net.neoforged.fml.ModContainer}. The 1.20.1 screen
 * body is preserved in the original source tree at
 * {@code C:\Users\crims\Documents\GitHub\ars-n-spells\src\main\java\com\otectus\arsnspells\config\ConfigScreenFactory.java}
 * and can be ported back when Phase 11 GUI work begins. Until then,
 * editing {@code config/ars_n_spells-common.toml} directly is the
 * supported path.
 */
public class ConfigScreenFactory {
    private ConfigScreenFactory() {}

    /** Returns the parent screen unchanged — no in-game editor in this build. */
    public static Screen createConfigScreen(Screen parent) {
        return parent;
    }
}
