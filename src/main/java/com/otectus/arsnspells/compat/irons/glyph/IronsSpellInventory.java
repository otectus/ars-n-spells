package com.otectus.arsnspells.compat.irons.glyph;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Iron's-isolated: what the loaded Iron's spell registry actually contains, in the shape the
 * imported-glyph catalog is generated from and verified against.
 *
 * <p>The shipped catalog ({@code ars_n_spells/iron_glyph_catalog.json}) is generated from
 * these inventories, taken in development runtimes of each pinned Iron's build with default
 * configs, and committed; a GameTest compares it with the registry of whatever Iron's build is
 * loaded. Rarity per level is read through {@link AbstractSpell#getRarity}, so it reflects the
 * loaded rarity configuration, which is what the default Ars tier is derived from.
 */
public final class IronsSpellInventory {
    private IronsSpellInventory() {}

    public record Entry(String spell, String school, String castType, int minLevel, int maxLevel, int minRarity,
                        List<String> rarityByLevel, boolean requiresLearning, boolean enabled, String spellClass,
                        String icon, boolean ansProxy, boolean none) {}

    public static List<Entry> loaded() {
        List<Entry> entries = new ArrayList<>();
        for (AbstractSpell spell : SpellRegistry.REGISTRY.get().getValues()) {
            boolean none = spell == SpellRegistry.none();
            boolean proxy = spell instanceof com.otectus.arsnspells.spell.irons.ArsCrossProxySpell;
            List<String> rarities = new ArrayList<>();
            int max = none ? 0 : spell.getMaxLevel();
            for (int level = 1; level <= max; level++) rarities.add(spell.getRarity(level).name());
            entries.add(new Entry(spell.getSpellResource().toString(),
                none ? "" : spell.getSchoolType().getId().toString(),
                spell.getCastType().name(), none ? 0 : spell.getMinLevel(), max, none ? 0 : spell.getMinRarity(), rarities,
                !none && spell.requiresLearning(), !none && spell.isEnabled(), spell.getClass().getName(),
                spell.getSpellIconResource().toString(), proxy, none));
        }
        entries.sort(Comparator.comparing(Entry::spell));
        return entries;
    }

    /** The loaded inventory as stable, sorted JSON, labelled with the Iron's build it came from. */
    public static String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("irons_spellbooks", ModList.get().getModContainerById("irons_spellbooks")
            .map(c -> c.getModInfo().getVersion().toString()).orElse("absent"));
        JsonArray spells = new JsonArray();
        for (Entry e : loaded()) {
            JsonObject o = new JsonObject();
            o.addProperty("spell", e.spell());
            o.addProperty("school", e.school());
            o.addProperty("cast_type", e.castType());
            o.addProperty("min_level", e.minLevel());
            o.addProperty("max_level", e.maxLevel());
            o.addProperty("min_rarity", e.minRarity());
            JsonArray rarity = new JsonArray();
            e.rarityByLevel().forEach(rarity::add);
            o.add("rarity_by_level", rarity);
            o.addProperty("requires_learning", e.requiresLearning());
            o.addProperty("enabled", e.enabled());
            o.addProperty("class", e.spellClass());
            o.addProperty("icon", e.icon());
            o.addProperty("ans_proxy", e.ansProxy());
            o.addProperty("none", e.none());
            spells.add(o);
        }
        root.add("spells", spells);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }
}
