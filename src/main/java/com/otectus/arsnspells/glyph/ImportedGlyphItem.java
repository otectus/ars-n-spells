package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.common.items.Glyph;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The physical imported glyph. Learning is Ars's own {@link Glyph} contract: server-side, synced,
 * and consuming one glyph only when it is newly learned; an already-known glyph is reported and
 * kept. Two gates come first and never consume anything: the glyph must be available on this
 * server (not dormant), and a spell Iron's makes players learn (the Eldritch school) must already
 * be learned in Iron's by the player using the glyph, so a pressed scroll never bypasses Iron's
 * own progression.
 *
 * <p>The stack carries no spell data of its own: its item id names the spell and level.
 */
public final class ImportedGlyphItem extends Glyph {

    private final ImportedIronEffect effect;

    public ImportedGlyphItem(ImportedIronEffect effect) {
        super(effect);
        this.effect = effect;
    }

    public ImportedIronEffect effect() {
        return effect;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }
        VariantBehavior behavior = effect.behavior();
        if (!behavior.available()) {
            player.displayClientMessage(Component.translatable("ars_n_spells.glyph.learn.dormant",
                Component.translatable(behavior.dormancy().translationKey())).withStyle(ChatFormatting.RED), false);
            return InteractionResultHolder.fail(stack);
        }
        if (effect.entry().requiresLearning() && player instanceof ServerPlayer serverPlayer
                && !IronGlyphBridges.get().hasLearned(serverPlayer, effect.entry().spellId())) {
            player.displayClientMessage(Component.translatable("ars_n_spells.glyph.learn.unlearned",
                effect.spellName()).withStyle(ChatFormatting.RED), false);
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        VariantBehavior behavior = effect.behavior();
        tooltip.add(Component.translatable("ars_n_spells.glyph.tooltip.spell", effect.spellName(),
            ImportedIronEffect.roman(effect.key().baseLevel())).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(effect.restrictionKey()).withStyle(ChatFormatting.GRAY));
        if (!behavior.available()) {
            tooltip.add(Component.translatable("ars_n_spells.glyph.tooltip.dormant",
                Component.translatable(behavior.dormancy().translationKey())).withStyle(ChatFormatting.RED));
        }
        if (effect.entry().requiresLearning()) {
            tooltip.add(Component.translatable("ars_n_spells.glyph.tooltip.requires_learning").withStyle(ChatFormatting.DARK_PURPLE));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
