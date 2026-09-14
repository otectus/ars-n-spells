package com.otectus.arsnspells.client;

/** Visibility corrections for the whole-number mana value sent by Iron's. */
public final class ManaBarVisibility {
    private ManaBarVisibility() {}

    public static boolean shouldHideContextualBar(boolean contextual, boolean holdingCastingItem,
                                                  int syncedMana, double maxMana) {
        // SyncManaPacket and the HUD both truncate to int. Comparing the synced
        // integer with the raw attribute leaves a fractional maximum unreachable.
        // Invalid maxima should retain the native result instead of hiding the HUD.
        return contextual && !holdingCastingItem
            && Double.isFinite(maxMana) && maxMana >= 0.0
            && syncedMana >= (int) maxMana;
    }
}
