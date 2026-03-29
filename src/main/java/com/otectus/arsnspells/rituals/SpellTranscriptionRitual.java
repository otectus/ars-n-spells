package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class SpellTranscriptionRitual extends AbstractRitual {
    @Override
    protected void tick() {}

    @Override
    public void onEnd() {
        if (this.getWorld() == null || this.getWorld().isClientSide()) {
            return;
        }
        ResourceLocation scrollId = new ResourceLocation("irons_spellbooks", "blank_scroll");
        var item = ForgeRegistries.ITEMS.getValue(scrollId);
        if (item != null) {
            double x = this.getPos().getX() + 0.5;
            double y = this.getPos().getY() + 1.2;
            double z = this.getPos().getZ() + 0.5;
            ItemEntity entity = new ItemEntity(this.getWorld(), x, y, z, new ItemStack(item));
            entity.setDeltaMovement(0, 0.2, 0);
            this.getWorld().addFreshEntity(entity);
        }
    }

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", "spell_transcription");
    }
}
