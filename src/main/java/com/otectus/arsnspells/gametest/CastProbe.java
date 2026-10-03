package com.otectus.arsnspells.gametest;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameTest stand-in for another mod that acts inside Iron's native mana block in
 * {@code AbstractSpell.castSpell}, between the final cost event and Iron's own mana write.
 *
 * <p>Animus for NeoForge 1.21.1 injects at that call to check affordability and, when blood
 * magic casting is enabled, to pay a shortfall from the soul network and top the pool up. The
 * three modes reproduce those behaviours and an unconditional veto. The probe runs only for a
 * player a test armed, and only when {@code MixinCastProbe} is applied, which
 * {@code ArsNSpellsMixinPlugin} allows only with {@value #PROPERTY} set, as the GameTest run
 * configuration does.
 */
public final class CastProbe {
    /** The JVM property the GameTest run configuration sets to apply {@code MixinCastProbe}. */
    public static final String PROPERTY = "ans.gametest.castProbe";

    public enum Mode {
        /** Cancel the cast when the pool Iron's reads holds less than the event's cost. */
        AFFORDABILITY,
        /** Raise the pool Iron's reads to the event's cost when it holds less, then continue. */
        TOP_UP,
        /** Cancel the cast unconditionally. */
        VETO
    }

    private static final Map<UUID, Mode> MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> OBSERVED = new ConcurrentHashMap<>();

    private CastProbe() {}

    public static void arm(Player player, Mode mode) {
        MODES.put(player.getUUID(), mode);
        OBSERVED.remove(player.getUUID());
    }

    public static void disarm(Player player) {
        MODES.remove(player.getUUID());
        OBSERVED.remove(player.getUUID());
    }

    /** How many times the armed probe ran inside Iron's mana block for this player. */
    public static int observed(Player player) {
        return OBSERVED.getOrDefault(player.getUUID(), 0);
    }

    /** Called by {@code MixinCastProbe}; false cancels the native invocation. */
    public static boolean allowNativeBlock(ServerPlayer player, MagicData data, int cost) {
        Mode mode = player == null ? null : MODES.get(player.getUUID());
        if (mode == null) return true;
        OBSERVED.merge(player.getUUID(), 1, Integer::sum);
        return switch (mode) {
            case AFFORDABILITY -> data.getMana() >= cost;
            case TOP_UP -> {
                if (data.getMana() < cost) data.setMana(cost);
                yield true;
            }
            case VETO -> false;
        };
    }
}
