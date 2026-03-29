package com.otectus.arsnspells;

import com.otectus.arsnspells.commands.ArsNSpellsCommands;
import com.otectus.arsnspells.aura.AuraCapabilityProvider;
import com.otectus.arsnspells.aura.IAuraCapability;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.ModCapabilityProvider;
import com.otectus.arsnspells.data.CooldownData;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.events.*;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.rituals.RitualRegistryHandler;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossCastIronsHandler;
import com.otectus.arsnspells.util.StartupValidator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ArsNSpells.MODID)
public class ArsNSpells {
    public static final String MODID = "ars_n_spells";
    public static final Logger LOGGER = LoggerFactory.getLogger(ArsNSpells.class);

    public ArsNSpells() {
        // Validate environment before initialization
        if (!StartupValidator.validate()) {
            LOGGER.warn("Startup validation failed - some features may not work correctly");
        }
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCaps);
        modEventBus.addListener(this::onConfigLoading);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AnsConfig.SPEC, "ars_n_spells-common.toml");

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, this::onAttachCapabilities);
        // Instance-registered handlers (no @Mod.EventBusSubscriber, use instance @SubscribeEvent methods)
        MinecraftForge.EVENT_BUS.register(new CooldownHandler());
        MinecraftForge.EVENT_BUS.register(new AffinityHandler());
        // ArsNSpellsCommands has no @Mod.EventBusSubscriber, needs explicit registration
        MinecraftForge.EVENT_BUS.register(ArsNSpellsCommands.class);
        // Note: CrossCastingHandler, EquipmentHandler, CurioDiscountHandler, CursedRingHandler,
        // VirtueRingHandler, LPDeathPrevention, AuraCapabilityProvider are auto-registered
        // via @Mod.EventBusSubscriber — do NOT register them here to avoid double-firing.

        if (ModList.get().isLoaded("irons_spellbooks")) {
            // Instance-registered handlers (no @Mod.EventBusSubscriber)
            MinecraftForge.EVENT_BUS.register(new IronsCooldownHandler());
            MinecraftForge.EVENT_BUS.register(new ProgressionHandler());
            MinecraftForge.EVENT_BUS.register(new ResonanceEvents());
            MinecraftForge.EVENT_BUS.register(new RegenSynergyHandler());
            MinecraftForge.EVENT_BUS.register(new CrossCastIronsHandler());
            // Note: IronsLPHandler is auto-registered via @Mod.EventBusSubscriber
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void registerCaps(RegisterCapabilitiesEvent event) {
        event.register(AffinityData.class);
        event.register(CooldownData.class);
        event.register(IAuraCapability.class);
        event.register(ProgressionData.class);
    }

    private void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(new ResourceLocation(MODID, "bridge_data"), new ModCapabilityProvider());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        try {
            BridgeManager.init(event);
            event.enqueueWork(() -> {
                try {
                    PacketHandler.register();
                    LOGGER.info("OK Packet handler registered");
                } catch (Exception e) {
                    LOGGER.error("FAILED to register packet handler", e);
                }
                try {
                    RitualRegistryHandler.registerRituals();
                    LOGGER.info("OK Rituals registered");
                } catch (Exception e) {
                    LOGGER.error("FAILED to register rituals", e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("========================================");
            LOGGER.error("CRITICAL: Ars 'n' Spells initialization failed");
            LOGGER.error("Mod will run in safe mode (features disabled)");
            LOGGER.error("========================================", e);
        }
    }

    private void onConfigLoading(final ModConfigEvent.Loading event) {
        if (!event.getConfig().getModId().equals(MODID)) {
            return;
        }

        try {
            SanctifiedLegacyCompat.init();
            if (SanctifiedLegacyCompat.isAvailable()) {
                LOGGER.info("OK Sanctified Legacy compatibility enabled");
                LOGGER.info("   - Cursed Ring support for Ars Nouveau spells");
                LOGGER.info("   - Virtue Ring support for Ars Nouveau spells");
            }
        } catch (Exception e) {
            LOGGER.error("FAILED to initialize Sanctified Legacy compatibility", e);
        }

        LOGGER.info("========================================");
        LOGGER.info("OK Ars 'n' Spells initialization complete");
        LOGGER.info("========================================");
    }
}
