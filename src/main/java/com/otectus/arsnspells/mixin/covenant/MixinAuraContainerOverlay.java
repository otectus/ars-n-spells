package com.otectus.arsnspells.mixin.covenant;

import com.otectus.arsnspells.client.ClientAuraPeakTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Rewires Covenant of the Seven's player-HUD aura bar so its "max" is dynamic
 * per-player rather than the hardcoded {@code 2_000_000} in upstream Covenant.
 *
 * <p>Upstream, {@code AuraContainerOverlay.render(...)} loads the literal
 * {@code 2_000_000} three times in its render method:
 * <ul>
 *   <li>as the divisor when computing fill width: {@code fill = 98 * (current / 2_000_000)},</li>
 *   <li>as the comparison ceiling in a clamp branch,</li>
 *   <li>as the clamp assignment value when current exceeds it.</li>
 * </ul>
 *
 * <p>All three sites are conceptually "the max." Replacing every occurrence of
 * the {@code 2_000_000} literal in this method with
 * {@link ClientAuraPeakTracker#getPeak()} makes the fill proportional to the
 * player's session peak, and the clamp follows naturally — the player can never
 * appear above 100% because the peak ratchets up to track their actual aura.
 *
 * <p>A second injector swaps the {@code String.valueOf(int)} call that renders
 * the digits below the bar so the text shows {@code current / peak} rather than
 * just the current value. That gives users a visible cue for what their peak is.
 *
 * <p>{@code @Pseudo} + class-name targeting is required because Covenant is an
 * optional dependency — the class might not exist on the classpath. The plugin
 * ({@code ArsNSpellsMixinPlugin}) additionally gates this mixin behind the
 * {@code sanctifiedPresent} probe to short-circuit application on Covenant-less
 * installs.
 *
 * <p>{@code require = 0} is implicit on these injectors (we override the default
 * of 1 via Sponge's lenient application). If Covenant ever renames the constant
 * or restructures the method, the mixin silently no-ops and Covenant's bar
 * reverts to its native half-fill behavior — degraded but safe.
 */
@Pseudo
@Mixin(targets = "net.llenzzz.covenant_of_the_seven.gui.AuraContainerOverlay", remap = false)
public abstract class MixinAuraContainerOverlay {

    /**
     * Replace every occurrence of the hardcoded {@code 2_000_000} max in the
     * render method with the player's session peak. The three call sites
     * (divisor + clamp comparison + clamp assignment) all move together, which
     * is the correct semantics: peak is "the new max."
     */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 2_000_000), require = 0)
    private int arsnspells$replaceHardcodedMax(int original) {
        return ClientAuraPeakTracker.getPeak();
    }

    /**
     * Rewrite the bar's text label from just the current aura to
     * {@code "current / peak"}. {@code @Redirect} on {@code String.valueOf(int)}
     * is the cleanest shape — we receive the int, can compute the peak ourselves,
     * and return the composite string that Covenant will hand to {@code drawString}.
     *
     * <p>If Covenant ever has a second {@code String.valueOf(int)} call in the
     * same method (e.g. for a different label), we'd need to disambiguate via
     * {@code ordinal} or {@code slice}. Today, bytecode inspection shows exactly
     * one such call in {@code render}, so the simple form suffices.
     */
    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;"),
        require = 0
    )
    private String arsnspells$labelWithPeak(int currentAura) {
        return currentAura + " / " + ClientAuraPeakTracker.getPeak();
    }
}
