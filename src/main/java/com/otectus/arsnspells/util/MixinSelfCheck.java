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
 * <p>Every injection in this mod carries {@code require = 0}, deliberately: a target that
 * drifted must not abort mod load and take Ars Nouveau or Iron's Spellbooks down with it.
 * The cost of that choice is that drift is <em>invisible</em> — the feature simply stops
 * working, with nothing in the log. That is not hypothetical here: {@code
 * neoforge.mods.toml} accepts Iron's {@code [1.21.1-3.15.0, 1.21.1-4.0.0)}, eleven published
 * releases, and this mod mixes into Iron's non-API internals
 * ({@code gui.inscription_table.*}, {@code item.Scroll}, {@code MagicData}'s private
 * {@code serverPlayer} field). Only 3.16.3 has ever been verified.
 *
 * <p>So this checks, at common setup, that every method a mixin targets still exists on the
 * class it targets, and that the one interface-injecting mixin actually attached. It cannot
 * prove an injection point inside a method still matches — nothing short of running the
 * feature can — but a renamed or removed method is the drift that actually happens across
 * releases, and it is exactly the drift {@code require = 0} hides.
 *
 * <p>Failures are logged, never thrown. A degraded integration is still better than a crash,
 * and the point is to give a user one line to grep for.
 */
public final class MixinSelfCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinSelfCheck.class);

    private MixinSelfCheck() {}

    /** One mixin's target: the class it injects into and the methods it names. */
    private record Target(String mixin, String targetClass, String... methods) {}

    private static final Target[] ARS_TARGETS = {
        new Target("MixinManaCapability",
            "com.hollingsworth.arsnouveau.common.capability.ManaCap",
            "getCurrentMana", "setMana", "addMana", "removeMana", "getMaxMana", "setMaxMana"),
        new Target("MixinSpellResolverMana",
            "com.hollingsworth.arsnouveau.api.spell.SpellResolver", "expendMana"),
        new Target("MixinSpellResolverContext",
            "com.hollingsworth.arsnouveau.api.spell.SpellResolver", "canCast"),
        new Target("MixinSpellResolverPreCast",
            "com.hollingsworth.arsnouveau.api.spell.SpellResolver", "canCast"),
    };

    private static final Target[] IRONS_TARGETS = {
        new Target("MixinIronsSpellDamage",
            "io.redspace.ironsspellbooks.api.spells.AbstractSpell", "getSpellPower"),
        new Target("MixinIronsCastValidation",
            "io.redspace.ironsspellbooks.api.spells.AbstractSpell", "canBeCastedBy"),
        new Target("MixinIronsMagicDataMana",
            "io.redspace.ironsspellbooks.api.magic.MagicData", "getMana", "setMana", "addMana"),
        new Target("MixinScrollItem",
            "io.redspace.ironsspellbooks.item.Scroll", "use"),
        new Target("MixinInscriptionTableMenu",
            "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu",
            "clickMenuButton", "doInscription"),
    };

    /**
     * Run the check and log the result. Call once, at common setup — late enough that both
     * mods' classes are loadable, early enough that the line lands near the rest of this
     * mod's startup banner.
     */
    public static void run() {
        List<String> problems = new ArrayList<>();

        checkAll(ARS_TARGETS, problems);
        if (IronsCompat.isLoaded()) {
            checkAll(IRONS_TARGETS, problems);
            checkMagicDataAccessor(problems);
        }

        if (problems.isEmpty()) {
            LOGGER.info("[SelfCheck] Mixin targets OK ({} checked{})",
                ARS_TARGETS.length + (IronsCompat.isLoaded() ? IRONS_TARGETS.length + 1 : 0),
                IronsCompat.isLoaded() ? "" : ", Iron's absent");
            return;
        }
        LOGGER.error("========================================");
        LOGGER.error("[SelfCheck] Mixin targets have drifted - these features are silently OFF:");
        for (String problem : problems) {
            LOGGER.error("[SelfCheck]   {}", problem);
        }
        LOGGER.error("[SelfCheck] This usually means an Ars Nouveau or Iron's Spellbooks version");
        LOGGER.error("[SelfCheck] outside what this build was tested against. Injections use");
        LOGGER.error("[SelfCheck] require = 0 so load did not fail, but the features are inert.");
        LOGGER.error("========================================");
    }

    private static void checkAll(Target[] targets, List<String> problems) {
        for (Target target : targets) {
            Class<?> clazz;
            try {
                clazz = Class.forName(target.targetClass(), false,
                    MixinSelfCheck.class.getClassLoader());
            } catch (Throwable notPresent) {
                problems.add(target.mixin() + ": target class " + target.targetClass()
                    + " is absent");
                continue;
            }
            for (String method : target.methods()) {
                if (!hasMethod(clazz, method)) {
                    problems.add(target.mixin() + ": " + target.targetClass()
                        + "#" + method + " no longer exists");
                }
            }
        }
    }

    /**
     * The accessor mixin injects an interface onto Iron's {@code MagicData}. If it did not
     * apply, {@code isAssignableFrom} is false and every cast through it fails at runtime -
     * which is the one case here that reflection can prove outright.
     */
    private static void checkMagicDataAccessor(List<String> problems) {
        try {
            Class<?> magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData",
                false, MixinSelfCheck.class.getClassLoader());
            Class<?> accessor = Class.forName(
                "com.otectus.arsnspells.mixin.irons.MagicDataAccessor",
                false, MixinSelfCheck.class.getClassLoader());
            if (!accessor.isAssignableFrom(magicData)) {
                problems.add("MagicDataAccessor: not attached to MagicData");
            }
        } catch (Throwable t) {
            problems.add("MagicDataAccessor: could not verify (" + t.getClass().getSimpleName() + ")");
        }
    }

    /**
     * Name-only lookup, walking the hierarchy. Descriptors are not compared: a mixin's
     * {@code method = "name"} form matches by name too, and an overload set that gained or
     * lost a parameter is drift this check is not trying to adjudicate.
     */
    private static boolean hasMethod(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
