package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastValidator;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.CrossModSpellList;
import com.otectus.arsnspells.spell.CrossSpellType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Appends cross-cast affordance lines to the tooltip of any item carrying an ANS
 * inscription. Because a bound Ars spell's payload lives in the ANS sidecar (not
 * Iron's slot UI data), this tooltip is the primary way players see what an
 * inscribed scroll or spellbook will cast.
 *
 * <p>Reads only ANS-owned components, so it is registered unconditionally
 * (client-side) and works for generic inscribed items, Iron's scrolls, and
 * Iron's spellbooks alike.
 *
 * <p><b>Why the whole body is guarded.</b> This handler runs on <em>every</em> hover of an
 * ANS-inscribed item, and it is the only ANS code on that path. A throw here does not
 * degrade to a missing tooltip line: it propagates out of {@code ItemStack.getTooltipLines}
 * into the render loop and takes the client down, which is exactly the shape of the reported
 * "game crashes when I hover the scroll the Spell Loom made". The guard catches
 * {@link Throwable}, not {@code Exception}, because the realistic failure in a large modpack
 * is a linkage {@code Error} - {@code NoSuchMethodError} or {@code NoClassDefFoundError} from
 * an Ars Nouveau version or addon-glyph skew reached through the spell codec - and
 * {@code catch (Exception)} sails straight past those. A missing tooltip line is a cosmetic
 * bug; a crashed client is not.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, value = Dist.CLIENT)
public final class CrossSpellTooltipHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrossSpellTooltipHandler.class);

    /**
     * Failure signatures already reported. The tooltip re-renders every frame while the
     * cursor rests on an item, so an unthrottled log would produce tens of lines a second
     * and bury the stack trace that actually matters.
     */
    private static final Set<String> loggedFailures = ConcurrentHashMap.newKeySet();

    private CrossSpellTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        try {
            appendCrossSpellLines(event, stack);
        } catch (Throwable t) {
            logOnce(stack, t);
        }
    }

    private static void appendCrossSpellLines(ItemTooltipEvent event, ItemStack stack) {
        if (stack.isEmpty() || !CrossModSpellComponents.has(stack)) {
            return;
        }
        CrossModSpellList list = CrossModSpellComponents.get(stack);
        int total = list.size();
        if (total == 0) {
            return;
        }
        int index = list.normalizedIndex();

        List<Component> tip = event.getToolTip();
        tip.add(Component.translatable("tooltip.ars_n_spells.cross_spell.header")
            .withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.ars_n_spells.cross_spell.entry",
                index + 1, total, labelFor(list.spells().get(index)))
            .withStyle(ChatFormatting.GRAY));
        if (total > 1) {
            tip.add(Component.translatable("tooltip.ars_n_spells.cross_spell.cycle_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.translatable("tooltip.ars_n_spells.cross_spell.cast_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component labelFor(CrossModSpell entry) {
        CrossSpellType type = CrossCastValidator.resolveType(entry);
        if (type == CrossSpellType.ARS_NOUVEAU && entry.arsSpellTag().isPresent()) {
            if (entry.customName().isPresent() && !entry.customName().get().isEmpty()) {
                return Component.literal(entry.customName().get());
            }
            try {
                Spell spell = CrossCastingHandler.decodeArsSpell(entry.arsSpellTag().get());
                if (spell != null) {
                    return Component.literal(ArsSpellExportUtil.buildDisplayLabel(spell));
                }
            } catch (Throwable t) {
                logOnce(null, t);
                // fall through to the generic label
            }
            return Component.translatable("tooltip.ars_n_spells.cross_spell.ars_generic");
        }
        String id = entry.spellId() == null ? "?" : entry.spellId().toString();
        int level = entry.level();
        String suffix = level > 0 ? " L" + level : "";
        return Component.literal(id + suffix);
    }

    /**
     * One ERROR line per distinct failure, carrying enough to act on: the item, the ANS
     * payload that provoked it, and the stack trace.
     */
    private static void logOnce(ItemStack stack, Throwable t) {
        String signature = t.getClass().getName() + ":" + String.valueOf(t.getMessage());
        if (!loggedFailures.add(signature)) {
            return;
        }
        String item = "unknown";
        String payload = "unavailable";
        try {
            if (stack != null && !stack.isEmpty()) {
                item = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()));
                payload = String.valueOf(CrossModSpellComponents.get(stack));
            }
        } catch (Throwable ignored) {
            // Diagnostics must never become the failure they are reporting.
        }
        LOGGER.error("Ars 'n' Spells: cross-spell tooltip failed for {} and was skipped. "
            + "The item is still usable; please report this with the payload below. Payload: {}",
            item, payload, t);
    }
}
