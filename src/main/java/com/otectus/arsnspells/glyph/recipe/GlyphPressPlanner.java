package com.otectus.arsnspells.glyph.recipe;

import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Plans one Glyph Press conversion from the press's real inputs. The only place a conversion is
 * decided: the screen's status, the start action, the completion re-check and the admin command
 * all ask this, so none of them can disagree.
 *
 * <p>The scroll is read strictly through Iron's own container ({@link IronGlyphBridge#readScroll}):
 * exactly one non-empty spell at a valid level in Iron's own scroll item. A carrier holding an
 * exported Ars spell, an ANS proxy spell, a blank or multi-spell scroll, an unreadable container
 * and a level outside the spell's range are each refused with their own reason and never
 * clamped or guessed. The glyph made is exactly the scroll's spell at the scroll's level.
 */
public final class GlyphPressPlanner {
    /** Iron's own scroll item, the one scroll this release reads. Common code: no Iron's type. */
    public static final net.minecraft.resources.ResourceLocation IRONS_SCROLL =
        new net.minecraft.resources.ResourceLocation("irons_spellbooks", "scroll");

    private GlyphPressPlanner() {}

    /**
     * @param scroll    slot 0
     * @param plate     slot 1
     * @param catalyst  slot 2
     * @param output    slot 3
     * @param player    the player who would start the job, for Iron's learning requirement; null to skip it
     * @param capacity  the press's Source capacity
     */
    public static GlyphPressPlan plan(ItemStack scroll, ItemStack plate, ItemStack catalyst, ItemStack output,
                                      @Nullable Player player, int capacity) {
        if (!enabled(AnsConfig.ENABLE_GLYPH_PRESS)) return GlyphPressPlan.refused(GlyphPressPlan.Reason.PRESS_DISABLED);
        if (!AnsConfig.ironGlyphsEnabled()) return GlyphPressPlan.refused(GlyphPressPlan.Reason.FEATURE_DISABLED);
        if (scroll.isEmpty()) return GlyphPressPlan.refused(GlyphPressPlan.Reason.NO_SCROLL);
        IronGlyphBridge bridge = IronGlyphBridges.get();
        if (!bridge.present()) return GlyphPressPlan.refused(GlyphPressPlan.Reason.IRONS_ABSENT);
        IronGlyphBridge.ScrollReading reading = bridge.readScroll(scroll);
        switch (reading.kind()) {
            case NOT_A_SCROLL -> { return GlyphPressPlan.refused(GlyphPressPlan.Reason.NOT_A_SCROLL); }
            case ARS_EXPORT -> { return GlyphPressPlan.refused(GlyphPressPlan.Reason.ARS_EXPORT); }
            case PROXY_SPELL -> { return GlyphPressPlan.refused(GlyphPressPlan.Reason.PROXY_SPELL); }
            case EMPTY, NONE_SPELL -> { return GlyphPressPlan.refused(GlyphPressPlan.Reason.BLANK_SCROLL); }
            case MULTIPLE -> { return GlyphPressPlan.refused(GlyphPressPlan.Reason.MULTIPLE_SPELLS); }
            case UNREADABLE -> { return GlyphPressPlan.refused(GlyphPressPlan.Reason.UNREADABLE); }
            case INVALID_LEVEL -> { return GlyphPressPlan.refused(GlyphPressPlan.Reason.INVALID_LEVEL); }
            case OK -> { }
        }
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        // No catalog lists the spell: an addon's spell needs an addon catalog (and a restart).
        if (catalog.levelsOf(reading.spell()).isEmpty()) return GlyphPressPlan.refused(GlyphPressPlan.Reason.NO_ADAPTER);
        if (reading.level() < 1 || reading.level() > ImportedGlyphKey.MAX_LEVEL)
            return GlyphPressPlan.refused(GlyphPressPlan.Reason.INVALID_LEVEL);
        // A level Iron's accepts but no catalog registered: above the spell's envelope. Never downcast.
        Optional<ImportedGlyphDefinition> found = catalog.byKey(new ImportedGlyphKey(reading.spell(), reading.level()));
        if (found.isEmpty()) return GlyphPressPlan.refused(GlyphPressPlan.Reason.LEVEL_NOT_CATALOGUED);
        ImportedGlyphDefinition definition = found.get();
        int tier = GlyphAvailability.tier(definition);
        GlyphMappingSnapshot.PressTerms terms = GlyphMappingSnapshot.current().pressTerms(definition, tier);
        GlyphAvailability.Status status = GlyphAvailability.status(definition);
        if (!status.available()) return GlyphPressPlan.refused(reasonFor(status.reason()), definition, tier, terms);
        // A job is never started on provisional facts: the conversion must be judged on Iron's real config.
        if (status.provisional()) return GlyphPressPlan.refused(GlyphPressPlan.Reason.CONFIG_PENDING, definition, tier, terms);
        // Iron's live learning requirement, not the catalog's record of it.
        if (player != null && !bridge.meetsLearning(player, definition.spell()))
            return GlyphPressPlan.refused(GlyphPressPlan.Reason.REQUIRES_LEARNING, definition, tier, terms);
        if (!isPlate(plate)) return GlyphPressPlan.refused(GlyphPressPlan.Reason.NO_PLATE, definition, tier, terms);
        if (!catalystSatisfied(catalyst, terms))
            return GlyphPressPlan.refused(GlyphPressPlan.Reason.NEEDS_CATALYST, definition, tier, terms);
        if (terms.sourceCost() > capacity) return GlyphPressPlan.refused(GlyphPressPlan.Reason.CAPACITY_TOO_SMALL, definition, tier, terms);
        ItemStack result = ImportedGlyphRegistrar.part(definition.key())
            .map(part -> new ItemStack(part.getGlyph())).orElse(ItemStack.EMPTY);
        if (result.isEmpty()) return GlyphPressPlan.refused(GlyphPressPlan.Reason.LEVEL_NOT_CATALOGUED);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameTags(output, result) || output.getCount() >= output.getMaxStackSize()))
            return GlyphPressPlan.refused(GlyphPressPlan.Reason.OUTPUT_FULL, definition, tier, terms);
        return new GlyphPressPlan(GlyphPressPlan.Reason.OK, definition, tier, terms, result);
    }

    /** The Press's own explanation for an availability refusal. */
    static GlyphPressPlan.Reason reasonFor(GlyphAvailability.Reason reason) {
        return switch (reason) {
            case LEVEL_UNSUPPORTED -> GlyphPressPlan.Reason.LEVEL_ABOVE_LIVE_MAX;
            case DISABLED_BY_DATAPACK -> GlyphPressPlan.Reason.DISABLED_BY_PACK;
            case RETIRED, UNSUPPORTED -> GlyphPressPlan.Reason.UNSUPPORTED_ADAPTER;
            case DISABLED_BY_SERVER -> GlyphPressPlan.Reason.FEATURE_DISABLED;
            case IRONS_ABSENT -> GlyphPressPlan.Reason.IRONS_ABSENT;
            case NOT_SYNCED -> GlyphPressPlan.Reason.CONFIG_PENDING;
            default -> GlyphPressPlan.Reason.SPELL_DISABLED;
        };
    }

    public static boolean isPlate(ItemStack stack) {
        return !stack.isEmpty() && ModItemsRegistry.blankGlyphPlate() != null && stack.is(ModItemsRegistry.blankGlyphPlate().get());
    }

    /**
     * Whether the catalyst slot meets {@code terms}. A zero-count conversion needs nothing, so an
     * empty slot satisfies it and nothing is consumed; a positive count needs that many items of
     * the terms' tag.
     */
    public static boolean catalystSatisfied(ItemStack catalyst, GlyphMappingSnapshot.PressTerms terms) {
        if (terms.catalystCount() <= 0) return true;
        return isCatalyst(catalyst, terms) && catalyst.getCount() >= terms.catalystCount();
    }

    /**
     * Whether a stack may sit in the catalyst slot at all: an item of any catalyst tag the current
     * behaviour names (the default tag, every tier's and every per-spell override's). The planner
     * still checks the one tag a given conversion needs.
     */
    public static boolean mayBeCatalyst(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (net.minecraft.resources.ResourceLocation tag : GlyphMappingSnapshot.current().catalystTags()) {
            try {
                if (stack.is(TagKey.create(Registries.ITEM, tag))) return true;
            } catch (RuntimeException unboundTags) {
                return false;
            }
        }
        return false;
    }

    /** Any item in the terms' catalyst tag (Source gems by default). */
    public static boolean isCatalyst(ItemStack stack, @Nullable GlyphMappingSnapshot.PressTerms terms) {
        if (stack.isEmpty()) return false;
        TagKey<Item> tag = TagKey.create(Registries.ITEM,
            terms == null ? GlyphMappingSnapshot.DEFAULT_CATALYST_TAG : terms.catalystTag());
        try {
            return stack.is(tag);
        } catch (RuntimeException unboundTags) {
            return false;
        }
    }

    /** Whether a stack could ever be a press scroll input, for slot filtering (a cheap, side-neutral check). */
    public static boolean mayBeScroll(ItemStack stack) {
        return !stack.isEmpty() && IRONS_SCROLL.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static boolean enabled(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException unloaded) {
            return true;
        }
    }
}
