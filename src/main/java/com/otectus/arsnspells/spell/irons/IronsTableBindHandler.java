package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.IronsSpellbookBinder;
import com.otectus.arsnspells.spell.ScrollKind;
import com.otectus.arsnspells.util.AdvancementUtil;
import io.redspace.ironsspellbooks.api.events.InscribeSpellEvent;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

/**
 * The Inscription Table half of the bind workflow: everything the menu mixin would otherwise
 * have to do inline.
 *
 * <p>Lives here rather than in the mixin for two reasons. A mixin body cannot be called from
 * a test, and this transaction — read two slots, classify, fire an event, bind, consume,
 * resync — is the part most worth testing; and keeping it in a plain class means the mixin
 * stays a router that is obviously correct by inspection.
 *
 * <p><b>Iron's-gated.</b> Reached only from {@code MixinInscriptionTableMenu}, which
 * {@code ArsNSpellsMixinPlugin} applies only when Iron's is present, and from GameTests that
 * run in the Iron's-loaded profile. It may therefore import Iron's API directly, which
 * {@link IronsSpellbookBinder} and {@link ScrollKind} deliberately may not.
 *
 * <p><b>{@code selectedSpellIndex} is ignored for carriers.</b> A bound Ars spell does not
 * occupy a player-chosen wheel slot: {@link IronsProxySlotWriter} appends it at
 * {@code getMaxSpellCount()} and grows the book's capacity by one, so it can never evict a
 * native spell. Honouring the player's selection would do exactly that. The practical effect
 * is that a carrier binds on the first click whether or not a wheel slot is selected, which
 * is also why the mixin router runs upstream of Iron's own {@code selectedSpellIndex >= 0}
 * precondition.
 */
public final class IronsTableBindHandler {

    private IronsTableBindHandler() {}

    /** What {@link #handle} did, in a shape both the mixin and a GameTest can assert on. */
    public enum Status {
        /** Not an ANS concern; Iron's own inscription logic must run unchanged. */
        PASS_THROUGH,
        /** ANS handled the click and refused it. Nothing was consumed or written. */
        REJECTED,
        /** Another mod cancelled {@link InscribeSpellEvent}. Nothing was consumed or written. */
        CANCELLED,
        /** The spell is now on the book and one scroll has been consumed. */
        BOUND
    }

    /**
     * Result of one table click.
     *
     * @param status what happened
     * @param result the binder's verdict, present for {@link Status#REJECTED} and
     *               {@link Status#BOUND} and null otherwise
     */
    public record Outcome(Status status, @Nullable IronsSpellbookBinder.BindResult result) {
        public boolean wasHandled() {
            return status != Status.PASS_THROUGH;
        }
    }

    private static final Outcome PASS_THROUGH = new Outcome(Status.PASS_THROUGH, null);
    private static final Outcome CANCELLED = new Outcome(Status.CANCELLED, null);

    /**
     * Handle an inscribe click on {@code menu} for {@code player}.
     *
     * <p>Replay-safe: a second click on an already-bound carrier either finds the scroll slot
     * empty (pass through) or hits {@link IronsSpellbookBinder.BindResult#DUPLICATE}, and
     * consumes nothing either way. All of it runs on the server thread inside the vanilla
     * button-click handler, so no other player action can interleave.
     */
    public static Outcome handle(ServerPlayer player, InscriptionTableMenu menu) {
        if (menu == null) {
            return PASS_THROUGH;
        }
        Slot scrollSlot = menu.getScrollSlot();
        Slot bookSlot = menu.getSpellBookSlot();
        if (scrollSlot == null || bookSlot == null) {
            return PASS_THROUGH;
        }
        ItemStack scroll = scrollSlot.getItem();
        ItemStack book = bookSlot.getItem();

        ScrollKind kind = ScrollKind.classify(scroll);
        if (kind == ScrollKind.INVALID) {
            // Reported, never repaired-by-deletion: the player keeps the item and can tell a
            // broken scroll from a rejected one.
            notify(player, "message.ars_n_spells.bind.invalid_carrier", ChatFormatting.RED);
            return new Outcome(Status.REJECTED, IronsSpellbookBinder.BindResult.INVALID_CARRIER);
        }
        if (kind != ScrollKind.ANS_CARRIER) {
            return PASS_THROUGH;
        }

        // Fire Iron's own event, with the same SpellData Iron's would have passed (the
        // carrier's native slot 0, which is a real SpellRegistry.none() entry), so a mod
        // listening for inscriptions still sees ours and can still veto it. Fired before any
        // mutation, so a cancel costs the player nothing.
        if (postInscribeEvent(player, scroll)) {
            return CANCELLED;
        }

        IronsSpellbookBinder.BindResult result = IronsSpellbookBinder.bind(
            player, scroll, book, IronsSpellbookBinder.Caller.TABLE);
        if (!result.wasAdded()) {
            notify(player, result.messageKey(), ChatFormatting.RED);
            return new Outcome(Status.REJECTED, result);
        }

        scrollSlot.remove(1);
        bookSlot.setChanged();
        menu.broadcastChanges();
        notify(player, IronsSpellbookBinder.BindResult.ADDED.messageKey(), ChatFormatting.GREEN);
        AdvancementUtil.grant(player, "bind_spell");
        return new Outcome(Status.BOUND, result);
    }

    /**
     * Post {@link InscribeSpellEvent} exactly as {@code clickMenuButton} does, and report
     * whether it was cancelled. A container read that throws is treated as "not cancelled":
     * the classifier already declared this carrier well-formed, and failing the bind because
     * the courtesy event could not be built would be the wrong trade.
     */
    private static boolean postInscribeEvent(ServerPlayer player, ItemStack scroll) {
        SpellData spellData;
        try {
            ISpellContainer container = ISpellContainer.get(scroll);
            spellData = container == null ? SpellData.EMPTY : container.getSpellAtIndex(0);
        } catch (Throwable ignored) {
            spellData = SpellData.EMPTY;
        }
        return MinecraftForge.EVENT_BUS.post(new InscribeSpellEvent(player, spellData));
    }

    private static void notify(ServerPlayer player, String key, ChatFormatting colour) {
        if (player != null) {
            player.displayClientMessage(
                Component.translatable(key).withStyle(colour), false);
        }
    }
}
