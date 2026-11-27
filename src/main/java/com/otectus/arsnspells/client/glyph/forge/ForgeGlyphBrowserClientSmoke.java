package com.otectus.arsnspells.client.glyph.forge;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.hollingsworth.arsnouveau.client.gui.book.InfinityGuiSpellBook;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.setup.config.ServerConfig;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.otectus.arsnspells.client.glyph.ArsGlyphEditorHost;
import com.otectus.arsnspells.client.glyph.BrowserPlatform;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Opt-in, disposable-save client driver for the Forge Ars 4.12.7 glyph-browser hosts. */
@Mod.EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class ForgeGlyphBrowserClientSmoke {
    public static final String SAVE = "ANS Glyph Browser QA";
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeGlyphBrowserClientSmoke.class);
    private static int stage, wait, age;
    private static CompletableFuture<Void> serverWork;
    private static ImportedIronEffect low;
    private static int originalKnowledge;
    private static boolean knowledgeChangeScheduled;
    private static boolean reboundAfterSave;
    private static boolean acquisitionRouteOpened;
    private static int captureCount;

    private ForgeGlyphBrowserClientSmoke() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ans.glyphBrowserSmoke")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++age > 7200) throw new IllegalStateException("Glyph browser smoke timed out at stage " + stage + " screen=" + mc.screen);
        if (mc.getOverlay() != null) return;
        if (stage == 0) {
            if (!(mc.screen instanceof TitleScreen) && (mc.screen == null
                    || !mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) return;
            if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat")))
                throw new IllegalStateException("Missing isolated glyph-browser QA save " + SAVE);
            mc.options.guiScale().set(3);
            mc.getWindow().setWindowed(1280, 960);
            mc.resizeDisplay();
            titleSettings(mc);
            loadQaWorld(mc);
            stage = 1;
            LOGGER.info("ANS_GLYPH_BROWSER_PROGRESS stage=world-open-requested");
            return;
        }
        if (stage == 1) {
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            var id = mc.player.getUUID();
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            if (serverPlayer == null || !CapabilityRegistry.getPlayerDataCap(serverPlayer).isPresent()) return;
            LOGGER.info("ANS_GLYPH_BROWSER_PROGRESS stage=seed player={}", id);
            serverWork = CompletableFuture.runAsync(() -> seed(serverPlayer), mc.getSingleplayerServer());
            stage = 2;
            return;
        }
        if (stage == 2) {
            if (age % 200 == 0) LOGGER.info("ANS_GLYPH_BROWSER_PROGRESS stage=sync work_done={} held={} knowledge={}/{}",
                serverWork.isDone(), mc.player.getMainHandItem().getItem(),
                CapabilityRegistry.getPlayerDataCap(mc.player).map(cap -> cap.getKnownGlyphs().size()).orElse(-1), originalKnowledge);
            if (!serverWork.isDone()) return;
            serverWork.join();
            if (mc.player.getMainHandItem().getItem() != ItemsRegistry.ARCHMAGE_SPELLBOOK.get()) return;
            if (CapabilityRegistry.getPlayerDataCap(mc.player).map(cap -> cap.getKnownGlyphs().size()).orElse(0) < originalKnowledge) return;
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 960);
            mc.resizeDisplay();
            AnsClientConfig.setMode(DisplayMode.GROUPED);
            mc.setScreen(new GuiSpellBook(InteractionHand.MAIN_HAND));
            stage = 3;
            wait = 0;
            return;
        }
        if (++wait < 24) return;
        wait = 0;
        switch (stage) {
            case 3 -> {
                GuiSpellBook screen = require(mc, GuiSpellBook.class);
                requireHost(screen);
                assertImportedPalette(screen.unlockedSpells, false);
                screenshot(mc, "01-standard-grouped");
                screen.spell_name.setValue("Unsaved Browser Draft");
                screen.craftingCells.get(0).setAbstractSpellPart(MethodSelf.INSTANCE);
                screen.craftingCells.get(1).clear();
                screen.craftingCells.get(2).setAbstractSpellPart(low);
                screen.searchBar.setValue("fire");
                screenshot(mc, "02-standard-draft-gap");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1024, 720);
                mc.resizeDisplay();
            }
            case 4 -> {
                GuiSpellBook screen = require(mc, GuiSpellBook.class);
                if (!"Unsaved Browser Draft".equals(screen.spell_name.getValue())
                        || screen.craftingCells.get(0).getAbstractSpellPart() != MethodSelf.INSTANCE
                        || screen.craftingCells.get(1).getAbstractSpellPart() != null
                        || screen.craftingCells.get(2).getAbstractSpellPart() != low
                        || !"fire".equals(screen.searchBar.getValue()))
                    throw new IllegalStateException("Standard draft/name/gap/query was lost on resize");
                screenshot(mc, "03-standard-resized-draft-retained");
                AnsClientConfig.setMode(DisplayMode.FLAT);
            }
            case 5 -> {
                GuiSpellBook screen = require(mc, GuiSpellBook.class);
                assertImportedPalette(screen.unlockedSpells, !BrowserPlatform.pending() && BrowserPlatform.available(low));
                screenshot(mc, "04-standard-flat");
                AnsClientConfig.setMode(DisplayMode.HIDDEN);
            }
            case 6 -> {
                GuiSpellBook screen = require(mc, GuiSpellBook.class);
                assertImportedPalette(screen.unlockedSpells, false);
                if (((ArsGlyphEditorHost) screen).browserController().bookmark() != null)
                    throw new IllegalStateException("Hidden mode retained the browser bookmark");
                screenshot(mc, "05-standard-hidden");
                AnsClientConfig.setMode(DisplayMode.GROUPED);
                screen.searchBar.setValue("");
                ((ArsGlyphEditorHost) screen).browserController().open();
            }
            case 7 -> {
                if (!mc.screen.getClass().getSimpleName().equals("GlyphLibraryScreen"))
                    throw new IllegalStateException("Standard bookmark did not open the layered library: " + mc.screen);
                screenshot(mc, "06-standard-library");
                mc.screen.onClose();
            }
            case 8 -> {
                GuiSpellBook screen = require(mc, GuiSpellBook.class);
                for (var cell : screen.craftingCells) cell.clear();
                screen.craftingCells.get(0).setAbstractSpellPart(MethodSelf.INSTANCE);
                Component error = ((ArsGlyphEditorHost) screen).browserError(low);
                if (BrowserPlatform.available(low)) {
                    if (error != null) throw new IllegalStateException("Available imported glyph was refused: " + error.getString());
                    ((ArsGlyphEditorHost) screen).browserInsert(low);
                    if (screen.craftingCells.get(1).getAbstractSpellPart() != low)
                        throw new IllegalStateException("Standard host did not use the native insertion path");
                    screen.spell_name.setValue("Browser Accepted Save");
                    screen.onCreateClick(null);
                } else if (error == null) {
                    throw new IllegalStateException("Dormant/absent imported glyph was accepted");
                }
                screenshot(mc, "07-standard-insertion-state");
            }
            case 9 -> {
                GuiSpellBook screen = require(mc, GuiSpellBook.class);
                if (!reboundAfterSave) {
                    reboundAfterSave = true;
                    mc.setScreen(new GuiSpellBook(InteractionHand.MAIN_HAND));
                    wait = 23;
                    return;
                }
                if (!((ArsGlyphEditorHost) screen).browserOwnsBook()) throw new IllegalStateException("Reopened editor did not bind the current book");
                if (!knowledgeChangeScheduled) {
                    knowledgeChangeScheduled = true;
                    var id = mc.player.getUUID();
                    serverWork = CompletableFuture.runAsync(() -> changeKnowledge(mc.getSingleplayerServer().getPlayerList().getPlayer(id)), mc.getSingleplayerServer());
                    wait = 23;
                    return;
                }
                if (!serverWork.isDone()) { wait = 23; return; }
                serverWork.join();
                int now = ((ArsGlyphEditorHost) screen).browserKnowledge().size();
                if (now >= originalKnowledge) { wait = 23; return; }
                screenshot(mc, "08-standard-knowledge-refresh");
                var id = mc.player.getUUID();
                serverWork = CompletableFuture.runAsync(() -> equip(mc.getSingleplayerServer().getPlayerList().getPlayer(id), false), mc.getSingleplayerServer());
            }
            case 10 -> {
                if (!serverWork.isDone() || mc.player.getMainHandItem().getItem() != ItemsRegistry.NOVICE_SPELLBOOK.get()) { wait = 23; return; }
                mc.setScreen(new GuiSpellBook(InteractionHand.MAIN_HAND));
            }
            case 11 -> {
                GuiSpellBook screen = require(mc, GuiSpellBook.class);
                ImportedIronEffect restricted = ((ArsGlyphEditorHost) screen).browserKnowledge().stream()
                    .filter(ImportedIronEffect.class::isInstance).map(ImportedIronEffect.class::cast)
                    .filter(BrowserPlatform::available).filter(part -> GlyphAvailability.tier(part.definition()) > 1).findFirst().orElse(null);
                if (restricted != null && ((ArsGlyphEditorHost) screen).browserError(restricted) == null)
                    throw new IllegalStateException("Novice editor accepted a higher-tier imported glyph");
                screenshot(mc, "09-standard-tier-restriction");
                var id = mc.player.getUUID();
                serverWork = CompletableFuture.runAsync(() -> equip(mc.getSingleplayerServer().getPlayerList().getPlayer(id), true), mc.getSingleplayerServer());
            }
            case 12 -> {
                if (!serverWork.isDone() || mc.player.getMainHandItem().getItem() != ItemsRegistry.ARCHMAGE_SPELLBOOK.get()) { wait = 23; return; }
                ServerConfig.INFINITE_SPELLS.set(true);
                AnsClientConfig.setMode(DisplayMode.GROUPED);
                mc.setScreen(new InfinityGuiSpellBook(InteractionHand.MAIN_HAND));
            }
            case 13 -> {
                InfinityGuiSpellBook screen = require(mc, InfinityGuiSpellBook.class);
                screen.spell_name.setValue("Infinity Unsaved Draft");
                screen.spell = new ArrayList<>(Arrays.asList(MethodSelf.INSTANCE, null, low));
                screen.updateWindowOffset(0);
                screenshot(mc, "10-infinity-grouped-gap");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 960);
                mc.resizeDisplay();
            }
            case 14 -> {
                InfinityGuiSpellBook screen = require(mc, InfinityGuiSpellBook.class);
                if (!"Infinity Unsaved Draft".equals(screen.spell_name.getValue()) || screen.spell.size() != 3
                        || screen.spell.get(1) != null || screen.spell.get(2) != low)
                    throw new IllegalStateException("Infinity backing draft/window was lost on resize");
                screenshot(mc, "11-infinity-resized-retained");
                AnsClientConfig.setMode(DisplayMode.FLAT);
            }
            case 15 -> {
                screenshot(mc, "12-infinity-flat");
                AnsClientConfig.setMode(DisplayMode.HIDDEN);
            }
            case 16 -> {
                InfinityGuiSpellBook screen = require(mc, InfinityGuiSpellBook.class);
                assertImportedPalette(screen.unlockedSpells, false);
                screenshot(mc, "13-infinity-hidden");
                AnsClientConfig.setMode(DisplayMode.GROUPED);
                ((ArsGlyphEditorHost) screen).browserController().open();
            }
            case 17 -> {
                screenshot(mc, "14-infinity-library");
                mc.screen.onClose();
            }
            case 18 -> {
                require(mc, InfinityGuiSpellBook.class);
                mc.setScreen(new GlyphUnlockMenu(BlockPos.ZERO));
            }
            case 19 -> {
                GlyphUnlockMenu screen = require(mc, GlyphUnlockMenu.class);
                if (screen.allParts.stream().anyMatch(ImportedIronEffect.class::isInstance))
                    throw new IllegalStateException("Grouped acquisition leaked individual imported levels");
                screenshot(mc, "15-acquisition-grouped");
                Button route = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.visible && button.getMessage().getString().equals(Component.translatable(
                        "ars_n_spells.browser.acquisition.bookmark").getString()))
                    .findFirst().orElse(null);
                boolean routeExpected = !ForgeGlyphAcquisition.groups().isEmpty()
                    || ForgeGlyphAcquisition.hasAvailableImports();
                if ((route != null) != routeExpected)
                    throw new IllegalStateException("Grouped acquisition route presence=" + (route != null)
                        + " expected=" + routeExpected);
                acquisitionRouteOpened = route != null;
                if (route != null) route.onPress();
            }
            case 20 -> {
                if (!acquisitionRouteOpened) break;
                require(mc, ForgeGlyphAcquisitionScreen.class);
                screenshot(mc, ForgeGlyphAcquisition.groups().isEmpty() ? "16-acquisition-press-help" : "16-acquisition-real-groups");
                if (ForgeGlyphAcquisition.groups().isEmpty()) {
                    mc.screen.onClose();
                } else {
                    pressFirstChoice(mc);
                }
            }
            case 21 -> {
                if (!acquisitionRouteOpened) break;
                if (!ForgeGlyphAcquisition.groups().isEmpty()) {
                    require(mc, ForgeGlyphAcquisitionScreen.class);
                    screenshot(mc, "17-acquisition-real-levels");
                    pressFirstChoice(mc);
                    GlyphUnlockMenu screen = require(mc, GlyphUnlockMenu.class);
                    if (screen.selectedRecipe == null || !(screen.selectedRecipe.getSpellPart() instanceof ImportedIronEffect))
                        throw new IllegalStateException("Concrete grouped acquisition did not return the real recipe");
                }
                screenshot(mc, "18-acquisition-return");
            }
            case 22 -> {
                LOGGER.info("ANS_GLYPH_BROWSER_CLIENT_COMPLETE irons={} version={} registered={} initial_knowledge={} final_knowledge={} "
                        + "standard=true infinity=true resize_gaps=true modes=grouped,flat,hidden acquisition_real={} pending={} screenshots={}",
                    ModList.get().isLoaded("irons_spellbooks"), ModList.get().getModContainerById("irons_spellbooks")
                        .map(mod -> mod.getModInfo().getVersion().toString()).orElse("absent"),
                    ImportedGlyphRegistrar.parts().size(), originalKnowledge,
                    CapabilityRegistry.getPlayerDataCap(mc.player).map(cap -> cap.getKnownGlyphs().size()).orElse(0),
                    !ForgeGlyphAcquisition.groups().isEmpty(), BrowserPlatform.pending(), captureCount);
                mc.stop();
            }
            default -> throw new IllegalStateException("Unknown glyph browser smoke stage " + stage);
        }
        stage++;
    }

    private static void seed(ServerPlayer player) {
        if (player == null) throw new IllegalStateException("Glyph browser QA player did not join");
        LOGGER.info("ANS_GLYPH_BROWSER_PROGRESS stage=seed-server-start");
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.setAirSupply(player.getMaxAirSupply());
        player.teleportTo(0.5, 200.0, 0.5);
        low = ImportedGlyphRegistrar.parts().stream()
            .filter(part -> part.definition().spell().toString().equals("irons_spellbooks:firebolt"))
            .min(java.util.Comparator.comparingInt(part -> part.definition().level())).orElseThrow();
        var known = new ArrayList<AbstractSpellPart>();
        known.add(MethodSelf.INSTANCE);
        known.addAll(ImportedGlyphRegistrar.parts());
        CapabilityRegistry.getPlayerDataCap(player).orElseThrow(() -> new IllegalStateException("Missing Ars player capability")).setKnownGlyphs(known);
        originalKnowledge = known.size();
        CapabilityRegistry.EventHandler.syncPlayerCap(player);
        equip(player, true);
        LOGGER.info("ANS_GLYPH_BROWSER_PROGRESS stage=seed-server-complete knowledge={}", originalKnowledge);
    }

    private static void equip(ServerPlayer player, boolean archmage) {
        ItemStack book = new ItemStack((archmage ? ItemsRegistry.ARCHMAGE_SPELLBOOK : ItemsRegistry.NOVICE_SPELLBOOK).get());
        SpellCaster caster = new SpellCaster(book);
        caster.setSpell(new Spell(MethodSelf.INSTANCE), 0);
        caster.setSpellName(archmage ? "QA Archmage" : "QA Novice", 0);
        caster.writeItem(book);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, book);
        player.inventoryMenu.broadcastChanges();
    }

    private static void changeKnowledge(ServerPlayer player) {
        Collection<AbstractSpellPart> current = CapabilityRegistry.getPlayerDataCap(player)
            .orElseThrow(() -> new IllegalStateException("Missing Ars player capability")).getKnownGlyphs();
        List<AbstractSpellPart> reduced = new ArrayList<>();
        reduced.add(MethodSelf.INSTANCE);
        int index = 0;
        for (AbstractSpellPart part : current) if (part instanceof ImportedIronEffect && index++ % 2 == 0) reduced.add(part);
        CapabilityRegistry.getPlayerDataCap(player).orElseThrow(() -> new IllegalStateException("Missing Ars player capability"))
            .setKnownGlyphs(reduced);
        CapabilityRegistry.EventHandler.syncPlayerCap(player);
    }

    private static void assertImportedPalette(List<AbstractSpellPart> palette, boolean expected) {
        boolean present = palette.stream().anyMatch(ImportedIronEffect.class::isInstance);
        if (present != expected) throw new IllegalStateException("Imported palette presence=" + present + " expected=" + expected);
    }

    private static ArsGlyphEditorHost requireHost(Object screen) {
        if (!(screen instanceof ArsGlyphEditorHost host)) throw new IllegalStateException("Editor host mixin did not apply: " + screen.getClass());
        return host;
    }

    private static <T> T require(Minecraft mc, Class<T> type) {
        if (!type.isInstance(mc.screen)) throw new IllegalStateException("Expected " + type.getSimpleName() + ", found " + mc.screen);
        return type.cast(mc.screen);
    }

    private static void pressFirstChoice(Minecraft mc) {
        Button choice = mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.active && !button.getMessage().getString().equals(Component.translatable("gui.close").getString())
                && !button.getMessage().getString().equals(Component.translatable("gui.back").getString())
                && !button.getMessage().getString().equals("<") && !button.getMessage().getString().equals(">"))
            .findFirst().orElseThrow(() -> new IllegalStateException("No acquisition choice button in " + mc.screen));
        choice.onPress();
    }

    /** The disposable QA copies are trusted; skip Minecraft's one-time experimental-world prompt. */
    private static void titleSettings(Minecraft mc) {
        Screen settings = com.otectus.arsnspells.client.screen.ConfigScreenFactory.createConfigScreen(new TitleScreen());
        mc.setScreen(settings);
        pressSetting(mc, "ars_n_spells.browser.personal");
        if (!(mc.screen instanceof com.otectus.arsnspells.client.glyph.PersonalDisplayScreen))
            throw new IllegalStateException("Personal display did not open at the title screen");
        DisplayMode original = AnsClientConfig.mode();
        pressSetting(mc, "ars_n_spells.browser.reset");
        pressSetting(mc, "gui.cancel");
        if (mc.screen != settings || AnsClientConfig.mode() != original) throw new IllegalStateException("Cancel changed the title preference");
        pressSetting(mc, "ars_n_spells.browser.personal");
        pressSetting(mc, "ars_n_spells.browser.reset");
        pressSetting(mc, "ars_n_spells.browser.apply");
        if (AnsClientConfig.mode() != DisplayMode.GROUPED) throw new IllegalStateException("Personal reset did not apply");
        mc.setScreen(new TitleScreen());
        LOGGER.info("ANS_GLYPH_BROWSER_TITLE_PASS");
    }

    private static void pressSetting(Minecraft mc, String key) {
        String label = Component.translatable(key).getString();
        mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.visible && button.active && button.getMessage().getString().equals(label))
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing title control " + key)).onPress();
    }

    private static void loadQaWorld(Minecraft mc) {
        WorldOpenFlows flows = mc.createWorldOpenFlows();
        try {
            var load = ObfuscationReflectionHelper.findMethod(WorldOpenFlows.class, "m_233145_",
                Screen.class, String.class, boolean.class, boolean.class);
            load.invoke(flows, new TitleScreen(), SAVE, false, false);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Could not open the isolated glyph-browser QA save", failure);
        }
    }

    private static void screenshot(Minecraft mc, String name) {
        try {
            var directory = mc.gameDirectory.toPath().resolve("glyph-browser-review");
            Files.createDirectories(directory);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(directory.resolve(name + ".png"));
            }
            LOGGER.info("ANS_GLYPH_BROWSER_CAPTURE name={} viewport={}x{} scale={} screen={}", name,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), mc.getWindow().getGuiScale(),
                mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
            captureCount++;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not capture glyph browser smoke", failure);
        }
    }
}
