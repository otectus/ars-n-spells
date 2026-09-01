package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.common.util.PortUtil;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.CastingAuthority;
import com.otectus.arsnspells.spell.CrossCastContext;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

/**
 * PRE-CAST VALIDATION MIXIN
 *
 * <p>This is the HARD GATE that prevents Ars Nouveau spells from executing if the
 * player does not have sufficient resources.
 *
 * <p>Injects at the HEAD of {@code SpellResolver.canCast()} to validate BEFORE any
 * spell logic executes. {@code canCast()} is called by {@code onCast()},
 * {@code onCastOnBlock()} and {@code onCastOnEntity()} — covering every Ars cast path.
 *
 * <p><b>Why it takes full ownership of the result.</b> We cancel the native
 * {@code canCast()} outright rather than letting it continue, because Ars's own
 * {@code enoughMana()} reads {@code ManaCap.getCurrentMana()}, which can be stale in
 * ISS_PRIMARY mode where {@code playerOnTick} is suppressed. Since we bypass the rest
 * of the method we must also replicate its native recipe validation — hence the
 * explicit {@code getSpellCastingSpellValidator().validate(...)} call.
 *
 * <p><b>Covenant of the Seven on 1.21.1.</b> The 1.20.1 build also validated Cursed
 * Ring LP and Virtue Ring aura in the {@code cost <= 0} branch, because those handlers
 * zero the mana cost during {@code SpellCostCalcEvent} and stash a pending alternate
 * cost. Covenant has no 1.21.1 release, so a zero cost here means only "genuinely free
 * spell". Covenant of the Seven has no 1.21.1 release, so that path has no consumer here.
 */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverPreCast {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinSpellResolverPreCast.class);

    @Shadow public SpellContext spellContext;
    @Shadow public Spell spell;
    @Shadow public boolean silent;
    @Shadow public abstract int getResolveCost();

    @Inject(method = "canCast", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$validatePreCast(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        // When mana unification is off, let Ars Nouveau handle canCast() natively: with no
        // bridged pool there is nothing for us to validate against, and interposing would
        // only risk disagreeing with Ars's own accounting.
        //
        // The 1.20.1 build had a second clause here, allowing a Covenant ring's ACTIVE cost
        // path to take over validation even with unification off. Inert on 1.21.1.
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }

        // Replicate native spell recipe validation since we bypass the rest of canCast().
        // This checks: non-empty recipe, starts with a cast method, at most one cast method,
        // augment caps and glyph limits — matching Ars Nouveau's StandardSpellValidator.
        // Ars 5.x made Spell.recipe private; unsafeList() is the List-typed accessor the
        // validator's signature wants (recipe() returns only an Iterable).
        List<SpellValidationError> validationErrors = ArsNouveauAPI.getInstance()
            .getSpellCastingSpellValidator()
            .validate(this.spell.unsafeList());

        if (!validationErrors.isEmpty()) {
            if (!this.silent) {
                PortUtil.sendMessageNoSpam(entity, validationErrors.get(0).makeTextComponentExisting());
            }
            arsnspells$finish(cir, false);
            return;
        }

        SpellResolver resolver = (SpellResolver) (Object) this;

        int cost = resolver.getResolveCost();

        if (player.isCreative()) {
            arsnspells$finish(cir, true);
            return;
        }

        if (cost <= 0) {
            // Zero-cost spell: always allow, bypassing native enoughMana() so a stale
            // ManaCap read cannot deny a spell that costs nothing.
            arsnspells$finish(cir, true);
            return;
        }

        LOGGER.debug("PRE-CAST VALIDATION: Player={}, Cost={}", player.getName().getString(), cost);

        // HARD GATE: validate resources BEFORE spell execution.
        boolean canCast = CastingAuthority.canCastArsSpell(player, resolver);

        if (!canCast) {
            LOGGER.warn("SPELL CAST DENIED for {}", player.getName().getString());
        }

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        UUID attemptId = entry != null ? entry.attemptId : null;
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.RESOURCE_CHECK,
            "cost", cost, "approved", canCast);

        // Always set the return value — both pass and fail — to prevent Ars's native
        // enoughMana() from running against possibly-stale ManaCap data.
        arsnspells$finish(cir, canCast);
    }

    /** Set the result and cancel. */
    private static void arsnspells$finish(CallbackInfoReturnable<Boolean> cir, boolean result) {
        cir.setReturnValue(result);
        cir.cancel();
    }
}
