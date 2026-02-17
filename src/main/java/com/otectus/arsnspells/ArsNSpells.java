package com.otectus.arsnspells;

import com.otectus.arsnspells.aura.AuraCapabilityProvider;
import com.otectus.arsnspells.aura.IAuraCapability;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.ModCapabilityProvider;
import com.otectus.arsnspells.data.CooldownData;
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
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ArsNSpells.MODID)
public class ArsNSpells {
    public static final String MODID = "ars_n_spells";
    public static final Logger LOGGER = LogManager.getLogger();

    public ArsNSpells() {
        // Validate environment before initialization
        if (!StartupValidator.validate()) {
            LOGGER.warn("Startup validation failed - some features may not work correctly");
        }
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCaps);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AnsConfig.SPEC, "ars_n_spells-common.toml");

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, this::onAttachCapabilities);
        MinecraftForge.EVENT_BUS.register(new CooldownHandler());
        MinecraftForge.EVENT_BUS.register(new AffinityHandler());
        MinecraftForge.EVENT_BUS.register(CrossCastingHandler.class);
        MinecraftForge.EVENT_BUS.register(EquipmentHandler.class);
        MinecraftForge.EVENT_BUS.register(CurioDiscountHandler.class);
        MinecraftForge.EVENT_BUS.register(CursedRingHandler.class);
        MinecraftForge.EVENT_BUS.register(VirtueRingHandler.class);
        MinecraftForge.EVENT_BUS.register(LPDeathPrevention.class);
        MinecraftForge.EVENT_BUS.register(AuraCapabilityProvider.class);

        if (ModList.get().isLoaded("irons_spellbooks")) {
            MinecraftForge.EVENT_BUS.register(new IronsCooldownHandler());
            MinecraftForge.EVENT_BUS.register(new ProgressionHandler());
            MinecraftForge.EVENT_BUS.register(new ResonanceEvents());
            MinecraftForge.EVENT_BUS.register(new RegenSynergyHandler());
            MinecraftForge.EVENT_BUS.register(new CrossCastIronsHandler());
            MinecraftForge.EVENT_BUS.register(IronsLPHandler.class);
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void registerCaps(RegisterCapabilitiesEvent event) {
        event.register(AffinityData.class);
        event.register(CooldownData.class);
        event.register(IAuraCapability.class);
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
                // Add delay to prevent config race conditions with other mods
                try {
                    Thread.sleep(100); // 100ms delay
                    LOGGER.debug("Config loading delay complete");
                } catch (InterruptedException e) {
                    LOGGER.warn("Config loading delay interrupted", e);
                    Thread.currentThread().interrupt();
                }
                
                // Initialize features with error handling
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
                
                // Initialize Sanctified Legacy compatibility
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
            });
        } catch (Exception e) {
            LOGGER.error("========================================");
            LOGGER.error("CRITICAL: Ars 'n' Spells initialization failed");
            LOGGER.error("Mod will run in safe mode (features disabled)");
            LOGGER.error("========================================", e);
        }
    }
}
