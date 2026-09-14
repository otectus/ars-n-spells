package com.otectus.arsnspells.inscription;

import com.otectus.arsnspells.contract.InscriptionSourceKind;
import com.otectus.arsnspells.contract.InscriptionView;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * The Forge 1.20.1 implementation of {@link InscriptionView}: two real item stacks read through
 * {@link InscriptionClassifier}.
 *
 * <p>Reads only. Nothing in here mutates either stack, which is what lets a caller plan an
 * inscription, show the player what it would do, and still have moved nothing.
 */
public record StackInscriptionView(ItemStack source, ItemStack target) implements InscriptionView {

    public StackInscriptionView {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public InscriptionSourceKind sourceKind() {
        return InscriptionClassifier.classify(source);
    }

    @Override
    public InscriptionSourceKind targetKind() {
        return InscriptionClassifier.classify(target);
    }

    @Override
    public boolean targetIsNativelyEmpty() {
        return InscriptionClassifier.isNativelyEmpty(target);
    }

    @Override
    public int sourceStackCount() {
        return source.getCount();
    }
}
