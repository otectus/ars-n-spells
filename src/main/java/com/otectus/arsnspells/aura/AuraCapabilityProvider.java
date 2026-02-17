package com.otectus.arsnspells.aura;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge capability provider for attaching aura data to players.
 * Handles capability attachment, serialization, and player clone (death/dimension change).
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class AuraCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
    public static final ResourceLocation IDENTIFIER = new ResourceLocation("ars_n_spells", "aura");
    public static final Capability<IAuraCapability> AURA_CAP = CapabilityManager.get(new CapabilityToken<>() {});

    private final AuraCapability backend = new AuraCapability();
    private final LazyOptional<IAuraCapability> optional = LazyOptional.of(() -> backend);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == AURA_CAP) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        backend.saveNBTData(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        backend.loadNBTData(tag);
    }

    public void invalidate() {
        optional.invalidate();
    }

    // --- Event Handlers ---

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(IDENTIFIER, new AuraCapabilityProvider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Copy aura data on death/dimension change
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(AURA_CAP).ifPresent(oldAura -> {
            event.getEntity().getCapability(AURA_CAP).ifPresent(newAura -> {
                CompoundTag tag = new CompoundTag();
                oldAura.saveNBTData(tag);
                newAura.loadNBTData(tag);
            });
        });
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide()) {
            return;
        }
        event.player.getCapability(AURA_CAP).ifPresent(IAuraCapability::regenTick);
    }
}
