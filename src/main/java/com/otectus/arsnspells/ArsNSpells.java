package com.otectus.arsnspells;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.commands.ArsNSpellsCommands;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.compat.ModPresence;
import com.otectus.arsnspells.compat.curios.IronsCurioDiscountHandler;
import com.otectus.arsnspells.compat.irons_spells.SchoolIndex;
import com.otectus.arsnspells.combat.ArsDamageBridge;
import com.otectus.arsnspells.combat.IronsDamageBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.events.AffinityDecayHandler;
import com.otectus.arsnspells.events.AffinityHandler;
import com.otectus.arsnspells.events.AffinitySyncOnLoginHandler;
import com.otectus.arsnspells.events.CooldownHandler;
import com.otectus.arsnspells.events.IronsAffinityHandler;
import com.otectus.arsnspells.events.IronsCooldownHandler;
import com.otectus.arsnspells.events.IronsProgressionHandler;
import com.otectus.arsnspells.events.ProgressionHandler;
import com.otectus.arsnspells.events.ResonanceEvents;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.registry.ModBlockEntities;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModCreativeTabs;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.registry.ModMenus;
import com.otectus.arsnspells.rituals.RitualRegistryHandler;
import com.otectus.arsnspells.spell.CrossCastIronsHandler;
import com.otectus.arsnspells.spell.ModDataComponents;
import com.otectus.arsnspells.util.StartupValidator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
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
            // Native-wheel proxy spells (ars_cross_1..8). FQN-only reference:
            // ArsCrossProxyRegistry's static init classloads Iron's SpellRegistry,
            // so the class must never be touched on Iron's-less installs.
            com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry.register(modBus);
        }
        ModItemsRegistry.register(modBus);
        AttachmentTypes.register(modBus);
        ModDataComponents.register(modBus);
        // Spell Loom workstation (block + block item + block entity + menu).
        ModBlocksRegistry.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        // 3.2.0: the mod's own creative tab. Registered AFTER the item registers so its
        // displayItems callback enumerates a populated DeferredRegister; the Iron's gate above
        // is what decides whether the four Iron's-only tablets are in it.
        ModCreativeTabs.register(modBus);

        // ---- Mod-bus listeners ----
        modBus.addListener(this::commonSetup);
        // Mixin self-check at load-complete, not common setup: inspecting a class loads it,
        // and loading Iron's classes early can interfere with other mods' mixins that have
        // not been applied yet. By load-complete every config in the pack is through APPLY.
        modBus.addListener(this::onLoadComplete);
        modBus.addListener(this::onConfigLoading);
        modBus.addListener(this::onConfigReloading);
        modBus.addListener(PacketHandler::onRegisterPayloadHandlers);
        modBus.addListener(ArsNSpells::onRegisterCapabilities);

        // ---- Config registration via ModContainer ----
        // SERVER (was COMMON): gameplay tunables — mana mode, conversion rates, dual-cost
        // splits, resonance — must be server-authoritative on dedicated servers and
        // auto-synced to clients on login. A COMMON config does not sync, so the in-game
        // config screen and HUD on a connected client would read stale local values.
        // The live file moves to <world>/serverconfig/ars_n_spells-server.toml; the old
        // global config/ars_n_spells-common.toml is ignored (re-apply settings once).
        container.registerConfig(ModConfig.Type.SERVER, AnsConfig.SPEC, "ars_n_spells-server.toml");

        // ---- Game-bus instance handlers (NeoForge.EVENT_BUS) ----
        // NeoForge 1.21.1 rejects EVENT_BUS.register(x) when x has zero
        // @SubscribeEvent methods, so we only register classes that
        // actually have at least one. Phase 3 will restore the stub
        // handlers (RegenSynergyHandler, etc.) here as they gain real
        // subscribed methods.
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
            NeoForge.EVENT_BUS.register(new ArsDamageBridge());
            NeoForge.EVENT_BUS.register(new IronsDamageBridge());
            NeoForge.EVENT_BUS.register(new ResonanceEvents());
            NeoForge.EVENT_BUS.register(new CrossCastIronsHandler());
            NeoForge.EVENT_BUS.register(new IronsCurioDiscountHandler());
            // RegenSynergyHandler (Source-Jar proximity regen) auto-registers via its
            // @EventBusSubscriber annotation and self-gates on IronsCompat.isLoaded().
        }

        // ArsNSpells's own lifecycle methods (commonSetup, onConfigLoading) are
        // mod-bus listeners via modBus.addListener above — the class has no
        // game-bus @SubscribeEvent methods, so we do NOT call
        // NeoForge.EVENT_BUS.register(this) here.
    }

    private static void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
        // Expose the Spell Loom's slots to hoppers/pipes (parity with the 1.20.1
        // ForgeCapabilities.ITEM_HANDLER exposure).
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
            ModBlockEntities.SPELL_LOOM.get(), (be, side) -> be.getItems());
    }

    /**
     * Catches silent mixin failures that would otherwise surface only as "nothing happens
     * when I cast". See {@link com.otectus.arsnspells.util.MixinSelfCheck}.
     */
    private void onLoadComplete(final net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        try {
            com.otectus.arsnspells.util.MixinSelfCheck.run();
        } catch (Throwable t) {
            LOGGER.error("[SelfCheck] the self-check itself failed", t);
        }
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
            // Snapshot the Iron's school registry for /ans diagnostics, after
            // registries freeze. Gated so SchoolIndex never classloads without Iron's.
            if (ModPresence.isLoaded(CompatIds.IRONS_SPELLBOOKS)) {
                event.enqueueWork(SchoolIndex::snapshot);
            }

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
        // The SERVER config is now readable — (re)build the mana bridges for the active
        // mode. This is the real bridge-init point on a server (common setup runs before
        // the SERVER config loads). Idempotent with BridgeManager.init().
        BridgeManager.refreshMode();
        syncClientDiagnostics();

        LOGGER.info("========================================");
        LOGGER.info("OK Ars 'n' Spells initialization complete");
        LOGGER.info("========================================");
    }

    private void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (!event.getConfig().getModId().equals(MODID)) {
            return;
        }
        // Pick up runtime config edits (e.g. mana_unification_mode changed on disk or via
        // the config screen) without a restart.
        BridgeManager.refreshMode();
        syncClientDiagnostics();
    }

    /**
     * Follow {@code debug_mode} with the client-side overlay diagnostics.
     *
     * <p>Split behind a {@link FMLEnvironment#dist} check and a client-only holder because
     * {@code OverlayDiagnostics} registers a {@code RenderGuiLayerEvent} listener: naming it
     * from this common class directly would classload a client type on a dedicated server.
     * Same shape the payload classes use for their client handlers.
     */
    private static void syncClientDiagnostics() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ClientDiagnosticsHook.sync();
    }

    /** Client-only indirection for {@link #syncClientDiagnostics()}. */
    @OnlyIn(Dist.CLIENT)
    private static final class ClientDiagnosticsHook {
        private ClientDiagnosticsHook() {}

        static void sync() {
            com.otectus.arsnspells.client.OverlayDiagnostics.syncWithConfig();
        }
    }
}
