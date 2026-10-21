package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.spell.ISpellCaster;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.PlayerCaster;
import com.hollingsworth.arsnouveau.api.util.SpellUtil;
import com.hollingsworth.arsnouveau.common.block.tile.ScribesTile;
import com.hollingsworth.arsnouveau.common.datagen.BlockTagProvider;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSensitive;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.spell.target.CastTargetKind;
import com.otectus.arsnspells.spell.target.InteractionOrigin;
import com.otectus.arsnspells.spell.target.TargetHint;
import com.otectus.arsnspells.spell.target.ValidatedCastTarget;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/**
 * The one place an ANS carrier hands an Ars spell to Ars's resolver.
 *
 * <p>Generic inscribed carriers and the Iron's native proxy ({@code ArsCrossProxySpell}) both
 * cast through here, so a Touch spell meets the same target rules whichever way it was
 * started. The target is the server's own: {@link SpellUtil#rayTrace} with the caster's block
 * reach plus Ars's half-block margin and Sensitive's fluid picking, which is exactly what
 * {@code ISpellCaster.castSpell} uses for a native spell book in Ars 4.12.7. That includes its
 * entity rule: Ars picks a living entity on the look ray within five blocks without testing
 * block occlusion, and this dispatcher deliberately keeps that native behaviour rather than
 * inventing a stricter one for ANS carriers only.
 *
 * <p>The pick then selects the resolver entry exactly as a native book does: a living entity
 * goes to {@link SpellResolver#onCastOnEntity}, a block (or Sensitive's fluid/air face) to
 * {@link SpellResolver#onCastOnBlock(UseOnContext)}, and anything else to
 * {@link SpellResolver#onCast}. Each form keeps its own semantics there: Projectile and Self
 * ignore the block or entity they were handed, and only Touch needs one. The entry methods
 * carry Ars's validator, the cancellable {@code SpellCastEvent}, ANS's reservation and the
 * expenditure, so nothing here applies an effect directly.
 *
 * <p>Two refusals happen before the resolver is entered. A block entity that owns right-click
 * defers to the block, as a native book does. A Touch spell with nothing living or solid in
 * reach is a miss: Ars would post a {@code SpellCastEvent}, reserve the price and only then let
 * Touch fail, so a miss looked like a cast to every listener (ANS's own school training among
 * them). It is refused cleanly instead, with nothing posted, reserved, paid or trained.
 */
public final class ArsCastDispatcher {
    private ArsCastDispatcher() {}

    public enum Outcome {
        /** Ars's resolver ran the cast and reported success. */
        RESOLVED,
        /** The resolver refused: its validator, mana, a vetoed cast event, or the form itself. */
        RESOLVER_REFUSED,
        /** Touch with nothing it can act on in reach. Refused before the resolver. */
        NO_TARGET,
        /** The pick landed on a block entity that owns right-click; the native book defers too. */
        DEFERRED_TO_BLOCK,
        /** The recipe was empty or invalid once the caster's pre-cast hook had run. */
        INVALID_SPELL,
        /** The pick did not survive validation: another world, an unloaded chunk, the world border. */
        INVALID_TARGET
    }

    /** Which resolver entry the cast used, for traces and tests. */
    public enum Entry { NONE, GENERIC, BLOCK, ENTITY }

    /**
     * @param caster      the player casting
     * @param spellCaster the Ars caster wrapper for the carrier; its resolver hook stamps the school
     * @param tool        the item physically used to cast: the held carrier, Iron's staff or scroll,
     *                    or the Iron's book itself for a hotkey cast. It becomes the context's caster
     *                    tool, which Ars reads for tool discounts. It is never the reason a book is found.
     * @param hand        the hand Ars reports as casting
     * @param spell       the decoded, already validated recipe
     * @param origin      which input produced the request
     * @param hint        the client's aim when the request was sent; compared, never trusted
     * @param attemptId   the trace correlation id
     */
    public record Request(ServerPlayer caster, ISpellCaster spellCaster, ItemStack tool, InteractionHand hand,
                          Spell spell, InteractionOrigin origin, TargetHint hint, UUID attemptId) {
        public Request {
            if (caster == null || spellCaster == null || spell == null) throw new IllegalArgumentException("Incomplete cast request");
            tool = tool == null ? ItemStack.EMPTY : tool;
            hand = hand == null ? InteractionHand.MAIN_HAND : hand;
            origin = origin == null ? InteractionOrigin.SERVER : origin;
            hint = hint == null ? TargetHint.NONE : hint;
            attemptId = attemptId == null ? UUID.randomUUID() : attemptId;
        }
    }

    public record Result(Outcome outcome, Entry entry, ValidatedCastTarget target, boolean hintAgreed) {
        public boolean resolved() { return outcome == Outcome.RESOLVED; }
    }

    public static Result dispatch(Request request) {
        ServerPlayer player = request.caster();
        Level level = player.level();
        Spell spell = request.spellCaster().modifySpellBeforeCasting(level, player, request.hand(), request.spell());
        if (spell == null || !spell.isValid()) {
            trace(request, "invalid_spell", null, Entry.NONE, true);
            return new Result(Outcome.INVALID_SPELL, Entry.NONE, null, true);
        }
        SpellContext context = new SpellContext(level, spell, player, new PlayerCaster(player), request.tool());
        SpellResolver resolver = request.spellCaster().getSpellResolver(context, level, player, request.hand());
        boolean sensitive = resolver.spell.getBuffsAtIndex(0, player, AugmentSensitive.INSTANCE) > 0;
        HitResult pick = SpellUtil.rayTrace(player, 0.5 + player.getBlockReach(), 0.0f, sensitive);
        ValidatedCastTarget target = ValidatedCastTarget.of(level, pick);
        boolean agreed = request.hint().agreesWith(target);

        if (!withinWorld(level, pick)) {
            trace(request, "invalid_target", target, Entry.NONE, agreed);
            return new Result(Outcome.INVALID_TARGET, Entry.NONE, target, agreed);
        }
        if (pick instanceof BlockHitResult blockHit) {
            BlockEntity tile = level.getBlockEntity(blockHit.getBlockPos());
            if (tile instanceof ScribesTile
                || (!player.isShiftKeyDown() && tile != null
                    && !level.getBlockState(blockHit.getBlockPos()).is(BlockTagProvider.IGNORE_TILE))) {
                trace(request, "deferred_to_block", target, Entry.NONE, agreed);
                return new Result(Outcome.DEFERRED_TO_BLOCK, Entry.NONE, target, agreed);
            }
        }

        if (pick instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            boolean ok = resolver.onCastOnEntity(request.tool(), living, request.hand());
            return finish(request, resolver, ok, Entry.ENTITY, target, agreed);
        }
        if (pick instanceof BlockHitResult blockHit && (pick.getType() == HitResult.Type.BLOCK || sensitive)) {
            boolean ok = resolver.onCastOnBlock(new UseOnContext(player, request.hand(), blockHit));
            return finish(request, resolver, ok, Entry.BLOCK, target, agreed);
        }
        if (resolver.castType instanceof MethodTouch) {
            trace(request, "no_target", target, Entry.NONE, agreed);
            player.displayClientMessage(Component.translatable("message.ars_n_spells.cast.touch_no_target")
                .withStyle(ChatFormatting.GRAY), true);
            return new Result(Outcome.NO_TARGET, Entry.NONE, target, agreed);
        }
        boolean ok = resolver.onCast(request.tool(), level);
        return finish(request, resolver, ok, Entry.GENERIC, target, agreed);
    }

    private static Result finish(Request request, SpellResolver resolver, boolean ok, Entry entry,
                                 ValidatedCastTarget target, boolean agreed) {
        if (ok) {
            request.spellCaster().playSound(request.caster().getOnPos(), request.caster().level(), request.caster(),
                resolver.spell.sound, SoundSource.PLAYERS);
        }
        trace(request, ok ? "resolved" : "resolver_refused", target, entry, agreed);
        return new Result(ok ? Outcome.RESOLVED : Outcome.RESOLVER_REFUSED, entry, target, agreed);
    }

    /** The pick is inside the caster's world border and in a loaded chunk; an entity is still present. */
    private static boolean withinWorld(Level level, HitResult pick) {
        if (pick instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            return !entity.isRemoved() && entity.level() == level && level.getWorldBorder().isWithinBounds(entity.blockPosition())
                && level.isLoaded(entity.blockPosition());
        }
        if (pick instanceof BlockHitResult blockHit && pick.getType() == HitResult.Type.BLOCK) {
            return level.getWorldBorder().isWithinBounds(blockHit.getBlockPos()) && level.isLoaded(blockHit.getBlockPos());
        }
        return true;
    }

    private static void trace(Request request, String stage, ValidatedCastTarget target, Entry entry, boolean agreed) {
        CrossCastTrace.log(request.attemptId(), request.caster(), CrossCastTrace.Side.S, CrossCastTrace.Stage.DISPATCH,
            "result", stage, "origin", request.origin(), "entry", entry, "target", target == null ? "none" : target,
            "hint", request.hint().kind() == CastTargetKind.AIR ? "air" : (agreed ? "agreed" : "stale"),
            "form", request.spell().getCastMethod() == null ? "none" : request.spell().getCastMethod().getRegistryName());
    }
}
