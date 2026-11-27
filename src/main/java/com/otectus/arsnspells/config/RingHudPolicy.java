package com.otectus.arsnspells.config;

/**
 * Whether a worn ring replaces the mana bar, by the same rules the server uses to route the cost
 * (audit ANS-008). Pure, so the client can apply it and tests can enumerate it.
 *
 * <p>A ring hides the mana bars only when its resource route is actually active: the Cursed Ring
 * with {@code enable_lp_system} on, the Virtue Ring with {@code enable_virtue_aura_system} on. With a
 * toggle off the cast spends mana, so the mana bar stays. Wearing both rings cancels both, as it does
 * for payment; {@code hide_mana_bar_with_ring} remains the player-facing preference over all of it.
 */
public final class RingHudPolicy {
    private RingHudPolicy() {}

    /**
     * @param cursedOnly the Cursed Ring is worn without the Virtue Ring
     * @param virtueOnly the Virtue Ring is worn without the Cursed Ring (Covenant present)
     */
    public static boolean hidesManaBars(AnsSettings.ClientPolicy policy, boolean ringModPresent, boolean cursedOnly, boolean virtueOnly) {
        if (policy == null || !policy.hideManaBarWithRing() || !ringModPresent) return false;
        return (policy.lpSystem() && cursedOnly) || (policy.virtueAuraSystem() && virtueOnly);
    }
}
