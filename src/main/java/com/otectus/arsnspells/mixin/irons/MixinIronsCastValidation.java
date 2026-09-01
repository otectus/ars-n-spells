package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.spell.CastValidationScope;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scopes the mana-value adjustment that applies inside Iron's
 * {@code AbstractSpell.canBeCastedBy}.
 *
 * <p>Background — what this fixes: {@code canBeCastedBy} reads {@code MagicData.getMana()} and
 * returns {@code CastResult.FAILURE} with "cast_error_mana" if mana &lt; cost, BEFORE
 * {@code SpellPreCastEvent} fires. That means any cross-mod mana unification in ARS_PRIMARY
 * mode is compared against the wrong scale.
 *
 * <p><b>Why this is a HEAD/RETURN pair and not a {@code @Redirect}.</b> This was a single
 * {@code @Redirect} onto the {@code INVOKE MagicData.getMana()} instruction inside
 * {@code canBeCastedBy}. Mixin rejects instruction-level injection points outright when another
 * mod has {@code @Overwrite}-merged the target method at equal-or-higher priority, and that
 * rejection is an {@code InvalidInjectionException} thrown during PREPARE — before the
 * {@code require} count is consulted, so {@code require = 0} did not soften it. One Mana Bar
 * overwrites {@code canBeCastedBy} at priority 1000, and on the 1.20.1 line the resulting hard
 * failure took down twelve mods in a user's pack. {@code MethodHead} and {@code BeforeReturn}
 * both override {@code InjectionPoint.checkPriority} to return {@code true}, so HEAD and RETURN
 * injections can never hit that failure.
 *
 * <p><b>Do not reintroduce an instruction-level {@code @At} on this method, and do not "fix" a
 * future conflict by raising the mixin priority.</b> A higher priority would dodge the same
 * check, but it is an arms race that does not generalise, and it would also insert our HEAD
 * callback ahead of other mods'. The priority stays at the default 1000 deliberately.
 *
 * <p>The adjustment itself lives in {@link CastValidationScope}: HEAD opens a per-thread scope
 * keyed on the {@code MagicData} instance, RETURN closes it, and the pre-existing HEAD hook on
 * {@code MagicData.getMana} in {@link MixinIronsMagicDataMana} applies it. In stock Iron's,
 * {@code canBeCastedBy} calls {@code getMana()} exactly once, so the scope covers precisely the
 * one read the old redirect did; all other call sites (HUD, regen, cost consumption in
 * {@code castSpell}) still see the real value.
 *
 * <p>This class deliberately declares no fields. Mixin merges mixin state into the target
 * class, where an un-{@code @Unique} name can collide with {@code AbstractSpell}'s own members
 * or with another mod's mixin — so the logger and its throttle map live on
 * {@link CastValidationScope} instead.
 *
 * <p><b>Covenant of the Seven on 1.21.1.</b> The 1.20.1 build folded a third behaviour in here:
 * a Cursed/Virtue ring wearer got {@code Float.MAX_VALUE} so Iron's own mana gate stepped aside
 * and Covenant's LP/aura listener could take the cost instead. Covenant has no 1.21.1 release,
 * so that branch has no consumer and is not carried here. It belongs at the top of the HEAD
 * hook if the subsystem is ever re-enabled, because the bypass must win over the conversion.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsCastValidation {

    @Inject(method = "canBeCastedBy", at = @At("HEAD"), require = 0)
    private void arsnspells$openCastValidationScope(int spellLevel, CastSource castSource,
            MagicData playerMagicData, Player player, CallbackInfoReturnable<CastResult> cir) {
        if (playerMagicData == null) {
            return;
        }

        // Resolve the player from the MagicData rather than from the `player` parameter: the
        // consumer hook keys off MagicData.serverPlayer, and the read must run against the
        // same player. This also reproduces the old redirect's client-side bail exactly — a
        // client MagicData has a null serverPlayer, and neither side then applies anything.
        ServerPlayer serverPlayer;
        try {
            serverPlayer = ((MagicDataAccessor) (Object) playerMagicData).arsnspells$getServerPlayer();
        } catch (Throwable t) {
            // MagicDataAccessor did not attach. The compat mixin config is "required": false,
            // so this is genuinely reachable — leave the gate alone.
            CastValidationScope.clear();
            return;
        }
        if (serverPlayer == null) {
            CastValidationScope.clear();
            return;
        }

        // ARS_PRIMARY cross-conversion: Iron's is about to compare this value against a cost
        // denominated in Iron's mana, but in ARS_PRIMARY the pool being spent is Ars's. The
        // consumer divides by the rate so the comparison happens on one scale.
        double rate = 0.0;
        try {
            if (BridgeManager.isUnificationEnabled()
                && BridgeManager.getCurrentMode() == ManaUnificationMode.ARS_PRIMARY) {
                rate = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
            }
        } catch (IllegalStateException configNotReady) {
            // Config not loaded yet (very early game tick) — fall through to no adjustment.
        }
        if (rate > 0.0) {
            if (AnsConfig.debugEnabled()) {
                CastValidationScope.throttledLog(serverPlayer.getUUID(),
                    "[CastValidation] ARS_PRIMARY scaling mana for {} (rate={})",
                    serverPlayer.getName().getString(), rate);
            }
            CastValidationScope.push(playerMagicData, serverPlayer.getUUID(), rate);
            return;
        }

        // Normal flow: nothing to adjust.
        CastValidationScope.clear();
    }

    /**
     * Close the scope.
     *
     * <p>{@code @At("RETURN")} matches every exit instruction, so this can fire more than once
     * per call; {@link CastValidationScope#clear()} is idempotent. It also does not fire at all
     * when the method exits by throwing, which is why the scope additionally self-expires.
     */
    @Inject(method = "canBeCastedBy", at = @At("RETURN"), require = 0)
    private void arsnspells$closeCastValidationScope(int spellLevel, CastSource castSource,
            MagicData playerMagicData, Player player, CallbackInfoReturnable<CastResult> cir) {
        CastValidationScope.clear();
    }
}
