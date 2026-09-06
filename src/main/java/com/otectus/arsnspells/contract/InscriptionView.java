package com.otectus.arsnspells.contract;

/**
 * Everything {@link InscriptionPlanner} needs to read about one inscription (audit V18).
 *
 * <p>Closes the finding that emptiness was inferred from the absence of an ANS data component.
 * An implementation of this port must answer {@link #targetIsNativelyEmpty()} from the real
 * native container API of whichever mod owns the target item, never from "ANS wrote nothing
 * here". Those are different questions and the difference is a destroyed spell scroll.
 *
 * <p>Implemented per loader, outside this package, against the real item stack.
 */
public interface InscriptionView {

    /** What the source item is. */
    InscriptionSourceKind sourceKind();

    /** What the target item is. */
    InscriptionSourceKind targetKind();

    /**
     * Whether the target's own native spell container is empty, as that mod reports it.
     * Not "ANS has not written to it".
     */
    boolean targetIsNativelyEmpty();

    /** How many items are in the source stack. */
    int sourceStackCount();
}
