package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.contract.CarrierPolicy;
import io.redspace.ironsspellbooks.api.spells.CastSource;

/**
 * Maps a loader-neutral {@link CarrierPolicy} onto Iron's own {@link CastSource} (audit V02).
 *
 * <p>This is the <b>only</b> place a {@code CastSource} is chosen, and it chooses from the
 * carrier's item kind alone. The value serialized onto a cross-cast item is ignored for billing:
 * it is edit-controllable, it survives being copied onto a different item, and in the pinned
 * Iron's 1.21.1-3.16.3 it decides whether the cast costs anything at all.
 * {@code CastSource.consumesMana()} is true only for {@code SPELLBOOK} (and {@code SWORD} under a
 * server config), {@code AbstractSpell.castSpell} skips the mana subtraction entirely when it is
 * false, and {@code respectsCooldown()} is false for everything but those two. The old default of
 * {@code SCROLL} therefore made every cross-cast free and cooldown-exempt.
 *
 * <p><b>Why this is its own class.</b> It must not be a method on {@code CrossCastingHandler}.
 * That class is an {@code @EventBusSubscriber}, and NeoForge's automatic registration walks
 * {@code getDeclaredMethods()}, which resolves every declared method's return type - so a method
 * returning {@code CastSource} makes the whole handler fail to load on an install without Iron's.
 * Here the type appears only behind {@link com.otectus.arsnspells.compat.IronsCompat#isLoaded()}.
 */
public final class IronsCastSourceAdapter {

    private IronsCastSourceAdapter() {}

    /**
     * The native cast source giving {@code carrier}'s semantics.
     *
     * <p>A reusable carrier gets {@link CastSource#SPELLBOOK}, the one source Iron's both charges
     * mana for and applies a cooldown to. A real scroll keeps {@link CastSource#SCROLL}, because a
     * scroll is paid for by being consumed. A cast with no ANS carrier is not ours to price, so it
     * also keeps scroll semantics rather than silently gaining a cooldown it never had.
     */
    public static CastSource forCarrier(CarrierPolicy carrier) {
        return carrier == CarrierPolicy.REUSABLE_BOOK_SEMANTICS
            ? CastSource.SPELLBOOK
            : CastSource.SCROLL;
    }
}
