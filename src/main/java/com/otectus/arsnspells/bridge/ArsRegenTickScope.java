package com.otectus.arsnspells.bridge;

import java.util.UUID;

/**
 * Marks the window in which Ars Nouveau is running its own mana regeneration tick (audit V06).
 *
 * <p>Closes the finding that ANS suppressed Ars's regen by making {@code ManaCap.addMana} a
 * no-op outright in the shared modes. That did stop the double-fill, and it also silently
 * discarded every other {@code addMana} in the game - a mana potion, a Void Jar, an addon
 * refunding a cancelled spell. The call reported success and moved nothing.
 *
 * <p>The fix needs to know <em>who is calling</em>, so the tick announces itself. Confirmed
 * against the pinned Ars Nouveau 4.12.7 jar ({@code ars-nouveau-401955-6688854.jar}, sha256
 * {@code 1f1debc282a0c379c1141f2840ea294eede6f6b544c589663f40bbe17b59a1af}), the regen tick is
 * {@code com/hollingsworth/arsnouveau/common/event/ManaCapEvents.playerOnTick}, descriptor
 * {@code (Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V}, which computes the amount
 * from {@code ManaUtil.getManaRegen(Player)} and applies it through the single
 * {@code IManaCap.addMana:(D)D} call in that method. A scan of the whole jar finds exactly two
 * callers of {@code addMana}: that one and {@code common/items/VoidJar}, which is a legitimate
 * player-facing mutation and must keep working.
 *
 * <p>The scope is entered at the head of {@code playerOnTick} and left at its return, so only
 * that caller is inside it. Scoping this way rather than with an instruction-level
 * {@code @Redirect} is deliberate: an {@code INVOKE} injection point is rejected outright when
 * another mod {@code @Overwrite}-merges the target method, and the Ars mixins live in the
 * required config where that is a hard boot failure.
 *
 * <p>Server-thread state held in a {@link ThreadLocal}. If {@code playerOnTick} throws, the
 * return injection does not run and the mark survives; the next tick for that player
 * overwrites it and its return clears it, so the exposure is bounded at one tick.
 */
public final class ArsRegenTickScope {

    private static final ThreadLocal<UUID> IN_REGEN_TICK = new ThreadLocal<>();

    private ArsRegenTickScope() {
    }

    /** Called at the head of Ars's regen tick for {@code player}. */
    public static void enter(UUID player) {
        IN_REGEN_TICK.set(player);
    }

    /** Called at every return of Ars's regen tick. */
    public static void exit() {
        IN_REGEN_TICK.remove();
    }

    /** Whether the mutation currently being processed came from Ars's regen tick for this player. */
    public static boolean isInRegenTickFor(UUID player) {
        UUID inTick = IN_REGEN_TICK.get();
        return inTick != null && inTick.equals(player);
    }
}
