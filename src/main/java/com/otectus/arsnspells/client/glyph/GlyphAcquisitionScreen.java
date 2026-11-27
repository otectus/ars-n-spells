package com.otectus.arsnspells.client.glyph;

import com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu;
import com.hollingsworth.arsnouveau.common.crafting.recipes.GlyphRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import com.otectus.arsnspells.client.GlyphBrowserClientEvents;
import net.minecraft.client.Minecraft;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Grouped presentation of concrete datapack glyph recipes; choosing retains the exact recipe holder. */
public final class GlyphAcquisitionScreen extends Screen {
    private final GlyphUnlockMenu parent;
    private long recipeRevision;
    private List<Group> groups = List.of();
    private Group detail;
    private int page;
    private long generation = Long.MIN_VALUE;

    public GlyphAcquisitionScreen(GlyphUnlockMenu parent) {
        super(Component.translatable("ars_n_spells.browser.acquisition.title"));
        this.parent = parent;
    }

    public static List<RecipeHolder<GlyphRecipe>> recipes() {
        var level = Minecraft.getInstance().level;
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(RecipeRegistry.GLYPH_TYPE.get());
    }

    @Override protected void init() {
        rebuildModel();
        rebuildButtons();
    }

    @Override public void tick() {
        if (AnsClientConfig.mode() != DisplayMode.GROUPED) {
            BrowserPlatform.pop();
            return;
        }
        if (generation != BrowserPlatform.generation() || recipeRevision != GlyphBrowserClientEvents.recipeRevision()) {
            rebuildModel();
            rebuildButtons();
        }
    }

    private void rebuildModel() {
        generation = BrowserPlatform.generation();
        recipeRevision = GlyphBrowserClientEvents.recipeRevision();
        Map<String, List<Choice>> grouped = new LinkedHashMap<>();
        for (var holder : recipes()) {
            if (!(holder.value().getSpellPart() instanceof ImportedIronEffect effect) || !BrowserPlatform.available(effect)) continue;
            grouped.computeIfAbsent(effect.key().ironSpellId().toString(), unused -> new ArrayList<>())
                .add(new Choice(effect, holder));
        }
        List<Group> next = new ArrayList<>();
        grouped.forEach((id, choices) -> {
            choices.sort(Comparator.comparingInt((Choice choice) -> choice.effect.key().baseLevel())
                .thenComparing(choice -> choice.recipe.id()));
            next.add(new Group(id, List.copyOf(choices)));
        });
        next.sort(Comparator.comparing((Group group) -> group.choices.getFirst().effect.spellName().getString())
            .thenComparing(Group::spellId));
        groups = List.copyOf(next);
        if (detail != null) detail = groups.stream().filter(group -> group.spellId.equals(detail.spellId)).findFirst().orElse(null);
    }

    private void rebuildButtons() {
        clearWidgets();
        int x = Math.max(8, width / 2 - 170), top = Math.max(28, height / 2 - 92), w = Math.min(340, width - 16);
        int rows = Math.max(1, (Math.min(height - top - 45, 154)) / 24);
        int size = detail == null ? groups.size() : detail.choices.size();
        page = BrowserModel.clampPage(page, size, rows);
        int first = page * rows;
        if (detail == null) {
            for (int i = first; i < Math.min(size, first + rows); i++) {
                Group group = groups.get(i);
                ImportedIronEffect effect = group.choices.getFirst().effect;
                Component label = Component.literal(effect.spellName().getString() + " · ")
                    .append(Component.translatable("ars_n_spells.browser.acquisition.recipes", group.choices.size()))
                    .append(" · " + BrowserPlatform.sourceName(effect.key().ironSpellId().getNamespace()));
                addRenderableWidget(Button.builder(label, button -> {
                    if (group.choices.size() == 1) choose(group.choices.getFirst().recipe);
                    else { detail = group; page = 0; rebuildButtons(); }
                }).bounds(x, top + (i - first) * 24, w, 20)
                    .tooltip(Tooltip.create(Component.literal(group.spellId))).build());
            }
        } else {
            for (int i = first; i < Math.min(size, first + rows); i++) {
                Choice choice = detail.choices.get(i);
                Component label = Component.translatable("ars_n_spells.browser.acquisition.choose", choice.effect.getLocaleName());
                addRenderableWidget(Button.builder(label, button -> choose(choice.recipe))
                    .bounds(x, top + (i - first) * 24, w, 20)
                    .tooltip(Tooltip.create(Component.literal(choice.effect.getRegistryName().toString()))).build());
            }
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> { page--; rebuildButtons(); })
            .bounds(x, top + rows * 24, 32, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> { page++; rebuildButtons(); })
            .bounds(x + 34, top + rows * 24, 32, 20).build());
        next.active = (page + 1) * rows < size;
        addRenderableWidget(Button.builder(detail == null ? Component.translatable("gui.done")
                : Component.translatable("ars_n_spells.browser.back"), b -> onClose())
            .bounds(x + w - 90, top + rows * 24, 90, 20).build());
    }

    private void choose(RecipeHolder<GlyphRecipe> recipe) {
        if (AnsClientConfig.mode() != DisplayMode.GROUPED || recipeRevision != GlyphBrowserClientEvents.recipeRevision()
                || !(recipe.value().getSpellPart() instanceof ImportedIronEffect effect) || !BrowserPlatform.available(effect)) {
            tick();
            return;
        }
        parent.selectedRecipe = recipe;
        parent.updateRecipeButtons();
        BrowserPlatform.pop();
    }

    @Override public void onClose() {
        if (detail != null) {
            detail = null;
            page = 0;
            rebuildButtons();
        } else BrowserPlatform.pop();
    }

    @Override public boolean isPauseScreen() { return false; }

    /** The underlying editor is already rendered by NeoForge's GUI layer stack. */
    @Override public void renderBackground(GuiGraphics graphics, int mx, int my, float frame) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float frame) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int x = Math.max(8, width / 2 - 178), y = Math.max(8, height / 2 - 118), w = Math.min(356, width - 16);
        GlyphLibraryScreen.paper(graphics, x, y, w, Math.min(236, height - 16));
        Component heading = detail == null ? title : detail.choices.getFirst().effect.spellName();
        graphics.drawCenteredString(font, heading, width / 2, y + 8, 0xFFF4E7BB);
        if (groups.isEmpty()) {
            int lineY = y + 35;
            for (var line : font.split(Component.translatable("ars_n_spells.browser.press_help"), w - 30)) {
                graphics.drawString(font, line, x + 15, lineY, 0xFF382C20, false);
                lineY += font.lineHeight + 2;
            }
        }
        super.render(graphics, mouseX, mouseY, frame);
    }

    private record Choice(ImportedIronEffect effect, RecipeHolder<GlyphRecipe> recipe) {}
    private record Group(String spellId, List<Choice> choices) {}
}
