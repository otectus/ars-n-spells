package com.otectus.arsnspells.client.glyph;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.hollingsworth.arsnouveau.client.gui.buttons.CreateSpellButton;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.otectus.arsnspells.client.screen.ConfigScreenFactory;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.DisplayMode;
import com.otectus.arsnspells.gametest.GlyphBrowserFixture;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphMappings;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static com.otectus.arsnspells.client.glyph.BrowserController.tr;
import static org.lwjgl.glfw.GLFW.*;

/** Drives actual widgets and a native save packet in an explicitly selected disposable client profile. */
@EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class GlyphBrowserSmoke {
    private static final String WORLD = "ANS Browser QA", NAME = "Browser draft";
    private static int stage, ticks, lifetime, idleBuilds;
    private static GuiSpellBook book;
    private static BrowserController controller;
    private static Set<String> known;
    private static CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
    private static boolean absent, replacementChecked, flatRendered;
    private static GlyphBrowserFixture.CastResult referenceCast;
    private static GlyphMappings.Result previousMappings;
    private static int overrideTier;
    private static boolean tierVerified;
    private static int reloadStep, originalServerGeneration;
    private static long originalClientGeneration;
    private static String groupedConfig;
    private static net.minecraft.client.player.LocalPlayer beforeRespawn;
    private static BrowserController beforeReplacement;
    private static int mana, visibleWidgets;
    private static double regen;
    private static String identity;
    private static net.minecraft.world.item.ItemStack beforeEdit;
    private static java.nio.file.attribute.FileTime idlePreferences;


    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("ans.browserSmoke")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++lifetime > 3000) throw new IllegalStateException("Browser smoke timeout at " + stage + " on " + mc.screen);
        if (mc.getOverlay() != null || ++ticks < 14 || !work.isDone()) return;
        work.join();
        ticks = 0;
        String address = System.getProperty("ans.browserSmoke.address", "");
        switch (stage) {
            case 0 -> {
                if (!(mc.screen instanceof TitleScreen) && (mc.screen == null || !mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) return;
                mc.options.guiScale().set(3); mc.getWindow().setWindowed(1280, 720); mc.resizeDisplay();
                mc.setScreen(ConfigScreenFactory.createConfigScreen(new TitleScreen()));
                press(s -> s.equals(tr("personal").getString()));
            }
            case 1 -> {
                check(mc.screen instanceof PersonalDisplayScreen, "title-screen personal access");
                press(s -> s.equals(tr("reset").getString()));
                press(s -> s.equals(tr("apply").getString()));
                check(AnsClientConfig.mode() == DisplayMode.GROUPED, "grouped personal default");
                if (address.isBlank()) {
                    check(Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")), "isolated fixture save required");
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
                } else {
                    var data = new net.minecraft.client.multiplayer.ServerData("Browser QA", address, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address), data, false, null);
                }
            }
            case 2 -> {
                if (mc.player == null || mc.level == null || BrowserPlatform.pending()) return;
                if (address.isBlank()) work = server(() -> GlyphBrowserFixture.seed(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));
                else check(!mc.player.hasPermissions(2), "nonoperator authority");
            }
            case 3 -> {
                if (!mc.player.getMainHandItem().is(ItemsRegistry.ARCHMAGE_SPELLBOOK.get()) || knowledge().size() < 300) return;
                absent = !GlyphBrowserFixture.bolt(1).isEnabled();
                if (!replacementChecked) {
                    var replacement = new ReplacementSpellBook();
                    mc.setScreen(replacement);
                    check(((ArsGlyphEditorHost) (Object) replacement).browserController() == null
                        && (replacement.unlockedSpells.contains(GlyphBrowserFixture.bolt(3)) != absent), "replacement editor fallback");
                    replacementChecked = true;
                    return;
                }
                known = knowledge();
                GuiSpellBook.open(InteractionHand.MAIN_HAND);
                book = (GuiSpellBook) mc.screen;
                controller = ((ArsGlyphEditorHost) book).browserController();
            }
            case 4 -> {
                if (!absent && address.isBlank() && !exerciseConfigReload()) return;
                check(book.unlockedSpells.stream().noneMatch(ImportedIronEffect.class::isInstance), "grouped native palette");
                book.spellNameBox.setValue(NAME);
                if (!"Firebolt".equals(book.searchBar.getValue())) {
                    book.searchBar.setValue("Firebolt");
                    return; // let the search result and its bookmark label render before pressing it
                }
                mana = com.hollingsworth.arsnouveau.api.util.ManaUtil.getMaxMana(mc.player);
                regen = com.hollingsworth.arsnouveau.api.util.ManaUtil.getManaRegen(mc.player);
                identity = com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog.get().hash();
                beforeEdit = mc.player.getMainHandItem().copy();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 12, 12);
                check(book.spell.equals(List.of(MethodProjectile.INSTANCE)), "native initial draft");
                press(s -> s.equals(controller.bookmark().getString()));
            }
            case 5 -> {
                check(mc.screen instanceof GlyphLibraryScreen, "bookmark opens real library");
                var search = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                if (!search.getValue().isEmpty()) { search.setValue(""); return; }
                visibleWidgets = (int) mc.screen.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible).count();
                idlePreferences = preferenceTime();
                capture("organized");
                idleBuilds = controller.builds();
            }
            case 6 -> {
                check(idleBuilds == controller.builds(), "idle redraw rebuilt knowledge");
                check(java.util.Objects.equals(idlePreferences, preferenceTime()), "idle redraw wrote favorites");
                if (absent) {
                    press(s -> s.equals(tr("dormant").getString()));
                } else {
                    var query = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                    query.setValue("Firebolt III");
                    var row = button(s -> s.startsWith("Firebolt."));
                    var panel = mc.screen;
                    panel.clearFocus();
                    panel.keyPressed(GLFW_KEY_TAB, 0, 0);
                    var firstFocus = panel.getFocused();
                    panel.keyPressed(GLFW_KEY_TAB, 0, 0);
                    check(firstFocus != null && firstFocus != panel.getFocused(), "Tab navigation");
                    var reverse = panel.nextFocusPath(new net.minecraft.client.gui.navigation.FocusNavigationEvent.TabNavigation(false));
                    check(reverse != null, "reverse Tab navigation");
                    panel.clearFocus();
                    reverse.applyFocus(true);
                    check(panel.getFocused() == firstFocus, "reverse Tab target");
                    check(narration(row).contains("Firebolt") && narration(row).contains(BrowserPlatform.sourceName("irons_spellbooks")),
                        "row narration omitted spell");
                    panel.setFocused(row);
                    panel.keyPressed(GLFW_KEY_ENTER, 0, 0);
                }
            }
            case 7 -> {
                if (absent) capture("dormant");
                else {
                    capture("level-three");
                    var add = button(s -> s.equals(tr("add", "Firebolt III").getString()));
                    check(narration(add).contains(tr("add", "Firebolt III").getString()), "selected Add narration");
                    if (mc.getNarrator().isActive()) {
                        mc.options.narrator().set(net.minecraft.client.NarratorStatus.SYSTEM);
                        mc.getNarrator().sayNow(narration(add));
                    }
                    mc.screen.setFocused(add);
                    mc.screen.keyPressed(GLFW_KEY_SPACE, 0, 0);
                    check(book.spell.equals(List.of(MethodProjectile.INSTANCE, GlyphBrowserFixture.bolt(3))), "exact backing list insertion");
                    check(SpellCasterRegistry.from(mc.player.getMainHandItem()).getSpell().unsafeList().size() == 1, "Add saved prematurely");
                    mc.screen.keyPressed(GLFW_KEY_ESCAPE, 0, 0);
                }
                press(s -> s.equals(tr("settings").getString()));
                // Move grouped → flat and resize the complete stack of actual GUI layers.
                press(s -> s.startsWith("Imported Iron's glyphs"));
                mc.options.guiScale().set(4); mc.getWindow().setWindowed(1920, 1080); mc.resizeDisplay();
                press(s -> s.equals(tr("apply").getString()));
            }
            case 8 -> {
                if (!flatRendered) {
                    check(mc.screen instanceof GlyphLibraryScreen, "settings returned to original panel");
                    mc.screen.onClose();
                    flatRendered = true;
                    return; // Capture after the native glyph palette has drawn a frame.
                }
                check(mc.screen == book && NAME.equals(book.spellNameBox.getValue()), "lost named draft on resize");
                check("Firebolt".equals(book.searchBar.getValue()), "lost native search");
                check(book.displayedGlyphs.contains(GlyphBrowserFixture.bolt(3)) != absent, "flat candidate visibility");
                check(net.minecraft.world.item.ItemStack.matches(beforeEdit, mc.player.getMainHandItem()), "pre-save item changed");
                check(mana == com.hollingsworth.arsnouveau.api.util.ManaUtil.getMaxMana(mc.player)
                    && regen == com.hollingsworth.arsnouveau.api.util.ManaUtil.getManaRegen(mc.player), "local display changed mana");
                check(identity.equals(com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog.get().hash()), "local display changed identity digest");
                capture("individual-levels");
                if (!absent && address.isBlank()) {
                    var exact = controller.model().variant(GlyphBrowserFixture.bolt(3).getRegistryName().toString());
                    overrideTier = exact.tier() == 3 ? 1 : 3;
                    work = server(() -> {
                        previousMappings = GlyphBehavior.mappings();
                        String data = """
                            {"schema_version":2,"spell":"irons_spellbooks:firebolt","levels":{"3":{"ars_tier":%d}}}
                            """.formatted(overrideTier);
                        var source = new GlyphMappings.Source(ResourceLocation.parse("ans_probe:live_tier"), "browser fixture", 0,
                            com.google.gson.JsonParser.parseString(data));
                        var mappings = GlyphMappings.parse(List.of(source), ImportedGlyphCatalog.get());
                        check(mappings.valid(), "invalid test mapping " + mappings.errors());
                        GlyphBehavior.acceptMappings(mappings);
                        GlyphBehavior.onConfigChanged();
                    });
                }
            }
            case 9 -> {
                if (!absent && address.isBlank()) {
                    if (!tierVerified) {
                        String id = GlyphBrowserFixture.bolt(3).getRegistryName().toString();
                        if (controller.model().variant(id).tier() != overrideTier) return;
                        boolean fitsNovice = controller.model().rows(DisplayMode.FLAT, false, BrowserModel.Filters.DEFAULT, Set.of(), 1)
                            .stream().flatMap(row -> row.choices().stream()).anyMatch(choice -> choice.partId().equals(id));
                        check(fitsNovice == (overrideTier == 1) && book.spell.get(1) == GlyphBrowserFixture.bolt(3),
                            "published tier override did not update filter and retain draft");
                        org.slf4j.LoggerFactory.getLogger(GlyphBrowserSmoke.class).info("ANS_BROWSER_TIER_UPDATED tier={} fits_novice={}", overrideTier, fitsNovice);
                        tierVerified = true;
                        work = server(() -> {
                            GlyphBehavior.acceptMappings(previousMappings);
                            AnsConfig.ENABLE_IRON_GLYPHS.set(false);
                            GlyphBehavior.onConfigChanged();
                        });
                        return;
                    }
                    if (GlyphBrowserFixture.bolt(3).isEnabled()) return;
                    check(controller.add(GlyphBrowserFixture.bolt(3).getRegistryName().toString()) != null && book.spell.size() == 2,
                        "stale Add accepted after live disable");
                }
                press(s -> s.equals(controller.bookmark().getString()));
                press(s -> s.equals(tr("settings").getString()));
                press(s -> s.startsWith("Imported Iron's glyphs"));
                press(s -> s.equals(tr("apply").getString()));
            }
            case 10 -> {
                check(mc.screen == book && controller.bookmark() == null, "hidden controls");
                check(book.getFocused() instanceof net.minecraft.client.gui.components.AbstractWidget focused
                    && focused.visible && focused.active, "hidden panel left no usable native focus");
                check(book.unlockedSpells.stream().noneMatch(ImportedIronEffect.class::isInstance), "hidden palette");
                check(known.equals(knowledge()) && NAME.equals(book.spellNameBox.getValue()), "display edit changed knowledge/draft");
                if (!absent) {
                    check(book.spell.equals(List.of(MethodProjectile.INSTANCE, GlyphBrowserFixture.bolt(3))), "hidden draft changed");
                    if (address.isBlank()) {
                        var warning = new java.util.ArrayList<Component>();
                        book.craftingCells.get(1).getTooltip(warning);
                        String reason = BrowserPlatform.variant(GlyphBrowserFixture.bolt(3)).reason();
                        check(reason != null && warning.stream().anyMatch(line -> line.getString().contains(reason)), "dormant chip warning");
                    }
                }
                capture("hidden");
                if (!absent && address.isBlank()) work = server(() -> { AnsConfig.ENABLE_IRON_GLYPHS.set(true); GlyphBehavior.onConfigChanged(); });
            }
            case 11 -> {
                if (!absent && !GlyphBrowserFixture.bolt(3).isEnabled()) return;
                book.children().stream().filter(CreateSpellButton.class::isInstance).map(CreateSpellButton.class::cast).findFirst().orElseThrow().onPress();
            }
            case 12 -> {
                var caster = SpellCasterRegistry.from(mc.player.getMainHandItem());
                if (!NAME.equals(caster.getSpellName())) return;
                if (!absent) check(caster.getSpell().unsafeList().equals(List.of(MethodProjectile.INSTANCE, GlyphBrowserFixture.bolt(3))), "saved concrete IDs");
                GuiSpellBook.open(InteractionHand.MAIN_HAND);
                GuiSpellBook reopened = (GuiSpellBook) mc.screen;
                check(reopened.spell.equals(caster.getSpell().unsafeList()), "native reopen round-trip");
                reopened.onCopyOrExport(null);
                var exported = com.hollingsworth.arsnouveau.api.spell.Spell.fromBinaryBase64(mc.keyboardHandler.getClipboard());
                check(exported.unsafeList().equals(reopened.spell), "native clipboard export changed glyph IDs");
                if (!absent && net.neoforged.fml.ModList.get().isLoaded("jei")) {
                    com.otectus.arsnspells.glyph.recipe.GlyphPressRecipes.openViewer();
                    check(mc.screen != null && mc.screen.getClass().getName().startsWith("mezz.jei."),
                        "live Glyph Press viewer did not open after browser and behavior updates");
                }
                if (!absent && address.isBlank()) {
                    mc.setScreen(null);
                    work = server(() -> GlyphBrowserFixture.equipCastFixture(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));
                }
            }
            case 13, 14, 15 -> {
                if (!absent && address.isBlank()) {
                    var display = DisplayMode.values()[stage - 13];
                    AnsClientConfig.setMode(display);
                    check(AnsClientConfig.mode() == display, "local mode before cast");
                    work = server(() -> {
                        var result = GlyphBrowserFixture.castSavedBook(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));
                        check(result.paid() > 0 && result.healthRestored() == 2.0f && result.nativeCooldown(), "saved Heal did not pay, heal and cool down: " + result);
                        check(referenceCast == null || referenceCast.equals(result), "display mode changed cast result: " + result);
                        referenceCast = result;
                        org.slf4j.LoggerFactory.getLogger(GlyphBrowserSmoke.class).info("ANS_BROWSER_SAVED_CAST mode={} {}", display.id(), result);
                    });
                }
            }
            case 16 -> {
                if (!System.getProperty("ans.browserSmoke.upgradePeer", "").isBlank()) {
                    AnsClientConfig.setMode(DisplayMode.GROUPED);
                    beforeRespawn = mc.player;
                    beforeReplacement = openCurrentBook(mc);
                    verifyRestoredFavorite(beforeReplacement);
                    com.otectus.arsnspells.ArsNSpells.LOGGER.info("ANS_BROWSER_UPGRADE_READY_FOR_RESPAWN known={}", known.size());
                    break;
                }
                finish(mc, address);
            }
            case 17 -> {
                if (mc.player == null || mc.player == beforeRespawn || BrowserPlatform.pending()
                        || !mc.player.getMainHandItem().is(ItemsRegistry.ARCHMAGE_SPELLBOOK.get()) || !known.equals(knowledge())) return;
                String part = GlyphBrowserFixture.bolt(3).getRegistryName().toString();
                check(beforeReplacement.add(part) != null, "pre-respawn editor accepted an Add for the replacement player");
                beforeReplacement = openCurrentBook(mc);
                verifyRestoredFavorite(beforeReplacement);
                com.otectus.arsnspells.ArsNSpells.LOGGER.info("ANS_BROWSER_RESPAWN_PASS known={}", known.size());
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
            }
            case 18 -> {
                check(mc.player == null && BrowserPlatform.pending(), "client retained accepted facts after disconnect");
                String peer = System.getProperty("ans.browserSmoke.upgradePeer");
                var data = new net.minecraft.client.multiplayer.ServerData("Second browser world", peer, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(peer), data, false, null);
            }
            case 19 -> {
                if (mc.player == null || BrowserPlatform.pending() || !mc.player.getMainHandItem().is(ItemsRegistry.ARCHMAGE_SPELLBOOK.get())) return;
                check(!GlyphBehavior.current().settings().glyphsEnabled(), "previous server's availability leaked into a fresh world");
                check(knowledge().stream().noneMatch(id -> id.startsWith("ars_n_spells:iron/")), "previous player's knowledge leaked into a fresh world");
                check(beforeReplacement.add(GlyphBrowserFixture.bolt(3).getRegistryName().toString()) != null, "old server editor accepted Add");
                var fresh = openCurrentBook(mc);
                check(fresh.preferences().ids().contains("irons_spellbooks:firebolt") && favoriteRows(fresh).isEmpty(), "fresh world favorite eligibility");
                com.otectus.arsnspells.ArsNSpells.LOGGER.info("ANS_BROWSER_SECOND_SERVER_PASS known={} imported=0 eligible_favorites=0", knowledge().size());
                finish(mc, address);
            }
            default -> { return; }
        }
        stage++;
    }

    private static BrowserController openCurrentBook(Minecraft mc) {
        mc.setScreen(null);
        GuiSpellBook.open(InteractionHand.MAIN_HAND);
        var editor = ((ArsGlyphEditorHost) mc.screen).browserController();
        editor.tick();
        return editor;
    }

    private static List<BrowserModel.Row> favoriteRows(BrowserController editor) {
        return editor.rows(new BrowserModel.Filters(BrowserModel.View.FAVORITES, "", "", "", false));
    }

    private static void verifyRestoredFavorite(BrowserController editor) {
        check(favoriteRows(editor).stream().filter(row -> row.spell().spellId().equals("irons_spellbooks:firebolt"))
            .flatMap(row -> row.choices().stream()).anyMatch(level -> level.level() == 3 && known.contains(level.partId())),
            "restored Firebolt III not present under the saved favorite");
    }

    private static void finish(Minecraft mc, String address) {
        org.slf4j.LoggerFactory.getLogger(GlyphBrowserSmoke.class).info("ANS_BROWSER_CLIENT_PASS absent={} known={} builds={} server={}", absent, known.size(), controller.builds(), address.isBlank() ? "integrated" : "dedicated");
        org.slf4j.LoggerFactory.getLogger(GlyphBrowserSmoke.class).info(
            "ANS_BROWSER_METRICS registered={} known={} widgets={} idle_rebuilds=0 idle_favorites_writes=0 mana={} regen={} identity={}",
            com.otectus.arsnspells.glyph.ImportedGlyphRegistration.effects().size(), known.size(), visibleWidgets, mana, regen, identity);
        org.slf4j.LoggerFactory.getLogger(GlyphBrowserSmoke.class).info(
            "ANS_BROWSER_INPUT activated={} narrator_available={}", absent ? "dormant" : "tab,reverse-tab,enter,space,escape", mc.getNarrator().isActive());
        mc.stop();
    }

    private static boolean exerciseConfigReload() throws java.io.IOException {
        if (reloadStep == 3) return true;
        var path = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ars_n_spells-client.toml");
        if (reloadStep == 0) {
            originalClientGeneration = BrowserPlatform.generation();
            originalServerGeneration = GlyphBehavior.serverSnapshot().generation();
            groupedConfig = Files.readString(path);
            String flatConfig = groupedConfig.replace("\"grouped\"", "\"flat\"");
            check(!groupedConfig.equals(flatConfig), "grouped config file before reload");
            book.spellNameBox.setValue(NAME);
            Files.writeString(path, flatConfig);
            reloadStep++;
            return false;
        }
        boolean waitingForFlat = reloadStep == 1;
        var expected = waitingForFlat ? DisplayMode.FLAT : DisplayMode.GROUPED;
        if (AnsClientConfig.mode() != expected || book.unlockedSpells.contains(GlyphBrowserFixture.bolt(3)) != waitingForFlat) return false;
        check(Minecraft.getInstance().screen == book && NAME.equals(book.spellNameBox.getValue())
            && book.spell.equals(List.of(MethodProjectile.INSTANCE)), "open draft during external config reload");
        check(BrowserPlatform.generation() == originalClientGeneration
            && GlyphBehavior.serverSnapshot().generation() == originalServerGeneration, "client reload republished server behavior");
        reloadStep++;
        if (waitingForFlat) {
            Files.writeString(path, groupedConfig);
            return false;
        }
        com.otectus.arsnspells.ArsNSpells.LOGGER.info("ANS_BROWSER_EXTERNAL_CONFIG_RELOAD grouped-flat-grouped generation={}", originalServerGeneration);
        return true;
    }

    private static final class ReplacementSpellBook extends GuiSpellBook {
        ReplacementSpellBook() { super(InteractionHand.MAIN_HAND); }
    }
    private static java.nio.file.attribute.FileTime preferenceTime() throws java.io.IOException {
        var file = BrowserPlatform.favoritesPath();
        return Files.exists(file) ? Files.getLastModifiedTime(file) : null;
    }
    private static CompletableFuture<Void> server(Runnable action) {
        return CompletableFuture.runAsync(action, Minecraft.getInstance().getSingleplayerServer());
    }
    private static Set<String> knowledge() {
        return CapabilityRegistry.getPlayerDataCap(Minecraft.getInstance().player).getKnownGlyphs().stream()
            .map(AbstractSpellPart::getRegistryName).map(Object::toString).collect(Collectors.toSet());
    }
    private static String narration(Button widget) {
        var collector = new net.minecraft.client.gui.narration.ScreenNarrationCollector();
        collector.update(widget::updateNarration);
        return collector.collectNarrationText(true);
    }
    private static void press(Predicate<String> label) { button(label).onPress(); }
    private static Button button(Predicate<String> label) {
        var screen = Minecraft.getInstance().screen;
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(w -> w.visible && w.active && label.test(w.getMessage().getString())).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing widget at " + stage + " on " + screen + ": " + screen.children().stream()
                .filter(Button.class::isInstance).map(Button.class::cast).map(w -> w.getMessage().getString()).toList()));
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("Browser stage " + stage + ": " + message);
    }
    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance();
        var out = mc.gameDirectory.toPath().resolve("glyph-browser-review");
        Files.createDirectories(out);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(out.resolve(name + ".png")); }
    }
}
