package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The only door from imported-glyph code to Iron's Spells 'n Spellbooks.
 *
 * <p>Common code (glyph parts, items, the Glyph Press, the payment plan) holds this interface and
 * never an Iron's type, so none of it resolves an Iron's class when Iron's is absent.
 * {@link IronGlyphBridges#get()} returns the Iron's implementation only when Iron's is loaded,
 * and the absent implementation otherwise.
 */
public interface IronGlyphBridge {
    /** Whether Iron's is present, so imported glyphs can run at all. */
    boolean present();

    /** The live state of an Iron's spell: whether it is registered and what Iron's config says about it. */
    SpellState spellState(ResourceLocation spell);

    /** Iron's live rarity name for {@code level} of {@code spell}, or null when unavailable. */
    @Nullable String rarity(ResourceLocation spell, int level);

    /** Whether {@code player} has learned {@code spell} in Iron's (always true for spells that need no learning). */
    boolean meetsLearning(Player player, ResourceLocation spell);

    /** Iron's own mana cost of {@code spell} at {@code level}, before cast-time listeners; NaN when unknown. */
    double manaCost(ResourceLocation spell, int level);

    /** Iron's own cast time of {@code spell} at {@code level} in ticks (charge or channel length); -1 when unknown. */
    int castTimeTicks(ResourceLocation spell, int level);

    /** Iron's translated spell name, or empty when Iron's cannot name it. */
    Optional<Component> spellName(ResourceLocation spell);

    /** The spell's own icon texture, as Iron's names it. */
    @Nullable ResourceLocation icon(ResourceLocation spell);

    /**
     * A genuine Iron's scroll holding {@code spell} at {@code level}, built exactly as Iron's own
     * inscription builds one (for the recipe viewer and tests); empty when Iron's cannot.
     */
    ItemStack filledScroll(ResourceLocation spell, int level);

    /** The single spell a genuine Iron's scroll holds, read strictly; see {@link ScrollReading}. */
    ScrollReading readScroll(ItemStack stack);

    /** Run one imported-glyph invocation. Only called with {@link #present()} true. */
    InvocationOutcome invoke(com.otectus.arsnspells.glyph.GlyphInvocation invocation);

    /** Whether a native Iron's cast slot is busy for this player (a cast in progress). */
    boolean nativeCastInProgress(Player player);

    /** Whether {@code spell} is on {@code player}'s native Iron's cooldown. */
    boolean onCooldown(Player player, ResourceLocation spell);

    /** Whether {@code player} holds an unused native recast of {@code spell}, which Iron's casts free and without a cooldown check. */
    boolean hasRecast(Player player, ResourceLocation spell);

    /** Iron's own cast level for a base level: equipment affinity bonuses and level events applied. */
    int levelFor(ResourceLocation spell, int level, Player player);

    /** Whether Iron's own config makes a creative player's casts free of mana. */
    boolean creativeFree(Player player);

    /** Whether Iron's own config exempts a creative player's casts from cooldowns. */
    boolean creativeNoCooldown(Player player);

    enum SpellState {
        /** Iron's is absent or the spell is not in its registry. */
        MISSING,
        /** Registered, but disabled in Iron's spell config. */
        DISABLED,
        ENABLED
    }

    /**
     * What a scroll holds, under the Press's rules: exactly one non-empty entry with a valid
     * level, in a genuine Iron's scroll that is not an ANS carrier.
     */
    record ScrollReading(Kind kind, @Nullable ResourceLocation spell, int level) {
        public enum Kind { OK, NOT_A_SCROLL, EMPTY, ARS_EXPORT, PROXY_SPELL, MULTIPLE, INVALID_LEVEL, UNREADABLE, NONE_SPELL }
        public static ScrollReading of(Kind kind) { return new ScrollReading(kind, null, 0); }
        public boolean ok() { return kind == Kind.OK; }
    }

    record InvocationOutcome(Status status, String reasonKey) {
        public enum Status { REJECTED, ACCEPTED_PENDING, EFFECT_APPLIED, COMPLETED, CANCELED }
        public static InvocationOutcome rejected(String reasonKey) { return new InvocationOutcome(Status.REJECTED, reasonKey); }
        public boolean applied() { return status == Status.EFFECT_APPLIED || status == Status.COMPLETED; }
    }
}
