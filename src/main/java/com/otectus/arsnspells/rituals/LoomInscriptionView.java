package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.item.ICasterTool;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.common.items.SpellParchment;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.contract.InscriptionPlanner;
import com.otectus.arsnspells.contract.InscriptionSourceKind;
import com.otectus.arsnspells.contract.InscriptionView;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.world.item.ItemStack;

/**
 * The loader adapter behind {@link InscriptionView}: reads real {@link ItemStack}s so
 * {@link InscriptionPlanner} can decide (audit V18).
 *
 * <p>Every "is this blank?" question in the loom, the menu and the transcription ritual comes
 * through here, and none of them answers it locally any more. That is the whole point of the
 * finding: each path used to decide for itself, and each decided "no ANS data means blank", so
 * a scroll Ars Nouveau or Iron's had already filled was read as empty parchment and
 * overwritten.
 *
 * <h2>Native emptiness</h2>
 * {@link #targetIsNativelyEmpty()} never consults ANS's own data component. It asks the owning
 * mod:
 *
 * <ul>
 *   <li>Iron's Spellbooks - {@code ISpellContainer.isEmpty()}, descriptor
 *       {@code io/redspace/ironsspellbooks/api/spells/ISpellContainer.isEmpty()Z}, confirmed
 *       against the pinned {@code irons_spellbooks-1.21.1-3.16.3.jar} (sha256
 *       {@code 70cc7e37c48d45260eb101a4c536cd391b8e258ca95199bde4f06707b2d5623b}). Reached only
 *       through {@code IronsScrollFactory}, gated on {@link IronsCompat#isLoaded()} and named by
 *       FQN, so no Iron's type is resolvable from this class on an install without Iron's.</li>
 *   <li>Ars Nouveau - {@code SpellCasterRegistry} via
 *       {@link InscriptionInputs#hasArsSpellAtRoot(ItemStack)}, which is the same native caster
 *       read the cross-cast pipeline uses.</li>
 * </ul>
 *
 * <h2>Reusability policy</h2>
 * A source is consumed only when it is an explicitly disposable medium - Ars's spell parchment.
 * Spellbooks and every other caster tool are read and handed back: {@link InscriptionPlan}
 * refuses to be constructed saying otherwise, so this is enforced rather than remembered.
 */
public final class LoomInscriptionView implements InscriptionView {

    private final InscriptionSourceKind sourceKind;
    private final InscriptionSourceKind targetKind;
    private final boolean targetNativelyEmpty;
    private final int sourceStackCount;

    private LoomInscriptionView(InscriptionSourceKind sourceKind, InscriptionSourceKind targetKind,
                                boolean targetNativelyEmpty, int sourceStackCount) {
        this.sourceKind = sourceKind;
        this.targetKind = targetKind;
        this.targetNativelyEmpty = targetNativelyEmpty;
        this.sourceStackCount = sourceStackCount;
    }

    /** Read both stacks once. Copies nothing and mutates nothing. */
    public static LoomInscriptionView of(ItemStack source, ItemStack target) {
        return new LoomInscriptionView(
            classify(source),
            classify(target),
            isNativelyEmpty(target),
            source == null ? 0 : source.getCount());
    }

    /**
     * The one-unit plan for inscribing {@code source} onto {@code target}. This is the preview:
     * it reads the stacks and returns a description, and no caller has moved an item yet.
     */
    public static InscriptionPlan plan(ItemStack source, ItemStack target) {
        return InscriptionPlanner.plan(of(source, target));
    }

    /**
     * What {@code stack} is, from the item itself.
     *
     * <p>Presence of ANS data does mean "filled" - it is the reverse inference, absence meaning
     * "blank", that destroyed scrolls. A stack with no ANS data still has to answer to its own
     * mod's container before it is called empty.
     */
    public static InscriptionSourceKind classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return InscriptionSourceKind.BLANK_PARCHMENT;
        }
        if (stack.getItem() instanceof SpellBook || IronsBookBindingUtil.isIronsSpellBook(stack)) {
            return InscriptionSourceKind.REUSABLE_BOOK;
        }
        boolean filled = CrossModSpellComponents.has(stack) || !isNativelyEmpty(stack);
        if (stack.getItem() instanceof SpellParchment) {
            // The one disposable medium: parchment is what Ars itself spends to carry a spell.
            return filled
                ? InscriptionSourceKind.CONSUMABLE_SCROLL
                : InscriptionSourceKind.BLANK_PARCHMENT;
        }
        if (stack.getItem() instanceof ICasterTool) {
            // Wands, enchanter's gear, tomes: read and handed back, never spent.
            return InscriptionSourceKind.REUSABLE_FOCUS;
        }
        return filled
            ? InscriptionSourceKind.FILLED_SCROLL
            : InscriptionSourceKind.BLANK_PARCHMENT;
    }

    /**
     * Whether {@code stack}'s own native spell container reports itself empty, as its mod
     * reports it. Never "ANS has not written here".
     */
    public static boolean isNativelyEmpty(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        // Gated + FQN: the Iron's-importing helper only classloads with Iron's present.
        if (IronsCompat.isLoaded()
            && com.otectus.arsnspells.spell.irons.IronsScrollFactory.hasNativeContainer(stack)) {
            return com.otectus.arsnspells.spell.irons.IronsScrollFactory
                .isNativeContainerEmpty(stack);
        }
        return !InscriptionInputs.hasArsSpellAtRoot(stack);
    }

    @Override
    public InscriptionSourceKind sourceKind() {
        return sourceKind;
    }

    @Override
    public InscriptionSourceKind targetKind() {
        return targetKind;
    }

    @Override
    public boolean targetIsNativelyEmpty() {
        return targetNativelyEmpty;
    }

    @Override
    public int sourceStackCount() {
        return sourceStackCount;
    }
}
