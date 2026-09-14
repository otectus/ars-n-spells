package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The one place an Ars spell carried by an Iron's scroll becomes an entry on an Iron's
 * spellbook. Used by all three routes — the Spellbook Binding ritual, the
 * {@code /ans bind_scroll_to_irons_book} command, and Iron's own Inscription Table.
 *
 * <p>Before this existed each route re-implemented the same six checks in its own order,
 * and they drifted: the command skipped the kill switch for a while, the ritual reported a
 * failed native write as a scroll parse error, and the table route did not exist because
 * nobody wanted to write the checks a fourth time.
 *
 * <p><b>Validation only, plus the append.</b> The binder never consumes the scroll, never
 * sends a message, and never touches an inventory or a menu: consumption, feedback wording
 * and advancement grants belong to the caller, which is the only thing that knows whether it
 * is talking to a brazier, a command source or a container slot. Every check runs before any
 * mutation, so a non-{@link BindResult#ADDED} result leaves both stacks byte-identical.
 *
 * <p><b>No Iron's imports.</b> Item identity comes from {@link IronsBookBindingUtil}
 * (registry ids and an item tag) and the one Iron's-touching call is gated on
 * {@link IronsCompat#isLoaded()} and made by FQN, so this class loads with Iron's absent.
 */
public final class IronsSpellbookBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(IronsSpellbookBinder.class);

    /** Which route asked for the bind. Diagnostics only; the rules are identical for all three. */
    public enum Caller {
        /** The Spellbook Binding brazier ritual. */
        RITUAL,
        /** {@code /ans bind_scroll_to_irons_book}. */
        COMMAND,
        /** Iron's Inscription Table, rerouted by the ANS menu mixin. */
        TABLE
    }

    /**
     * Outcome of a bind attempt. Only {@link #ADDED} mutated the book; everything else is a
     * refusal that changed nothing.
     */
    public enum BindResult {
        /** The spell is now an entry on the book, with a native wheel slot to match. */
        ADDED,
        /** The book already carries an equivalent payload. */
        DUPLICATE,
        /** Every proxy pool slot allowed by the config cap is taken. */
        BOOK_FULL,
        /** {@code allow_ars_spells_in_irons_spellbooks} is false on this server. */
        DISABLED,
        /** The book stack is not an Iron's spellbook (or is empty). */
        NO_BOOK,
        /** The scroll carries no ANS payload at all — a native, blank or unrelated item. */
        NOT_A_CARRIER,
        /** An ANS carrier ANS refuses to read: wrong schema, wrong entry count, broken data. */
        INVALID_CARRIER,
        /** The payload parsed but does not deserialize into a castable spell. */
        UNCASTABLE,
        /** The append itself failed and was rolled back (native container refused the slot). */
        FAILED;

        /** True only for the one result that mutated the book. */
        public boolean wasAdded() {
            return this == ADDED;
        }

        /**
         * Generic translation key for this result, under {@code message.ars_n_spells.bind.*}.
         * The ritual and the command map onto their own older, wordier keys instead; the table
         * route, which has no keys of its own, uses these.
         */
        public String messageKey() {
            return "message.ars_n_spells.bind." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private IronsSpellbookBinder() {}

    /**
     * Append the Ars spell carried by {@code scroll} onto {@code book}.
     *
     * <p>Order matters and is the same for every caller: feature gate first (a server with
     * the feature off is not a "you are holding the wrong things" problem), then book
     * identity, then a reconcile pass on the scroll — a legacy container-less carrier is
     * repaired here rather than rejected, since something clearly has the stack in hand —
     * then classification, payload extraction, castability, and only then the append.
     *
     * @param actor  whoever is responsible, for diagnostics; may be null (the ritual has no
     *               owning player of its own)
     * @param scroll the carrier scroll; never consumed here
     * @param book   the Iron's spellbook; mutated only on {@link BindResult#ADDED}
     */
    public static BindResult bind(@Nullable ServerPlayer actor, ItemStack scroll, ItemStack book,
                                  Caller caller) {
        BindResult result = evaluateAndBind(scroll, book);
        if (AnsConfig.debugEnabled()) {
            LOGGER.info("[IronsSpellbookBinder] player={} caller={} kind={} result={}",
                actor != null ? actor.getGameProfile().getName() : "?", caller,
                ScrollKind.classify(scroll), result);
        }
        return result;
    }

    private static BindResult evaluateAndBind(ItemStack scroll, ItemStack book) {
        if (!AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.get()) {
            return BindResult.DISABLED;
        }
        if (book == null || book.isEmpty() || !IronsBookBindingUtil.isIronsSpellBook(book)) {
            return BindResult.NO_BOOK;
        }
        if (scroll == null || scroll.isEmpty()) {
            return BindResult.NOT_A_CARRIER;
        }
        // Repair before classifying, or a legacy carrier with no native container classifies
        // as INVALID and is refused forever even though its Ars payload is perfectly good.
        // Gated + FQN so the Iron's-importing reconciler only classloads with Iron's present.
        if (IronsCompat.isLoaded()) {
            com.otectus.arsnspells.spell.irons.CarrierReconciler.reconcile(scroll);
        }
        switch (ScrollKind.classify(scroll)) {
            case ANS_CARRIER:
                break;
            case INVALID:
                return BindResult.INVALID_CARRIER;
            default:
                return BindResult.NOT_A_CARRIER;
        }
        // The WHOLE entry, not just the ars spell payload: the Spell Loom's chosen name,
        // nature and icon are its siblings and must ride onto the book, or the bound wheel
        // entry loses the identity the player gave it at the loom.
        Optional<CrossModSpell> entryOpt = IronsBookBindingUtil.extractSingleEntry(scroll);
        if (entryOpt.isEmpty()) {
            return BindResult.INVALID_CARRIER;
        }
        CrossModSpell entry = entryOpt.get();
        CompoundTag arsTag = entry.arsSpellTag().orElse(null);
        if (!IronsBookBindingUtil.isCastableArsPayload(arsTag)) {
            return BindResult.UNCASTABLE;
        }
        int maxCap = AnsConfig.MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK.get();
        IronsBookBindingUtil.AppendResult append = IronsBookBindingUtil.appendArsSpellToBook(
            book, arsTag,
            entry.customName().orElse(null),
            entry.nature().orElse(null),
            entry.iconSymbol().orElse(null),
            maxCap);
        switch (append) {
            case ADDED:
                return BindResult.ADDED;
            case DUPLICATE:
                return BindResult.DUPLICATE;
            case BOOK_FULL:
                return BindResult.BOOK_FULL;
            case FAILED:
            default:
                return BindResult.FAILED;
        }
    }
}
