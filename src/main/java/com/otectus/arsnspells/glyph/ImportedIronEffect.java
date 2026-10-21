package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.items.Glyph;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * An Ars Nouveau effect that is one Iron's spell at one base level.
 *
 * <p>Registered at startup for every catalogued (spell, level) pair, whether or not Iron's is
 * installed, so a learned glyph and every saved spell that contains one keep their meaning: a
 * dormant glyph is still this part, never Ars's unknown-id fallback. This class references no
 * Iron's type; everything that touches Iron's runs through {@code IronGlyphBridge}.
 *
 * <p>Its Ars casting cost is zero, and the Ars cost of augments placed after it is zero as well
 * ({@code MixinAugmentCostForImportedGlyph}): the Iron's spell is priced in Iron's units by its own
 * cost, at the level the augments produce, as a separate payment leg. Charging it again inside
 * the Ars leg would make the player pay twice for the same effect.
 *
 * <p>Amplify and Dampen are the only effect augments, and only where they change something:
 * each raises or lowers the cast level by one, never past the spell's own range, so a level-1
 * glyph accepts no Dampen and a max-level glyph accepts no Amplify. The learned base level, and
 * with it the glyph's identity, never changes.
 */
public final class ImportedIronEffect extends AbstractEffect {
    private final ImportedGlyphDefinition definition;

    public ImportedIronEffect(ImportedGlyphDefinition definition) {
        super(definition.glyphId(), definition.spellName() + " " + definition.levelNumeral());
        this.definition = definition;
        // AbstractSpellPart's constructor asked for these before the definition existed.
        this.compatibleAugments.addAll(getCompatibleAugments());
    }

    public ImportedGlyphDefinition definition() {
        return definition;
    }

    @Override
    public int getDefaultManaCost() {
        return 0;
    }

    @Override
    public int getCastingCost() {
        return 0;
    }

    @Override
    public SpellTier defaultTier() {
        return tier(definition.defaultTier());
    }

    @Override
    public SpellTier getConfigTier() {
        return tier(GlyphAvailability.tier(definition));
    }

    private static SpellTier tier(int value) {
        return switch (value) {
            case 1 -> SpellTier.ONE;
            case 2 -> SpellTier.TWO;
            default -> SpellTier.THREE;
        };
    }

    /** Hidden from the editor and skipped by the resolver while dormant; see {@link GlyphAvailability}. */
    @Override
    public boolean isEnabled() {
        return GlyphAvailability.status(definition).available();
    }

    @Override
    public boolean shouldShowInUnlock() {
        return isEnabled();
    }

    @Override
    public boolean shouldShowInSpellBook() {
        return isEnabled();
    }

    @NotNull
    @Override
    protected Set<AbstractAugment> getCompatibleAugments() {
        if (definition == null) return Set.of();
        Set<AbstractAugment> augments = new HashSet<>();
        if (definition.canAmplify()) augments.add(AugmentAmplify.INSTANCE);
        if (definition.canDampen()) augments.add(AugmentDampen.INSTANCE);
        return augments;
    }

    /** Amplify may raise the level to the spell's maximum and no further; Dampen may lower it to 1. */
    @Override
    public int getAugmentLimit(ResourceLocation augmentTag) {
        if (AugmentAmplify.INSTANCE.getRegistryName().equals(augmentTag)) return definition.maxLevel() - definition.level();
        if (AugmentDampen.INSTANCE.getRegistryName().equals(augmentTag)) return definition.level() - 1;
        return super.getAugmentLimit(augmentTag);
    }

    @Override
    public void onResolve(HitResult hit, Level world, @NotNull LivingEntity shooter, SpellStats stats,
                          SpellContext context, SpellResolver resolver) {
        ImportedGlyphRuntime.resolve(this, hit, world, shooter, stats, context, resolver);
    }

    @Override
    public Glyph getGlyph() {
        if (glyphItem == null) glyphItem = new ImportedGlyphItem(this);
        return glyphItem;
    }

    /** "Firebolt III", translated where Iron's language data is loaded, else the catalog's English name. */
    @Override
    public String getLocaleName() {
        return displayName().getString();
    }

    public Component displayName() {
        String key = "spell." + definition.spell().getNamespace() + "." + definition.spell().getPath();
        Component spell = net.minecraft.locale.Language.getInstance().has(key)
            ? Component.translatable(key) : Component.literal(definition.spellName());
        return Component.translatable("ars_n_spells.imported_glyph.name", spell, definition.levelNumeral());
    }

    @Override
    public Component getBookDescLang() {
        return Component.translatable("ars_n_spells.imported_glyph.description." + definition.family().name().toLowerCase(java.util.Locale.ROOT),
            displayName());
    }

    @Override
    public String getBookDescription() {
        return "The Iron's spell " + definition.spellName() + " at level " + definition.level() + ".";
    }
}
