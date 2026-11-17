package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.CrossCastNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// ANS-HIGH-002: Iron's-only imports moved to IronsInscriptionReader so this class
// is safe to classload on Iron's-less servers. Uninscription (which calls into this
// class) must work even after Iron's has been uninstalled — that's the documented
// recovery flow.

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
 *
 * <p><b>Precedence depends on the operation (3.4.1).</b> An Iron's book bound through the
 * normal workflow is both: it carries ANS inscriptions (the sidecar and its native proxy
 * slots) and, often, ordinary native spells. Transcription asks "does this item hold a spell
 * to copy?", so a readable spell wins there, as it always has. Uninscription asks "does this
 * item hold ANS inscriptions to remove?", so the inscription wins: classifying the bound book
 * as a source first made the ritual refuse it with "spell-bearing items in range" and the
 * inscription could never be removed (GitHub issue #14).
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

    /** Which ritual is asking: it decides whether a readable spell or an ANS inscription wins. */
    public enum Operation {
        /** Spell Transcription: a readable spell makes an item a source. */
        TRANSCRIPTION,
        /** Spell Uninscription: ANS inscriptions make an item removable, whatever else it holds. */
        UNINSCRIPTION
    }

    /** Transcription precedence; see {@link #classify(List, Operation)}. */
    public static InscriptionInputs classify(List<ItemEntity> entities) {
        return classify(entities, Operation.TRANSCRIPTION);
    }

    public static InscriptionInputs classify(List<ItemEntity> entities, Operation operation) {
        List<ItemEntity> sources = new ArrayList<>();
        List<ItemEntity> inscribed = new ArrayList<>();
        List<ItemEntity> blanks = new ArrayList<>();
        for (ItemEntity entity : entities) {
            if (entity == null || !entity.isAlive()) continue;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;
            if (operation == Operation.UNINSCRIPTION && isInscribed(stack)) {
                inscribed.add(entity);
            } else if (readSource(stack) != null) {
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
        if (stack.hasTag() && CrossCastNbt.hasCrossModSpells(stack.getTag())) return true;
        // 3.4.1 review UNBIND-01: a book left holding an ANS proxy whose sidecar entry was lost is
        // still ANS-inscribed, so Spell Uninscription can repair it. Gated + FQN: Iron's only.
        return com.otectus.arsnspells.compat.IronsCompat.isLoaded() && stack.hasTag()
            && !com.otectus.arsnspells.spell.irons.IronsProxySlotWriter.nativeProxyPoolIds(stack).isEmpty();
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
        } catch (Exception ignored) {
            // ANS-MED-020: narrowed from Throwable to Exception so LinkageError and
            // other Error subclasses propagate instead of silently masking real bugs.
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

        // ANS-MED-020 / ANS-LOW-019: narrow from Throwable to Exception so
        // LinkageError / OutOfMemoryError propagate instead of being silently masked.
        try {
            // SpellCaster(ItemStack) creates a tag on a blank item. Source discovery and
            // previews must be read-only, including for untagged stacks.
            Spell arsSpell = new SpellCaster(stack.hasTag() ? stack.getTag().copy() : new CompoundTag()).getSpell();
            if (arsSpell != null && arsSpell.recipe != null && !arsSpell.recipe.isEmpty()) {
                return InscriptionSource.ars(arsSpell);
            }
        } catch (Exception ignored) {
        }
        if (stack.hasTag()) {
            try {
                Spell parchmentSpell = Spell.fromTag(stack.getOrCreateTag());
                if (parchmentSpell != null && parchmentSpell.recipe != null
                    && !parchmentSpell.recipe.isEmpty()) {
                    return InscriptionSource.ars(parchmentSpell);
                }
            } catch (Exception ignored) {
            }
        }

        // ANS-HIGH-002: delegate Iron's parsing to the dedicated reader so the
        // JVM never resolves Iron's classes when Iron's is absent. The reader
        // self-gates on IronsCompat.isLoaded() too, but the call-site gate is
        // what keeps the helper class from being verified at all.
        if (IronsCompat.isLoaded()) {
            InscriptionSource ironsSource = IronsInscriptionReader.tryRead(stack);
            if (ironsSource != null) {
                return ironsSource;
            }
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
