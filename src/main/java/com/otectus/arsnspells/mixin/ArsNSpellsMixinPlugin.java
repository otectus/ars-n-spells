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
        // ANS-CRIT-001: probe via Class.forName(name, false, loader) — this is more
        // reliable inside the mixin bootstrap than ClassLoader.getResource on .class
        // paths, and matches the canonical pattern used by ArsNSpells.canLoad.
        ironsPresent = canLoadClass("io.redspace.ironsspellbooks.api.spells.AbstractSpell")
            && canLoadClass("io.redspace.ironsspellbooks.api.magic.MagicData");
        // ANS-HIGH-009: probe a Sanctified Legacy marker class. MixinSanctifiedAbstractSpell
        // injects into the canBeCraftedBy method that Sanctified Legacy adds via its own
        // mixin to Iron's AbstractSpell. Without Sanctified, that target method is absent
        // and require=0 saves the inject from crashing — but the architecture is brittle
        // to refactors, so we gate explicitly.
        // NEEDS MANUAL VERIFICATION: the exact Sanctified marker class path. We try two
        // common patterns; if neither matches the actual jar, sanctifiedPresent stays
        // false and the mixin is skipped (regression-safe — Sanctified absent = no need).
        sanctifiedPresent = canLoadClass("net.sanctifiedlegacy.SanctifiedLegacy")
            || canLoadClass("com.dt.sanctifiedlegacy.SanctifiedLegacy")
            || canLoadClass("net.sanctified.SanctifiedMod");
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

    private static boolean canLoadClass(String fqn) {
        try {
            Class.forName(fqn, false, ArsNSpellsMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
