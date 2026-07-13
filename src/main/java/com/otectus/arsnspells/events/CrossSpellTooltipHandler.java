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

import java.util.List;

/**
 * Appends cross-cast affordance lines to the tooltip of any item carrying an ANS
 * inscription. Because a bound Ars spell's payload lives in the ANS sidecar (not
 * Iron's slot UI data), this tooltip is the primary way players see what an
 * inscribed scroll or spellbook will cast.
 *
 * <p>Reads only ANS-owned components, so it is registered unconditionally
 * (client-side) and works for generic inscribed items, Iron's scrolls, and
 * Iron's spellbooks alike.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, value = Dist.CLIENT)
public final class CrossSpellTooltipHandler {

    private CrossSpellTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
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
            } catch (Exception ignored) {
                // fall through to the generic label
            }
            return Component.translatable("tooltip.ars_n_spells.cross_spell.ars_generic");
        }
        String id = entry.spellId() == null ? "?" : entry.spellId().toString();
        int level = entry.level();
        String suffix = level > 0 ? " L" + level : "";
        return Component.literal(id + suffix);
    }
}
