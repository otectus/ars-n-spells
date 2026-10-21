package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.glyph.catalog.SpellInventoryEntry;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads the loaded Iron's spell registry into plain {@link SpellInventoryEntry} records.
 *
 * <p>Iron's-isolated: reached only through {@link IronGlyphBridgeImpl}. Every value comes from
 * Iron's own API at the moment of the call, so the result reflects the server's current spell
 * config (maximum levels, rarity weights, disables), which is what the catalog check compares
 * against the shipped startup catalog.
 */
public final class IronsSpellInventory {

    private IronsSpellInventory() {
    }

    public static List<SpellInventoryEntry> snapshot() {
        List<SpellInventoryEntry> out = new ArrayList<>();
        AbstractSpell none = SpellRegistry.none();
        for (AbstractSpell spell : SpellRegistry.REGISTRY) {
            out.add(entry(spell, spell == none));
        }
        out.sort(Comparator.comparing(SpellInventoryEntry::spellId));
        return out;
    }

    static SpellInventoryEntry entry(AbstractSpell spell, boolean isNone) {
        String id = spell.getSpellId();
        int min = Math.max(1, spell.getMinLevel());
        int max = Math.max(min, spell.getMaxLevel());
        List<String> rarities = new ArrayList<>();
        List<Integer> costs = new ArrayList<>();
        List<Integer> castTimes = new ArrayList<>();
        for (int level = min; level <= max; level++) {
            rarities.add(isNone ? "COMMON" : spell.getRarity(level).name());
            costs.add(spell.getManaCost(level));
            castTimes.add(spell.getCastTime(level));
        }
        String school = isNone || spell.getSchoolType() == null ? "" : spell.getSchoolType().getId().toString();
        String name = Component.translatable(spell.getComponentId()).getString();
        if (name.equals(spell.getComponentId())) {
            name = titleCase(spell.getSpellName());
        }
        return new SpellInventoryEntry(id, school, spell.getCastType().name(), min, max, rarities, costs, castTimes,
            spell.getSpellCooldown(), isNone ? 0 : spell.getRecastCount(max, null), spell.requiresLearning(),
            spell.isEnabled(), spell.allowCrafting(), spell.allowLooting(), isNone,
            CrossModSpellComponents.isArsCrossProxyId(id), name);
    }

    static String titleCase(String path) {
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.toString();
    }
}
