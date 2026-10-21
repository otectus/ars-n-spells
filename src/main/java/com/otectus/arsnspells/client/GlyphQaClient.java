package com.otectus.arsnspells.client;

import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.client.screen.GlyphPressScreen;
import com.otectus.arsnspells.glyph.ImportedGlyphItem;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client half of the disposable 3.4.0 QA ({@code -PwithGlyphQaClient -PglyphQaAddress=host:port}),
 * inert without its profile; it only ever connects to the explicit QA address. Every action goes
 * through the client's real input handling: hotbar selection and look direction as the player
 * would set them, {@code keyUse} clicks processed by Minecraft's own key handling, container
 * clicks through the multiplayer game mode, and the screen's own buttons. The server half
 * ({@code GlyphQaServer}) judges the world effects; this side checks what the client sees and
 * requests GUI scales 4 and 1, logging the actual scale and viewport into {@code glyph-qa-review/}. With
 * {@code -PglyphQaClientPhase=2} it logs in to the restarted server, casts the saved book once and
 * leaves; with {@code -PglyphQaClientPhase=4 -PglyphQaRole=A|B -PclientUser=<name>} two clients share
 * one Press.
 */
@EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class GlyphQaClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("ArsNSpells/GlyphQaClient");
    private static final net.minecraft.resources.ResourceLocation FIREBOLT =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");
    private static int stage, age, wait, poll, helpReview;
    private static long editorOpenNanos;
    private static float pigHealth;
    /** The pig a cast was aimed at; checks follow that entity, not whichever pig is nearest later. */
    private static Pig target;

    private GlyphQaClient() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ans.glyphQa.client")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++age > 12000) throw new IllegalStateException("Glyph QA client timed out at stage " + stage + " screen=" + mc.screen);
        if (age % 200 == 0 && mc.player != null) {
            var inventory = mc.player.getInventory();
            LOGGER.info("ANS_GLYPH_QA_CLIENT heartbeat stage={} wait={} poll={} screen={} selected={} slots[2,4,5,7,9]={} | {} | {} | {} | {}",
                stage, wait, poll, mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), inventory.selected,
                inventory.getItem(2), inventory.getItem(4), inventory.getItem(5), inventory.getItem(7), inventory.getItem(9));
            if (mc.screen instanceof GlyphPressScreen pressScreen) {
                GlyphPressMenu menu = pressScreen.getMenu();
                LOGGER.info("ANS_GLYPH_QA_CLIENT heartbeat press reason={} state={} tier={} gems={} need={} free={} capacity={} slots={} | {} | {} | {}",
                    menu.reason(), menu.state(), menu.value(GlyphPressMenu.D_TIER), menu.value(GlyphPressMenu.D_GEMS),
                    menu.wide(GlyphPressMenu.D_NEED), menu.wide(GlyphPressMenu.D_FREE), menu.wide(GlyphPressMenu.D_CAPACITY),
                    menu.getSlot(0).getItem(), menu.getSlot(1).getItem(), menu.getSlot(2).getItem(), menu.getSlot(3).getItem());
            }
        }
        if (mc.getOverlay() != null) return;
        if (stage >= 2 && stage != 22 && (mc.player == null || mc.level == null)) {
            throw new IllegalStateException("Glyph QA client lost the QA server at stage " + stage);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 0 -> {
                if (mc.screen == null || (!(mc.screen instanceof TitleScreen)
                        && !mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) return;
                String address = System.getProperty("ans.glyphQa.address", "");
                if (address.isBlank()) throw new IllegalStateException("Glyph QA needs -PglyphQaAddress=<host:port>");
                mc.options.guiScale().set(4);
                mc.getWindow().setWindowed(1280, 960);
                mc.resizeDisplay();
                var info = new net.minecraft.client.multiplayer.ServerData("ANS glyph QA", address,
                    net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address), info, false, null);
                stage = 1;
            }
            case 1 -> {
                if (mc.player == null || mc.level == null) return;
                if (Integer.getInteger("ans.glyphQa.clientPhase", 1) == 4) {
                    // Two clients at one Press: role A works it, role B watches the same job and takes the result.
                    String role = System.getProperty("ans.glyphQa.role", "");
                    require(role.equals("A") || role.equals("B"), "two-client QA needs -PglyphQaRole=A or B");
                    pass("duo_join", "role " + role + " logged in as " + mc.player.getGameProfile().getName());
                    stage = role.equals("A") ? 70 : 80;
                    wait = 40;
                    return;
                }
                if (Integer.getInteger("ans.glyphQa.clientPhase", 1) == 2) {
                    // Restart phase: log in as the first player after the restart, then cast the saved book once.
                    pass("restart_join", "logged in to the restarted server");
                    stage = 60;
                    wait = 60;
                    return;
                }
                // The server builds the scene 60 ticks after login; wait for its first carrier to arrive.
                // A fresh player may receive a starter notebook before the server seeds the scene.
                // Wait for the actual QA carrier; using the notebook would open Patchouli instead.
                if (!com.otectus.arsnspells.spell.ArsSpellExportUtil.isScrollCarrier(mc.player.getInventory().getItem(0))
                        || !pressPlaced(mc) || mc.player.getInventory().getItem(9).isEmpty()) return;
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
                stage = 2;
                wait = 20;
            }
            case 2 -> {
                select(mc, 0);
                look(mc, new Vec3(0.5, -58.5, 2.5));
                stage = 3;
                wait = 5;
            }
            case 3 -> {
                use(mc);
                stage = 4;
                wait = 10;
            }
            case 4 -> {
                if (!seen(mc.level.getBlockState(net.minecraft.core.BlockPos.containing(0, -59, 2)).isAir(), "the dirt is still there")) return;
                pass("touch_block", "a right-click with the exported Touch -> Break scroll broke the aimed block");
                Pig pig = target = pig(mc);
                pigHealth = pig.getHealth();
                select(mc, 1);
                look(mc, pig.getBoundingBox().getCenter());
                stage = 5;
                wait = 5;
            }
            case 5 -> {
                use(mc);
                stage = 6;
                wait = 10;
            }
            case 6 -> {
                Pig pig = target;
                if (!seen(pig.getHealth() < pigHealth, "the pig was not hurt: " + pig.getHealth())) return;
                pass("touch_entity", "a right-click with the exported Touch -> Harm scroll hurt the aimed pig");
                pigHealth = pig.getHealth();
                select(mc, 7);
                look(mc, new Vec3(-1.5, -59.6, 0.5));
                stage = 7;
                wait = 5;
            }
            case 7 -> {
                use(mc);
                stage = 8;
                wait = 10;
            }
            case 8 -> {
                if (!(mc.screen instanceof GlyphPressScreen)) return;
                screenshot(mc, "01-press-empty");
                GlyphPressMenu menu = menu(mc);
                for (int inventorySlot = 9; inventorySlot <= 11; inventorySlot++) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, 4 + inventorySlot - 9, 0, ClickType.QUICK_MOVE, mc.player);
                }
                stage = 9;
                wait = 10;
            }
            case 9 -> {
                GlyphPressMenu menu = menu(mc);
                if (menu.reason() != GlyphPressPlan.Reason.OK) return;
                require(menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).hasItem() && menu.getSlot(GlyphPressBlockEntity.SLOT_PLATE).hasItem()
                    && menu.getSlot(GlyphPressBlockEntity.SLOT_CATALYST).hasItem(), "shift-click did not route the inputs");
                pass("press_inputs", "shift-click routed scroll, plate and gems into their slots; the server reports Ready");
                screenshot(mc, "02-press-ready-gui4");
                mc.options.guiScale().set(1);
                mc.resizeDisplay();
                stage = 10;
                wait = 5;
            }
            case 10 -> {
                screenshot(mc, "02b-press-ready-gui1");
                mc.options.guiScale().set(4);
                mc.resizeDisplay();
                stage = 11;
                wait = 5;
            }
            case 11 -> {
                mc.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                require(mc.screen.getFocused() instanceof Button, "Tab did not focus a button: " + mc.screen.getFocused());
                pass("keyboard", "Tab focuses the Press's buttons: " + ((Button) mc.screen.getFocused()).getMessage().getString());
                press(mc, null, "?");
                stage = 12;
                wait = 5;
            }
            case 12 -> {
                if (helpReview == 0) {
                    screenshot(mc, "03-press-help");
                    require(mc.screen.getFocused() != null && mc.screen.getFocused().keyPressed(GLFW.GLFW_KEY_END, 0, 0),
                        "Press help did not accept End");
                    helpReview++;
                    wait = 5;
                    return;
                }
                if (helpReview == 1) {
                    screenshot(mc, "03b-press-help-end");
                    require(mc.screen.getFocused().keyPressed(GLFW.GLFW_KEY_HOME, 0, 0), "Press help did not accept Home");
                    pass("help_scroll", "the clipped help panel accepts End and Home");
                    helpReview++;
                    wait = 5;
                    return;
                }
                press(mc, null, "?");
                press(mc, "ars_n_spells.glyph_press.inscribe", null);
                stage = 13;
                wait = 5;
            }
            case 13 -> {
                GlyphPressMenu menu = menu(mc);
                if (menu.state() != GlyphPressBlockEntity.State.PROCESSING) return;
                if (menu.wide(GlyphPressMenu.D_PROGRESS) < 85) return;
                pass("press_running", "the server started the job; both gauges are synchronized");
                screenshot(mc, "04-press-working");
                var state = mc.level.getBlockState(com.otectus.arsnspells.gametest.GlyphQaServer.PRESS);
                var model = mc.getBlockRenderer().getBlockModel(state);
                var random = net.minecraft.util.RandomSource.create(0);
                var data = net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
                int solid = model.getQuads(state, null, random, data, net.minecraft.client.renderer.RenderType.solid()).size();
                int translucent = model.getQuads(state, null, random, data, net.minecraft.client.renderer.RenderType.translucent()).size();
                require(solid == 122 && translucent == 1, "working model pass counts: solid=" + solid + ", translucent=" + translucent);
                pass("model_layers", "122 unculled body quads in the solid pass; only the rune quad in the translucent pass");
                stage = 14;
            }
            case 14 -> {
                GlyphPressMenu menu = menu(mc);
                if (menu.state() == GlyphPressBlockEntity.State.PROCESSING || !menu.getSlot(GlyphPressBlockEntity.SLOT_OUTPUT).hasItem()) return;
                require(menu.getSlot(GlyphPressBlockEntity.SLOT_OUTPUT).getItem().getItem() instanceof ImportedGlyphItem, "no glyph in the output");
                screenshot(mc, "05-press-done");
                mc.gameMode.handleInventoryMouseClick(menu.containerId, GlyphPressBlockEntity.SLOT_OUTPUT, 0, ClickType.QUICK_MOVE, mc.player);
                stage = 15;
                wait = 10;
            }
            case 15 -> {
                mc.player.closeContainer();
                int slot = glyphSlot(mc);
                if (!seen(slot >= 0, "the glyph was not taken")) return;
                require(slot < 9, "the glyph did not land in the hotbar: slot " + slot);
                pass("press", "the finished glyph was taken from the output slot");
                select(mc, slot);
                look(mc, mc.player.getEyePosition().add(0, 10, 0.01));
                stage = 16;
                wait = 5;
            }
            case 16 -> {
                use(mc);
                stage = 17;
                wait = 10;
            }
            case 17 -> {
                if (!seen(glyphSlot(mc) < 0, "the glyph was not consumed by learning")) return;
                pass("learn", "using the glyph consumed it (learned on the server)");
                stage = 18;
                if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                    com.otectus.arsnspells.glyph.recipe.GlyphPressRecipes.openViewer();
                    stage = 30;
                    wait = 20;
                }
            }
            case 30 -> {
                require(mc.screen != null, "the recipe viewer did not open");
                screenshot(mc, "06-jei-glyph-press");
                pass("viewer", "JEI shows the Glyph Press category: " + mc.screen.getClass().getSimpleName());
                mc.screen.onClose();
                stage = 18;
                wait = 10;
            }
            case 18 -> {
                if (mc.player.getInventory().getItem(3).isEmpty()) return;
                Pig pig = target = pig(mc);
                pigHealth = pig.getHealth();
                select(mc, 3);
                look(mc, pig.getBoundingBox().getCenter());
                stage = 19;
                wait = 5;
            }
            case 19 -> {
                use(mc);
                stage = 20;
                wait = 20;
            }
            case 20 -> {
                Pig pig = target;
                if (!seen(pig.getHealth() < pigHealth || pig.isOnFire() || pig.isRemoved(), "the learned glyph did not strike the pig")) return;
                pass("glyph_cast", "Touch -> Glyph of Firebolt I from a spell book struck the aimed pig");
                stage = 40;
                wait = 20;
            }
            // ---- Journey two: a tier III scroll, a refused input, the real spell book editor, an augment, a nested export ----
            case 40 -> {
                var inventory = mc.player.getInventory();
                if (inventory.getItem(9).isEmpty() || inventory.getItem(4).isEmpty() || inventory.getItem(2).isEmpty()) return;
                select(mc, 7);
                look(mc, new Vec3(-1.5, -59.6, 0.5));
                stage = 41;
                wait = 5;
            }
            case 41 -> {
                use(mc);
                stage = 42;
                wait = 10;
            }
            case 42 -> {
                if (!(mc.screen instanceof GlyphPressScreen)) return;
                GlyphPressMenu menu = menu(mc);
                for (int inventorySlot = 9; inventorySlot <= 11; inventorySlot++) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, 4 + inventorySlot - 9, 0, ClickType.QUICK_MOVE, mc.player);
                }
                stage = 43;
                wait = 10;
            }
            case 43 -> {
                GlyphPressMenu menu = menu(mc);
                if (menu.reason() != GlyphPressPlan.Reason.OK) return;
                require(menu.value(GlyphPressMenu.D_TIER) == 3, "the Firebolt X scroll is not shown as tier III: " + menu.value(GlyphPressMenu.D_TIER));
                pass("press_tier3_ready", "the Press shows the Firebolt X scroll as tier III, needing " + menu.value(GlyphPressMenu.D_GEMS)
                    + " catalysts and " + menu.wide(GlyphPressMenu.D_NEED) + " Source; the server reports Ready");
                screenshot(mc, "07-press-tier3-ready");
                press(mc, "ars_n_spells.glyph_press.inscribe", null);
                stage = 44;
                wait = 10;
            }
            case 44 -> {
                GlyphPressMenu menu = menu(mc);
                if (menu.state() == GlyphPressBlockEntity.State.PROCESSING || !menu.getSlot(GlyphPressBlockEntity.SLOT_OUTPUT).hasItem()) return;
                mc.gameMode.handleInventoryMouseClick(menu.containerId, GlyphPressBlockEntity.SLOT_OUTPUT, 0, ClickType.QUICK_MOVE, mc.player);
                pass("press_tier3", "the tier III job finished and the glyph was taken");
                stage = 45;
                wait = 10;
            }
            case 45 -> {
                // An unsupported input: the Spell Loom export in hotbar slot 0 (menu slot 31) is shift-clicked in.
                GlyphPressMenu menu = menu(mc);
                mc.gameMode.handleInventoryMouseClick(menu.containerId, 31, 0, ClickType.QUICK_MOVE, mc.player);
                stage = 46;
                wait = 10;
            }
            case 46 -> {
                GlyphPressMenu menu = menu(mc);
                if (!seen(menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).hasItem() && menu.reason() == GlyphPressPlan.Reason.EXPORTED_ARS_SPELL,
                        "the exported scroll was not refused in the scroll slot: " + menu.reason())) return;
                pass("press_refusal", "an exported Ars spell scroll shows \""
                    + net.minecraft.network.chat.Component.translatable(menu.reason().translationKey()).getString() + "\" and Inscribe stays off");
                screenshot(mc, "08-press-refuses-export");
                mc.gameMode.handleInventoryMouseClick(menu.containerId, GlyphPressBlockEntity.SLOT_SCROLL, 0, ClickType.QUICK_MOVE, mc.player);
                stage = 47;
                wait = 10;
            }
            case 47 -> {
                if (!seen(!menu(mc).getSlot(GlyphPressBlockEntity.SLOT_SCROLL).hasItem(), "the exported scroll could not be taken back")) return;
                mc.player.closeContainer();
                int slot = glyphSlot(mc);
                require(slot >= 0 && slot < 9, "the tier III glyph did not land in the hotbar: slot " + slot);
                select(mc, slot);
                look(mc, mc.player.getEyePosition().add(0, 10, 0.01));
                stage = 48;
                wait = 5;
            }
            case 48 -> {
                use(mc);
                stage = 49;
                wait = 10;
            }
            case 49 -> {
                if (!seen(glyphSlot(mc) < 0, "the tier III glyph was not consumed by learning")) return;
                pass("learn_tier3", "using the Glyph of Firebolt X learned it");
                select(mc, 4);
                key(mc, GLFW.GLFW_KEY_C);
                stage = 50;
                wait = 10;
            }
            case 50 -> {
                // Ars's editor is matched by name: its screen base class is not on ANS's compile classpath.
                if (!isSpellBookEditor(mc.screen)) return;
                net.minecraft.client.gui.screens.Screen book = mc.screen;
                var effect1 = ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 1));
                var effect10 = ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 10));
                var tierThree = glyphButton(book, effect10);
                require(tierThree != null && !tierThree.validationErrors.isEmpty(), "the tier III glyph is not refused by the novice book");
                pass("editor_tier_gate", "the novice book's editor lists Glyph of Firebolt X but refuses it: "
                    + tierThree.validationErrors.get(0).makeTextComponentExisting().getString());
                for (var part : new com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart[]{
                        com.hollingsworth.arsnouveau.common.spell.method.MethodTouch.INSTANCE, effect1,
                        com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify.INSTANCE}) {
                    var button = glyphButton(book, part);
                    require(button != null && button.validationErrors.isEmpty(), "no usable editor button for " + part.getRegistryName());
                    click(book, button);
                }
                stage = 51;
                wait = 5;
            }
            case 51 -> {
                if (!isSpellBookEditor(mc.screen)) throw new IllegalStateException("the spell book editor closed: " + mc.screen);
                net.minecraft.client.gui.screens.Screen book = mc.screen;
                screenshot(mc, "09-editor-composed");
                var create = book.children().stream()
                    .filter(com.hollingsworth.arsnouveau.client.gui.buttons.CreateSpellButton.class::isInstance)
                    .map(com.hollingsworth.arsnouveau.client.gui.buttons.CreateSpellButton.class::cast).findFirst()
                    .orElseThrow(() -> new IllegalStateException("the editor has no Create button"));
                click(book, create);
                stage = 52;
                wait = 20;
            }
            case 52 -> {
                var recipe = com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry.from(mc.player.getInventory().getItem(4))
                    .getSpell().unsafeList();
                if (recipe.size() != 3) return;
                pass("editor_compose", "the editor saved " + recipe.stream().map(part -> part.getLocaleName()).toList() + " to the book");
                mc.screen.onClose();
                Pig pig = target = pig(mc);
                pigHealth = pig.getHealth();
                select(mc, 4);
                look(mc, pig.getBoundingBox().getCenter());
                stage = 53;
                wait = 5;
            }
            case 53 -> {
                use(mc);
                stage = 54;
                wait = 20;
            }
            case 54 -> {
                Pig pig = target;
                if (!seen(pig.getHealth() < pigHealth, "the augmented glyph cast did not hurt the pig")) return;
                pass("augmented_cast", "Touch -> Glyph of Firebolt I -> Amplify from the composed book hurt the aimed pig: "
                    + pigHealth + " -> " + pig.getHealth());
                pigHealth = pig.getHealth();
                select(mc, 2);
                look(mc, pig.getBoundingBox().getCenter());
                stage = 55;
                wait = 5;
            }
            case 55 -> {
                use(mc);
                stage = 56;
                wait = 20;
            }
            case 56 -> {
                Pig pig = target;
                if (!seen(pig.getHealth() < pigHealth, "the nested export did not hurt the pig")) return;
                pass("nested_cast", "a Spell Loom export of Touch -> Glyph of Firebolt I hurt the aimed pig: " + pigHealth + " -> " + pig.getHealth());
                stage = 57;
                wait = 10;
            }
            case 57 -> {
                // Editor responsiveness with the full catalog: the creative book lists every spell part.
                select(mc, 5);
                long start = System.nanoTime();
                key(mc, GLFW.GLFW_KEY_C);
                editorOpenNanos = System.nanoTime() - start;
                stage = 58;
                wait = 40;
            }
            case 58 -> {
                if (!isSpellBookEditor(mc.screen)) throw new IllegalStateException("the creative book's editor did not open: " + mc.screen);
                int parts = com.hollingsworth.arsnouveau.api.registry.GlyphRegistry.getSpellpartMap().size();
                long imported = com.hollingsworth.arsnouveau.api.registry.GlyphRegistry.getSpellpartMap().values().stream()
                    .filter(com.otectus.arsnspells.glyph.ImportedIronEffect.class::isInstance).count();
                int fps = mc.getFps();
                // Ars's search bar is a nuggets text field (an AbstractWidget, not a vanilla EditBox):
                // click it to focus, then type through the screen, as keyboard input does.
                var search = mc.screen.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                    .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                    .filter(box -> box.getClass().getSimpleName().equals("SearchBar")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("the editor has no search bar"));
                click(mc.screen, search);
                long start = System.nanoTime();
                for (char c : "Firebolt".toCharArray()) mc.screen.charTyped(c, 0);
                long searchNanos = System.nanoTime() - start;
                long shown = mc.screen.children().stream().filter(com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton.class::isInstance)
                    .map(com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton.class::cast).filter(button -> button.visible).count();
                LOGGER.info("ANS-PERF editor_full_catalog spell_parts={} imported_glyphs={} open_ms={} search_ms={} fps_while_open={} shown_after_search={}",
                    parts, imported, String.format(java.util.Locale.ROOT, "%.1f", editorOpenNanos / 1.0e6),
                    String.format(java.util.Locale.ROOT, "%.1f", searchNanos / 1.0e6), fps, shown);
                require(shown >= 10, "searching \"Firebolt\" did not show its glyphs: " + shown);
                pass("editor_full_catalog", "the creative book's editor opened with all " + parts + " spell parts (" + imported
                    + " imported glyphs); searching \"Firebolt\" shows " + shown + " glyph buttons");
                stage = 59;
                wait = 5;
            }
            case 59 -> {
                screenshot(mc, "12-editor-search-firebolt");
                mc.screen.onClose();
                LOGGER.info("ANS_GLYPH_QA_CLIENT_PASS");
                stage = 22;
                wait = 20;
            }
            // ---- Two clients, role A: fill the Press, then click Inscribe twice in one tick while B watches ----
            case 70 -> {
                // The server builds the scene once both clients are in: wait for the Press and A's inputs.
                if (!pressPlaced(mc) || mc.player.getInventory().getItem(9).isEmpty()) return;
                select(mc, 7);
                look(mc, new Vec3(-1.5, -59.6, 0.5));
                stage = 71;
                wait = 5;
            }
            case 71 -> {
                use(mc);
                stage = 72;
                wait = 10;
            }
            case 72 -> {
                if (!(mc.screen instanceof GlyphPressScreen)) return;
                GlyphPressMenu menu = menu(mc);
                for (int inventorySlot = 9; inventorySlot <= 11; inventorySlot++) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, 4 + inventorySlot - 9, 0, ClickType.QUICK_MOVE, mc.player);
                }
                stage = 73;
                wait = 10;
            }
            case 73 -> {
                GlyphPressMenu menu = menu(mc);
                // The server marks inventory slot 20 once B has the same Press open with A's inputs in view.
                if (menu.reason() != GlyphPressPlan.Reason.OK || mc.player.getInventory().getItem(20).isEmpty()) return;
                pass("duo_a_ready", "A's inputs are in and the server reports Ready; B has the same Press open");
                press(mc, "ars_n_spells.glyph_press.inscribe", null);
                press(mc, "ars_n_spells.glyph_press.inscribe", null);
                stage = 74;
            }
            case 74 -> {
                if (menu(mc).state() != GlyphPressBlockEntity.State.PROCESSING) return;
                pass("duo_a_running", "two Inscribe clicks in one tick; the Press shows one running job");
                screenshot(mc, "10-duo-a-running");
                stage = 75;
            }
            case 75 -> {
                GlyphPressMenu menu = menu(mc);
                if (menu.state() == GlyphPressBlockEntity.State.PROCESSING || menu.getSlot(GlyphPressBlockEntity.SLOT_OUTPUT).hasItem()) return;
                require(glyphSlot(mc) < 0, "A received a glyph although B took it");
                pass("duo_a_done", "the job finished and B took the glyph; A's open menu followed");
                mc.player.closeContainer();
                LOGGER.info("ANS_GLYPH_QA_CLIENT_DUO_PASS role=A");
                stage = 22;
                wait = 20;
            }
            // ---- Two clients, role B: watch A's job, try the locked inputs, take the result ----
            case 80 -> {
                if (!pressPlaced(mc)) return;
                select(mc, 7);
                look(mc, new Vec3(-1.5, -59.6, 0.5));
                stage = 81;
                wait = 5;
            }
            case 81 -> {
                use(mc);
                stage = 82;
                wait = 10;
            }
            case 82 -> {
                if (!(mc.screen instanceof GlyphPressScreen)) return;
                if (!menu(mc).getSlot(GlyphPressBlockEntity.SLOT_SCROLL).hasItem()) return;
                pass("duo_b_sees_inputs", "B's open menu shows the scroll, plate and catalyst A put in");
                stage = 83;
            }
            case 83 -> {
                GlyphPressMenu menu = menu(mc);
                if (menu.state() != GlyphPressBlockEntity.State.PROCESSING) return;
                require(!button(mc, "ars_n_spells.glyph_press.inscribe", null).active, "Inscribe is active for B during A's job");
                screenshot(mc, "11-duo-b-watching");
                mc.gameMode.handleInventoryMouseClick(menu.containerId, GlyphPressBlockEntity.SLOT_SCROLL, 0, ClickType.QUICK_MOVE, mc.player);
                mc.gameMode.handleInventoryMouseClick(menu.containerId, GlyphPressBlockEntity.SLOT_SCROLL, 0, ClickType.PICKUP, mc.player);
                stage = 84;
                wait = 10;
            }
            case 84 -> {
                GlyphPressMenu menu = menu(mc);
                boolean intact = menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).hasItem() && menu.getCarried().isEmpty();
                if (!seen(intact, "B moved the locked scroll: slot=" + menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).getItem()
                        + " carried=" + menu.getCarried())) return;
                pass("duo_b_locked", "B's shift-click and pickup on the locked scroll changed nothing; Inscribe stays off for B");
                stage = 85;
            }
            case 85 -> {
                GlyphPressMenu menu = menu(mc);
                if (menu.state() == GlyphPressBlockEntity.State.PROCESSING || !menu.getSlot(GlyphPressBlockEntity.SLOT_OUTPUT).hasItem()) return;
                mc.gameMode.handleInventoryMouseClick(menu.containerId, GlyphPressBlockEntity.SLOT_OUTPUT, 0, ClickType.QUICK_MOVE, mc.player);
                stage = 86;
                wait = 10;
            }
            case 86 -> {
                if (!seen(glyphSlot(mc) >= 0, "B did not receive the glyph")) return;
                pass("duo_b_took_output", "B took the one finished glyph");
                mc.player.closeContainer();
                LOGGER.info("ANS_GLYPH_QA_CLIENT_DUO_PASS role=B");
                stage = 22;
                wait = 40;
            }
            // ---- Restart phase: the saved book still casts ----
            case 60 -> {
                Pig pig = target = pig(mc);
                pigHealth = pig.getHealth();
                select(mc, 4);
                look(mc, pig.getBoundingBox().getCenter());
                stage = 61;
                wait = 5;
            }
            case 61 -> {
                use(mc);
                stage = 62;
                wait = 20;
            }
            case 62 -> {
                Pig pig = target;
                if (!seen(pig.getHealth() < pigHealth, "after the restart the saved book did not hurt the pig")) return;
                pass("restart_cast", "after the restart the saved book's Touch -> Glyph of Firebolt I -> Amplify hurt the aimed pig: "
                    + pigHealth + " -> " + pig.getHealth());
                LOGGER.info("ANS_GLYPH_QA_CLIENT_RESTART_PASS");
                stage = 22;
                wait = 40;
            }
            case 22 -> mc.stop();
            default -> {
            }
        }
    }

    private static void pass(String stage, String detail) {
        LOGGER.info("ANS_GLYPH_QA_CLIENT stage={} result=PASS detail={}", stage, detail);
    }

    /** For effects the server applies and syncs back: true once seen, a failure after 100 ticks without it. */
    private static boolean seen(boolean condition, String failure) {
        if (condition) {
            poll = 0;
            return true;
        }
        require(++poll <= 100, failure + " (waited " + poll + " ticks)");
        return false;
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new IllegalStateException("Glyph QA client failed at stage " + stage + ": " + failure);
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().selected = slot;
    }

    private static void look(Minecraft mc, Vec3 at) {
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, at);
        mc.player.yRotO = mc.player.getYRot();
        mc.player.xRotO = mc.player.getXRot();
    }

    /** One press of the use key, handled by Minecraft's own key processing on the next tick. */
    private static void use(Minecraft mc) {
        KeyMapping.click(mc.options.keyUse.getKey());
    }

    private static boolean pressPlaced(Minecraft mc) {
        return mc.level.getBlockState(net.minecraft.core.BlockPos.containing(-2, -60, 0)).getBlock()
            instanceof com.otectus.arsnspells.block.GlyphPressBlock;
    }

    private static boolean isSpellBookEditor(@org.jetbrains.annotations.Nullable net.minecraft.client.gui.screens.Screen screen) {
        return screen != null && screen.getClass().getName().equals("com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook");
    }

    /** A real key press and release through Minecraft's keyboard handler (fires the key input event mods listen to). */
    private static void key(Minecraft mc, int key) {
        long window = mc.getWindow().getWindow();
        int scancode = GLFW.glfwGetKeyScancode(key);
        mc.keyboardHandler.keyPress(window, key, scancode, GLFW.GLFW_PRESS, 0);
        mc.keyboardHandler.keyPress(window, key, scancode, GLFW.GLFW_RELEASE, 0);
    }

    /** A left click at the centre of a widget, dispatched by the screen as a mouse click would be. */
    private static void click(net.minecraft.client.gui.screens.Screen screen, net.minecraft.client.gui.components.AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @org.jetbrains.annotations.Nullable
    private static com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton glyphButton(
            net.minecraft.client.gui.screens.Screen screen, com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart part) {
        return screen.children().stream().filter(com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton.class::isInstance)
            .map(com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton.class::cast)
            .filter(button -> button.visible && button.abstractSpellPart.getRegistryName().equals(part.getRegistryName()))
            .findFirst().orElse(null);
    }

    private static Pig pig(Minecraft mc) {
        var found = mc.level.getEntitiesOfClass(Pig.class, mc.player.getBoundingBox().inflate(8), Pig::isAlive).stream().findFirst();
        if (found.isEmpty()) {
            java.util.List<String> seen = new java.util.ArrayList<>();
            mc.level.entitiesForRendering().forEach(e -> seen.add(e.getType().toShortString() + "@" + e.blockPosition().toShortString()));
            LOGGER.info("ANS_GLYPH_QA_CLIENT entities player={} seen={}", mc.player.position(), seen);
        }
        return found.orElseThrow(() -> new IllegalStateException("no QA pig in view"));
    }

    private static GlyphPressMenu menu(Minecraft mc) {
        if (!(mc.screen instanceof GlyphPressScreen screen)) throw new IllegalStateException("the Press screen closed: " + mc.screen);
        return screen.getMenu();
    }

    private static int glyphSlot(Minecraft mc) {
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof ImportedGlyphItem) return i;
        }
        return -1;
    }

    /** Press a button by translation key, or by literal label when {@code key} is null. */
    private static void press(Minecraft mc, String key, String literal) {
        Button button = button(mc, key, literal);
        if (!button.active) throw new IllegalStateException("Inactive button " + (key == null ? literal : key));
        button.onPress();
    }

    private static Button button(Minecraft mc, String key, String literal) {
        return mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(candidate -> key == null ? candidate.getMessage().getString().equals(literal)
                : candidate.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key))
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing button " + (key == null ? literal : key)));
    }

    private static void screenshot(Minecraft mc, String name) {
        try {
            LOGGER.info("ANS_GLYPH_QA_CAPTURE name={} viewport={}x{} effective_scale={} requested_scale={}", name,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                mc.getWindow().getGuiScale(), mc.options.guiScale().get());
            var directory = mc.gameDirectory.toPath().resolve("glyph-qa-review");
            java.nio.file.Files.createDirectories(directory);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(directory.resolve(name + ".png"));
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
