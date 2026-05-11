package com.otectus.arsnspells.rituals;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * STUB — the 1.20.1 implementation classified ritual brazier contents into
 * "spell sources / already-inscribed / blank targets" via AN's
 * {@code SpellCaster}/{@code Spell.fromTag} and Iron's {@code ISpellContainer}
 * APIs. Both have shifted in 5.x / 3.x; the readSource path also depends on
 * the parchment-spell extraction shape that's gated on Phase 11.
 *
 * Until then: classifies every brazier item as a "blank target" (so the
 * transcribe ritual short-circuits on "no source"); the uninscribe ritual
 * still detects component-tagged items via
 * {@link com.otectus.arsnspells.spell.CrossModSpellComponents#has}.
 */
public final class InscriptionInputs {
    public final List<ItemEntity> sources;
    public final List<ItemEntity> inscribed;
    public final List<ItemEntity> blankTargets;

    private InscriptionInputs(List<ItemEntity> sources, List<ItemEntity> inscribed,
                              List<ItemEntity> blankTargets) {
        this.sources = Collections.unmodifiableList(sources);
        this.inscribed = Collections.unmodifiableList(inscribed);
        this.blankTargets = Collections.unmodifiableList(blankTargets);
    }

    public static InscriptionInputs classify(List<ItemEntity> entities) {
        List<ItemEntity> sources = new ArrayList<>();
        List<ItemEntity> inscribed = new ArrayList<>();
        List<ItemEntity> blanks = new ArrayList<>();
        for (ItemEntity entity : entities) {
            if (entity == null || !entity.isAlive()) continue;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;
            if (isInscribed(stack)) {
                inscribed.add(entity);
            } else {
                blanks.add(entity);
            }
        }
        return new InscriptionInputs(sources, inscribed, blanks);
    }

    public static boolean isInscribed(ItemStack stack) {
        return com.otectus.arsnspells.spell.CrossModSpellComponents.has(stack);
    }

    public static boolean hasArsSpellAtRoot(ItemStack stack) {
        return false;
    }

    public static InscriptionSource readSource(ItemStack stack) {
        return null; // TODO(Phase 11)
    }

    public static String joinNames(List<ItemEntity> entities) {
        if (entities.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entities.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(entities.get(i).getItem().getHoverName().getString());
        }
        return sb.toString();
    }
}
