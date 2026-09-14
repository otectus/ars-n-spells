package com.otectus.arsnspells.mixin.sanctified;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mixin to intercept Sanctified Legacy's AbstractSpellMixin and override death penalty behavior.
 * This allows our config to control whether insufficient LP causes death or just cancels the spell.
 * 
 * Priority: 900 (runs BEFORE Sanctified Legacy's mixin at priority 1000)
 */
@Mixin(value = AbstractSpell.class, priority = 900, remap = false)
public abstract class MixinSanctifiedAbstractSpell {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinSanctifiedAbstractSpell.class);
    
    /**
     * Intercept the canBeCraftedBy method which Sanctified Legacy uses to check LP.
     * We inject BEFORE their mixin to apply our config settings.
     */
    @Inject(
        method = "canBeCraftedBy",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void arsnspells$checkLPWithConfig(Player player, CallbackInfoReturnable<Boolean> cir) {
        // Only intercept for server-side players wearing Cursed Ring
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }
        
        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            return;
        }
        
        // Audit F13: this bypass must track the LP system's own master toggle, not
        // mana unification — CursedRingHandler / IronsLPHandler are gated on
        // ENABLE_LP_SYSTEM. Guarding on the unification toggle meant that with
        // unification off + LP on, ANS charged LP while Covenant's native check
        // still ran (the double-penalty this mixin exists to prevent), and with
        // unification on + LP off, Covenant's native handling was bypassed with
        // nobody charging LP at all.
        if (!AnsConfig.ENABLE_LP_SYSTEM.get()) {
            return;
        }
        
        LOGGER.debug("Intercepting Sanctified Legacy LP check for {}", player.getName().getString());

        // Our LP system (IronsLPHandler / CursedRingHandler) handles this player's LP costs.
        // Return true to bypass Sanctified Legacy's native LP check and death penalty.
        // This prevents the "instant death" bug when scrolls or spells trigger
        // Sanctified Legacy's handler before our system processes the cost.
        cir.setReturnValue(true);
    }

    /**
     * Covenant 2.2.6-hotfix modifies the first boolean local in Iron's
     * {@code canBeCastedBy} method, turning the native mana check into a bypass
     * whenever either ring is worn.  Keep that bypass only for a resource path
     * that is actually enabled: ANS settles Cursed Ring LP, while active Virtue
     * aura remains Covenant-native.  With either toggle off the original Iron's
     * mana result must survive, otherwise the ring would make casts free.
     *
     * <p>{@code require = 0} keeps this compatible with Iron's builds whose
     * method name or local layout differs; the pinned 3.3.0 Covenant profile has
     * the exact {@code canBeCastedBy(...)}/ordinal-zero surface.
     */
    @ModifyVariable(method = "canBeCastedBy", at = @At("STORE"), ordinal = 0, require = 0)
    private boolean arsnspells$gateNativeManaBypass(
        boolean value, int spellLevel, CastSource castSource, MagicData playerMagicData, Player player) {
        if (!(player instanceof ServerPlayer) || !SanctifiedLegacyCompat.isAvailable()) {
            return value;
        }
        if (SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            return AnsConfig.ENABLE_LP_SYSTEM.get();
        }
        if (SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
            return AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM.get();
        }
        return value;
    }
}
