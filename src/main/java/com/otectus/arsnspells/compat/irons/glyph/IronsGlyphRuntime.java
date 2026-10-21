package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.spell.CrossCastNbt;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/**
 * Iron's-isolated implementation of {@link IronGlyphBridge}. Constructed by
 * {@link IronGlyphBridges} only when Iron's is loaded.
 */
public final class IronsGlyphRuntime implements IronGlyphBridge {
    /** The one scroll item this release reads. Addon scroll items need their own reader. */
    public static final ResourceLocation IRONS_SCROLL = com.otectus.arsnspells.glyph.recipe.GlyphPressPlanner.IRONS_SCROLL;

    @Override
    public boolean present() {
        return true;
    }

    /** The registered spell, or null; {@code SpellRegistry.getSpell} answers "none" for unknown ids. */
    static AbstractSpell spell(ResourceLocation id) {
        AbstractSpell spell = SpellRegistry.getSpell(id);
        return spell == null || spell == SpellRegistry.none() ? null : spell;
    }

    @Override
    public SpellState spellState(ResourceLocation id) {
        AbstractSpell spell = spell(id);
        if (spell == null) return SpellState.MISSING;
        return spell.isEnabled() ? SpellState.ENABLED : SpellState.DISABLED;
    }

    @Override
    public String rarity(ResourceLocation id, int level) {
        AbstractSpell spell = spell(id);
        if (spell == null || level < 1 || level > spell.getMaxLevel()) return null;
        return spell.getRarity(level).name();
    }

    /** Live maximum level, or 0 when the spell is missing. */
    public static int maxLevel(ResourceLocation id) {
        AbstractSpell spell = spell(id);
        return spell == null ? 0 : spell.getMaxLevel();
    }

    @Override
    public boolean meetsLearning(Player player, ResourceLocation id) {
        AbstractSpell spell = spell(id);
        return spell != null && (!spell.requiresLearning() || spell.isLearned(player));
    }

    @Override
    public double manaCost(ResourceLocation id, int level) {
        AbstractSpell spell = spell(id);
        return spell == null ? Double.NaN : spell.getManaCost(level);
    }

    @Override
    public int castTimeTicks(ResourceLocation id, int level) {
        AbstractSpell spell = spell(id);
        return spell == null ? -1 : spell.getCastTime(level);
    }

    @Override
    public Optional<Component> spellName(ResourceLocation id) {
        AbstractSpell spell = spell(id);
        return spell == null ? Optional.empty() : Optional.of(Component.translatable(spell.getComponentId()));
    }

    @Override
    public ResourceLocation icon(ResourceLocation id) {
        AbstractSpell spell = spell(id);
        return spell == null ? null : spell.getSpellIconResource();
    }

    @Override
    public ItemStack filledScroll(ResourceLocation id, int level) {
        AbstractSpell spell = spell(id);
        var item = ForgeRegistries.ITEMS.getValue(IRONS_SCROLL);
        if (spell == null || item == null || level < spell.getMinLevel() || level > spell.getMaxLevel()) return ItemStack.EMPTY;
        ItemStack scroll = new ItemStack(item);
        ISpellContainer.set(scroll, ISpellContainer.createScrollContainer(spell, level, scroll));
        return scroll;
    }

    @Override
    public ScrollReading readScroll(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !IRONS_SCROLL.equals(ForgeRegistries.ITEMS.getKey(stack.getItem())))
            return ScrollReading.of(ScrollReading.Kind.NOT_A_SCROLL);
        // An ANS carrier is an Iron's scroll that holds an exported Ars spell in its sidecar.
        if (stack.hasTag() && (stack.getTag().contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS)
                || stack.getTag().contains(com.otectus.arsnspells.spell.ArsSpellExportUtil.TAG_EXPORT_MODE)))
            return ScrollReading.of(ScrollReading.Kind.ARS_EXPORT);
        if (!ISpellContainer.isSpellContainer(stack)) return ScrollReading.of(ScrollReading.Kind.EMPTY);
        ISpellContainer container;
        try {
            container = ISpellContainer.get(stack);
        } catch (RuntimeException unreadable) {
            return ScrollReading.of(ScrollReading.Kind.UNREADABLE);
        }
        if (container == null) return ScrollReading.of(ScrollReading.Kind.UNREADABLE);
        if (container.getMaxSpellCount() != 1) return ScrollReading.of(ScrollReading.Kind.MULTIPLE);
        List<SpellSlot> active = container.getActiveSpells();
        if (active.isEmpty()) return ScrollReading.of(ScrollReading.Kind.EMPTY);
        if (active.size() != 1) return ScrollReading.of(ScrollReading.Kind.MULTIPLE);
        SpellSlot slot = active.get(0);
        AbstractSpell spell = slot.getSpell();
        if (spell == null || spell == SpellRegistry.none()) return ScrollReading.of(ScrollReading.Kind.NONE_SPELL);
        ResourceLocation id = spell.getSpellResource();
        if (spell instanceof com.otectus.arsnspells.spell.irons.ArsCrossProxySpell || CrossCastNbt.isArsCrossProxyId(id.toString()))
            return ScrollReading.of(ScrollReading.Kind.PROXY_SPELL);
        int level = slot.getLevel();
        if (level < spell.getMinLevel() || level > spell.getMaxLevel()) return new ScrollReading(ScrollReading.Kind.INVALID_LEVEL, id, level);
        return new ScrollReading(ScrollReading.Kind.OK, id, level);
    }

    @Override
    public InvocationOutcome invoke(com.otectus.arsnspells.glyph.GlyphInvocation invocation) {
        return IronsGlyphInvoker.invoke(invocation);
    }

    @Override
    public boolean onCooldown(Player player, ResourceLocation id) {
        AbstractSpell spell = spell(id);
        return spell != null && io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player)
            .getPlayerCooldowns().isOnCooldown(spell);
    }

    @Override
    public boolean hasRecast(Player player, ResourceLocation id) {
        AbstractSpell spell = spell(id);
        return spell != null && io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player)
            .getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
    }

    @Override
    public int levelFor(ResourceLocation id, int level, Player player) {
        AbstractSpell spell = spell(id);
        return spell == null ? level : spell.getLevelFor(level, player);
    }

    @Override
    public boolean creativeFree(Player player) {
        return player.isCreative() && !io.redspace.ironsspellbooks.config.ServerConfigs.CREATIVE_MANA_COST.get();
    }

    @Override
    public boolean creativeNoCooldown(Player player) {
        return player.isCreative() && !io.redspace.ironsspellbooks.config.ServerConfigs.CREATIVE_COOLDOWN.get();
    }

    @Override
    public boolean nativeCastInProgress(Player player) {
        return player instanceof net.minecraft.server.level.ServerPlayer
            && io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).isCasting();
    }
}
