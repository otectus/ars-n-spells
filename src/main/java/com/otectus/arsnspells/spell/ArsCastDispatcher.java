package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.otectus.arsnspells.spell.target.ValidatedCastTarget;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/**
 * The one place an ANS carrier hands an Ars spell to its resolver.
 *
 * <p>Ars resolves a cast through three entry points, and a form decides what each one does:
 * {@link SpellResolver#onCast} for nothing in particular, {@code onCastOnBlock} and
 * {@code onCastOnEntity} for a block or living target. Touch only resolves through the last two;
 * its {@code onCast} always fails. Before 3.4.0 every carrier called {@code onCast}, so every
 * Touch spell on a scroll, a generic carrier or a spellbook wheel slot failed. This class picks
 * the entry point exactly as an Ars spellbook does ({@code AbstractCaster.castSpell} in 5.13.1),
 * so every form keeps its native meaning: Self still resolves on the caster, Projectile still
 * launches from the caster, and Touch resolves on what is in reach.
 *
 * <p>Each entry point wraps Ars's own validation, cast event, form logic and mana expenditure,
 * which is where ANS's payment hooks sit. Effects are never called directly.
 */
public final class ArsCastDispatcher {

    /** Which resolver entry point a dispatch used, or why none was. */
    public enum Entry { ON_CAST, ON_CAST_ON_BLOCK, ON_CAST_ON_ENTITY, REFUSED_NO_TARGET, REFUSED_REQUEST }

    /** The outcome of one dispatch. {@code accepted} is the resolver's own success answer. */
    public record Result(Entry entry, boolean accepted, String detail) {
        public boolean refused() {
            return entry == Entry.REFUSED_NO_TARGET || entry == Entry.REFUSED_REQUEST;
        }
    }

    private ArsCastDispatcher() {
    }

    /**
     * Whether {@code form} only resolves against a target. Touch is the only such form in Ars
     * 5.13.1; addon forms keep their own entry-point behaviour.
     */
    public static boolean requiresTarget(AbstractCastMethod form) {
        return form instanceof MethodTouch;
    }

    /**
     * Run {@code resolver} against {@code target}.
     *
     * <p>A form that needs a target and has none is refused here, before the resolver runs: no
     * cast event fires, nothing is reserved, and nothing is paid or put on cooldown.
     *
     * @param casterTool the carrier the spell came from, handed to Ars as the casting stack
     * @param hand       the hand the cast is attributed to; block casts carry it in their context
     */
    public static Result dispatch(ServerPlayer player, ItemStack casterTool, InteractionHand hand,
                                  SpellResolver resolver, ValidatedCastTarget target, UUID attemptId) {
        AbstractCastMethod form = resolver.castType;
        if (target.isRefused()) {
            trace(attemptId, player, Entry.REFUSED_REQUEST, target, false);
            return new Result(Entry.REFUSED_REQUEST, false, target.refusalKey());
        }
        if (target.kind() == ValidatedCastTarget.Kind.AIR && requiresTarget(form)) {
            trace(attemptId, player, Entry.REFUSED_NO_TARGET, target, false);
            return new Result(Entry.REFUSED_NO_TARGET, false, "message.ars_n_spells.cast.target.none");
        }
        return switch (target.kind()) {
            case ENTITY -> {
                boolean accepted = resolver.onCastOnEntity(casterTool, target.entity(), hand);
                trace(attemptId, player, Entry.ON_CAST_ON_ENTITY, target, accepted);
                yield new Result(Entry.ON_CAST_ON_ENTITY, accepted, target.describe());
            }
            case BLOCK -> {
                // The UseOnContext overload is what an Ars spellbook uses for a player: the
                // BlockHitResult overload is the turret path, and Projectile's turns the caster.
                boolean accepted = resolver.onCastOnBlock(new UseOnContext(player, hand, target.block()));
                trace(attemptId, player, Entry.ON_CAST_ON_BLOCK, target, accepted);
                yield new Result(Entry.ON_CAST_ON_BLOCK, accepted, target.describe());
            }
            default -> {
                boolean accepted = resolver.onCast(casterTool, player.level());
                trace(attemptId, player, Entry.ON_CAST, target, accepted);
                yield new Result(Entry.ON_CAST, accepted, target.describe());
            }
        };
    }

    private static void trace(UUID attemptId, ServerPlayer player, Entry entry, ValidatedCastTarget target, boolean accepted) {
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S, CrossCastTrace.Stage.DISPATCH,
            "entry", entry, "target", target.describe(), "accepted", accepted,
            "missType", target.block() != null && target.block().getType() == HitResult.Type.MISS);
    }
}
