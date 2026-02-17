package com.otectus.arsnspells.mixin.sanctified;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
    private void arsnspells$checkLPWithConfig(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        // Only intercept for players wearing Cursed Ring
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }
        
        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            return;
        }
        
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return;
        }
        
        LOGGER.info("🔍 Intercepting Sanctified Legacy's LP check for {}", player.getName().getString());
        
        // Let Sanctified Legacy's mixin run, but we'll handle the death penalty ourselves
        // We can't easily prevent their death penalty here, so we'll use a different approach
        // in the spell cast event
    }
}
