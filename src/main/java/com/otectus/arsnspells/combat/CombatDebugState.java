package com.otectus.arsnspells.combat;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.spell.CrossCastContext;
import com.otectus.arsnspells.util.SpellAnalysis;
import com.otectus.arsnspells.util.SpellScalingUtil;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last damage event each half of the cross-mod combat bridge touched, per player, so
 * {@code /ans debug combat} can answer "why did my armour / my spell power do nothing?" without
 * a debugger attached.
 *
 * <p>The bug this exists for presented as "armour does nothing", and every candidate cause -
 * a missing attribute, a school that resolved to something other than the glyph's element, a
 * config toggle left off, or the formula itself - produced the same symptom. Each snapshot
 * therefore carries the whole chain: the inputs, the {@link SpellScalingUtil.SpellPowerBreakdown}
 * the formula produced, and the number before and after the bridge multiplied it.
 *
 * <p>One entry per player per side, overwritten on every hit. This is deliberately not a
 * history: a history costs memory on a hot path in exchange for detail nobody reads, and the
 * question being asked is always about the shot just fired.
 *
 * <p><b>Debug-off costs one boolean.</b> Both recorders test {@link AnsConfig#debugEnabled()}
 * as their first statement and return before allocating a record, resolving a school set, or
 * boxing a float. Nothing here logs, at any level: these methods run once per spell damage
 * event.
 *
 * <p>No Iron's Spellbooks type is imported. This class is reachable from
 * {@link com.otectus.arsnspells.events.StateEvictionHandler}, which is registered
 * unconditionally, so the Iron's spell id and school arrive as {@link String}s already resolved
 * by {@link IronsDamageBridge} - the only caller that is behind the Iron's-loaded gate.
 */
public final class CombatDebugState {

    private static final Map<UUID, ArsSnapshot> ARS = new ConcurrentHashMap<>();
    private static final Map<UUID, IronsSnapshot> IRONS = new ConcurrentHashMap<>();

    private CombatDebugState() {}

    /**
     * One Ars Nouveau spell hit after {@link ArsDamageBridge} scaled it.
     *
     * @param spellId    the first effect glyph's registry id, the closest thing an Ars spell has
     *                   to an identity, or {@code "?"} when the recipe had no effect
     * @param spellName  the player-given spell name, or {@code ""} for an unnamed cast
     * @param schools    every school {@link SpellAnalysis} resolved for the recipe - the set the
     *                   power policy chose from, not just the one that won
     * @param rawDamage  {@code event.damage} as the bridge received it
     * @param breakdown  the factors that produced the multiplier, reused verbatim from the
     *                   bridge's own call - nothing here recomputes the scaling
     * @param finalDamage {@code event.damage} after the multiply
     * @param crossCast  whether the caster had an in-flight cross-cast when the hit landed
     */
    public record ArsSnapshot(String spellId, String spellName, Set<String> schools,
                              float rawDamage, SpellScalingUtil.SpellPowerBreakdown breakdown,
                              float finalDamage, boolean crossCast) {}

    /**
     * One Iron's Spellbooks spell hit after {@link IronsDamageBridge} added the Ars perk.
     *
     * @param spellId      Iron's own spell id, resolved by the caller
     * @param school       the spell's school id, or {@code null} when Iron's did not supply one
     * @param nativeAmount the amount Iron's computed, before this mod touched it
     * @param arsBonus     the flat {@code PerkAttributes.SPELL_DAMAGE_BONUS} that was added
     * @param finalAmount  the amount after the addition
     */
    public record IronsSnapshot(String spellId, String school, float nativeAmount,
                                float arsBonus, float finalAmount) {}

    /**
     * Record an Ars spell hit, if debug mode is on.
     *
     * <p>The school set and the cross-cast flag are resolved <em>here</em>, after the flag test,
     * rather than in the bridge: that keeps the bridge's debug-off cost at one call and one
     * boolean, and keeps the re-analysis out of the damage path entirely when nobody is looking.
     */
    public static void recordArs(Player player, Spell spell,
                                 SpellScalingUtil.SpellPowerBreakdown breakdown,
                                 float rawDamage, float finalDamage) {
        if (!AnsConfig.debugEnabled()) {
            return;
        }
        if (player == null || spell == null || breakdown == null) {
            return;
        }

        SpellAnalysis.Result analysis = SpellAnalysis.analyze(spell);
        AbstractSpellPart firstEffect = analysis.firstEffect();
        String spellId = firstEffect == null || firstEffect.getRegistryName() == null
            ? "?" : firstEffect.getRegistryName().toString();
        String spellName = spell.name() == null ? "" : spell.name();

        // The Ars event carries no Iron's spell id, so CrossModSpellComponents.isArsCrossProxyId
        // has nothing to test here - by the time SpellDamageEvent.Pre is posted, a proxy cast is
        // an ordinary Ars resolve. The in-flight cross-cast entry is the one cheap signal that
        // survives that far: a map get on the caster's UUID, no registry scan and no item walk.
        boolean crossCast = CrossCastContext.peek(player) != null;

        ARS.put(player.getUUID(), new ArsSnapshot(spellId, spellName, analysis.schools(),
                                                  rawDamage, breakdown, finalDamage, crossCast));
    }

    /**
     * Record an Iron's spell hit, if debug mode is on.
     *
     * <p>{@code spellId} and {@code school} are already-resolved strings; see the class note on
     * why no Iron's type may appear in this signature.
     */
    public static void recordIrons(Player player, String spellId, String school,
                                   float nativeAmount, float arsBonus, float finalAmount) {
        if (!AnsConfig.debugEnabled()) {
            return;
        }
        if (player == null) {
            return;
        }
        IRONS.put(player.getUUID(),
                  new IronsSnapshot(spellId, school, nativeAmount, arsBonus, finalAmount));
    }

    /** The player's last recorded Ars hit, or {@code null} when nothing has been recorded. */
    public static ArsSnapshot lastArs(UUID playerId) {
        return playerId == null ? null : ARS.get(playerId);
    }

    /** The player's last recorded Iron's hit, or {@code null} when nothing has been recorded. */
    public static IronsSnapshot lastIrons(UUID playerId) {
        return playerId == null ? null : IRONS.get(playerId);
    }

    /** Drop one player's snapshots. Logout. */
    public static void clear(UUID playerId) {
        if (playerId != null) {
            ARS.remove(playerId);
            IRONS.remove(playerId);
        }
    }

    /** Drop every player's snapshots. Server stop / integrated-server exit. */
    public static void clearAll() {
        ARS.clear();
        IRONS.clear();
    }

    /**
     * Store an Ars snapshot with no config read. Package-private and test-only: the debug flag
     * is a {@code ModConfigSpec} value that cannot be loaded in a plain JUnit run, so the map
     * semantics (one entry, overwritten, evictable) are exercised through here while
     * {@link #recordArs} covers the gate itself.
     */
    static void storeArs(UUID playerId, ArsSnapshot snapshot) {
        ARS.put(playerId, snapshot);
    }

    /** {@link #storeArs} for the Iron's side. */
    static void storeIrons(UUID playerId, IronsSnapshot snapshot) {
        IRONS.put(playerId, snapshot);
    }
}
