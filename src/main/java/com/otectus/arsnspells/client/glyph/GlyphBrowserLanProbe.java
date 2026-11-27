package com.otectus.arsnspells.client.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Two-client, opt-in LAN acceptance probe. Inert in every ordinary launch. */
@EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class GlyphBrowserLanProbe {
    private static final String WORLD = "ANS Browser QA";
    private static final int PORT = 25939;
    private static int stage, ticks, lifetime;
    private static GuiSpellBook book;
    private static BrowserController controller;
    private static CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
    private static boolean guestSeeded;

    private GlyphBrowserLanProbe() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        String role = System.getProperty("ans.browserLanProbe.role", "");
        if (!role.equals("host") && !role.equals("guest")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++lifetime > 5000) throw new IllegalStateException("Browser LAN " + role + " timeout at " + stage + " on " + mc.screen);
        if (mc.getOverlay() != null || ++ticks < 10 || !work.isDone()) return;
        work.join();
        ticks = 0;
        if (role.equals("host")) host(mc); else guest(mc);
    }

    private static void host(Minecraft mc) throws Exception {
        switch (stage) {
            case 0 -> {
                if (!title(mc.screen)) return;
                mc.options.guiScale().set(3);
                mc.getWindow().setWindowed(1280, 720);
                mc.resizeDisplay();
                check(Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")), "host fixture save missing");
                mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
            }
            case 1 -> {
                if (mc.player == null || mc.level == null || BrowserPlatform.pending()) return;
                AnsClientConfig.setMode(DisplayMode.GROUPED);
                work = server(() -> {
                    seed(serverPlayer("LanHost"));
                    var server = mc.getSingleplayerServer();
                    // Disposable dev profiles have no Mojang session. Make the LAN fixture's native
                    // authentication setting explicit before accepting the second offline profile.
                    server.setUsesAuthentication(false);
                    if (!server.isPublished() && !server.publishServer(GameType.SURVIVAL, false, PORT))
                        throw new IllegalStateException("Could not publish LAN server on " + PORT);
                    checkServer(server.getPort() == PORT, "LAN port changed to " + server.getPort());
                    checkServer(!server.usesAuthentication(), "LAN fixture unexpectedly requires authentication");
                });
            }
            case 2 -> {
                if (!mc.player.getMainHandItem().is(ItemsRegistry.ARCHMAGE_SPELLBOOK.get()) || knowledge().size() < 100) return;
                GuiSpellBook.open(InteractionHand.MAIN_HAND);
                book = (GuiSpellBook) mc.screen;
                controller = ((ArsGlyphEditorHost) book).browserController();
                forceRefresh();
                check(AnsClientConfig.mode() == DisplayMode.GROUPED, "host mode is not Organized");
                press(label -> label.equals(controller.bookmark().getString()));
            }
            case 3 -> {
                check(mc.screen instanceof GlyphLibraryScreen, "host Organized library did not open");
                write("host-ready", catalog());
            }
            case 4 -> {
                check(mc.screen instanceof GlyphLibraryScreen, "host library closed while guest joined");
                if (!guestSeeded) {
                    work = server(() -> {
                        var guest = mc.getSingleplayerServer().getPlayerList().getPlayerByName("LanGuest");
                        if (guest != null) {
                            seed(guest);
                            guestSeeded = true;
                        }
                    });
                    return;
                }
                if (!exists("guest-ready")) return;
                work = server(() -> {
                    var server = mc.getSingleplayerServer();
                    var guest = server.getPlayerList().getPlayerByName("LanGuest");
                    checkServer(server.getPlayerList().getPlayerCount() == 2, "expected two simultaneous players");
                    checkServer(guest != null && !server.getPlayerList().isOp(guest.getGameProfile()), "guest was an operator");
                });
            }
            case 5 -> {
                check(mc.screen instanceof GlyphLibraryScreen, "host library did not remain open at barrier");
                check(read("guest-ready").equals(catalog()), "host/guest authoritative catalogs differ");
                capture("host-organized-simultaneous");
                write("host-captured", catalog());
            }
            case 6 -> {
                check(mc.screen instanceof GlyphLibraryScreen, "host library closed during guest restart");
                if (!exists("guest-done")) return;
                check(read("guest-done").equals(catalog()), "reconnected guest catalog differs");
                capture("host-organized-after-reconnect");
                org.slf4j.LoggerFactory.getLogger(GlyphBrowserLanProbe.class).info(
                    "ANS_BROWSER_LAN_HOST_PASS catalog={} players=2 port={} authentication=offline-fixture", catalog(), PORT);
                mc.stop();
            }
            default -> { return; }
        }
        stage++;
    }

    private static void guest(Minecraft mc) throws Exception {
        boolean reconnect = Boolean.getBoolean("ans.browserLanProbe.reconnect");
        switch (stage) {
            case 0 -> {
                if (!title(mc.screen) || !exists("host-ready")) return;
                mc.options.guiScale().set(3);
                mc.getWindow().setWindowed(1280, 720);
                mc.resizeDisplay();
                var data = new net.minecraft.client.multiplayer.ServerData("ANS Browser LAN", "127.0.0.1:" + PORT,
                    net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:" + PORT), data, false, null);
            }
            case 1 -> {
                if (mc.player == null || mc.level == null || BrowserPlatform.pending()
                        || !mc.player.getMainHandItem().is(ItemsRegistry.ARCHMAGE_SPELLBOOK.get()) || knowledge().size() < 100) return;
                check(!mc.player.hasPermissions(2), "guest unexpectedly has operator authority");
                if (reconnect) check(AnsClientConfig.mode() == DisplayMode.HIDDEN, "Hidden was not reloaded from the guest client config");
                else AnsClientConfig.setMode(DisplayMode.HIDDEN);
                GuiSpellBook.open(InteractionHand.MAIN_HAND);
                book = (GuiSpellBook) mc.screen;
                controller = ((ArsGlyphEditorHost) book).browserController();
                forceRefresh();
            }
            case 2 -> {
                check(AnsClientConfig.mode() == DisplayMode.HIDDEN, "guest mode is not Hidden");
                check(controller.bookmark() == null, "Hidden guest has a library bookmark");
                check(book.unlockedSpells.stream().noneMatch(ImportedIronEffect.class::isInstance), "Hidden guest palette exposed imports");
                check(catalog().equals(read("host-ready")), "guest received a different authoritative catalog");
                book.searchBar.setValue("Firebolt");
                check(book.displayedGlyphs.stream().noneMatch(ImportedIronEffect.class::isInstance), "Hidden guest search exposed imports");
                if (reconnect) {
                    capture("guest-hidden-reconnected");
                    write("guest-done", catalog());
                    org.slf4j.LoggerFactory.getLogger(GlyphBrowserLanProbe.class).info(
                        "ANS_BROWSER_LAN_GUEST_RECONNECT_PASS catalog={} mode={}", catalog(), AnsClientConfig.mode().id());
                    mc.stop();
                    return;
                }
                capture("guest-hidden-simultaneous");
                write("guest-ready", catalog());
            }
            case 3 -> {
                check(mc.screen == book, "guest Hidden editor closed before host captured its library");
                if (!exists("host-captured")) return;
                check(read("host-captured").equals(catalog()), "barrier catalog mismatch");
                org.slf4j.LoggerFactory.getLogger(GlyphBrowserLanProbe.class).info(
                    "ANS_BROWSER_LAN_GUEST_PHASE1_PASS catalog={} mode={}", catalog(), AnsClientConfig.mode().id());
                mc.stop();
            }
            default -> { return; }
        }
        stage++;
    }

    private static void seed(ServerPlayer player) {
        if (player == null) throw new IllegalStateException("LAN player missing");
        player.setGameMode(GameType.SURVIVAL);
        var known = new ArrayList<AbstractSpellPart>();
        known.add(MethodProjectile.INSTANCE);
        for (ImportedIronEffect effect : ImportedGlyphRegistration.effects().values())
            if (effect.behavior().available()) known.add(effect);
        var cap = CapabilityRegistry.getPlayerDataCap(player);
        cap.setKnownGlyphs(known);
        CapabilityRegistry.EventHandler.syncPlayerCap(player);
        ItemStack book = ItemsRegistry.ARCHMAGE_SPELLBOOK.get().getDefaultInstance();
        new SpellCaster().setSpell(new Spell(MethodProjectile.INSTANCE)).saveToStack(book);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.inventoryMenu.broadcastChanges();
    }

    private static String catalog() {
        var snapshot = GlyphBehavior.current();
        return snapshot.catalogHash() + "|" + snapshot.variantCount() + "|" + snapshot.generation();
    }

    private static Set<String> knowledge() {
        return CapabilityRegistry.getPlayerDataCap(Minecraft.getInstance().player).getKnownGlyphs().stream()
            .map(AbstractSpellPart::getRegistryName).map(Object::toString).collect(Collectors.toSet());
    }

    private static ServerPlayer serverPlayer(String name) {
        var player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayerByName(name);
        if (player == null) throw new IllegalStateException("Integrated player " + name + " missing");
        return player;
    }

    private static CompletableFuture<Void> server(Runnable action) {
        return CompletableFuture.runAsync(action, Minecraft.getInstance().getSingleplayerServer());
    }

    private static void forceRefresh() {
        for (int i = 0; i < 12; i++) controller.tick();
    }

    private static boolean title(Screen screen) {
        return screen instanceof TitleScreen || screen != null && screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen");
    }

    private static Path sync() {
        String value = System.getProperty("ans.browserLanProbe.syncDir", "");
        if (value.isBlank()) throw new IllegalStateException("Browser LAN syncDir is required");
        return Path.of(value);
    }

    private static boolean exists(String name) { return Files.exists(sync().resolve(name)); }
    private static String read(String name) throws Exception { return Files.readString(sync().resolve(name)).strip(); }
    private static void write(String name, String value) throws Exception {
        Files.createDirectories(sync());
        Files.writeString(sync().resolve(name), value);
    }

    private static void press(Predicate<String> label) {
        Screen screen = Minecraft.getInstance().screen;
        Button button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(widget -> widget.visible && widget.active && label.test(widget.getMessage().getString())).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing LAN widget at " + stage + " on " + screen));
        button.onPress();
    }

    private static void capture(String name) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        Path out = mc.gameDirectory.toPath().resolve("glyph-browser-lan-review");
        Files.createDirectories(out);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(out.resolve(name + ".png"));
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Browser LAN " + System.getProperty("ans.browserLanProbe.role")
            + " stage " + stage + ": " + message);
    }

    private static void checkServer(boolean value, String message) {
        if (!value) throw new IllegalStateException("Browser LAN server: " + message);
    }
}
