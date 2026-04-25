package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.otectus.arsnspells.spell.CrossCastNbt;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Classifies dropped items in a ritual brazier's work area into three disjoint
 * buckets used by the transcribe and uninscribe rituals:
 * <ul>
 *     <li>{@link #sources} &mdash; items carrying a filled Ars Nouveau or Iron's
 *         Spellbooks spell that the cross-cast pipeline can read.</li>
 *     <li>{@link #inscribed} &mdash; items already carrying a cross-cast NBT list.</li>
 *     <li>{@link #blankTargets} &mdash; everything else.</li>
 * </ul>
 * Classification is deterministic and order-free. Callers must enforce their
 * own count-based validation rules on top of the buckets.
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
            if (readSource(stack) != null) {
                sources.add(entity);
            } else if (isInscribed(stack)) {
                inscribed.add(entity);
            } else {
                blanks.add(entity);
            }
        }
        return new InscriptionInputs(sources, inscribed, blanks);
    }

    public static boolean isInscribed(ItemStack stack) {
        return stack.hasTag() && CrossCastNbt.hasCrossModSpells(stack.getTag());
    }

    /**
     * CompoundTag-only companion to {@link #isInscribed(ItemStack)} so the
     * disambiguation contract is unit-testable without bootstrapping the
     * vanilla item registry.
     */
    public static boolean isInscribed(CompoundTag stackTag) {
        return CrossCastNbt.hasCrossModSpells(stackTag);
    }

    /**
     * True when the stack's root NBT parses as an Ars Nouveau spell with a
     * non-empty glyph recipe. Used by the transcribe ritual's defensive pass:
     * an item in this state would let Ars's own right-click handler fire
     * before the cross-cast dispatcher, so we refuse to inscribe onto it.
     */
    public static boolean hasArsSpellAtRoot(ItemStack stack) {
        if (!stack.hasTag()) return false;
        try {
            Spell parsed = Spell.fromTag(stack.getOrCreateTag());
            return parsed != null && parsed.recipe != null && !parsed.recipe.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Attempts to parse a spell payload from the stack. Tries Ars Nouveau
     * first (via {@link SpellCaster} for focus/spellbook, then root-tag parse
     * for spell parchment), then Iron's Spellbooks via {@link ISpellContainer}.
     * Returns {@code null} if no readable spell is found.
     */
    public static InscriptionSource readSource(ItemStack stack) {
        if (stack.isEmpty()) return null;

        try {
            Spell arsSpell = new SpellCaster(stack).getSpell();
            if (arsSpell != null && arsSpell.recipe != null && !arsSpell.recipe.isEmpty()) {
                return InscriptionSource.ars(arsSpell);
            }
        } catch (Throwable ignored) {
        }
        if (stack.hasTag()) {
            try {
                Spell parchmentSpell = Spell.fromTag(stack.getOrCreateTag());
                if (parchmentSpell != null && parchmentSpell.recipe != null
                    && !parchmentSpell.recipe.isEmpty()) {
                    return InscriptionSource.ars(parchmentSpell);
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container != null) {
                SpellData data = container.getSpellAtIndex(0);
                if (data != null) {
                    AbstractSpell spell = data.getSpell();
                    if (spell != null && spell.getSpellId() != null) {
                        ResourceLocation id = ResourceLocation.tryParse(spell.getSpellId());
                        if (id != null) {
                            return InscriptionSource.irons(id, Math.max(1, data.getLevel()));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    /** Comma-joined display names for use in translated error messages. */
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
