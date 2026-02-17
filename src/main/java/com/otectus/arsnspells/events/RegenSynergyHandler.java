package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class RegenSynergyHandler {
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only run if Iron's Spellbooks is loaded
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return;
        }
        
        // Logic finalized: Runs on server, every second (20 ticks)
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide() && event.player.tickCount % 20 == 0) {
            Player player = event.player;
            Level level = player.level();
            BlockPos pos = player.blockPosition();
            
            boolean nearSource = false;
            // Spatial Scan for Source Jars within 4-block radius
            for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 2, 4))) {
                Block block = level.getBlockState(checkPos).getBlock();
                var blockKey = ForgeRegistries.BLOCKS.getKey(block);
                if (blockKey != null && blockKey.getPath().contains("source_jar")) {
                    nearSource = true;
                    break;
                }
            }

            if (nearSource) {
                try {
                    // Logic: Uses the official conversion rate spec for the boost
                    float boost = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get().floatValue();
                    MagicData data = MagicData.getPlayerMagicData(player);
                    if (data != null) {
                        data.addMana(boost);
                    }
                } catch (Exception e) {
                    // Silently fail if Iron's API is unavailable
                }
            }
        }
    }
}
