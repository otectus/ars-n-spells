package com.otectus.arsnspells.contract;

import java.util.UUID;

/**
 * The unconditional cleanup contract for one feature (audit V07).
 *
 * <p>Closes the finding that every cleanup path was guarded by the same condition that had
 * enabled the feature. Turn the feature off, or unload the mod it bridged to, and the guard now
 * evaluated false, so the removal never ran and the modifier the feature had applied stayed on
 * the player permanently. {@code removeAll} takes no condition and consults no config: it is
 * called on logout, on dimension change, on config reload and on feature disable, and it removes
 * everything the feature could have applied, including under every legacy key in
 * {@link AnsModifierIds}.
 *
 * <p>Implemented per loader, outside this package.
 */
public interface FeatureCleanup {

    /**
     * Remove every trace of this feature from a player. Must be safe to call when the feature
     * never applied anything, when it is disabled, and repeatedly.
     */
    void removeAll(UUID player);
}
