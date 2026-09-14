package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ScrollKind;
import net.minecraft.world.item.ItemStack;

/**
 * Decides what Iron's native Inscription Table should do with a given scroll.
 *
 * <p>Shared by the client screen mixin and the server menu mixin so both sides reach the
 * same verdict — a client-only guard would leave a dedicated server crashable by any
 * player with a legacy carrier, and a server-only guard would still let the client screen
 * NPE before the packet is ever sent.
 *
 * <p>Three non-trivial verdicts, because they need three different responses:
 * <ul>
 *   <li>{@link Verdict#BIND_CARRIER} — a well-formed ANS carrier. Iron's cannot read its
 *       payload: {@code getSpellAtIndex(0)} yields {@code SpellData.EMPTY}, whose
 *       {@code getSpell()} is a real {@code SpellRegistry.none()}, and {@code doInscription}
 *       would write that dud into the book and eat the scroll. Until 3.3.3 this was simply
 *       refused with advice to use the ritual; now the table performs the bind itself via
 *       {@link IronsTableBindHandler}, so the verdict names the action rather than the
 *       rejection.</li>
 *   <li>{@link Verdict#REJECT_NO_CONTAINER} — a scroll with no readable container at all.
 *       This is the crash: Iron's dereferences {@code ISpellContainer.get(...)} unguarded.
 *       Reached only when the reconciler could not repair the stack.</li>
 *   <li>{@link Verdict#REJECT_INVALID} — an ANS carrier ANS itself will not read: a newer
 *       schema, not exactly one Ars entry, or a payload that no longer deserializes.
 *       Binding it would produce a wheel entry that does nothing.</li>
 * </ul>
 *
 * <p><b>Iron's-gated.</b> Reached only from Iron's-targeting mixins and from the Iron's
 * profile GameTests.
 */
public final class IronsInscriptionPolicy {

    /** What the native inscription table should do with a scroll. */
    public enum Verdict {
        /** Not our concern — let Iron's handle it normally. */
        ALLOW,
        /** A valid ANS carrier; ANS binds it instead of Iron's inscribing it. */
        BIND_CARRIER,
        /** Malformed scroll with no readable native container; Iron's would NPE on it. */
        REJECT_NO_CONTAINER,
        /** An ANS carrier whose payload ANS refuses to bind. */
        REJECT_INVALID;

        /** True when the table must do nothing at all and say why. */
        public boolean isRejection() {
            return this == REJECT_NO_CONTAINER || this == REJECT_INVALID;
        }

        /** True when ANS takes the click over and performs the bind itself. */
        public boolean isBind() {
            return this == BIND_CARRIER;
        }

        /** Translation key for the message shown to the player on a rejection. */
        public String messageKey() {
            return this == REJECT_INVALID
                ? "message.ars_n_spells.bind.invalid_carrier"
                : "message.ars_n_spells.inscription.no_container";
        }
    }

    private IronsInscriptionPolicy() {}

    /**
     * Evaluate the stack sitting in the table's scroll slot.
     *
     * <p>A legacy carrier is reconciled <em>before</em> classification, not after: something
     * already holds the stack, the repair is deterministic, and a container-less carrier that
     * was classified first would be branded {@code INVALID} and refused forever even though
     * its Ars payload is perfectly good. Repairing here also means the same scroll stops
     * being a crash risk everywhere else, not just in front of this guard.
     */
    public static Verdict evaluate(ItemStack scroll) {
        if (scroll == null || scroll.isEmpty() || !IronsBookBindingUtil.isIronsScroll(scroll)) {
            return Verdict.ALLOW;
        }
        boolean hasSidecar = scroll.hasTag() && CrossCastNbt.hasCrossModSpells(scroll.getTag());
        if (hasSidecar && IronsCompat.isLoaded()) {
            CarrierReconciler.reconcile(scroll);
        }
        switch (ScrollKind.classify(scroll)) {
            case ANS_CARRIER:
                return Verdict.BIND_CARRIER;
            case INVALID:
                // Which rejection depends on which invariant broke: a sidecar we cannot read
                // is an ANS problem, no readable container is the Iron's crash.
                return hasSidecar ? Verdict.REJECT_INVALID : Verdict.REJECT_NO_CONTAINER;
            default:
                return Verdict.ALLOW;
        }
    }
}
