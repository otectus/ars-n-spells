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
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Appends cross-cast affordance lines to the tooltip of any item carrying an ANS
 * inscription. Because a bound Ars spell does not appear in Iron's native
 * spellbook slot UI, this tooltip is the primary way players see what an
 * inscribed scroll or spellbook will cast.
 *
 * <p>Reads only ANS-owned NBT, so it is registered unconditionally (client-side)
 * and works for generic inscribed items, Iron's scrolls, and Iron's spellbooks
 * alike.
 *
 * <p><b>Why the whole body is guarded.</b> This handler runs on <em>every</em> hover of an
 * ANS-inscribed item, and it is the only ANS code on that path. A throw here does not
 * degrade to a missing tooltip line: it propagates out of {@code ItemStack.getTooltipLines}
 * into the render loop and takes the client down, which is exactly the shape of the reported
 * "game crashes when I hover the scroll the Spell Loom made". The guard catches
 * {@link Throwable}, not {@code Exception}, because the realistic failure in a large modpack
 * is a linkage {@code Error} — {@code NoSuchMethodError} or {@code NoClassDefFoundError} from
 * an Ars Nouveau version or addon-glyph skew reached through {@link Spell#fromTag} — and
 * {@code catch (Exception)} sails straight past those. A missing tooltip line is a cosmetic
 * bug; a crashed client is not.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
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
        try {
            CrossSpellType type = CrossCastValidator.resolveType(entry);
            if (type == CrossSpellType.ARS_NOUVEAU
                && entry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)) {
                Spell spell = Spell.fromTag(entry.getCompound(CrossCastNbt.TAG_ARS_SPELL));
                return Component.literal(ArsSpellExportUtil.buildDisplayLabel(spell));
            }
            String id = entry.getString(CrossCastNbt.TAG_SPELL_ID);
            int level = entry.getInt(CrossCastNbt.TAG_SPELL_LEVEL);
            String suffix = level > 0 ? " L" + level : "";
            return Component.literal((id == null || id.isEmpty() ? "?" : id) + suffix);
        } catch (Throwable t) {
            logOnce(null, t);
            return Component.translatable("tooltip.ars_n_spells.cross_spell.ars_generic");
        }
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
                item = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
                CompoundTag tag = stack.getTag();
                if (tag != null) {
                    payload = String.valueOf(tag.get(CrossCastNbt.TAG_CROSS_MOD_SPELLS));
                }
            }
        } catch (Throwable ignored) {
            // Diagnostics must never become the failure they are reporting.
        }
        LOGGER.error("Ars 'n' Spells: cross-spell tooltip failed for {} and was skipped. "
            + "The item is still usable; please report this with the payload below. Payload: {}",
            item, payload, t);
    }
}
