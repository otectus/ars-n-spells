package com.otectus.arsnspells.mixinplugin;

import com.otectus.arsnspells.TestPaths;
import com.otectus.arsnspells.mixin.ArsNSpellsMixinPlugin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-CRIT-001 — verifies that {@link ArsNSpellsMixinPlugin#shouldApplyMixin} now gates
 * {@code MixinIronsCastValidation} and {@code MagicDataAccessor} on Iron's presence.
 * Prior to the fix, both fell through to the unconditional {@code return true} path,
 * causing {@code NoClassDefFoundError} at mixin apply on Iron's-less servers.
 */
// Deliberately NOT in com.otectus.arsnspells.mixin. ModDevGradle's unitTest harness runs the
// suite with the Mixin transformer active, and Mixin refuses to load any class that sits inside
// a package declared in a mixins.json ("is in a defined mixin package ... and cannot be
// referenced directly"). On 1.20.1 the tests ran without the transformer, so the test could live
// beside the plugin it exercises; here it cannot.
class ArsNSpellsMixinPluginGatingTest {

    /**
     * A plugin with {@code ironsPresent} forced and every Ars probe satisfied.
     *
     * <p>This branch's plugin probes three Ars targets as well as Iron's, so the 1.20.1
     * harness (which set only {@code ironsPresent}) left them false and every Ars-side gate
     * reported "skip". {@code onLoad} is never called in a unit test, so all four flags have
     * to be set explicitly.
     */
    private static ArsNSpellsMixinPlugin newPluginWithIronsPresent(boolean ironsPresent) throws Exception {
        return newPlugin(ironsPresent, true);
    }

    private static ArsNSpellsMixinPlugin newPlugin(boolean ironsPresent, boolean arsPresent) throws Exception {
        ArsNSpellsMixinPlugin plugin = new ArsNSpellsMixinPlugin();
        set(plugin, "ironsPresent", ironsPresent);
        set(plugin, "arsManaCapPresent", arsPresent);
        set(plugin, "arsSpellResolverPresent", arsPresent);
        return plugin;
    }

    private static void set(ArsNSpellsMixinPlugin plugin, String field, boolean value) throws Exception {
        Field f = ArsNSpellsMixinPlugin.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setBoolean(plugin, value);
    }

    @Test
    void mixinIronsCastValidation_isGatedOnIronsAbsence() throws Exception {
        ArsNSpellsMixinPlugin plugin = newPluginWithIronsPresent(false);
        boolean apply = plugin.shouldApplyMixin(
            "io.redspace.ironsspellbooks.api.spells.AbstractSpell",
            "com.otectus.arsnspells.mixin.irons.MixinIronsCastValidation");
        assertFalse(apply, "MixinIronsCastValidation must NOT apply when Iron's is absent");
    }

    @Test
    void mixinIronsCastValidation_appliesWhenIronsPresent() throws Exception {
        ArsNSpellsMixinPlugin plugin = newPluginWithIronsPresent(true);
        boolean apply = plugin.shouldApplyMixin(
            "io.redspace.ironsspellbooks.api.spells.AbstractSpell",
            "com.otectus.arsnspells.mixin.irons.MixinIronsCastValidation");
        assertTrue(apply, "MixinIronsCastValidation must apply when Iron's is present");
    }

    @Test
    void magicDataAccessor_isGatedOnIronsAbsence() throws Exception {
        ArsNSpellsMixinPlugin plugin = newPluginWithIronsPresent(false);
        boolean apply = plugin.shouldApplyMixin(
            "io.redspace.ironsspellbooks.api.magic.MagicData",
            "com.otectus.arsnspells.mixin.irons.MagicDataAccessor");
        assertFalse(apply, "MagicDataAccessor must NOT apply when Iron's is absent");
    }

    @Test
    void magicDataAccessor_appliesWhenIronsPresent() throws Exception {
        ArsNSpellsMixinPlugin plugin = newPluginWithIronsPresent(true);
        boolean apply = plugin.shouldApplyMixin(
            "io.redspace.ironsspellbooks.api.magic.MagicData",
            "com.otectus.arsnspells.mixin.irons.MagicDataAccessor");
        assertTrue(apply, "MagicDataAccessor must apply when Iron's is present");
    }

    @Test
    void existingIronsGatedMixins_stillGate() throws Exception {
        ArsNSpellsMixinPlugin plugin = newPluginWithIronsPresent(false);
        String[] gatedSuffixes = {
            "MixinIronsSpellPowerResonance",
            "MixinIronsMagicDataMana",
            "MixinScrollItem",
            "MixinIronsCastValidation",
            "MagicDataAccessor",
            "MixinInscriptionTableMenu",
            "MixinInscriptionTableScreen",
            // MixinSanctifiedAbstractSpell is deliberately absent: Covenant of the Seven has
            // no 1.21.1 release, so that mixin is not in this build at all
            // (src/covenant-disabled). Asserting on a name the plugin no longer knows would
            // only test the unconditional fall-through.
        };
        for (String suffix : gatedSuffixes) {
            String fqn = "com.otectus.arsnspells.mixin.irons." + suffix;
            assertFalse(plugin.shouldApplyMixin("any.target", fqn),
                suffix + " must NOT apply when Iron's is absent (regression check)");
        }
    }

    private static final String[] ARS_MIXINS = {
        "com.otectus.arsnspells.mixin.ars.MixinManaCapability",
        "com.otectus.arsnspells.mixin.ars.MixinSpellResolverMana",
        "com.otectus.arsnspells.mixin.ars.MixinSpellResolverPreCast",
    };

    @Test
    void arsMixinsApplyWithoutIrons() throws Exception {
        // Ars is a hard dependency, so its mixins must not be collateral damage of the Iron's
        // gate - an Ars-only install still gets the whole mana bridge.
        ArsNSpellsMixinPlugin plugin = newPlugin(false, true);
        for (String fqn : ARS_MIXINS) {
            assertTrue(plugin.shouldApplyMixin("any.target", fqn),
                fqn + " must apply on an Ars-only install (Ars is a hard dependency)");
        }
    }

    @Test
    void arsMixinsSkipWhenTheirTargetClassIsGone() throws Exception {
        // Unlike 1.20.1, this branch also probes the Ars target classes, so an Ars restructure
        // makes the mixin skip instead of crashing mod load. `require = 0` on each inject
        // covers method drift; this covers class drift.
        ArsNSpellsMixinPlugin plugin = newPlugin(true, false);
        for (String fqn : ARS_MIXINS) {
            assertFalse(plugin.shouldApplyMixin("any.target", fqn),
                fqn + " must skip when its Ars target class is absent, not fail mod load");
        }
    }

    /**
     * {@code MixinArsPotionEffects} must stay deleted.
     *
     * <p>It mirrored the Ars {@code mana_regen} / {@code mana_boost} effects onto Iron's
     * attributes. Both halves were wrong against Ars 5.x: {@code ars_nouveau:mana_boost} is
     * not a registered {@code MobEffect} at all (only {@code MANA_REGEN_EFFECT} exists), so
     * the max-mana half could never fire; and {@code MANA_REGEN_EFFECT} already applies a
     * modifier to {@code PerkAttributes.MANA_REGEN_BONUS}, which
     * {@code EquipmentIntegration} reads and mirrors onto Iron's {@code MANA_REGEN} once per
     * second — so the mixin added the same potion's regen a second time. Its {@code @Inject}
     * also sat at the HEAD of {@code ManaCapEvents.playerOnTick}, ahead of Ars's own
     * {@code ServerPlayer} and interval guards, so it ran at 20 Hz on both logical sides.
     *
     * <p>The mod's own changelog already recorded the 1 Hz refresh as "replacing the old
     * MixinArsPotionEffects with no double-counting". The port shipped both.
     */
    @Test
    void mixinArsPotionEffects_staysDeleted() {
        assertFalse(TestPaths.of(
                "src/main/java/com/otectus/arsnspells/mixin/ars/MixinArsPotionEffects.java")
                .toFile().exists(),
            "MixinArsPotionEffects double-counts Ars mana regen against EquipmentIntegration");
        assertFalse(
            io_readMixinManifest().contains("MixinArsPotionEffects"),
            "MixinArsPotionEffects is still listed in ars_n_spells.mixins.json");
    }

    private static String io_readMixinManifest() {
        try {
            return java.nio.file.Files.readString(
                TestPaths.of("src/main/resources/ars_n_spells.mixins.json"));
        } catch (java.io.IOException e) {
            throw new AssertionError("could not read the mixin manifest", e);
        }
    }

    @Test
    void resourceExists_returnsFalseForNonexistentResource() throws Exception {
        // Probe a guaranteed-absent .class path to confirm the probe is exception-safe.
        java.lang.reflect.Method probe = ArsNSpellsMixinPlugin.class
            .getDeclaredMethod("resourceExists", String.class);
        probe.setAccessible(true);
        boolean result = (boolean) probe.invoke(null,
            "com/example/definitely/not/a/real/Class.class");
        assertFalse(result,
            "resourceExists must return false for a non-existent .class path");
    }

    @Test
    void resourceExists_returnsTrueForJavaLangObject() throws Exception {
        // Sanity probe: java.lang.Object's .class file is on every Java classpath.
        java.lang.reflect.Method probe = ArsNSpellsMixinPlugin.class
            .getDeclaredMethod("resourceExists", String.class);
        probe.setAccessible(true);
        boolean result = (boolean) probe.invoke(null, "java/lang/Object.class");
        assertTrue(result, "resourceExists must return true for java/lang/Object.class");
    }
}
