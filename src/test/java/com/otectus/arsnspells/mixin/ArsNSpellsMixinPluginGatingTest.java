package com.otectus.arsnspells.mixin;

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
class ArsNSpellsMixinPluginGatingTest {

    private static ArsNSpellsMixinPlugin newPluginWithIronsPresent(boolean ironsPresent) throws Exception {
        ArsNSpellsMixinPlugin plugin = new ArsNSpellsMixinPlugin();
        Field f = ArsNSpellsMixinPlugin.class.getDeclaredField("ironsPresent");
        f.setAccessible(true);
        f.setBoolean(plugin, ironsPresent);
        return plugin;
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
            "MixinIronsSpellDamage",
            "MixinIronsManaBarOverlay",
            "MixinIronsMagicDataMana",
            "MixinScrollItem",
            "MixinSanctifiedAbstractSpell",
        };
        for (String suffix : gatedSuffixes) {
            String fqn = "com.otectus.arsnspells.mixin.irons." + suffix;
            assertFalse(plugin.shouldApplyMixin("any.target", fqn),
                suffix + " must NOT apply when Iron's is absent (regression check)");
        }
    }

    @Test
    void arsMixinsAlwaysApply() throws Exception {
        ArsNSpellsMixinPlugin plugin = newPluginWithIronsPresent(false);
        String[] arsMixins = {
            "com.otectus.arsnspells.mixin.ars.MixinManaCapability",
            "com.otectus.arsnspells.mixin.ars.MixinSpellResolverMana",
            "com.otectus.arsnspells.mixin.ars.MixinSpellResolverPreCast",
            "com.otectus.arsnspells.mixin.ars.MixinSpellResolverContext",
            "com.otectus.arsnspells.mixin.ars.MixinArsPotionEffects",
        };
        for (String fqn : arsMixins) {
            assertTrue(plugin.shouldApplyMixin("any.target", fqn),
                fqn + " must apply unconditionally (Ars is a hard dependency)");
        }
    }

    @Test
    void canLoadClass_returnsFalseForNonexistentClass() throws Exception {
        // Probe a guaranteed-absent class to confirm the probe is exception-safe.
        java.lang.reflect.Method canLoad = ArsNSpellsMixinPlugin.class
            .getDeclaredMethod("canLoadClass", String.class);
        canLoad.setAccessible(true);
        boolean result = (boolean) canLoad.invoke(null,
            "com.example.definitely.not.a.real.Class");
        assertFalse(result, "canLoadClass must return false for a non-existent class");
    }

    @Test
    void canLoadClass_returnsTrueForJavaLangObject() throws Exception {
        // Sanity probe: java.lang.Object is always present.
        java.lang.reflect.Method canLoad = ArsNSpellsMixinPlugin.class
            .getDeclaredMethod("canLoadClass", String.class);
        canLoad.setAccessible(true);
        boolean result = (boolean) canLoad.invoke(null, "java.lang.Object");
        assertTrue(result, "canLoadClass must return true for java.lang.Object");
    }
}
