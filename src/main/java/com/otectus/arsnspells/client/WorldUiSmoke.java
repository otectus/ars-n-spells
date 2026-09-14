package com.otectus.arsnspells.client;

import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.common.spell.effect.*;
import com.hollingsworth.arsnouveau.common.spell.method.*;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.client.screen.SpellIconPickerScreen;
import com.otectus.arsnspells.client.screen.SpellLoomScreen;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.concurrent.CompletableFuture;

/** Explicit developer harness; it opens only a pre-copied disposable save with this fixed name. */
@EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class WorldUiSmoke {
    public static final String SAVE = "ANS Icon QA";
    private static int stage, ticks, age;
    private static CompletableFuture<Void> preparation;
    private WorldUiSmoke() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ans.worldUiSmoke")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++age > 3600) throw new IllegalStateException("World UI smoke timed out at stage " + stage + " screen=" + mc.screen);
        if (mc.getOverlay() != null) return;
        if (stage == 0) {
            if (mc.screen == null || (!(mc.screen instanceof TitleScreen)
                && !mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) return;
            if (!java.nio.file.Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat")))
                throw new IllegalStateException("World UI smoke needs the isolated copied save: " + SAVE);
            if (!com.otectus.arsnspells.compat.IronsCompat.isLoaded()) throw new IllegalStateException("World UI smoke needs Iron's runtime");
            mc.options.guiScale().set(4); mc.getWindow().setWindowed(1280, 960); mc.resizeDisplay();
            stage = 1;
            mc.createWorldOpenFlows().openWorld(SAVE, () -> mc.setScreen(new TitleScreen()));
            return;
        }
        if (stage == 1) {
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            var id = mc.player.getUUID();
            var server = mc.getSingleplayerServer();
            preparation = CompletableFuture.runAsync(() -> seed(server.getPlayerList().getPlayer(id)), server);
            stage = 2; return;
        }
        if (stage == 2) {
            if (!preparation.isDone()) return;
            preparation.join();
            if (!(mc.screen instanceof SpellLoomScreen)) return;
            // Loading a world can restore options/window state. Set the measured
            // viewport after loading, and keep the pointer away from tooltips.
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 960);
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
            mc.options.guiScale().set(4);
            mc.resizeDisplay();
            stage = 3; ticks = 0; return;
        }
        if (++ticks < 30) return;
        ticks = 0;
        switch (stage) {
            case 3 -> {
                screenshot(mc, "01-loom-ready");
                mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("Aurora Mend");
                press(mc, "ars_n_spells.spell_loom.details.open");
            }
            case 4 -> {
                screenshot(mc, "02-loom-details");
                var text = mc.screen.children().stream().filter(child -> child.getClass().getSimpleName().equals("ScrollableTextPanel")).findFirst().orElseThrow();
                mc.screen.setFocused(text);
                text.keyPressed(269, 0, 0);
            }
            case 5 -> { screenshot(mc, "02b-details-scrolled"); mc.screen.onClose(); press(mc, "ars_n_spells.icon_picker.open"); }
            case 6 -> {
                screenshot(mc, "03-picker-from-loom");
                mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("heal");
            }
            case 7 -> {
                screenshot(mc, "04-picker-heal-search");
                press(mc, com.otectus.arsnspells.icons.IconCatalog.label("spell/heal"));
                press(mc, "gui.done");
            }
            case 8 -> {
                screenshot(mc, "05-loom-cosmetic-return");
                String name = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().getValue();
                if (!"Aurora Mend".equals(name)) throw new IllegalStateException("Loom name lost across child screens");
                press(mc, "ars_n_spells.spell_loom.export");
            }
            case 9 -> {
                if (!(mc.screen instanceof SpellLoomScreen loom) || loom.getMenu().getSlot(SpellLoomBlockEntity.SLOT_OUTPUT).getItem().isEmpty())
                    throw new IllegalStateException("Native Loom packet did not produce an inscribed scroll");
                screenshot(mc, "06-loom-inscribed");
                mc.player.closeContainer();
            }
            case 10 -> Wheel.open();
            case 11 -> screenshot(mc, "07-native-wheel");
            case 12 -> {
                Wheel.close();
                org.slf4j.LoggerFactory.getLogger(WorldUiSmoke.class).info("ANS_WORLD_UI_REVIEW_COMPLETE stages=7 real_server_menu=true native_wheel=true");
                mc.stop();
            }
            default -> throw new IllegalStateException("Unknown world UI smoke stage " + stage);
        }
        stage++;
    }
    private static void press(Minecraft mc, String key) {
        Button button = mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(candidate -> candidate.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key))
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing button " + key + " in " + mc.screen));
        if (!button.active) throw new IllegalStateException("Inactive button " + key);
        button.onPress();
    }
    private static void screenshot(Minecraft mc, String name) {
        try {
            if (mc.getWindow().getGuiScaledWidth() < 320 || mc.getWindow().getGuiScaledHeight() < 240)
                throw new IllegalStateException("Review viewport is below Minecraft's minimum: "
                    + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
            org.slf4j.LoggerFactory.getLogger(WorldUiSmoke.class).info("ANS_WORLD_GUI_CAPTURE name={} viewport={}x{}",
                name, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            var directory = mc.gameDirectory.toPath().resolve("world-ui-review");
            java.nio.file.Files.createDirectories(directory);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(name + ".png")); }
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static ItemStack item(String id) {
        var value = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (value == null || value == net.minecraft.world.item.Items.AIR) throw new IllegalStateException("Missing review item " + id);
        return new ItemStack(value);
    }
    private static void seed(ServerPlayer player) {
        if (player == null) throw new IllegalStateException("Review player has not joined the integrated server");
        player.setGameMode(GameType.CREATIVE);
        var level = player.serverLevel();
        BlockPos pos = new BlockPos(0, 129, 0);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 128, z), Blocks.STONE.defaultBlockState());
            for (int y = 129; y <= 132; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(.5, 129, 2.5);
        level.setBlockAndUpdate(pos, ModBlocksRegistry.SPELL_LOOM.get().defaultBlockState());
        SpellLoomBlockEntity loom = (SpellLoomBlockEntity) level.getBlockEntity(pos);
        ItemStack source = item("ars_nouveau:novice_spell_book");
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        new SpellCaster().setSpell(heal).saveToStack(source);
        loom.getItems().setStackInSlot(SpellLoomBlockEntity.SLOT_SOURCE, source);
        loom.getItems().setStackInSlot(SpellLoomBlockEntity.SLOT_SCROLL, item("irons_spellbooks:scroll"));
        loom.getItems().setStackInSlot(SpellLoomBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        ItemStack book = item("irons_spellbooks:netherite_spell_book");
        bind(book, heal, "Aurora Mend", "water", "spell/heal");
        bind(book, new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE), "Ember Touch", "fire", "spell/fireball");
        bind(book, new Spell(MethodProjectile.INSTANCE, EffectBreak.INSTANCE), "Stone Needle", "earth", "element/earth");
        Wheel.equip(player, book);
        // The client constructor consumes both the block position and the
        // request-session UUID.  The bare NeoForge openMenu(MenuProvider,
        // BlockPos) overload only writes the position, so use the same
        // extended payload as SpellLoomBlock itself.
        java.util.UUID session = java.util.UUID.randomUUID();
        var provider = new net.minecraft.world.SimpleMenuProvider(
            (id, inventory, menuPlayer) -> new com.otectus.arsnspells.menu.SpellLoomMenu(id, inventory, loom, session),
            loom.getDisplayName());
        player.openMenu(provider, buffer -> { buffer.writeBlockPos(pos); buffer.writeUUID(session); });
    }
    private static void bind(ItemStack book, Spell spell, String name, String background, String icon) {
        if (!IronsBookBindingUtil.appendArsSpellToBook(book, com.otectus.arsnspells.spell.CrossCastingHandler.encodeArsSpell(spell), name, background, icon, -1).wasAdded())
            throw new IllegalStateException("Review native spell binding failed: " + name);
    }
    private static final class Wheel {
        static void equip(ServerPlayer player, ItemStack book) { io.redspace.ironsspellbooks.api.util.Utils.setPlayerSpellbookStack(player, book); }
        static void open() {
            io.redspace.ironsspellbooks.player.ClientMagicData.updateSpellSelectionManager();
            io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.open();
        }
        static void close() { io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay.instance.close(); }
    }
}
