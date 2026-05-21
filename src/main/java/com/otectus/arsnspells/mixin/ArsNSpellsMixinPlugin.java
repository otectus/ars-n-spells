package com.otectus.arsnspells.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin to conditionally apply Iron's Spellbooks mixins
 * when the dependency is present on the classpath.
 */
public class ArsNSpellsMixinPlugin implements IMixinConfigPlugin {
    private boolean ironsPresent;
    private boolean sanctifiedPresent;

    @Override
    public void onLoad(String mixinPackage) {
        // CRITICAL: do NOT classload these targets — other mods' mixins need them
        // un-loaded until the mixin processor reaches their config. getResource on
        // the .class path is the only safe probe inside an IMixinConfigPlugin.
        //
        // Earlier this method called Class.forName(name, false, loader); even with
        // initialize=false the JVM still classloads the target, which broke
        // mixins.covenant_of_the_seven.json:irons_spellbooks.AbstractSpellMixin —
        // Covenant's AbstractSpellMixin could not transform AbstractSpell because
        // our probe had already loaded it. See `MixinTargetAlreadyLoadedException`
        // in latest.log of the deployment where this crash was observed.
        ironsPresent = resourceExists("io/redspace/ironsspellbooks/api/spells/AbstractSpell.class")
            && resourceExists("io/redspace/ironsspellbooks/api/magic/MagicData.class");
        // ANS-HIGH-009: probe the Covenant-of-the-Seven main class. The mod that
        // people call "Sanctified Legacy" is actually published as
        // `covenant_of_the_seven` (mod ID) with main class
        // `net.llenzzz.covenant_of_the_seven.CovenantOfTheSeven`. Same
        // classpath-only probe as the Iron's-class checks above — must not
        // classload.
        sanctifiedPresent = resourceExists("net/llenzzz/covenant_of_the_seven/CovenantOfTheSeven.class");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // ANS-HIGH-009: MixinSanctifiedAbstractSpell needs BOTH Iron's (to load AbstractSpell)
        // AND Sanctified Legacy (which adds the canBeCraftedBy method). Without Sanctified,
        // require=0 saves us, but the explicit gate documents the intent and is robust to
        // future refactors that might add a require=1 inject.
        if (mixinClassName.endsWith("MixinSanctifiedAbstractSpell")) {
            return ironsPresent && sanctifiedPresent;
        }
        // ANS-CRIT-001: MixinIronsCastValidation and MagicDataAccessor were missing
        // from this gated list — both directly target Iron's classes, so on an
        // Iron's-less server the mixin loader was crashing with NoClassDefFoundError.
        if (mixinClassName.endsWith("MixinIronsSpellDamage")
            || mixinClassName.endsWith("MixinIronsManaBarOverlay")
            || mixinClassName.endsWith("MixinIronsMagicDataMana")
            || mixinClassName.endsWith("MixinIronsCastValidation")
            || mixinClassName.endsWith("MagicDataAccessor")
            || mixinClassName.endsWith("MixinScrollItem")) {
            return ironsPresent;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No-op
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op
    }

    /**
     * Probe a class file's existence on the classpath WITHOUT classloading it.
     *
     * <p>We used to call {@code Class.forName(name, false, loader)} here, but even
     * with {@code initialize=false} the JVM still classloads the target. That
     * broke other mods' mixin preparation when our probe ran inside {@code onLoad}
     * — any subsequent mixin targeting the probed class would fail with
     * {@code MixinTargetAlreadyLoadedException}. Specifically
     * {@code mixins.covenant_of_the_seven.json:irons_spellbooks.AbstractSpellMixin}
     * could not transform {@code AbstractSpell} after our probe touched it.
     *
     * <p>{@code getResource} on a {@code .class} path checks the classpath manifest
     * only — it never invokes the classloader's resolution step, so the target
     * stays un-loaded and downstream mixins can still attach.
     */
    private static boolean resourceExists(String classFilePath) {
        return ArsNSpellsMixinPlugin.class.getClassLoader().getResource(classFilePath) != null;
    }
}
