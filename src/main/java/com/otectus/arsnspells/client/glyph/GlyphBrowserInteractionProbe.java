package com.otectus.arsnspells.client.glyph;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.otectus.arsnspells.client.glyph.BrowserController.tr;

/**
 * Opt-in interaction probe for the real Ars 5.13 editor and acquisition screens.
 * It is inert outside the disposable QA profile selected with {@code -Dans.browserInteractionProbe=true}.
 */
@EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class GlyphBrowserInteractionProbe {
    private static final String WORLD = "ANS Browser QA";
    private static final ResourceLocation RECIPE_TWO = ResourceLocation.parse("ans_probe:firebolt_custom_a");
    private static final ResourceLocation RECIPE_FOUR = ResourceLocation.parse("ans_probe:firebolt_custom_b");
    private static int stage, ticks, lifetime;
    private static GuiSpellBook book;
    private static BrowserController controller;
    private static CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
    private static GlyphBehaviorSnapshot accepted;
    private static String favorite;

    private GlyphBrowserInteractionProbe() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("ans.browserInteractionProbe")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++lifetime > 4200) throw new IllegalStateException("Browser interaction timeout at " + stage + " on " + mc.screen);
        if (mc.getOverlay() != null || ++ticks < 12 || !work.isDone()) return;
        work.join();
        ticks = 0;
        switch (stage) {
            case 0 -> {
                if (!(mc.screen instanceof TitleScreen)
                        && (mc.screen == null || !mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) return;
                mc.options.guiScale().set(3);
                mc.getWindow().setWindowed(1280, 720);
                mc.resizeDisplay();
                check(Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")),
                    "isolated fixture save required");
                mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
            }
            case 1 -> {
                if (mc.player == null || mc.level == null || BrowserPlatform.pending()) return;
                check(recipeIds().containsAll(Set.of(RECIPE_TWO, RECIPE_FOUR)), "staged datapack glyph recipes did not load");
                check(new GlyphFavorites(BrowserPlatform.favoritesPath()).ids().isEmpty(),
                    "Use a fresh QA profile with empty favorites");
                AnsClientConfig.setMode(DisplayMode.GROUPED);
                work = server(() -> seedBook(serverPlayer(), ItemsRegistry.ARCHMAGE_SPELLBOOK.get(), false));
            }
            case 2 -> {
                if (!mc.player.getMainHandItem().is(ItemsRegistry.ARCHMAGE_SPELLBOOK.get()) || knowledge().size() < 100) return;
                GuiSpellBook.open(InteractionHand.MAIN_HAND);
                book = (GuiSpellBook) mc.screen;
                controller = ((ArsGlyphEditorHost) book).browserController();
                forceRefresh();
                check(book.unlockedSpells.stream().noneMatch(ImportedIronEffect.class::isInstance), "grouped palette leaked imports");
            }
            case 3 -> {
                accepted = GlyphBehavior.current();
                check(accepted.generation() > 0, "authoritative generation missing");
                GlyphBehavior.clearClient();
                forceRefresh();
                check(controller.pending(), "controller did not enter pending state");
                check(book.unlockedSpells.stream().noneMatch(ImportedIronEffect.class::isInstance), "pending palette exposed imports");
                GlyphBehavior.receiveClient(accepted);
                forceRefresh();
                check(!controller.pending(), "controller did not recover accepted state");
            }
            case 4 -> {
                ImportedIronEffect added = effect("firebolt", 7);
                check(controller.model().variant(id(added)) == null, "knowledge fixture already contained refresh target");
                work = server(() -> {
                    var cap = CapabilityRegistry.getPlayerDataCap(serverPlayer());
                    var next = new ArrayList<>(cap.getKnownGlyphs());
                    next.add(added);
                    cap.setKnownGlyphs(next);
                    CapabilityRegistry.EventHandler.syncPlayerCap(serverPlayer());
                });
            }
            case 5 -> {
                forceRefresh();
                if (controller.model().variant(id(effect("firebolt", 7))) == null) return;
                check(knowledge().contains(id(effect("firebolt", 7))), "synced knowledge did not reach client");
                AnsClientConfig.setMode(DisplayMode.FLAT);
                forceRefresh();
                book.searchBar.setValue("");
            }
            case 6 -> {
                check(book.getNumPages() > 1, "flat native palette did not paginate");
                book.onPageIncrease(book.nextButton);
                check(book.page == 1, "native next page did not advance");
                capture("native-page-two");
                book.searchBar.setValue("Firebolt");
            }
            case 7 -> {
                check(book.page == 0, "native search did not reset page");
                check(!book.displayedGlyphs.isEmpty() && book.displayedGlyphs.stream().allMatch(ImportedIronEffect.class::isInstance),
                    "native search returned a non-imported glyph");
                check(book.displayedGlyphs.stream().allMatch(part -> part.getLocaleName().contains("Firebolt")),
                    "native search returned another imported spell");
                capture("native-search");
                AnsClientConfig.setMode(DisplayMode.GROUPED);
                forceRefresh();
                press(s -> s.equals(controller.bookmark().getString()));
            }
            case 8 -> {
                check(mc.screen instanceof GlyphLibraryScreen, "grouped bookmark did not open library");
                check(controller.preferences().ids().isEmpty(), "interaction fixture favorites were not clean");
                press("☆"::equals);
                favorite = controller.preferences().ids().stream().findFirst().orElseThrow();
                press(s -> s.equals(tr("favorites").getString()));
            }
            case 9 -> {
                check(mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .anyMatch(button -> button.visible && button.getMessage().getString().equals("★")), "favorite row missing");
                check(controller.preferences().ids().equals(Set.of(favorite)), "favorite persisted under the wrong original spell ID");
                capture("favorite");
                mc.screen.onClose();
                check(mc.screen == book, "favorite close did not return to editor");
                ImportedIronEffect cleanse = effect("cleanse", 1);
                Component wrongForm = controller.error(id(cleanse));
                check(wrongForm != null && wrongForm.getString().equals(
                    Component.translatable("ars_n_spells.glyph.validation.self_only", cleanse.spellName()).getString()),
                    "wrong-form rejection was not contextual");
                work = server(() -> seedBook(serverPlayer(), ItemsRegistry.NOVICE_SPELLBOOK.get(), true));
            }
            case 10 -> {
                if (!mc.player.getMainHandItem().is(ItemsRegistry.NOVICE_SPELLBOOK.get())) return;
                GuiSpellBook.open(InteractionHand.MAIN_HAND);
                book = (GuiSpellBook) mc.screen;
                controller = ((ArsGlyphEditorHost) book).browserController();
                forceRefresh();
                Component tier = controller.error(id(effect("cleanse", 1)));
                check(tier != null && tier.getString().equals(tr("requires_tier", 3).getString()), "tier rejection missing");
                capture("tier-rejection");
                work = server(() -> seedBook(serverPlayer(), ItemsRegistry.ARCHMAGE_SPELLBOOK.get(), false));
            }
            case 11 -> {
                if (!mc.player.getMainHandItem().is(ItemsRegistry.ARCHMAGE_SPELLBOOK.get())) return;
                GuiSpellBook.open(InteractionHand.MAIN_HAND);
                book = (GuiSpellBook) mc.screen;
                controller = ((ArsGlyphEditorHost) book).browserController();
                forceRefresh();
                prepareSparseWindow();
                ImportedIronEffect candidate = effect("firebolt", 5);
                Component limit = controller.error(id(candidate));
                check(limit != null && limit.getString().contains("4"), "following-glyph limit rejection missing");
                capture("sparse-limit-rejection");
                book.spell.set(11, null);
                rebuildBook();
                book.updateWindowOffset(5);
                forceRefresh();
                check(controller.add(id(candidate)) == null, "valid bonus-window insertion was rejected");
                check(book.spell.get(10) == candidate, "backing list insertion missed sparse slot 10");
                check(book.craftingCells.stream().anyMatch(cell -> cell.slotNum == 10 && cell.getAbstractSpellPart() == candidate),
                    "native CraftingButton was not updated");
            }
            case 12 -> {
                while (book.spell.size() < 15) book.spell.add(EffectHarm.INSTANCE);
                for (int i = 11; i < 15; i++) book.spell.set(i, EffectHarm.INSTANCE);
                rebuildBook();
                book.updateWindowOffset(5);
                forceRefresh();
                Component full = controller.error(id(effect("firebolt", 6)));
                check(full != null && full.getString().equals(tr("no_visible_slot").getString()), "full bonus window was insertable");
                capture("bonus-window-full");
                mc.setScreen(new GlyphUnlockMenu(BlockPos.ZERO));
            }
            case 13 -> {
                GlyphUnlockMenu acquire = (GlyphUnlockMenu) mc.screen;
                check(acquire.allParts.stream().noneMatch(holder -> holder.value().getSpellPart() instanceof ImportedIronEffect),
                    "grouped acquisition leaked imported recipes into the wall");
                press(s -> s.equals(tr("acquisition.bookmark").getString()));
            }
            case 14 -> {
                check(mc.screen instanceof GlyphAcquisitionScreen, "grouped acquisition section did not open");
                press(s -> s.startsWith("Firebolt · "));
            }
            case 15 -> {
                press(s -> s.equals(Component.translatable("ars_n_spells.browser.acquisition.choose", effect("firebolt", 4).getLocaleName()).getString()));
            }
            case 16 -> {
                check(mc.screen instanceof GlyphUnlockMenu, "recipe choice did not return to native acquisition screen");
                GlyphUnlockMenu acquire = (GlyphUnlockMenu) mc.screen;
                check(acquire.selectedRecipe != null && acquire.selectedRecipe.id().equals(RECIPE_FOUR),
                    "grouped acquisition did not retain exact recipe ID");
                capture("acquisition-exact-recipe");
                AnsClientConfig.setMode(DisplayMode.FLAT);
                mc.setScreen(new GlyphUnlockMenu(BlockPos.ZERO));
            }
            case 17 -> {
                GlyphUnlockMenu acquire = (GlyphUnlockMenu) mc.screen;
                check(recipeIds(acquire).containsAll(Set.of(RECIPE_TWO, RECIPE_FOUR)), "flat acquisition omitted real imported recipes");
                AnsClientConfig.setMode(DisplayMode.HIDDEN);
                mc.setScreen(new GlyphUnlockMenu(BlockPos.ZERO));
            }
            case 18 -> {
                GlyphUnlockMenu acquire = (GlyphUnlockMenu) mc.screen;
                check(!recipeIds(acquire).contains(RECIPE_TWO) && !recipeIds(acquire).contains(RECIPE_FOUR),
                    "hidden acquisition exposed imported recipes");
                AnsClientConfig.setMode(DisplayMode.GROUPED);
                work = server(() -> seedCreativeBook(serverPlayer()));
            }
            case 19 -> {
                if (!mc.player.getMainHandItem().is(ItemsRegistry.CREATIVE_SPELLBOOK.get())) return;
                GuiSpellBook.open(InteractionHand.MAIN_HAND);
                book = (GuiSpellBook) mc.screen;
                controller = ((ArsGlyphEditorHost) book).browserController();
                forceRefresh();
                ArsGlyphEditorHost host = (ArsGlyphEditorHost) book;
                long imported = host.browserKnowledge().stream().filter(ImportedIronEffect.class::isInstance).count();
                check(host.browserCreative() && imported > 500, "creative editor did not use registry eligibility");
                check(knowledge().stream().noneMatch(id -> id.startsWith("ars_n_spells:iron/")),
                    "creative fixture unexpectedly learned imported glyphs");
                press(s -> s.equals(controller.bookmark().getString()));
            }
            case 20 -> {
                check(mc.screen instanceof GlyphLibraryScreen, "creative library did not open");
                capture("creative-library");
                mc.screen.onClose();
                mc.setScreen(new UnsupportedSpellbookScreen());
            }
            case 21 -> {
                check(mc.screen instanceof UnsupportedSpellbookScreen, "unsupported replacement editor was transformed or closed");
                capture("unsupported-fallback");
                org.slf4j.LoggerFactory.getLogger(GlyphBrowserInteractionProbe.class).info(
                    "ANS_BROWSER_INTERACTION_PASS favorite={} recipes={},{} stages={}", favorite, RECIPE_TWO, RECIPE_FOUR, stage + 1);
                check(controller.preferences().toggle(favorite) == null, "could not restore the fixture favorite");
                mc.stop();
            }
            default -> { return; }
        }
        stage++;
    }

    private static void prepareSparseWindow() {
        book.bonusSlots = 5;
        book.spell.clear();
        book.spell.add(MethodProjectile.INSTANCE);
        book.spell.add(effect("firebolt", 1));
        book.spell.add(effect("firebolt", 2));
        book.spell.add(effect("firebolt", 3));
        for (int i = 4; i < 10; i++) book.spell.add(EffectHarm.INSTANCE);
        book.spell.add(null);
        book.spell.add(effect("firebolt", 4));
        rebuildBook();
        book.updateWindowOffset(5);
        check(book.spellWindowOffset == 5 && book.craftingCells.size() == 15, "bonus window was not constructed");
    }

    private static void seedBook(ServerPlayer player, Item item, boolean selfForm) {
        player.setGameMode(GameType.SURVIVAL);
        var cap = CapabilityRegistry.getPlayerDataCap(player);
        var known = new ArrayList<AbstractSpellPart>();
        known.add(MethodProjectile.INSTANCE);
        known.add(MethodSelf.INSTANCE);
        known.add(EffectHarm.INSTANCE);
        ImportedIronEffect omitted = effect("firebolt", 7);
        for (ImportedIronEffect effect : ImportedGlyphRegistration.effects().values()) {
            if (effect != omitted && effect.behavior().available()) known.add(effect);
        }
        cap.setKnownGlyphs(known);
        CapabilityRegistry.EventHandler.syncPlayerCap(player);
        ItemStack stack = item.getDefaultInstance();
        new SpellCaster().setSpell(new Spell(selfForm ? MethodSelf.INSTANCE : MethodProjectile.INSTANCE)).saveToStack(stack);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.inventoryMenu.broadcastChanges();
    }

    private static void seedCreativeBook(ServerPlayer player) {
        var cap = CapabilityRegistry.getPlayerDataCap(player);
        cap.setKnownGlyphs(new ArrayList<>(List.of(MethodProjectile.INSTANCE, MethodSelf.INSTANCE, EffectHarm.INSTANCE)));
        CapabilityRegistry.EventHandler.syncPlayerCap(player);
        ItemStack stack = ItemsRegistry.CREATIVE_SPELLBOOK.get().getDefaultInstance();
        new SpellCaster().setSpell(new Spell(MethodProjectile.INSTANCE)).saveToStack(stack);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.inventoryMenu.broadcastChanges();
    }

    private static ImportedIronEffect effect(String path, int level) {
        ImportedIronEffect effect = ImportedGlyphRegistration.effect(new ImportedGlyphKey(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", path), level));
        if (effect == null) throw new IllegalStateException("Missing imported fixture " + path + "@" + level);
        return effect;
    }

    private static String id(AbstractSpellPart part) { return part.getRegistryName().toString(); }

    private static Set<String> knowledge() {
        return CapabilityRegistry.getPlayerDataCap(Minecraft.getInstance().player).getKnownGlyphs().stream()
            .map(GlyphBrowserInteractionProbe::id).collect(Collectors.toSet());
    }

    private static Set<ResourceLocation> recipeIds() {
        return Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(RecipeRegistry.GLYPH_TYPE.get()).stream()
            .map(holder -> holder.id()).collect(Collectors.toSet());
    }

    private static Set<ResourceLocation> recipeIds(GlyphUnlockMenu screen) {
        return screen.allParts.stream().map(holder -> holder.id()).collect(Collectors.toSet());
    }

    private static ServerPlayer serverPlayer() {
        Minecraft mc = Minecraft.getInstance();
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        if (player == null) throw new IllegalStateException("Integrated server player missing");
        return player;
    }

    private static CompletableFuture<Void> server(Runnable action) {
        return CompletableFuture.runAsync(action, Minecraft.getInstance().getSingleplayerServer());
    }

    private static void forceRefresh() {
        for (int i = 0; i < 12; i++) controller.tick();
    }

    private static void rebuildBook() {
        Minecraft mc = Minecraft.getInstance();
        book.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private static void press(Predicate<String> label) {
        Screen screen = Minecraft.getInstance().screen;
        Button button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(widget -> widget.visible && widget.active && label.test(widget.getMessage().getString())).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing widget at " + stage + " on " + screen + ": "
                + screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .map(widget -> widget.getMessage().getString()).toList()));
        button.onPress();
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("Browser interaction stage " + stage + ": " + message);
    }

    private static void capture(String name) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        var out = mc.gameDirectory.toPath().resolve("glyph-browser-interaction-review");
        Files.createDirectories(out);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(out.resolve(name + ".png"));
        }
    }

    /** Identifiable replacement editor used to prove that the compatibility fallback is non-destructive. */
    private static final class UnsupportedSpellbookScreen extends Screen {
        private UnsupportedSpellbookScreen() { super(Component.literal("Unsupported spellbook fixture")); }
        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.literal("Native replacement control"), ignored -> {})
                .bounds(width / 2 - 80, height / 2 - 10, 160, 20).build());
        }
    }
}
