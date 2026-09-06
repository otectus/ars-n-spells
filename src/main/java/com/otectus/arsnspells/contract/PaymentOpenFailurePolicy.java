package com.otectus.arsnspells.contract;

/**
 * What to do when an alternative payment leg cannot be confirmed (audit V24 migration).
 *
 * <p>Closes the finding that a failed alternative payment fell through to casting the spell
 * anyway. When the Living-Point or aura adapter could not confirm a debit the code logged and
 * continued, so a ring whose backing mod had drifted turned every spell free. Refusing the cast
 * is the correct behaviour and is what a new install gets; existing worlds keep the old
 * behaviour until their owner opts in, which is why this is a policy rather than a fix.
 */
public enum PaymentOpenFailurePolicy {
    /**
     * The historical behaviour: log and cast anyway. Selected for a config file written before
     * the schema version existed, so an existing world's behaviour does not change under it.
     */
    LEGACY_OPEN,
    /** Refuse the cast. The safe policy, and the default on a freshly generated config. */
    REFUSE,
    /** Fall back to the native mana cost instead of the alternative resource. */
    NATIVE_FALLBACK
}
