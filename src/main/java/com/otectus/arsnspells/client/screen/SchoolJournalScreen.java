package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.network.SchoolJournalSnapshot;
import com.otectus.arsnspells.client.icons.SpellIconRegistry;
import com.otectus.arsnspells.util.SchoolKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Standalone graphical journal: every value is a captured server result, never local progress. */
public final class SchoolJournalScreen extends VanillaPanelScreen {
    private static final int PAGE_SIZE = 5;
    private final SchoolJournalSnapshot snapshot;
    private int page, left, top;
    public SchoolJournalScreen(SchoolJournalSnapshot snapshot) {
        super(Component.translatable("ars_n_spells.journal.title")); this.snapshot = snapshot;
    }
    public static void open(SchoolJournalSnapshot snapshot) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.setScreen(new SchoolJournalScreen(snapshot));
    }
    @Override protected void init() {
        left = (width - 300) / 2; top = (height - 220) / 2;
        rebuild();
    }
    private void rebuild() {
        clearWidgets();
        var previous = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> { page--; rebuild(); })
            .bounds(left + 8, top + 192, 64, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.icon_picker.next"), button -> { page++; rebuild(); })
            .bounds(left + 78, top + 192, 64, 20).build());
        next.active = (page + 1) * PAGE_SIZE < snapshot.rows().size();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(left + 198, top + 192, 94, 20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    private static String number(double value) { return String.format(Locale.ROOT, "%.4f", value); }
    private Component rowText(SchoolJournalSnapshot.Row row) {
        return Component.translatable("ars_n_spells.journal.row", row.affinity(), row.casts(), number(row.applied()));
    }
    @Override public Component getNarrationMessage() {
        var result = title.copy().append(". ");
        for (int i = page * PAGE_SIZE; i < Math.min(snapshot.rows().size(), (page + 1) * PAGE_SIZE); i++) {
            var row = snapshot.rows().get(i); result.append(row.school()).append(". ").append(rowText(row)).append(". ");
        }
        return result;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics, mouseX, mouseY, partialTick, left, top, 300, 220);
        VanillaGui.centered(graphics, font, title, left + 150, top + 8, VanillaGui.TEXT);
        Component caps = Component.translatable("ars_n_spells.journal.caps", number(snapshot.perCast()), number(snapshot.cap()));
        graphics.drawString(font, VanillaGui.ellipsize(font, caps.getString(), 284), left + 8, top + 23, VanillaGui.MUTED, false);
        Component captured = Component.translatable("ars_n_spells.journal.captured", snapshot.mappingDigest().substring(0, Math.min(12, snapshot.mappingDigest().length())));
        graphics.drawString(font, VanillaGui.ellipsize(font, captured.getString(), 284), left + 8, top + 36, VanillaGui.MUTED, false);
        if (snapshot.rows().isEmpty()) VanillaGui.wrapped(graphics, font,
            Component.translatable("ars_n_spells.diagnostics.empty_journal"), left + 8, top + 60, 284, VanillaGui.TEXT);
        for (int i = page * PAGE_SIZE; i < Math.min(snapshot.rows().size(), (page + 1) * PAGE_SIZE); i++) {
            var row = snapshot.rows().get(i); int y = top + 54 + (i % PAGE_SIZE) * 25;
            String schoolIcon = SchoolKeys.builtin(row.school()).id();
            if ("generic".equals(schoolIcon) && !SchoolKeys.GENERIC.equals(row.school())) schoolIcon = "unknown";
            graphics.fill(left + 8, y + 23, left + 292, y + 24, 0xFFA0A0A0);
            graphics.blit(SpellIconRegistry.INSTANCE.resolve("school/" + schoolIcon, "generic"),
                left + 8, y + 2, 0, 0, 16, 16, 16, 16);
            graphics.drawString(font, VanillaGui.ellipsize(font, row.school(), 258), left + 30, y, VanillaGui.TEXT, false);
            graphics.drawString(font, VanillaGui.ellipsize(font, rowText(row).getString(), 258), left + 30, y + 11, VanillaGui.MUTED, false);
            if (mouseX >= left + 8 && mouseX <= left + 292 && mouseY >= y && mouseY < y + 23) {
                var tooltip = Component.literal(row.school()).append("\n").append(rowText(row)).append("\n")
                    .append(Component.translatable("ars_n_spells.journal.attribute", row.attribute()));
                setTooltipForNextRenderPass(font.split(tooltip, 280));
            }
        }
        graphics.drawString(font, Component.translatable("ars_n_spells.journal.page", page + 1,
            Math.max(1, (snapshot.rows().size() + PAGE_SIZE - 1) / PAGE_SIZE), snapshot.rows().size(), snapshot.totalSchools()),
            left + 8, top + 179, VanillaGui.MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
