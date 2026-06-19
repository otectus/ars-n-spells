package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastValidator;
import com.otectus.arsnspells.spell.CrossSpellType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Appends cross-cast affordance lines to the tooltip of any item carrying an ANS
 * inscription. Because a bound Ars spell does not appear in Iron's native
 * spellbook slot UI, this tooltip is the primary way players see what an
 * inscribed scroll or spellbook will cast.
 *
 * <p>Reads only ANS-owned NBT, so it is registered unconditionally (client-side)
 * and works for generic inscribed items, Iron's scrolls, and Iron's spellbooks
 * alike.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class CrossSpellTooltipHandler {

    private CrossSpellTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.hasTag()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (!CrossCastNbt.hasCrossModSpells(tag)) {
            return;
        }

        ListTag list = tag.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        int total = list.size();
        if (total == 0) {
            return;
        }
        int index = tag.getInt(CrossCastNbt.TAG_SPELL_INDEX);
        if (index < 0 || index >= total) {
            index = 0;
        }

        List<Component> tip = event.getToolTip();
        tip.add(Component.translatable("tooltip.ars_n_spells.cross_spell.header")
            .withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.ars_n_spells.cross_spell.entry",
                index + 1, total, labelFor(list.getCompound(index)))
            .withStyle(ChatFormatting.GRAY));
        if (total > 1) {
            tip.add(Component.translatable("tooltip.ars_n_spells.cross_spell.cycle_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.translatable("tooltip.ars_n_spells.cross_spell.cast_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component labelFor(CompoundTag entry) {
        CrossSpellType type = CrossCastValidator.resolveType(entry);
        if (type == CrossSpellType.ARS_NOUVEAU
            && entry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)) {
            try {
                Spell spell = Spell.fromTag(entry.getCompound(CrossCastNbt.TAG_ARS_SPELL));
                return Component.literal(ArsSpellExportUtil.buildDisplayLabel(spell));
            } catch (Exception ignored) {
                return Component.translatable("tooltip.ars_n_spells.cross_spell.ars_generic");
            }
        }
        String id = entry.getString(CrossCastNbt.TAG_SPELL_ID);
        int level = entry.getInt(CrossCastNbt.TAG_SPELL_LEVEL);
        String suffix = level > 0 ? " L" + level : "";
        return Component.literal((id == null || id.isEmpty() ? "?" : id) + suffix);
    }
}
