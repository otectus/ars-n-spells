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

    @Override
    public void onLoad(String mixinPackage) {
        ironsPresent = resourceExists("io/redspace/ironsspellbooks/api/spells/AbstractSpell.class")
            && resourceExists("io/redspace/ironsspellbooks/api/magic/MagicData.class");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("MixinIronsSpellDamage")
            || mixinClassName.endsWith("MixinIronsManaBarOverlay")
            || mixinClassName.endsWith("MixinIronsMagicDataMana")
            || mixinClassName.endsWith("MixinSpellStatsPotency")) {
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

    private static boolean resourceExists(String resourcePath) {
        return ArsNSpellsMixinPlugin.class.getClassLoader().getResource(resourcePath) != null;
    }
}
