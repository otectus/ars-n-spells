package com.otectus.arsnspells.util;

import com.otectus.arsnspells.compat.IronsCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Startup tripwire for silently-skipped mixins.
 *
 * <p>Every injection in this mod carries {@code require = 0}, and the Iron's half now lives in
 * a {@code "required": false} config — both deliberately, so that a drifted target degrades one
 * feature instead of aborting mod load for the whole pack. The cost of that choice is that
 * failure is <em>invisible</em>: the feature simply stops working, with nothing in the log.
 *
 * <p><b>What this checks, and why the obvious check is worthless.</b> An earlier version of
 * this class asked "does the method the mixin targets still exist?". That answers the wrong
 * question twice over. It cannot see whether the injection actually applied, and for a target
 * inherited from a superclass — {@code Scroll.use}, inherited from vanilla {@code Item} — a
 * hierarchy-walking lookup finds the inherited method and reports OK even when the mixin never
 * applied at all. That is precisely the failure it appeared to cover: on the 1.20.1 line
 * {@code MixinScrollItem} matched nothing in a released jar and scrolls cast completely free
 * for several releases, silently.
 *
 * <p>So instead this asks the direct question: <b>did our code get merged into the target
 * class?</b> When a mixin applies, its handler methods are merged into the target, where they
 * are visible via {@code getDeclaredMethods()}. A target class with no {@code arsnspells$}
 * member did not receive our mixin, full stop.
 *
 * <p>{@code Scroll} gets a second, mapping-independent probe: whether it still overrides
 * {@code use} at all, matched on the parameter signature rather than the name, since the name
 * is what differs between a development and a production mapping set.
 *
 * <p>Runs at {@code FMLLoadCompleteEvent}, not common setup. Inspecting a class loads it, and
 * loading Iron's classes early can interfere with other mods' mixins that have not been applied
 * yet. By load-complete every config in the pack has been through PREPARE and APPLY.
 *
 * <p>Failures are logged, never thrown. A degraded integration is still better than a crash,
 * and the point is to give a user one line to grep for.
 */
public final class MixinSelfCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinSelfCheck.class);

    private MixinSelfCheck() {}

    /** A mixin target: the human-facing feature name and the class our code merges into. */
    private record Probe(String feature, String targetClass) {}

    private static final Probe[] ARS_PROBES = {
        new Probe("ManaCap.bridge", "com.hollingsworth.arsnouveau.common.capability.ManaCap"),
        new Probe("SpellResolver.cost", "com.hollingsworth.arsnouveau.api.spell.SpellResolver"),
    };

    private static final Probe[] IRONS_PROBES = {
        new Probe("MagicData.mana", "io.redspace.ironsspellbooks.api.magic.MagicData"),
        new Probe("AbstractSpell.castGate", "io.redspace.ironsspellbooks.api.spells.AbstractSpell"),
        new Probe("Scroll.cost", "io.redspace.ironsspellbooks.item.Scroll"),
        new Probe("InscriptionTable.guard",
            "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu"),
    };

    /**
     * Run the check and log the result. Call once, from {@code FMLLoadCompleteEvent}.
     */
    public static void run() {
        StringBuilder report = new StringBuilder("[SelfCheck] ");
        List<String> degraded = new ArrayList<>();

        for (Probe probe : ARS_PROBES) {
            appendProbe(report, degraded, probe);
        }

        if (!IronsCompat.isLoaded()) {
            report.append(" | Iron's=absent");
            LOGGER.info(report.toString());
            return;
        }

        for (Probe probe : IRONS_PROBES) {
            appendProbe(report, degraded, probe);
        }
        appendAccessorProbe(report, degraded);
        appendScrollUseProbe(report, degraded);

        if (degraded.isEmpty()) {
            LOGGER.info(report.toString());
            return;
        }
        LOGGER.error(report.toString());
        LOGGER.error("[SelfCheck] These features are silently OFF: {}", String.join(", ", degraded));
        LOGGER.error("[SelfCheck] Injections use require = 0 and the Iron's mixins are in a");
        LOGGER.error("[SelfCheck] non-required config, so load did not fail - but the features are inert.");
        LOGGER.error("[SelfCheck] The usual cause is another mod's @Overwrite on the same method,");
        LOGGER.error("[SelfCheck] or an Ars Nouveau / Iron's Spellbooks version outside what this");
        LOGGER.error("[SelfCheck] build was tested against. Check the early-startup log for");
        LOGGER.error("[SelfCheck] 'ars_n_spells.compat.mixins.json' warnings.");
    }

    /** Did any of our handlers get merged into the target class? */
    private static void appendProbe(StringBuilder report, List<String> degraded, Probe probe) {
        report.append(" | ").append(probe.feature()).append('=');
        try {
            Class<?> target = Class.forName(probe.targetClass(), false,
                MixinSelfCheck.class.getClassLoader());
            // getDeclaredMethods, NOT a hierarchy walk: a merged handler lands on the target
            // class itself, and walking up would let an unrelated superclass member pass.
            for (Method m : target.getDeclaredMethods()) {
                if (m.getName().contains("arsnspells$")) {
                    report.append("OK");
                    return;
                }
            }
            report.append("NOT-APPLIED");
            degraded.add(probe.feature());
        } catch (Throwable t) {
            report.append("ERROR(").append(t.getClass().getSimpleName()).append(')');
            degraded.add(probe.feature());
        }
    }

    /**
     * The accessor mixin injects an interface onto {@code MagicData}. If it did not apply,
     * {@code isAssignableFrom} is false and every cast through it fails at runtime — the one
     * case here that reflection can prove outright.
     */
    private static void appendAccessorProbe(StringBuilder report, List<String> degraded) {
        report.append(" | MagicDataAccessor=");
        try {
            Class<?> magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData",
                false, MixinSelfCheck.class.getClassLoader());
            Class<?> accessor = Class.forName(
                "com.otectus.arsnspells.mixin.irons.MagicDataAccessor",
                false, MixinSelfCheck.class.getClassLoader());
            if (accessor.isAssignableFrom(magicData)) {
                report.append("OK");
                return;
            }
            report.append("NOT-ATTACHED");
            degraded.add("MagicDataAccessor");
        } catch (Throwable t) {
            report.append("ERROR(").append(t.getClass().getSimpleName()).append(')');
            degraded.add("MagicDataAccessor");
        }
    }

    /**
     * Does {@code Scroll} still override {@code use}?
     *
     * <p>{@code MixinScrollItem} injects into that override, and the method's <em>name</em> is
     * mapping-dependent. Matching on the parameter signature instead keeps the probe valid in
     * both a development and a production mapping set — which is the difference that let scroll
     * cost enforcement silently no-op on the 1.20.1 line.
     */
    private static void appendScrollUseProbe(StringBuilder report, List<String> degraded) {
        report.append(" | Scroll.use=");
        try {
            ClassLoader cl = MixinSelfCheck.class.getClassLoader();
            Class<?> scroll = Class.forName("io.redspace.ironsspellbooks.item.Scroll", false, cl);
            Class<?> level = Class.forName("net.minecraft.world.level.Level", false, cl);
            Class<?> player = Class.forName("net.minecraft.world.entity.player.Player", false, cl);
            Class<?> hand = Class.forName("net.minecraft.world.InteractionHand", false, cl);
            for (Method m : scroll.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 3 && params[0] == level && params[1] == player
                    && params[2] == hand) {
                    report.append("OK");
                    return;
                }
            }
            report.append("MISSING");
            degraded.add("Scroll.use (scroll costs are not being charged)");
        } catch (Throwable t) {
            report.append("ERROR(").append(t.getClass().getSimpleName()).append(')');
            degraded.add("Scroll.use");
        }
    }
}
