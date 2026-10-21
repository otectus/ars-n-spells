package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import net.minecraft.network.chat.Component;

/**
 * Whether an imported glyph may be used right now, and why not.
 *
 * <p>Availability never changes registration: a dormant glyph is still registered, still known
 * to players who learned it and still stored in their spells. It is hidden from the editor, its
 * glyph item will not teach it, and a cast that contains it is refused with this reason before
 * anything is paid. Checks run cheapest first and every one is a map lookup, because Ars asks
 * {@code isEnabled()} from editor rendering and validation, not only at cast time.
 */
public final class GlyphAvailability {
    private GlyphAvailability() {}

    public enum Reason {
        AVAILABLE(null),
        DISABLED_BY_SERVER("message.ars_n_spells.glyph.dormant.server_disabled"),
        IRONS_ABSENT("message.ars_n_spells.glyph.dormant.irons_absent"),
        SPELL_MISSING("message.ars_n_spells.glyph.dormant.spell_missing"),
        DISABLED_UPSTREAM("message.ars_n_spells.glyph.dormant.upstream_disabled"),
        LEVEL_UNSUPPORTED("message.ars_n_spells.glyph.dormant.level_unsupported"),
        DISABLED_BY_DATAPACK("message.ars_n_spells.glyph.dormant.datapack_disabled");

        private final String key;

        Reason(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public record Status(Reason reason) {
        public boolean available() {
            return reason == Reason.AVAILABLE;
        }

        public Component message(ImportedGlyphDefinition definition) {
            return available() ? Component.empty()
                : Component.translatable(reason.key(), definition.spellName() + " " + definition.levelNumeral());
        }
    }

    private static final Status AVAILABLE = new Status(Reason.AVAILABLE);

    public static Status status(ImportedGlyphDefinition definition) {
        if (!AnsConfig.ironGlyphsEnabled()) return new Status(Reason.DISABLED_BY_SERVER);
        IronGlyphBridge bridge = IronGlyphBridges.get();
        if (!bridge.present()) return new Status(Reason.IRONS_ABSENT);
        IronGlyphBridge.SpellState state = bridge.spellState(definition.spell());
        if (state == IronGlyphBridge.SpellState.MISSING) return new Status(Reason.SPELL_MISSING);
        if (state == IronGlyphBridge.SpellState.DISABLED) return new Status(Reason.DISABLED_UPSTREAM);
        if (bridge.rarity(definition.spell(), definition.level()) == null) return new Status(Reason.LEVEL_UNSUPPORTED);
        if (!GlyphMappingSnapshot.current().allows(definition)) return new Status(Reason.DISABLED_BY_DATAPACK);
        return AVAILABLE;
    }

    /**
     * The glyph's Ars tier: a datapack override, else Iron's live rarity for this level, else the
     * catalog default. Common is tier I, Uncommon and Rare tier II, Epic and Legendary tier III.
     */
    public static int tier(ImportedGlyphDefinition definition) {
        Integer override = GlyphMappingSnapshot.current().tierOverride(definition);
        if (override != null) return override;
        String rarity = IronGlyphBridges.get().rarity(definition.spell(), definition.level());
        return rarity == null ? definition.defaultTier() : tierOfRarity(rarity);
    }

    public static int tierOfRarity(String rarity) {
        return switch (rarity) {
            case "COMMON" -> 1;
            case "UNCOMMON", "RARE" -> 2;
            default -> 3;
        };
    }
}
