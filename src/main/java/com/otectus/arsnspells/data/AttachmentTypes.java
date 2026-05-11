package com.otectus.arsnspells.data;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Replaces Forge capabilities (AffinityData, CooldownData, ProgressionData)
 * with NeoForge data attachments.
 *
 * Each attachment carries a Codec (from the holder type itself) and an
 * optional copyOnDeath flag. Cooldown intentionally does NOT copy on death
 * (resets on respawn); the other two persist (matches the Forge
 * ModCapabilityProvider.onPlayerClone behavior from 1.20.1).
 */
public final class AttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ArsNSpells.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AffinityData>> AFFINITY =
        ATTACHMENTS.register("affinity",
            () -> AttachmentType.builder(AffinityData::new)
                    .serialize(AffinityData.CODEC)
                    .copyOnDeath()
                    .build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CooldownData>> COOLDOWN =
        ATTACHMENTS.register("cooldown",
            () -> AttachmentType.builder(CooldownData::new)
                    .serialize(CooldownData.CODEC)
                    .build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ProgressionData>> PROGRESSION =
        ATTACHMENTS.register("progression",
            () -> AttachmentType.builder(ProgressionData::new)
                    .serialize(ProgressionData.CODEC)
                    .copyOnDeath()
                    .build()
        );

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    private AttachmentTypes() {}
}
