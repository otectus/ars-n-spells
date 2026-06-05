package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
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
 *         Spellbooks spell the cross-cast pipeline can read.</li>
 *     <li>{@link #inscribed} &mdash; items already carrying a cross-cast component.</li>
 *     <li>{@link #blankTargets} &mdash; everything else.</li>
 * </ul>
 *
 * <h2>Ars Nouveau 5.x / Iron's 3.15.6 migration</h2>
 * The Forge 1.20.1 source read used {@code new SpellCaster(stack)} and
 * {@code Spell.fromTag(rootNbt)} — both removed in Ars 5.x (spells moved to the
 * data-component system). The Ars read now goes through
 * {@link SpellCasterRegistry#from(ItemStack)} → {@link AbstractCaster#getSpell()}.
 * The inscribed check uses the data-component façade
 * ({@link CrossModSpellComponents#has}) instead of the old root-NBT key. Iron's
 * parsing stays isolated in {@link IronsInscriptionReader} so this class is safe
 * to classload on Iron's-less servers (uninscription is the Iron's-uninstall
 * recovery flow and must keep working).
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
        return CrossModSpellComponents.has(stack);
    }

    /**
     * True when the stack carries a non-empty Ars Nouveau spell. Used by the
     * transcribe ritual's defensive pass: a filled Ars caster item would let
     * Ars's own right-click handler fire before the cross-cast dispatcher, so
     * the ritual refuses to inscribe onto it.
     */
    public static boolean hasArsSpellAtRoot(ItemStack stack) {
        return readArsSpell(stack) != null;
    }

    /**
     * Attempts to parse a spell payload from the stack: Ars Nouveau first (via
     * the 5.x {@link SpellCasterRegistry}), then Iron's Spellbooks (via the
     * gated {@link IronsInscriptionReader}). Returns {@code null} if no readable
     * spell is found.
     */
    public static InscriptionSource readSource(ItemStack stack) {
        if (stack.isEmpty()) return null;

        Spell arsSpell = readArsSpell(stack);
        if (arsSpell != null) {
            return InscriptionSource.ars(arsSpell);
        }

        // Delegate Iron's parsing to the dedicated reader so the JVM never
        // resolves Iron's classes when Iron's is absent. The call-site gate is
        // what keeps the helper class from being verified at all.
        if (IronsCompat.isLoaded()) {
            InscriptionSource ironsSource = IronsInscriptionReader.tryRead(stack);
            if (ironsSource != null) {
                return ironsSource;
            }
        }

        return null;
    }

    /** Ars 5.x: read a non-empty {@link Spell} from a caster item, or null. */
    private static Spell readArsSpell(ItemStack stack) {
        try {
            if (!SpellCasterRegistry.hasCaster(stack)) {
                return null;
            }
            AbstractCaster<?> caster = SpellCasterRegistry.from(stack);
            if (caster == null) {
                return null;
            }
            Spell spell = caster.getSpell();
            if (spell != null && !spell.isEmpty()) {
                return spell;
            }
        } catch (Exception ignored) {
            // Narrow to Exception so LinkageError / OutOfMemoryError still propagate.
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
