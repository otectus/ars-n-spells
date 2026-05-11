package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * STUB — Ring of Virtue / Blasphemy mana cost discount via AN's
 * {@code SpellCostCalcEvent}. Depended on Sanctified Legacy curio probe +
 * AN 5.x event field access. Pending Phase 11.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public class CurioDiscountHandler {
    private CurioDiscountHandler() {}
    // TODO(Phase 11): SpellCostCalcEvent + Sanctified curio scan
}
