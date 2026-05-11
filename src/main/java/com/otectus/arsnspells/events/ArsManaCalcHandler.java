package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * STUB — listened on AN's {@code ManaRegenCalcEvent} and
 * {@code MaxManaCalcEvent} to feed cross-system bonuses into AN's mana
 * computation. Both event field surfaces drifted with AN 5.x; pending
 * Phase 11.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public class ArsManaCalcHandler {
    private ArsManaCalcHandler() {}
    // TODO(Phase 11): ManaRegenCalcEvent + MaxManaCalcEvent re-port
}
