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
import com.otectus.arsnspells.registry.ModItemsRegistry;
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

        // Common items (uninscribe tablet) are registered unconditionally so
        // they remain available for cleanup if Iron's Spellbooks is later
        // uninstalled. Iron's-dependent items (transcribe tablet) only when
        // Iron's is present. Both must be registered before
        // ITEMS.register(modEventBus) so the DeferredRegister carries the
        // entries when the item RegisterEvent fires.
        ModItemsRegistry.registerCommonItems();
        if (ModList.get().isLoaded("irons_spellbooks")) {
            ModItemsRegistry.registerIronsDependentItems();
        }
        ModItemsRegistry.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AnsConfig.SPEC, "ars_n_spells-common.toml");

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, this::onAttachCapabilities);
        // Instance-registered handlers (no @Mod.EventBusSubscriber, use instance @SubscribeEvent methods)
        MinecraftForge.EVENT_BUS.register(new CooldownHandler());
        MinecraftForge.EVENT_BUS.register(new AffinityHandler());
        MinecraftForge.EVENT_BUS.register(new AffinityDecayHandler());
        MinecraftForge.EVENT_BUS.register(new AffinitySyncOnLoginHandler());
        // ArsNSpellsCommands has no @Mod.EventBusSubscriber, needs explicit registration
        MinecraftForge.EVENT_BUS.register(ArsNSpellsCommands.class);
        // Note: CrossCastingHandler, EquipmentHandler, CurioDiscountHandler, CursedRingHandler,
        // VirtueRingHandler, LPDeathPrevention, AuraCapabilityProvider are auto-registered
        // via @Mod.EventBusSubscriber — do NOT register them here to avoid double-firing.

        if (ModList.get().isLoaded("irons_spellbooks")) {
            // Instance-registered handlers (no @Mod.EventBusSubscriber).
            // IronsLPHandler used to auto-subscribe but its Iron's-API imports
            // would crash an Iron's-less server at classload, so it is now
            // gated and instance-registered here too.
            MinecraftForge.EVENT_BUS.register(new IronsCooldownHandler());
            MinecraftForge.EVENT_BUS.register(new ProgressionHandler());
            MinecraftForge.EVENT_BUS.register(new IronsProgressionHandler());
            MinecraftForge.EVENT_BUS.register(new IronsAffinityHandler());
            MinecraftForge.EVENT_BUS.register(new ArsSpellScalingHandler());
            MinecraftForge.EVENT_BUS.register(new ResonanceEvents());
            MinecraftForge.EVENT_BUS.register(new RegenSynergyHandler());
            MinecraftForge.EVENT_BUS.register(new CrossCastIronsHandler());
            MinecraftForge.EVENT_BUS.register(new IronsLPHandler());
            MinecraftForge.EVENT_BUS.register(new IronsAuraHandler());
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

        // Ring/Iron's integration self-check. Catches silent mixin or registration
        // failures that would otherwise produce "nothing happens when I cast".
        if (ModList.get().isLoaded("irons_spellbooks")) {
            runRingIntegrationSelfCheck();
        }

        LOGGER.info("========================================");
        LOGGER.info("OK Ars 'n' Spells initialization complete");
        LOGGER.info("========================================");
    }

    /**
     * Verify that the mixins and event subscribers required for the ring/Iron's
     * integration actually applied at classload. Mixin only WARNs on @Redirect
     * conflicts — a downstream silent failure ("nothing happens when casting")
     * is the usual symptom. Logging at startup gives the user a single line to
     * grep for if the integration ever silently breaks.
     */
    private static void runRingIntegrationSelfCheck() {
        StringBuilder report = new StringBuilder("[SelfCheck] Iron's ring integration: ");
        boolean ok = true;

        // 1. Is the MagicDataAccessor interface attached to Iron's MagicData?
        try {
            Class<?> magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Class<?> accessor = Class.forName("com.otectus.arsnspells.mixin.irons.MagicDataAccessor");
            boolean attached = accessor.isAssignableFrom(magicData);
            report.append("MagicDataAccessor=").append(attached ? "OK" : "MISSING ");
            if (!attached) ok = false;
        } catch (Throwable t) {
            report.append("MagicDataAccessor=ERROR(").append(t.getClass().getSimpleName()).append(") ");
            ok = false;
        }

        // 2. Are IronsAuraHandler / IronsLPHandler registered on the EVENT_BUS?
        // We can't introspect the bus's listener list directly without API access,
        // so we just verify the classes were loaded (which happens lazily when
        // ArsNSpells.<init> registered them).
        report.append("| IronsAuraHandler=")
            .append(canLoad("com.otectus.arsnspells.events.IronsAuraHandler") ? "OK" : "MISSING ");
        report.append("| IronsLPHandler=")
            .append(canLoad("com.otectus.arsnspells.events.IronsLPHandler") ? "OK" : "MISSING ");
        report.append("| MixinIronsCastValidation=")
            .append(canLoad("com.otectus.arsnspells.mixin.irons.MixinIronsCastValidation") ? "OK" : "MISSING ");

        if (ok) {
            LOGGER.info(report.toString());
        } else {
            LOGGER.error(report.toString());
            LOGGER.error("[SelfCheck] Ring/Iron's integration is degraded — casts may silently fail.");
            LOGGER.error("[SelfCheck] Check the early-startup log for '@Redirect conflict' or '@Mixin' warnings.");
        }
    }

    private static boolean canLoad(String className) {
        try {
            Class.forName(className, false, ArsNSpells.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
