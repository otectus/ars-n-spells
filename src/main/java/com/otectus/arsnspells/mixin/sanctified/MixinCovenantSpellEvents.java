package com.otectus.arsnspells.mixin.sanctified;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Covenant's native Iron's handlers behind the 3.3.0 payment owner.
 *
 * <p>Covenant 2.2.6-hotfix registers static handlers in
 * {@code ISSSpellEvents}: the Cursed Ring handler debits LP during pre-cast and
 * {@code manaCostModifier} then changes the Iron's cost to zero; the Virtue Ring
 * handler does the same for ambient aura.  Those handlers do not know about
 * Ars 'n Spells' toggles and would either double-charge an ANS reservation or
 * make a disabled alternative path free.  This optional mixin is gated by the
 * config plugin on the exact pinned Covenant surface, so an absent or unrelated
 * Covenant build remains untouched.
 */
@Pseudo
@Mixin(value = {}, targets = "net.llenzzz.covenant_of_the_seven.events.ISSSpellEvents",
    remap = false, priority = 1500)
public abstract class MixinCovenantSpellEvents {

    @Inject(method = "cursedRingCastingEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private static void arsnspells$ownCursedPreCast(SpellPreCastEvent event, CallbackInfo ci) {
        Player player = event == null ? null : event.getEntity();
        // ANS owns this leg when the LP system is on.  When it is off, leaving
        // Covenant's native handler active would still bypass Iron's mana, so
        // suppress it in both cases and let the normal Iron's path continue.
        if (player != null && SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "virtueRingCastingEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private static void arsnspells$disableVirtuePreCastWhenOff(
        SpellPreCastEvent event, CallbackInfo ci) {
        Player player = event == null ? null : event.getEntity();
        if (player != null
            && SanctifiedLegacyCompat.isWearingVirtueRing(player)
            && !AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "manaCostModifier", at = @At("HEAD"), cancellable = true, require = 0)
    private static void arsnspells$keepNativeManaWhenAlternativeIsOwned(
        SpellOnCastEvent event, CallbackInfo ci) {
        Player player = event == null ? null : event.getEntity();
        if (player == null) return;

        boolean cursed = SanctifiedLegacyCompat.isWearingCursedRing(player);
        boolean virtueDisabled = SanctifiedLegacyCompat.isWearingVirtueRing(player)
            && !AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM.get();
        // Active Virtue aura deliberately remains Covenant-native: Covenant
        // owns the ambient-aura debit and ANS leaves its Iron's charge unset.
        if (cursed || virtueDisabled) {
            ci.cancel();
        }
    }
}
