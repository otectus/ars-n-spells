package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.rituals.InscriptionInputs;
import com.otectus.arsnspells.rituals.InscriptionSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * Exports an Ars Nouveau spell onto a real Iron's Spellbooks scroll item as an
 * ANS cross-cast sidecar payload. This is the first leg of the 3.0.0
 * Ars &rarr; scroll &rarr; spellbook workflow: the scroll is a carrier that a
 * later binding step copies onto a real spellbook.
 *
 * <p>The class never serializes the spell into a second format &mdash; it routes
 * through {@link CrossCastingHandler#encodeArsSpell(Spell)} so the carrier uses
 * the exact same {@link ModDataComponents#CROSS_SPELLS} schema as every other
 * inscribed item, and casting flows through the existing pipeline.
 *
 * <p>No top-level Iron's imports: the scroll item is resolved by registry id
 * string, and the only Iron's-presence concern (whether the scroll item exists)
 * is gated by {@link IronsCompat#isLoaded()}.
 */
public final class ArsSpellExportUtil {
    /** Real Iron's scroll item used as the Ars-spell carrier. */
    public static final ResourceLocation IRONS_SCROLL_ID =
        ResourceLocation.fromNamespaceAndPath(IronsCompat.MODID, "scroll");

    /** Value of {@link ModDataComponents#EXPORT_MODE} marking a scroll carrier. */
    public static final String EXPORT_MODE_SCROLL_CARRIER = "irons_scroll_carrier";

    private ArsSpellExportUtil() {}

    /**
     * Reads the Ars spell carried by {@code stack}, if any. Reuses the ritual
     * source parser so export behaviour never diverges from transcription
     * source parsing. Returns empty for non-Ars sources (e.g. Iron's scrolls).
     */
    public static Optional<Spell> extractArsSpell(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        InscriptionSource source = InscriptionInputs.readSource(stack);
        if (source != null && source.type == CrossSpellType.ARS_NOUVEAU) {
            return Optional.ofNullable(source.arsSpell);
        }
        return Optional.empty();
    }

    /**
     * Builds a real Iron's scroll carrying {@code arsSpell} as an ANS sidecar
     * payload. Returns {@link ItemStack#EMPTY} when Iron's is absent, the scroll
     * item cannot be resolved, or the spell is null.
     */
    public static ItemStack createIronsScrollCarrier(Spell arsSpell) {
        return createIronsScrollCarrier(arsSpell, null, null, null);
    }

    /**
     * Builds an Iron's scroll carrier that also records the Spell Loom display
     * metadata (custom name, nature, icon) on its single Ars entry, so the later
     * book-binding step can surface it in Iron's native spell wheel. Passing null
     * metadata yields a carrier identical to {@link #createIronsScrollCarrier(Spell)}.
     * The proxy pool id stays unallocated here — it is assigned only when the
     * scroll is bound onto an actual spellbook.
     */
    public static ItemStack createIronsScrollCarrier(Spell arsSpell, String customName,
                                                     String nature, String iconSymbol) {
        if (arsSpell == null || !IronsCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(IRONS_SCROLL_ID).orElse(null);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag arsTag = CrossCastingHandler.encodeArsSpell(arsSpell);
        if (arsTag == null) {
            return ItemStack.EMPTY;
        }

        ItemStack out = new ItemStack(item);
        // Same schema every inscribed item uses -- keeps storage and casting aligned.
        CrossModSpellComponents.addArsEntryWithMeta(out,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, arsTag,
            CrossModSpellComponents.NO_PROXY_POOL_ID, customName, nature, iconSymbol);

        // Cosmetic only; casting still keys off the cross-cast component.
        String label = (customName != null && !customName.isEmpty())
            ? customName : buildDisplayLabel(arsSpell);
        out.set(DataComponents.CUSTOM_NAME,
            Component.translatable("item.ars_n_spells.transcribed_ars_scroll", label));
        out.set(ModDataComponents.EXPORT_MODE.get(), EXPORT_MODE_SCROLL_CARRIER);
        return out;
    }

    /** True when {@code stack} was built by {@link #createIronsScrollCarrier}. */
    public static boolean isScrollCarrier(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && EXPORT_MODE_SCROLL_CARRIER.equals(stack.get(ModDataComponents.EXPORT_MODE.get()));
    }

    /**
     * Best-effort human-readable label for an Ars spell, derived from its glyph
     * recipe. Used for the carrier hover name and tooltip lines.
     */
    public static String buildDisplayLabel(Spell arsSpell) {
        if (arsSpell == null || arsSpell.isEmpty()) {
            return "Ars Spell";
        }
        StringBuilder sb = new StringBuilder();
        // AN 5.x: the recipe is no longer a public field; unsafeList() is the
        // registry-order part list (same accessor SpellAnalysis uses).
        for (AbstractSpellPart part : arsSpell.unsafeList()) {
            if (part == null || part.getRegistryName() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" → ");
            }
            sb.append(prettify(part.getRegistryName().getPath()));
            if (sb.length() > 48) {
                sb.append(" …");
                break;
            }
        }
        return sb.length() == 0 ? "Ars Spell" : sb.toString();
    }

    /** "glyph_lightning" -> "Lightning"; drops a leading "glyph_" prefix and title-cases. */
    private static String prettify(String path) {
        String p = path.startsWith("glyph_") ? path.substring("glyph_".length()) : path;
        p = p.replace('_', ' ').trim();
        if (p.isEmpty()) {
            return path;
        }
        return Character.toUpperCase(p.charAt(0)) + p.substring(1).toLowerCase(Locale.ROOT);
    }
}
