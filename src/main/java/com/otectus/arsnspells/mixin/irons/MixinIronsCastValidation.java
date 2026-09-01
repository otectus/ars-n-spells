package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.spell.CastValidationScope;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.network.chat.contents.TranslatableContents;
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
 * <p>Background — what this fixes:
 * {@code canBeCastedBy} reads {@code MagicData.getMana()} and returns
 * {@code CastResult.FAILURE} with "cast_error_mana" if mana &lt; cost — BEFORE
 * {@code SpellPreCastEvent} fires. That means our event-based handlers
 * ({@code IronsLPHandler}, and Covenant's own aura listener) never get a chance to
 * redirect the cost to LP / aura, AND any cross-mod mana unification in ARS_PRIMARY
 * mode is compared against the wrong scale.
 *
 * <p><b>Why this is a HEAD/RETURN pair and not a {@code @Redirect}.</b> Until 3.2.1
 * this was a single {@code @Redirect} onto the {@code INVOKE MagicData.getMana()}
 * instruction inside {@code canBeCastedBy}. Mixin rejects instruction-level injection
 * points outright when another mod has {@code @Overwrite}-merged the target method at
 * equal-or-higher priority, and that rejection is an {@code InvalidInjectionException}
 * thrown during PREPARE — before the {@code require} count is consulted, so
 * {@code require = 0} did not soften it. One Mana Bar overwrites {@code canBeCastedBy}
 * at priority 1000, and the resulting hard failure took down twelve mods in a user's
 * pack. {@code MethodHead} and {@code BeforeReturn} both override
 * {@code InjectionPoint.checkPriority} to return {@code true}, so HEAD and RETURN
 * injections can never hit that failure.
 *
 * <p><b>Do not reintroduce an instruction-level {@code @At} on this method, and do not
 * "fix" a future conflict by raising the mixin priority.</b> A higher priority would
 * dodge the same check, but it is an arms race that does not generalise, and it would
 * also insert our HEAD callback ahead of other mods' — leaking the ring bypass into
 * their logic. The priority stays at the default 1000 deliberately.
 *
 * <p>The adjustment itself lives in {@link CastValidationScope}: HEAD opens a
 * per-thread scope keyed on the {@code MagicData} instance, RETURN closes it, and the
 * pre-existing HEAD hook on {@code MagicData.getMana} in
 * {@link MixinIronsMagicDataMana} applies it. In stock Iron's,
 * {@code canBeCastedBy} calls {@code getMana()} exactly once, so the scope covers
 * precisely the one read the old redirect did; all other call sites (HUD, regen, cost
 * consumption in {@code castSpell}) still see the real value.
 *
 * <p>This class deliberately declares no fields. Mixin merges mixin state into the
 * target class, where an un-{@code @Unique} name can collide with
 * {@code AbstractSpell}'s own members or with another mod's mixin — so the logger and
 * its throttle map live on {@link CastValidationScope} instead.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsCastValidation {

    @Inject(method = "canBeCastedBy", at = @At("HEAD"), require = 0)
    private void arsnspells$openCastValidationScope(int spellLevel, CastSource castSource,
            MagicData playerMagicData, Player player, CallbackInfoReturnable<CastResult> cir) {
        if (playerMagicData == null) {
            return;
        }

        // Resolve the player from the MagicData rather than from the `player`
        // parameter: the consumer hook keys off MagicData.serverPlayer, and the ring
        // lookup must run against the same player the mana read will. This also
        // reproduces the old redirect's client-side bail exactly — a client MagicData
        // has a null serverPlayer, and neither side then applies any adjustment.
        ServerPlayer serverPlayer;
        try {
            serverPlayer = ((MagicDataAccessor) (Object) playerMagicData).arsnspells$getServerPlayer();
        } catch (Throwable t) {
            // MagicDataAccessor did not attach. The compat mixin config is
            // "required": false, so this is genuinely reachable — leave the gate alone.
            CastValidationScope.clear();
            return;
        }
        if (serverPlayer == null) {
            CastValidationScope.clear();
            return;
        }

        // ----- 1. Ring bypass (highest priority) -----
        // Virtue Ring bypass: Covenant of the Seven owns Iron's aura now — bypass
        // Iron's own mana check so Covenant's SpellPreCastEvent listener can take over.
        boolean wearsCursed = false;
        boolean wearsVirtue = false;
        try {
            if (SanctifiedLegacyCompat.isAvailable()) {
                wearsCursed = AnsConfig.ENABLE_LP_SYSTEM.get()
                    && SanctifiedLegacyCompat.isWearingCursedRing(serverPlayer);
                wearsVirtue = SanctifiedLegacyCompat.isWearingVirtueRing(serverPlayer);
            }
        } catch (IllegalStateException configNotReady) {
            // Config not loaded yet (very early game tick) — fall through to no-ring behaviour.
        }
        if (wearsCursed || wearsVirtue) {
            CastValidationScope.throttledLog(serverPlayer.getUUID(),
                "[CastValidation] Bypassing Iron's mana check for {} (cursed={}, virtue={})",
                serverPlayer.getName().getString(), wearsCursed, wearsVirtue);
            CastValidationScope.push(playerMagicData, serverPlayer.getUUID(), true, 0.0);
            return;
        }

        // ----- 2. ARS_PRIMARY cross-conversion -----
        double rate = 0.0;
        try {
            if (BridgeManager.isUnificationEnabled()
                && BridgeManager.getCurrentMode() == ManaUnificationMode.ARS_PRIMARY) {
                rate = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
            }
        } catch (IllegalStateException configNotReady) {
            // Same — fall through.
        }
        if (rate > 0.0) {
            CastValidationScope.push(playerMagicData, serverPlayer.getUUID(), false, rate);
            return;
        }

        // ----- 3. Normal flow: nothing to adjust -----
        CastValidationScope.clear();
    }

    /**
     * Close the scope, and rescue a ring wearer whose cast was refused for mana anyway.
     *
     * <p>The {@code getMana} route is the primary mechanism, but it only works if the
     * body of {@code canBeCastedBy} actually reads mana through
     * {@code MagicData.getMana()}. A mod that {@code @Overwrite}s the method — the very
     * situation this rewrite exists to survive — may compute mana some other way, and
     * the ring bypass would then silently stop working. This second, independent layer
     * catches that: if a ring bypass is in effect and the method is nonetheless about
     * to return the "not enough mana" failure, turn it into a success. On the normal
     * path the mana read already returned {@code Float.MAX_VALUE}, so the result is
     * never a mana failure and this branch never fires — it is purely additive.
     *
     * <p>{@code @At("RETURN")} matches every exit instruction, so this can fire more
     * than once per call; {@link CastValidationScope#clear()} is idempotent.
     */
    @Inject(method = "canBeCastedBy", at = @At("RETURN"), cancellable = true, require = 0)
    private void arsnspells$closeCastValidationScope(int spellLevel, CastSource castSource,
            MagicData playerMagicData, Player player, CallbackInfoReturnable<CastResult> cir) {
        try {
            if (CastValidationScope.isRingBypass(playerMagicData)
                && arsnspells$isManaFailure(cir.getReturnValue())) {
                cir.setReturnValue(new CastResult(CastResult.Type.SUCCESS));
            }
        } catch (Throwable ignored) {
            // A rescue that throws must never break the cast gate.
        } finally {
            CastValidationScope.clear();
        }
    }

    /** Whether a {@link CastResult} is Iron's "not enough mana" refusal specifically. */
    private static boolean arsnspells$isManaFailure(CastResult result) {
        if (result == null || result.isSuccess() || result.message == null) {
            return false;
        }
        return result.message.getContents() instanceof TranslatableContents contents
            && "ui.irons_spellbooks.cast_error_mana".equals(contents.getKey());
    }
}
