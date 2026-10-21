package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellSchool;
import com.hollingsworth.arsnouveau.api.spell.SpellSchools;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.items.Glyph;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAccelerate;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDecelerate;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDurationDown;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.runtime.GlyphRuntime;
import com.otectus.arsnspells.util.SchoolResolver;
import com.otectus.arsnspells.util.SpellSchoolId;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * An Ars effect glyph standing for one Iron's spell at one scroll level.
 *
 * <p>One instance per catalogued {@link ImportedGlyphKey}, all sharing this class; the key is in
 * the registered id, so it survives learning, spell composition, copying, parchment export and
 * every Ars codec. Iron's is never touched here: when Ars resolves the effect it is handed to
 * {@link GlyphRuntime}, which reaches Iron's only through the optional bridge.
 *
 * <p>Registered outside {@code GlyphRegistry.registerSpell}, which would write one server config
 * file per variant (hundreds of files). Instead availability, tier and cost come from the ANS
 * behaviour snapshot, and the config-backed getters Ars would otherwise read are overridden.
 *
 * <p>Ars cost: zero. Ars charges the form, its other effects and their augments as usual; the
 * imported effect's own price is Iron's, quoted and paid by {@link GlyphRuntime} per execution, so
 * the same cost is never in both legs. Amplify and Dampen on this glyph change its effective Iron's
 * level, which Iron's prices, so they add nothing to the Ars leg either
 * ({@code MixinImportedAugmentCost}).
 */
public final class ImportedIronEffect extends AbstractEffect {

    /** Amplify may raise the effective level by at most this much (and never past the spell's maximum). */
    public static final int MAX_AMPLIFY_LEVELS = com.otectus.arsnspells.glyph.runtime.GlyphArithmetic.MAX_AMPLIFY_LEVELS;

    private final ImportedGlyphKey key;
    private final CatalogEntry entry;
    private final Set<AbstractAugment> augments;
    private ImportedGlyphItem item;

    public ImportedIronEffect(ImportedGlyphKey key, CatalogEntry entry) {
        super(key.partId(), entry.name() + " " + roman(key.baseLevel()));
        this.key = key;
        this.entry = entry;
        this.augments = computeAugments(key, entry);
        // AbstractSpellPart's constructor read these before the fields above existed.
        this.compatibleAugments.addAll(augments);
        for (SpellSchool school : arsSchools(entry.school())) {
            school.addSpellPart(this);
            this.spellSchools.add(school);
        }
        Map<AbstractAugment, String> descriptions = new LinkedHashMap<>();
        addAugmentDescriptions(descriptions);
        for (var description : descriptions.entrySet()) {
            this.augmentDescriptions.put(description.getKey(), Component.translatable(description.getValue()));
        }
    }

    private static Set<AbstractAugment> computeAugments(ImportedGlyphKey key, CatalogEntry entry) {
        Set<AbstractAugment> set = new LinkedHashSet<>();
        if (key.baseLevel() < entry.maxLevel()) set.add(AugmentAmplify.INSTANCE);
        if (key.baseLevel() > entry.minLevel()) set.add(AugmentDampen.INSTANCE);
        if (entry.channelled()) {
            set.add(AugmentExtendTime.INSTANCE);
            set.add(AugmentDurationDown.INSTANCE);
        }
        if (entry.charged() || "ars_n_spells:projectile".equals(entry.adapter().toString())) {
            set.add(AugmentAccelerate.INSTANCE);
            set.add(AugmentDecelerate.INSTANCE);
        }
        return Set.copyOf(set);
    }

    /** The Ars schools an Iron's school translates to, through ANS's school table. */
    static Set<SpellSchool> arsSchools(ResourceLocation ironsSchool) {
        SpellSchoolId id = SpellSchoolId.fromId(ironsSchool.getPath());
        if (!"irons_spellbooks".equals(ironsSchool.getNamespace()) || id.isGeneric()) return Set.of();
        return SchoolResolver.arsSchoolFor(id).map(ImportedIronEffect::arsSchool).map(Set::of).orElse(Set.of());
    }

    private static SpellSchool arsSchool(String id) {
        return switch (id) {
            case "fire" -> SpellSchools.ELEMENTAL_FIRE;
            case "water" -> SpellSchools.ELEMENTAL_WATER;
            case "air" -> SpellSchools.ELEMENTAL_AIR;
            case "earth" -> SpellSchools.ELEMENTAL_EARTH;
            case "abjuration" -> SpellSchools.ABJURATION;
            case "conjuration" -> SpellSchools.CONJURATION;
            case "manipulation" -> SpellSchools.MANIPULATION;
            default -> SpellSchools.NECROMANCY;
        };
    }

    public ImportedGlyphKey key() {
        return key;
    }

    public CatalogEntry entry() {
        return entry;
    }

    public ResourceLocation ironsSchool() {
        return entry.school();
    }

    /** The behaviour this glyph has on the calling side right now. */
    public VariantBehavior behavior() {
        return GlyphBehavior.variant(key);
    }

    // ---- Resolution ----

    @Override
    public void onResolveEntity(EntityHitResult hit, Level world, @NotNull LivingEntity shooter, SpellStats stats,
                                SpellContext context, SpellResolver resolver) {
        GlyphRuntime.resolve(this, hit, world, shooter, stats, context, resolver);
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level world, @NotNull LivingEntity shooter, SpellStats stats,
                               SpellContext context, SpellResolver resolver) {
        GlyphRuntime.resolve(this, hit, world, shooter, stats, context, resolver);
    }

    // ---- Ars configuration surface, answered from the ANS behaviour snapshot ----

    @Override
    public int getDefaultManaCost() {
        return 0;
    }

    @Override
    public int getCastingCost() {
        return 0;
    }

    @Override
    public boolean isEnabled() {
        return behavior().available();
    }

    @Override
    public SpellTier getConfigTier() {
        SpellTier tier = SpellTier.SPELL_TIER_MAP.get(behavior().tier());
        return tier != null ? tier : defaultTier();
    }

    @Override
    public SpellTier defaultTier() {
        return switch (entry.defaultTierFor(key.baseLevel())) {
            case 1 -> SpellTier.ONE;
            case 2 -> SpellTier.TWO;
            default -> SpellTier.THREE;
        };
    }

    @Override
    public int getAugmentLimit(ResourceLocation augmentTag) {
        if (AugmentAmplify.INSTANCE.getRegistryName().equals(augmentTag)) {
            return Math.min(MAX_AMPLIFY_LEVELS, entry.maxLevel() - key.baseLevel());
        }
        if (AugmentDampen.INSTANCE.getRegistryName().equals(augmentTag)) {
            return key.baseLevel() - entry.minLevel();
        }
        if (AugmentExtendTime.INSTANCE.getRegistryName().equals(augmentTag)
                || AugmentDurationDown.INSTANCE.getRegistryName().equals(augmentTag)) {
            return 3;
        }
        if (AugmentAccelerate.INSTANCE.getRegistryName().equals(augmentTag)
                || AugmentDecelerate.INSTANCE.getRegistryName().equals(augmentTag)) {
            return 2;
        }
        return 0;
    }

    /** The augments this glyph supports, in a stable display order. */
    public java.util.List<AbstractAugment> compatibleAugmentList() {
        java.util.List<AbstractAugment> list = new java.util.ArrayList<>(getCompatibleAugments());
        list.sort(java.util.Comparator.comparing(augment -> augment.getRegistryName().toString()));
        return list;
    }

    @NotNull
    @Override
    protected Set<AbstractAugment> getCompatibleAugments() {
        return augments == null ? Set.of() : augments;
    }

    @Override
    public void addAugmentDescriptions(Map<AbstractAugment, String> map) {
        if (augments == null) return;
        for (AbstractAugment augment : augments) {
            map.put(augment, "ars_n_spells.glyph.augment." + augment.getRegistryName().getPath());
        }
    }

    @NotNull
    @Override
    protected Set<SpellSchool> getSchools() {
        return Set.of();
    }

    @Override
    public boolean shouldShowInUnlock() {
        // Imported glyphs are made at the Glyph Press, never unlocked at a Scribes Table.
        return false;
    }

    // ---- Names and descriptions ----

    /** The Iron's spell name, localized by Iron's when it is loaded, from the catalog otherwise. */
    public MutableComponent spellName() {
        return Component.translatableWithFallback("spell." + entry.spellId().getNamespace() + "." + entry.spellId().getPath(),
            entry.name());
    }

    @Override
    public String getLocaleName() {
        return Component.translatable("ars_n_spells.glyph.name", spellName(), roman(key.baseLevel())).getString();
    }

    @Override
    public Component getBookDescLang() {
        return Component.translatable("ars_n_spells.glyph.description", spellName(), key.baseLevel(),
            Component.translatableWithFallback("school." + entry.school().getNamespace() + "." + entry.school().getPath(),
                entry.school().getPath()),
            Component.translatable(restrictionKey()));
    }

    @Override
    public String getBookDescription() {
        return "Casts " + entry.name() + " at level " + key.baseLevel() + " where the spell resolves.";
    }

    public String restrictionKey() {
        return switch (entry.target()) {
            case SELF -> "ars_n_spells.glyph.restriction.self_only";
            case ENTITY -> "ars_n_spells.glyph.restriction.living_target";
            case ENTITY_OR_SELF -> "ars_n_spells.glyph.restriction.living_target_or_self";
            case BLOCK -> "ars_n_spells.glyph.restriction.block_target";
            case POSITION -> "ars_n_spells.glyph.restriction.position";
            case ANY -> "ars_n_spells.glyph.restriction.any";
        };
    }

    @Override
    public Glyph getGlyph() {
        if (item == null) {
            item = new ImportedGlyphItem(this);
            glyphItem = item;
        }
        return item;
    }

    public static String roman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level > 0 && level < numerals.length ? numerals[level] : Integer.toString(level);
    }
}
