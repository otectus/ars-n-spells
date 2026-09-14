package com.otectus.arsnspells.client.icons;

import com.otectus.arsnspells.client.screen.SpellIconPickerScreen;
import com.otectus.arsnspells.client.screen.CompatibilityScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.LoggerFactory;

/** Opt-in real-client visual harness. No world is opened and no inventory is mutated. */
@Mod.EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class IconClientSmoke {
    private static int ticks, stage;
    private static boolean started;
    private static java.util.concurrent.CompletableFuture<Void> packReload;
    private IconClientSmoke() {}
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ans.iconSmoke")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (packReload != null) {
            if (!packReload.isDone()) return;
            packReload.join(); packReload = null; ticks = 0;
            mc.setScreen(new IconReviewScreen()); return;
        }
        if (!started) {
            // Fresh isolated instances display accessibility onboarding before the title.
            if (mc.screen == null || mc.getOverlay() != null
                || (!(mc.screen instanceof TitleScreen)
                    && !mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) return;
            mc.getWindow().setWindowed(1280, 960);
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 960);
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
            started = true; ticks = 0;
            open(mc, 1);
            return;
        }
        if (mc.getOverlay() != null) return;
        if (++ticks < 16) return;
        ticks = 0;
        try {
            var directory = mc.gameDirectory.toPath().resolve("icon-review");
            LoggerFactory.getLogger(IconClientSmoke.class).info("ANS_GUI_CAPTURE stage={} scale={} viewport={}x{}",
                stage, mc.getWindow().getGuiScale(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            java.nio.file.Files.createDirectories(directory);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(directory.resolve("stage-" + stage + ".png"));
            }
            stage++;
            if (stage < 4) open(mc, stage + 1);
            else if (stage == 4 && mc.screen instanceof SpellIconPickerScreen picker) {
                EditBox search = picker.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("fire");
            } else if (stage == 5) {
                // Unknown saved keys and backgrounds must still create visible art.
                SpellIconRegistry.INSTANCE.resolve("../broken-future-icon", "not_a_school");
                mc.setScreen(new CompatibilityScreen(new TitleScreen()));
            } else if (stage == 6) {
                mc.setScreen(new IconReviewScreen());
            } else if (stage == 7 && !Boolean.getBoolean("ans.iconSmoke.packs")) {
                mc.setScreen(com.otectus.arsnspells.client.screen.ConfigScreenFactory.createConfigScreen(new TitleScreen()));
                // Opening settings without a server must keep every mutation control disabled.
                long disabled = mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> !button.active).count();
                if (disabled < 2) throw new IllegalStateException("Read-only settings exposed active mutation controls");
            } else if (stage == 8 && !Boolean.getBoolean("ans.iconSmoke.packs")) {
                mc.screen.keyPressed(267, 0, 0);
            } else if (stage == 9 && !Boolean.getBoolean("ans.iconSmoke.packs")) {
                var rows = new java.util.ArrayList<com.otectus.arsnspells.network.SchoolJournalSnapshot.Row>();
                for (String school : java.util.List.of("fire", "ice", "lightning", "nature", "holy", "ender"))
                    rows.add(new com.otectus.arsnspells.network.SchoolJournalSnapshot.Row("irons_spellbooks:" + school, 25, 123, 0.125, "irons_spellbooks:" + school + "_spell_power"));
                mc.setScreen(new com.otectus.arsnspells.client.screen.SchoolJournalScreen(
                    new com.otectus.arsnspells.network.SchoolJournalSnapshot("0123456789abcdef", .001, .25, rows.size(), rows)));
            } else if (stage == 10 && !Boolean.getBoolean("ans.iconSmoke.packs")) {
                mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals(Component.translatable("ars_n_spells.icon_picker.next").getString()))
                    .findFirst().orElseThrow().onPress();
            } else if (stage == 11 && !Boolean.getBoolean("ans.iconSmoke.packs")) {
                mc.setScreen(new SpellIconPickerScreen(new TitleScreen(), "spell/fireball", "none", (icon, background) -> {}));
                mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("no-such-icon");
            } else if (stage >= 7 && stage <= 9 && Boolean.getBoolean("ans.iconSmoke.packs")) {
                String pack = switch (stage) { case 7 -> "hd32"; case 8 -> "high-contrast"; default -> "monochrome"; };
                var repository = mc.getResourcePackRepository(); repository.reload();
                String id = "file/ars-n-spells-icons-" + pack + ".zip";
                if (!repository.getAvailableIds().contains(id)) throw new IllegalStateException("Review pack missing: " + id);
                var selected = new java.util.ArrayList<>(repository.getSelectedIds());
                selected.removeIf(key -> key.startsWith("file/ars-n-spells-icons-")); selected.add(id);
                repository.setSelected(selected);
                LoggerFactory.getLogger(IconClientSmoke.class).info("ANS_ICON_PACK_REVIEW pack={} selected={}", pack, repository.getSelectedIds());
                packReload = mc.reloadResourcePacks();
            } else {
                LoggerFactory.getLogger(IconClientSmoke.class).info("ANS_ICON_CLIENT_REVIEW_COMPLETE screenshots={} gui_scales=1,2,3,4 search=fire unknown_key=fallback", stage);
                mc.stop();
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Icon client review failed", e); }
    }
    private static void open(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
        mc.setScreen(new SpellIconPickerScreen(new TitleScreen(), "spell/fireball", "fire", (icon, background) -> {}));
    }
}
