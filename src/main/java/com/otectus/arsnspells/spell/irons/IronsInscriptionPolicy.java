package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.world.item.ItemStack;

/**
 * Decides whether Iron's native Inscription Table may act on a given scroll.
 *
 * <p>Shared by the client screen mixin and the server menu mixin so both sides reach the
 * same verdict — a client-only guard would leave a dedicated server crashable by any
 * player with a legacy carrier, and a server-only guard would still let the client screen
 * NPE before the packet is ever sent.
 *
 * <p>Two distinct rejections, because they need different advice:
 * <ul>
 *   <li>{@link Verdict#ANS_CARRIER} — a well-formed ANS carrier. It has a valid native
 *       container (so nothing would crash), but its payload is an Ars spell in the ANS
 *       sidecar that Iron's cannot read. Letting the table proceed would consume the
 *       scroll and write an empty {@code SpellRegistry.none()} entry into the player's
 *       book, because {@code getSpellAtIndex(0)} yields {@code SpellData.EMPTY} and
 *       Iron's inscribes it without checking. Direct the player at the Spell Loom
 *       workflow instead.</li>
 *   <li>{@link Verdict#NO_NATIVE_CONTAINER} — a scroll with no container at all. This is
 *       the crash: Iron's dereferences {@code ISpellContainer.get(...)} unguarded. Covers
 *       ANS carriers created before the container fix (which cannot be repaired in place
 *       from here, since they may sit in any inventory or chest) and any other source of
 *       a malformed scroll.</li>
 * </ul>
 *
 * <p><b>Iron's-gated.</b> Reached only from Iron's-targeting mixins.
 */
public final class IronsInscriptionPolicy {

    /** What the native inscription table should do with a scroll. */
    public enum Verdict {
        /** Not our concern — let Iron's handle it normally. */
        ALLOW,
        /** A valid ANS carrier; Iron's table cannot read its payload. */
        ANS_CARRIER,
        /** Malformed scroll with no native container; Iron's would NPE on it. */
        NO_NATIVE_CONTAINER;

        public boolean isRejection() {
            return this != ALLOW;
        }

        /** Translation key for the message shown to the player. */
        public String messageKey() {
            return this == ANS_CARRIER
                ? "message.ars_n_spells.inscription.ans_carrier"
                : "message.ars_n_spells.inscription.no_container";
        }
    }

    private IronsInscriptionPolicy() {}

    /**
     * Evaluate the stack sitting in the table's scroll slot.
     *
     * <p>The ANS-carrier test comes first and keys off the sidecar, not the container:
     * once the export fix is in, carriers <em>do</em> have a valid container, so a
     * container-presence test alone would wave them through.
     */
    public static Verdict evaluate(ItemStack scroll) {
        if (scroll == null || scroll.isEmpty() || !IronsBookBindingUtil.isIronsScroll(scroll)) {
            return Verdict.ALLOW;
        }
        if (scroll.hasTag() && CrossCastNbt.hasCrossModSpells(scroll.getTag())) {
            // A legacy carrier reaching the table is a repair opportunity: something already
            // holds the stack. Repairing here means the same scroll stops being a crash risk
            // everywhere else too, not just in front of this guard.
            CarrierReconciler.reconcile(scroll);
            return Verdict.ANS_CARRIER;
        }
        if (!IronsScrollFactory.hasNativeContainer(scroll)) {
            return Verdict.NO_NATIVE_CONTAINER;
        }
        return Verdict.ALLOW;
    }
}
