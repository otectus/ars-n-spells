package com.otectus.arsnspells.mixin.covenant;

import com.otectus.arsnspells.client.ClientAuraPeakTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Rewires Covenant of the Seven's player-HUD aura bar so its "max" tracks a
 * per-player session peak instead of upstream Covenant's hardcoded 2 M cap.
 *
 * <p>Targets {@code net.llenzzz.covenant_of_the_seven.gui.ResourceBarOverlay}
 * — the {@code IGuiOverlay} that renders the combined Cursed-Ring blood bar,
 * the Virtue-Ring aura bar, and the Iron's Spellbooks mana bar in a single
 * branching {@code render} method. We only touch the Virtue-Ring branch.
 *
 * <p>Bytecode-verified call sites in the {@code render} method
 * (Covenant 2.2.6-hotfix):
 *
 * <ul>
 *   <li>Offset 146: {@code ldc int 2000000} → stored to var 7 (the "max"
 *       used as the {@code ddiv} divisor at offset 540 for fill width).
 *       <strong>This is the constant we want to replace.</strong></li>
 *   <li>Offset 257: {@code ldc int 2000000} → used in a tier-comparison
 *       {@code if_icmpge} to pick between {@code aurabar_high.png} and
 *       {@code aurabar_creational.png} textures. Leave this one alone — it
 *       keeps the visual tier feedback working at absolute aura values.</li>
 *   <li>Offset 634: {@code String.valueOf(int)} → formats the current aura
 *       digits for the on-bar label (only invoked in the Virtue-Ring branch;
 *       the LP and mana branches use {@code invokedynamic makeConcatWithConstants}
 *       to format {@code current/max} strings). Redirect to inject the peak
 *       suffix.</li>
 * </ul>
 *
 * <p>The previous attempt at {@code MixinAuraContainerOverlay} silently
 * no-op'd because {@code AuraContainerOverlay} appears to be the altar UI
 * class, not the HUD. Switching to {@code ResourceBarOverlay} is the actual
 * fix; everything else was correct.
 *
 * <p>{@code @Pseudo} + class-name targeting required because Covenant is an
 * optional dependency. The plugin ({@code ArsNSpellsMixinPlugin}) additionally
 * gates this mixin behind {@code sanctifiedPresent} so Covenant-less installs
 * skip it entirely.
 *
 * <p>{@code priority = 1500} runs us before Covenant's own internal mixins
 * (which sit at the default 1000). Defensive — Covenant doesn't currently
 * self-mixin this class, but if upstream ever adds a self-mixin we want our
 * transformations to compose cleanly.
 */
@Pseudo
@Mixin(value = {}, targets = "net.llenzzz.covenant_of_the_seven.gui.ResourceBarOverlay", remap = false, priority = 1500)
public abstract class MixinResourceBarOverlay {

    /**
     * Replace ONLY the first {@code 2_000_000} literal in {@code render} —
     * the one at bytecode offset 146 used as the fill-width divisor. The
     * second ({@code ordinal = 1}, offset 257) is a tier-color threshold
     * and intentionally stays at the upstream value.
     */
    @ModifyConstant(
        method = "render",
        constant = @Constant(intValue = 2_000_000, ordinal = 0),
        require = 0
    )
    private int arsnspells$replaceAuraMaxDivisor(int original) {
        return ClientAuraPeakTracker.getPeak();
    }

    /**
     * Rewrite the bar's label from just the current aura to
     * {@code "current / peak"}. Only the Virtue-Ring branch reaches this
     * {@code String.valueOf(int)} call — the other branches format their
     * labels through {@code invokedynamic makeConcatWithConstants} — so the
     * redirect is scoped to the aura path even without an ordinal pin.
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
