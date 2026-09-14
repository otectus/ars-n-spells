package com.otectus.arsnspells.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.icons.IconCatalog;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Five explicit cosmetic-only presets, stored locally after the player presses Save. */
final class LoomCosmeticPresets {
    static final int COUNT = 5;
    record Preset(String name, String background, String icon) {
        Preset {
            name = name == null ? "" : name.strip().replaceAll("[\\p{Cntrl}]", "");
            name = name.codePoints().limit(40).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
            background = IconCatalog.background(background);
            String canonical = IconCatalog.canonical(icon);
            icon = canonical == null ? IconCatalog.DEFAULT : canonical;
        }
    }
    private final Preset[] slots = new Preset[COUNT];
    private final Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("config/ars_n_spells-loom-presets.json");
    private boolean invalidFile;
    LoomCosmeticPresets() {
        try {
            if (!Files.exists(file)) return;
            if (Files.size(file) > 65536) { invalidFile = true; return; }
            var root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("schema_version") || root.get("schema_version").getAsInt() != 1) { invalidFile = true; return; }
            JsonArray entries = root.getAsJsonArray("presets");
            for (int i = 0; i < Math.min(COUNT, entries.size()); i++) {
                if (!entries.get(i).isJsonObject()) continue;
                JsonObject value = entries.get(i).getAsJsonObject();
                slots[i] = new Preset(value.get("name").getAsString(), value.get("background").getAsString(), value.get("icon").getAsString());
            }
        } catch (RuntimeException | java.io.IOException invalid) { invalidFile = true; }
    }
    Preset get(int index) { return slots[index]; }
    boolean save(int index, Preset value) {
        if (invalidFile) return false; // Preserve malformed/future user files instead of overwriting them.
        Preset previous = slots[index]; slots[index] = value;
        JsonArray entries = new JsonArray();
        for (Preset preset : slots) {
            if (preset == null) { entries.add(com.google.gson.JsonNull.INSTANCE); continue; }
            JsonObject entry = new JsonObject();
            entry.addProperty("name", preset.name()); entry.addProperty("background", preset.background()); entry.addProperty("icon", preset.icon());
            entries.add(entry);
        }
        JsonObject output = new JsonObject(); output.addProperty("schema_version", 1); output.add("presets", entries);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, output.toString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException fallback) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (java.io.IOException failed) { slots[index] = previous; return false; }
    }
}
