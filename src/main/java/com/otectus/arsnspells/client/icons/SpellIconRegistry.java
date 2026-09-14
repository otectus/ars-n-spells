package com.otectus.arsnspells.client.icons;

import com.mojang.blaze3d.platform.NativeImage;
import com.otectus.arsnspells.icons.IconCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resource-aware, reloadable artwork. Nothing in the server catalog loads this class. */
public final class SpellIconRegistry implements ResourceManagerReloadListener {
    public static final SpellIconRegistry INSTANCE = new SpellIconRegistry();
    private static final int CAPACITY = 512;
    private final Map<String, ResourceLocation> composites = new LinkedHashMap<>(32, .75F, true);
    private ResourceLocation emergency;
    private int generation;

    private SpellIconRegistry() {}

    public ResourceLocation resolve(String selected, String school) {
        return resolve(selected, school, "normal");
    }

    public synchronized ResourceLocation resolve(String selected, String background, String state) {
        if (!java.util.List.of("normal", "selected", "disabled", "high_contrast", "monochrome").contains(state)) state = "normal";
        String id = IconCatalog.canonical(selected);
        if (id == null) id = IconCatalog.canonical("school/" + background);
        if (id == null) id = IconCatalog.DEFAULT;
        String frame = IconCatalog.background(background);
        String key = id + ":" + frame + ":" + state;
        ResourceLocation hit = composites.get(key);
        if (hit != null) return hit;
        // The same composition is used by the Loom and native ResourceLocation-only
        // wheel API. HD resource packs are sampled using their real dimensions.
        NativeImage glyph = read(texture(id, state));
        if (glyph == null && !state.equals("normal")) glyph = read(texture(id, "normal"));
        if (glyph == null) {
            String school = IconCatalog.canonical("school/" + background);
            if (school != null) glyph = read(texture(school, "normal"));
        }
        if (glyph == null) glyph = read(texture(IconCatalog.DEFAULT, "normal"));
        if (glyph == null) return emergency();
        NativeImage backdrop = frame.equals("none") ? null : read(new ResourceLocation("ars_n_spells",
            "textures/gui/icons/v2/background/" + frame + ".png"));
        NativeImage result = new NativeImage(16, 16, true);
        try {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int under = backdrop == null ? 0 : sample(backdrop, x, y);
                result.setPixelRGBA(x, y, blend(sample(glyph, x, y), under));
            }
        } finally {
            glyph.close();
            if (backdrop != null) backdrop.close();
        }
        ResourceLocation location = Minecraft.getInstance().getTextureManager().register(
            "ans_icon_" + generation + "_" + id.replace('/', '_') + "_" + frame + "_" + state,
            new DynamicTexture(result));
        composites.put(key, location);
        while (composites.size() > CAPACITY) {
            String eldest = composites.keySet().iterator().next();
            Minecraft.getInstance().getTextureManager().release(composites.remove(eldest));
        }
        return location;
    }

    private static int sample(NativeImage image, int x, int y) {
        return image.getPixelRGBA(x * image.getWidth() / 16, y * image.getHeight() / 16);
    }

    /** NativeImage pixels are ABGR. Straight-alpha composition preserves resource-pack alpha. */
    static int blend(int top, int bottom) {
        int a = top >>> 24, b = bottom >>> 24;
        int alpha = a + (b * (255 - a) + 127) / 255;
        if (alpha == 0) return 0;
        int result = alpha << 24;
        for (int shift = 0; shift < 24; shift += 8) {
            int t = (top >>> shift) & 255, u = (bottom >>> shift) & 255;
            int channel = (t * a + (u * b * (255 - a) + 127) / 255 + alpha / 2) / alpha;
            result |= Math.min(255, channel) << shift;
        }
        return result;
    }

    private static ResourceLocation texture(String id, String state) {
        return new ResourceLocation("ars_n_spells", "textures/gui/icons/v2/"
            + (state.equals("normal") ? "" : "variants/" + state + "/") + id + ".png");
    }

    private static NativeImage read(ResourceLocation id) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (var stream = resource.get().open()) {
            NativeImage image = NativeImage.read(stream);
            // Resource packs may be malformed. Never sample an empty or unbounded canvas.
            if (image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 512 || image.getHeight() > 512) {
                image.close(); return null;
            }
            return image;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private ResourceLocation emergency() {
        if (emergency == null) {
            NativeImage image = new NativeImage(16, 16, true);
            for (int y = 2; y <= 13; y++) for (int x = 2; x <= 13; x++) {
                int distance = Math.abs(x - 8) + Math.abs(y - 8);
                if (distance >= 4 && distance <= 6) image.setPixelRGBA(x, y, distance == 5 ? 0xFFFFFFFF : 0xFF241A18);
            }
            emergency = Minecraft.getInstance().getTextureManager().register("ans_icon_emergency", new DynamicTexture(image));
        }
        return emergency;
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager resources) {
        for (ResourceLocation location : composites.values()) Minecraft.getInstance().getTextureManager().release(location);
        composites.clear();
        if (emergency != null) Minecraft.getInstance().getTextureManager().release(emergency);
        emergency = null;
        generation++;
    }
}
