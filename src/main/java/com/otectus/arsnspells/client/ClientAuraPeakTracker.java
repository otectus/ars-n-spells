package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tracks the local player's personal aura-peak in-memory on the client.
 *
 * <p>Covenant of the Seven's {@code AuraContainerOverlay} divides the current
 * aura by a hardcoded {@code 2_000_000} when computing the bar's fill width.
 * For a player whose current peak is well below 2M, the bar will never appear
 * full. {@link com.otectus.arsnspells.mixin.covenant.MixinAuraContainerOverlay}
 * replaces that hardcoded divisor with the value this tracker returns, so the
 * bar fills relative to the player's <em>own</em> maximum.
 *
 * <p>The peak only grows during a session. On disconnect / world quit it is
 * reset to {@code 1} (the floor) so a re-login starts the peak at the player's
 * current aura, which is the natural "you can have at least this much" baseline.
 * Persistence across sessions is intentionally out of scope for v1 — see the
 * plan file for the rationale.
 *
 * <p>The floor of {@code 1} is important: the mixin uses the returned value as
 * an integer divisor inside Covenant's bytecode, and a {@code 0} divisor would
 * either throw {@code ArithmeticException} or produce {@code NaN} fill width.
 */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientAuraPeakTracker {

    /** Polling cadence: every 5 client ticks (~250 ms at 20 TPS). Cheap enough to ignore. */
    private static final int TICK_INTERVAL = 5;

    /** Floor so the mixin divisor never sees 0. The peak never decreases below this. */
    private static final int PEAK_FLOOR = 1;

    /** Local-client-only state; volatile because the mixin reads it from the render thread. */
    private static volatile int personalPeak = PEAK_FLOOR;

    private static int tickCounter;

    private ClientAuraPeakTracker() {
        // static-only
    }

    /**
     * Reads the current personal peak. Always returns at least {@link #PEAK_FLOOR}
     * so callers using it as a divisor are safe.
     */
    public static int getPeak() {
        return Math.max(PEAK_FLOOR, personalPeak);
    }

    /**
     * Public ratchet entry for testing. Updates the peak iff {@code current} exceeds it.
     */
    public static void updatePeak(int current) {
        if (current > personalPeak) {
            personalPeak = current;
        }
    }

    /** Reset the peak. Called on disconnect; also useful for tests. */
    public static void reset() {
        personalPeak = PEAK_FLOOR;
        tickCounter = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }
        // SanctifiedLegacyCompat.getCovenantAura returns 0 if reflection didn't resolve
        // (degraded mode) — don't ratchet down on that, the Math.max in updatePeak handles it.
        int current = SanctifiedLegacyCompat.getCovenantAura(player);
        if (current > 0) {
            updatePeak(current);
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
