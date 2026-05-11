package com.otectus.arsnspells;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.commands.ArsNSpellsCommands;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.events.AffinityDecayHandler;
import com.otectus.arsnspells.events.AffinityHandler;
import com.otectus.arsnspells.events.AffinitySyncOnLoginHandler;
import com.otectus.arsnspells.events.ArsSpellScalingHandler;
import com.otectus.arsnspells.events.CooldownHandler;
import com.otectus.arsnspells.events.IronsAffinityHandler;
import com.otectus.arsnspells.events.IronsCooldownHandler;
import com.otectus.arsnspells.events.IronsProgressionHandler;
import com.otectus.arsnspells.events.ProgressionHandler;
import com.otectus.arsnspells.events.RegenSynergyHandler;
import com.otectus.arsnspells.events.ResonanceEvents;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.rituals.RitualRegistryHandler;
import com.otectus.arsnspells.spell.CrossCastIronsHandler;
import com.otectus.arsnspells.spell.ModDataComponents;
import com.otectus.arsnspells.util.StartupValidator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ArsNSpells.MODID)
public class ArsNSpells {
    public static final String MODID = "ars_n_spells";
    public static final Logger LOGGER = LoggerFactory.getLogger(ArsNSpells.class);

    public ArsNSpells(IEventBus modBus, ModContainer container) {
        if (!StartupValidator.validate()) {
            LOGGER.warn("Startup validation failed - some features may not work correctly");
        }

        // ---- DeferredRegisters on the mod bus ----
        // Common items (uninscribe tablet) register unconditionally so they
        // remain available for cleanup if Iron's Spellbooks is later removed.
        // Iron's-dependent items (transcribe tablet) only when Iron's is
        // present. Both must be registered before ITEMS.register(modBus) so
        // the DeferredRegister carries the entries when the item RegisterEvent
        // fires.
        ModItemsRegistry.registerCommonItems();
        if (ModList.get().isLoaded("irons_spellbooks")) {
            ModItemsRegistry.registerIronsDependentItems();
        }
        ModItemsRegistry.register(modBus);
        AttachmentTypes.register(modBus);
        ModDataComponents.register(modBus);

        // ---- Mod-bus listeners ----
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::onConfigLoading);
        modBus.addListener(PacketHandler::onRegisterPayloadHandlers);

        // ---- Config registration via ModContainer ----
        container.registerConfig(ModConfig.Type.COMMON, AnsConfig.SPEC, "ars_n_spells-common.toml");

        // ---- Game-bus instance handlers (NeoForge.EVENT_BUS) ----
        // These have NO @EventBusSubscriber and must be registered explicitly.
        // CrossCastingHandler, EquipmentHandler, CurioDiscountHandler are
        // auto-registered via @EventBusSubscriber — do NOT register them
        // here to avoid double-firing.
        NeoForge.EVENT_BUS.register(new CooldownHandler());
        NeoForge.EVENT_BUS.register(new AffinityHandler());
        NeoForge.EVENT_BUS.register(new AffinityDecayHandler());
        NeoForge.EVENT_BUS.register(new AffinitySyncOnLoginHandler());
        NeoForge.EVENT_BUS.register(ArsNSpellsCommands.class);

        if (ModList.get().isLoaded("irons_spellbooks")) {
            NeoForge.EVENT_BUS.register(new IronsCooldownHandler());
            NeoForge.EVENT_BUS.register(new ProgressionHandler());
            NeoForge.EVENT_BUS.register(new IronsProgressionHandler());
            NeoForge.EVENT_BUS.register(new IronsAffinityHandler());
            NeoForge.EVENT_BUS.register(new ArsSpellScalingHandler());
            NeoForge.EVENT_BUS.register(new ResonanceEvents());
            NeoForge.EVENT_BUS.register(new RegenSynergyHandler());
            NeoForge.EVENT_BUS.register(new CrossCastIronsHandler());
        }

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        try {
            BridgeManager.init(event);
            event.enqueueWork(() -> {
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

        LOGGER.info("========================================");
        LOGGER.info("OK Ars 'n' Spells initialization complete");
        LOGGER.info("========================================");
    }
}
