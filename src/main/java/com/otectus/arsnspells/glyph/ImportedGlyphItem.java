package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.common.items.Glyph;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The physical imported glyph: right-click learns it through Ars's own glyph contract.
 *
 * <p>Learning is Ars's: the server unlocks the part, syncs the player's knowledge and consumes
 * exactly one glyph, and a known glyph is reported and kept. Before that, this item refuses
 * when the glyph is dormant (with the reason) or when Iron's requires the spell to be learned
 * and the player has not learned it, so a scroll can never become a way around Iron's own
 * progression. Nothing is consumed on a refusal.
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

    public ImportedGlyphDefinition definition() {
        return effect.definition();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            GlyphAvailability.Status status = GlyphAvailability.status(definition());
            if (!status.available()) {
                player.displayClientMessage(status.message(definition()).copy().withStyle(ChatFormatting.RED), false);
                return InteractionResultHolder.fail(stack);
            }
            if (definition().requiresLearning() && !IronGlyphBridges.get().meetsLearning(player, definition().spell())) {
                player.displayClientMessage(Component.translatable("message.ars_n_spells.glyph.requires_irons_learning",
                    effect.displayName()).withStyle(ChatFormatting.RED), false);
                return InteractionResultHolder.fail(stack);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("ars_nouveau.glyph_of", effect.displayName());
    }

    /**
     * Base level, school, Ars tier, the forms and augments it supports, and whether the local
     * player knows it. Client-only details (the local player's knowledge, held keys) come from
     * the client helper; nothing here loads a client class on a dedicated server.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ImportedGlyphTooltip.append(definition(), tooltip);
        if (level != null && level.isClientSide()) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.otectus.arsnspells.client.ImportedGlyphClientTooltip.appendKnowledge(effect, tooltip));
        }
    }
}
