package com.otectus.arsnspells.glyph.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * What the loaded Iron's registry says about one spell, read through the optional Iron's bridge.
 *
 * <p>Plain data with no Iron's types, so the catalog check, the coverage report and the GameTest
 * that regenerates the shipped catalog can all use it without linking Iron's classes.
 *
 * @param rarityByLevel Iron's rarity name for each level from {@code minLevel} to {@code maxLevel}
 * @param manaCostByLevel Iron's mana cost for each level, same indexing
 * @param castTimeByLevel Iron's base cast time in ticks for each level, same indexing
 */
public record SpellInventoryEntry(String spellId, String school, String castType, int minLevel, int maxLevel,
                                  List<String> rarityByLevel, List<Integer> manaCostByLevel,
                                  List<Integer> castTimeByLevel, int cooldownTicks, int recastCountAtMax,
                                  boolean requiresLearning, boolean enabled, boolean allowCrafting,
                                  boolean allowLooting, boolean none, boolean ansProxy, String displayName) {

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("spell", spellId);
        o.addProperty("name", displayName);
        o.addProperty("school", school);
        o.addProperty("cast_type", castType);
        o.addProperty("min_level", minLevel);
        o.addProperty("max_level", maxLevel);
        o.add("rarity", strings(rarityByLevel));
        o.add("mana_cost", ints(manaCostByLevel));
        o.add("cast_time", ints(castTimeByLevel));
        o.addProperty("cooldown_ticks", cooldownTicks);
        o.addProperty("recast_count_at_max", recastCountAtMax);
        o.addProperty("requires_learning", requiresLearning);
        o.addProperty("enabled", enabled);
        o.addProperty("allow_crafting", allowCrafting);
        o.addProperty("allow_looting", allowLooting);
        o.addProperty("none", none);
        o.addProperty("ans_proxy", ansProxy);
        return o;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray a = new JsonArray();
        values.forEach(a::add);
        return a;
    }

    private static JsonArray ints(List<Integer> values) {
        JsonArray a = new JsonArray();
        values.forEach(a::add);
        return a;
    }
}
