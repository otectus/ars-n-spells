package com.otectus.arsnspells;

import com.otectus.arsnspells.commands.ArsNSpellsCommands;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.ModCapabilityProvider;
import com.otectus.arsnspells.data.CooldownData;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.events.*;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.registry.ModBlockEntities;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModCreativeTabs;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.registry.ModLootModifiersRegistry;
import com.otectus.arsnspells.registry.ModMenus;
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
        modEventBus.addListener(this::onConfigReloading);
        // The Iron's integration self-check probes Iron's classes reflectively, which
        // classloads them. Doing that during mod construction or config load would
        // force AbstractSpell/Scroll to transform before other mods' mixin configs are
        // ready - the same MixinTargetAlreadyLoadedException hazard documented in
        // ArsNSpellsMixinPlugin. FMLLoadCompleteEvent is after every registry pass, so
        // the targets are already loaded and the probe merely observes them.
        modEventBus.addListener(this::onLoadComplete);

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

        // 3.3.3: blank-scroll chest loot. The serializer registers everywhere; the
        // modifier itself returns loot untouched without Iron's (see
        // BlankScrollLootModifier).
        ModLootModifiersRegistry.register(modEventBus);

        // 3.0.0: Spell Loom workstation — block, block entity, and menu. Not
        // Iron's-gated: the block registers everywhere; its export action no-ops
        // with a clear message when Iron's is absent (no Iron's scroll item).
        ModBlocksRegistry.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);

        // 3.2.0: the mod's own creative tab. Not Iron's-gated — the Spell Loom and the
        // uninscribe tablet are registered unconditionally, so the tab always has content.
        // Registration order does not matter here: the icon and display-item suppliers
        // dereference their RegistryObjects lazily, when the tab is built.
        ModCreativeTabs.register(modEventBus);

        // 3.0.0: register the Ars cross-cast proxy-spell pool (ars_cross_1..N)
        // into Iron's spell registry so Ars spells can appear as native entries
        // in Iron's spell wheel. Gated — ArsCrossProxyRegistry references Iron's
        // API (SpellRegistry.SPELL_REGISTRY_KEY) and must not classload without
        // Iron's present. Referenced by FQN inside the gate for the same reason.
        if (ModList.get().isLoaded("irons_spellbooks")) {
            com.otectus.arsnspells.spell.irons.ArsCrossProxyRegistry.register(modEventBus);
        }

        // ANS-HIGH-016: register as SERVER (was COMMON). Gameplay tunables — resonance,
        // conversion rates, dual-cost percentages, ring toggles — must be server-authoritative
        // on dedicated servers, otherwise the in-game config screen on a client silently
        // no-ops because COMMON configs don't auto-sync from client to server. SERVER configs
        // are loaded server-side and auto-synced to clients on login.
        // Existing user configs at config/ars_n_spells-common.toml will be ignored; document
        // this in the 2.0.1 CHANGELOG.
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AnsConfig.SPEC, "ars_n_spells-server.toml");

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, this::onAttachCapabilities);
        // Instance-registered handlers (no @Mod.EventBusSubscriber, use instance @SubscribeEvent methods)
        MinecraftForge.EVENT_BUS.register(new CooldownHandler());
        MinecraftForge.EVENT_BUS.register(new AffinityHandler());
        MinecraftForge.EVENT_BUS.register(new AffinityDecayHandler());
        // AffinitySyncOnLoginHandler retired in 2.0.0 — its job is subsumed by
        // CapabilityResyncHandler (auto-registered, covers login/respawn/dim).
        // ArsNSpellsCommands has no @Mod.EventBusSubscriber, needs explicit registration
        MinecraftForge.EVENT_BUS.register(ArsNSpellsCommands.class);
        // Note: CrossCastingHandler, EquipmentHandler, CurioDiscountHandler, CursedRingHandler,
        // VirtueRingHandler, LPDeathPrevention, AuraCapabilityProvider,
        // CapabilityResyncHandler are auto-registered via @Mod.EventBusSubscriber —
        // do NOT register them here to avoid double-firing.

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
            MinecraftForge.EVENT_BUS.register(new com.otectus.arsnspells.casting.IronsCastPayments());
            MinecraftForge.EVENT_BUS.register(new IronsLPHandler());
            // IronsAuraHandler deleted: Covenant of the Seven's own Iron's integration
            // deducts aura natively for Iron's spells. We were double-paying.
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Register the datapack-driven glyph → school overrides. Fires on world load and on
     * {@code /reload}, so pack authors can iterate without restarting.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onAddReloadListeners(net.minecraftforge.event.AddReloadListenerEvent event) {
        event.addListener(new com.otectus.arsnspells.data.GlyphSchoolReloadListener());
    }

    /**
     * Drop datapack overrides when the server stops, so a single-player session that loads a
     * pack with overrides does not leak them into the next world opened without it.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        com.otectus.arsnspells.util.SchoolMappings.reset();
    }

    private void registerCaps(RegisterCapabilitiesEvent event) {
        event.register(AffinityData.class);
        event.register(CooldownData.class);
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

        // 3.3.0 (T1.2): resolve the schema-migrated keys and log the migration report once,
        // before anything downstream reads a policy that migration may have decided.
        try {
            com.otectus.arsnspells.config.AnsConfig.onConfigLoaded(event.getConfig().getFullPath());
        } catch (Exception e) {
            LOGGER.error("FAILED to resolve config schema migration", e);
        }

        // ANS-HIGH-029: the config is SERVER-type (loaded at world/server start),
        // but BridgeManager.init runs at common setup and caches the mana mode from
        // an unloaded spec — i.e. from defaults. Without this refresh, the mode a
        // server owner set in ars_n_spells-server.toml was silently ignored until
        // someone ran /ans mode set. Re-select bridges now that real values exist.
        try {
            com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
            // V14: a SERVER config load can change the mode out from under players who are
            // already online (a /reload, or a config edit applied mid-session).
            com.otectus.arsnspells.bridge.ModeChangeCleanup.reconcileAll();
        } catch (Exception e) {
            LOGGER.error("FAILED to refresh mana bridge mode from loaded config", e);
        }

        // SERVER configs load on every world/server start (single-player included);
        // the compat init, self-check, and banner only need to happen once per game
        // session. The bridge refresh above intentionally runs every time.
        if (!initBannerLogged.compareAndSet(false, true)) {
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

    private static final java.util.concurrent.atomic.AtomicBoolean initBannerLogged =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * ANS-HIGH-029: file edits/`/reload` fire Reloading, not Loading — the bridge
     * mode must follow those too, matching what /ans mode set already does.
     */
    private void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (!event.getConfig().getModId().equals(MODID)) {
            return;
        }
        try {
            com.otectus.arsnspells.bridge.BridgeManager.refreshMode();
        } catch (Exception e) {
            LOGGER.error("FAILED to refresh mana bridge mode after config reload", e);
        }
    }

    /**
     * Ring/Iron's integration self-check. Catches silent mixin failures that would
     * otherwise surface only as "nothing happens when I cast".
     */
    private void onLoadComplete(final net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        try {
            runRingIntegrationSelfCheck();
        } catch (Throwable t) {
            LOGGER.error("[SelfCheck] Iron's integration self-check itself failed", t);
        }
    }

    /**
     * Verify that the mixins the Iron's integration depends on actually applied at
     * classload.
     *
     * <p>Every injector into Iron's carries {@code require = 0}, and the compat mixin
     * config is {@code "required": false}, so a conflict or an upstream rename
     * degrades quietly by design — that is what stops one third-party
     * {@code @Overwrite} from aborting mod loading for an entire pack. The cost of
     * that safety is that the failure is silent, so this check exists to make it
     * greppable in a single log line.
     *
     * <p>The probes below introspect the <em>target</em> classes, not our own. An
     * earlier version called {@code Class.forName} on our mixin classes, which always
     * succeeds — they ship in our jar whether or not they were ever applied — so it
     * could never detect the degradation it was written to catch. Mixin merges handler
     * methods into the target under their own names, so the presence of an
     * {@code arsnspells$}-prefixed member on an Iron's class is direct evidence that
     * our mixin applied to it.
     */
    private static void runRingIntegrationSelfCheck() {
        StringBuilder report = new StringBuilder("[SelfCheck] Iron's integration: ");
        boolean ok = true;

        // 1. Is the MagicDataAccessor interface attached to Iron's MagicData?
        try {
            Class<?> magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Class<?> accessor = Class.forName("com.otectus.arsnspells.mixin.irons.MagicDataAccessor");
            boolean attached = accessor.isAssignableFrom(magicData);
            report.append("MagicDataAccessor=").append(attached ? "OK" : "MISSING");
            if (!attached) ok = false;
        } catch (Throwable t) {
            report.append("MagicDataAccessor=ERROR(").append(t.getClass().getSimpleName()).append(")");
            ok = false;
        }

        // 2. Did the mana + cast-gate mixins merge into their Iron's targets?
        ok &= appendMixinProbe(report, "MagicData.mana",
            "io.redspace.ironsspellbooks.api.magic.MagicData");
        ok &= appendMixinProbe(report, "AbstractSpell.castGate",
            "io.redspace.ironsspellbooks.api.spells.AbstractSpell");
        ok &= appendMixinProbe(report, "Scroll.cost",
            "io.redspace.ironsspellbooks.item.Scroll");

        // 3. Does Scroll still override Item.use? MixinScrollItem injects into that
        // override, and its name is mapping-dependent (`use` in dev, `m_7203_` in
        // production) — which is exactly how a missing `remap = true` let scroll cost
        // enforcement silently no-op for several releases. Match on signature instead
        // of name so the probe is mapping-independent.
        boolean scrollUsePresent = scrollOverridesUse();
        report.append(" | Scroll.use=").append(scrollUsePresent ? "OK" : "MISSING");
        if (!scrollUsePresent) ok = false;

        // 4. Is IronsLPHandler registered on the EVENT_BUS?
        // We can't introspect the bus's listener list directly without API access,
        // so we just verify the classes were loaded (which happens lazily when
        // ArsNSpells.<init> registered them). IronsAuraHandler was deleted as part of
        // the aura-subsystem cleanup — Covenant owns Iron's-spell aura now.
        report.append(" | IronsLPHandler=")
            .append(canLoad("com.otectus.arsnspells.events.IronsLPHandler") ? "OK" : "MISSING");

        if (ok) {
            LOGGER.info(report.toString());
        } else {
            LOGGER.error(report.toString());
            LOGGER.error("[SelfCheck] Iron's integration is degraded - casts may silently ignore ANS costs.");
            LOGGER.error("[SelfCheck] Check the early-startup log for 'ars_n_spells.compat.mixins.json' warnings;");
            LOGGER.error("[SelfCheck] a conflicting mod's @Overwrite on an Iron's method is the usual cause.");
        }
    }

    /**
     * Report whether any of our mixins merged a member into {@code targetClassName}.
     *
     * @return true if the probe passed, so callers can fold it into an overall status
     */
    private static boolean appendMixinProbe(StringBuilder report, String label, String targetClassName) {
        report.append(" | ").append(label).append('=');
        try {
            Class<?> target = Class.forName(targetClassName);
            for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                if (m.getName().contains("arsnspells$")) {
                    report.append("OK");
                    return true;
                }
            }
            report.append("NOT-APPLIED");
            return false;
        } catch (Throwable t) {
            report.append("ERROR(").append(t.getClass().getSimpleName()).append(')');
            return false;
        }
    }

    /**
     * Whether Iron's {@code Scroll} still overrides {@code Item.use}. Matched by
     * signature because the method name differs between the dev and production
     * mappings.
     */
    private static boolean scrollOverridesUse() {
        try {
            Class<?> scroll = Class.forName("io.redspace.ironsspellbooks.item.Scroll");
            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            Class<?> player = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> hand = Class.forName("net.minecraft.world.InteractionHand");
            for (java.lang.reflect.Method m : scroll.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 3 && params[0] == level && params[1] == player && params[2] == hand) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
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
