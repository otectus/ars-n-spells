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
        };
        for (String fqn : arsMixins) {
            assertTrue(plugin.shouldApplyMixin("any.target", fqn),
                fqn + " must apply unconditionally (Ars is a hard dependency)");
        }
    }

    @Test
    void mixinArsPotionEffects_isGatedOnIronsAbsence() throws Exception {
        // ANS 3.0.1: targets an Ars class but its bytecode references Iron's
        // AttributeRegistry — un-gated it crashed Iron's-less servers at boot
        // (ClassMetadataNotFoundException while transforming ManaCapEvents,
        // taking Ars Nouveau down with it). The inject is only meaningful when
        // Iron's is primary, so nothing is lost by gating.
        ArsNSpellsMixinPlugin plugin = newPluginWithIronsPresent(false);
        assertFalse(plugin.shouldApplyMixin(
                "com.hollingsworth.arsnouveau.common.event.ManaCapEvents",
                "com.otectus.arsnspells.mixin.ars.MixinArsPotionEffects"),
            "MixinArsPotionEffects must NOT apply when Iron's is absent");
    }

    @Test
    void mixinArsPotionEffects_appliesWhenIronsPresent() throws Exception {
        ArsNSpellsMixinPlugin plugin = newPluginWithIronsPresent(true);
        assertTrue(plugin.shouldApplyMixin(
                "com.hollingsworth.arsnouveau.common.event.ManaCapEvents",
                "com.otectus.arsnspells.mixin.ars.MixinArsPotionEffects"),
            "MixinArsPotionEffects must apply when Iron's is present");
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
