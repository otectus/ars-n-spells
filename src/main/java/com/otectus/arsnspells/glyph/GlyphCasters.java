package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.PlayerCaster;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Who may run an imported glyph (audit ANS-002): a real player, never automation.
 *
 * <p>The published contract ({@code docs/3.4.1/casting-contract.md}) reserves imported glyphs for
 * a real player casting through a player caster. Root admission and every delayed execution used to
 * exclude only Ars's {@code ANFakePlayer}; Forge's own {@link FakePlayer}, which every other mod's
 * automation uses, is also a {@link ServerPlayer} and passed. Both are refused here, and only here,
 * so the two boundaries cannot drift apart again. Ordinary {@code ServerPlayer} subclasses are not
 * refused: real server implementations may subclass it. ({@code ANFakePlayer} extends Forge's
 * {@code FakePlayer} at the pinned Ars 4.12.7; it is named as well so a later Ars that stops
 * extending it is still refused.)
 */
public final class GlyphCasters {
    private GlyphCasters() {}

    /** The real, live server player {@code entity} is, or null for anything else. */
    @Nullable
    public static ServerPlayer realPlayer(@Nullable Entity entity) {
        if (!(entity instanceof ServerPlayer player) || isAutomation(entity)) return null;
        return player.isRemoved() || !player.isAlive() ? null : player;
    }

    /** Whether {@code entity} is a fake player of either kind. */
    public static boolean isAutomation(@Nullable Entity entity) {
        return entity instanceof FakePlayer || entity instanceof com.hollingsworth.arsnouveau.api.ANFakePlayer;
    }

    /** Root admission: a real player, casting through a player caster. */
    @Nullable
    public static ServerPlayer admissible(@Nullable Entity entity, SpellContext context) {
        ServerPlayer player = realPlayer(entity);
        return player != null && context != null && context.getCaster() instanceof PlayerCaster ? player : null;
    }
}
