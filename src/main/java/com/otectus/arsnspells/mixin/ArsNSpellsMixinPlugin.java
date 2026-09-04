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
    private boolean arsManaCapPresent;
    private boolean arsSpellResolverPresent;

    @Override
    public void onLoad(String mixinPackage) {
        ironsPresent = resourceExists("io/redspace/ironsspellbooks/api/spells/AbstractSpell.class")
            && resourceExists("io/redspace/ironsspellbooks/api/magic/MagicData.class");
        // Ars is a required dependency, but its version range is wide ([5.0.0,)),
        // so a future restructure could remove these targets. Probe them too so the
        // Ars-side mixins fail soft (skip) instead of crashing mod load on a class
        // rename. (require=0 on each inject covers method drift; this covers class drift.)
        arsManaCapPresent = resourceExists("com/hollingsworth/arsnouveau/common/capability/ManaCap.class")
            && resourceExists("com/hollingsworth/arsnouveau/common/capability/ManaData.class");
        arsSpellResolverPresent = resourceExists("com/hollingsworth/arsnouveau/api/spell/SpellResolver.class");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // ANS-CRIT-001: every mixin that names an Iron's class in its @Mixin target or
        // its own bytecode must be listed here. MixinIronsCastValidation and
        // MagicDataAccessor were once missing from this list, and an Iron's-less
        // dedicated server crashed in the mixin loader with NoClassDefFoundError before
        // any of the runtime gates could help.
        if (mixinClassName.endsWith("MixinIronsSpellPowerResonance")
            || mixinClassName.endsWith("MixinIronsMagicDataMana")
            || mixinClassName.endsWith("MixinIronsCastValidation")
            || mixinClassName.endsWith("MagicDataAccessor")
            || mixinClassName.endsWith("MixinScrollItem")
            || mixinClassName.endsWith("MixinInscriptionTableMenu")
            || mixinClassName.endsWith("MixinInscriptionTableScreen")
            || mixinClassName.endsWith("MixinAbstractSpellArsIcon")) {
            return ironsPresent;
        }
        if (mixinClassName.endsWith("MixinManaCapability")) {
            return arsManaCapPresent;
        }
        if (mixinClassName.endsWith("MixinSpellResolverMana")
            || mixinClassName.endsWith("MixinSpellResolverPreCast")) {
            return arsSpellResolverPresent;
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
