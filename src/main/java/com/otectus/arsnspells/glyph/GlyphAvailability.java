package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphLiveSnapshot;
import com.otectus.arsnspells.glyph.behavior.LiveSpellFacts;
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
 *
 * <p>Since 3.4.1 every Iron's fact comes from the server-authoritative live layer
 * ({@link GlyphBehavior}): the effective enabled state, live maximum and school, the same on the
 * server and on every synced client. A status computed before Iron's configuration is ready is
 * {@linkplain Status#provisional() provisional}, and a remote client that has not received the
 * server's facts reports {@link Reason#NOT_SYNCED}, never "unavailable".
 */
public final class GlyphAvailability {
    private GlyphAvailability() {}

    /** Ordinals are not persisted or synced; keys are the localized explanations. */
    public enum Reason {
        AVAILABLE(null),
        DISABLED_BY_SERVER("message.ars_n_spells.glyph.dormant.server_disabled"),
        IRONS_ABSENT("message.ars_n_spells.glyph.dormant.irons_absent"),
        SPELL_MISSING("message.ars_n_spells.glyph.dormant.spell_missing"),
        DISABLED_UPSTREAM("message.ars_n_spells.glyph.dormant.upstream_disabled"),
        /** The base level is above Iron's live maximum for the spell. */
        LEVEL_UNSUPPORTED("message.ars_n_spells.glyph.dormant.level_unsupported"),
        /** The base level is below Iron's live minimum for the spell (a minimum that rose after the level was registered). */
        LEVEL_BELOW_MINIMUM("message.ars_n_spells.glyph.dormant.level_below_minimum"),
        /** The catalog names an adapter contract version newer than this build implements. */
        ADAPTER_TOO_NEW("message.ars_n_spells.glyph.dormant.adapter_too_new"),
        DISABLED_BY_DATAPACK("message.ars_n_spells.glyph.dormant.datapack_disabled"),
        /** A remote client that has not received the server's live facts yet. */
        NOT_SYNCED("message.ars_n_spells.glyph.dormant.not_synced"),
        /** The catalog keeps this identity registered but its adapter is retired. */
        RETIRED("message.ars_n_spells.glyph.dormant.retired"),
        /** 3.4.1 review SAVE-01: only ANS's retained copy keeps this identity registered; its catalog package is not installed. */
        PACKAGE_MISSING("message.ars_n_spells.glyph.dormant.package_missing"),
        /** The catalog registers this identity but has no execution support for it. */
        UNSUPPORTED("message.ars_n_spells.glyph.dormant.unsupported");

        private final String key;

        Reason(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /**
     * @param provisional judged on catalog defaults because Iron's configuration is not built yet;
     *                    nothing final (cancelling a job, refusing for good) may rest on it
     * @param liveMax     Iron's effective maximum level for the spell, for the explanation
     */
    public record Status(Reason reason, boolean provisional, int liveMax) {
        public boolean available() {
            return reason == Reason.AVAILABLE;
        }

        public Component message(ImportedGlyphDefinition definition) {
            if (available()) return Component.empty();
            String name = definition.spellName() + " " + definition.levelNumeral();
            return switch (reason) {
                case LEVEL_UNSUPPORTED, LEVEL_BELOW_MINIMUM -> Component.translatable(reason.key(), name, liveMax);
                case ADAPTER_TOO_NEW -> Component.translatable(reason.key(), name, definition.adapterVersion(),
                    com.otectus.arsnspells.glyph.GlyphFamily.ADAPTER_VERSION);
                default -> Component.translatable(reason.key(), name);
            };
        }
    }

    public static Status status(ImportedGlyphDefinition definition) {
        GlyphLiveSnapshot live = GlyphBehavior.live();
        // 3.4.1 review SYNC-01: a remote client takes the feature switch from the server's snapshot, which follows
        // a server-side change; its own copy of the server config is only what it was sent at login.
        boolean enabled = live.received() ? live.glyphsEnabled() : AnsConfig.ironGlyphsEnabled();
        return decide(definition, enabled, IronGlyphBridges.get().present(), live, GlyphMappingSnapshot.current());
    }

    /** The decision itself, from explicit inputs: pure, so every rule is unit-tested without a server. */
    public static Status decide(ImportedGlyphDefinition definition, boolean glyphsEnabled, boolean ironsPresent,
                                GlyphLiveSnapshot live, GlyphMappingSnapshot mappings) {
        if (!glyphsEnabled) return new Status(Reason.DISABLED_BY_SERVER, false, 0);
        if (definition.retired())
            return new Status(definition.source().startsWith(RetainedCatalogs.SOURCE_PREFIX) ? Reason.PACKAGE_MISSING : Reason.RETIRED, false, 0);
        if (!definition.supported()) return new Status(Reason.UNSUPPORTED, false, 0);
        if (!definition.executable()) return new Status(Reason.ADAPTER_TOO_NEW, false, 0);
        if (!ironsPresent) return new Status(Reason.IRONS_ABSENT, false, 0);
        if (live.readiness() == GlyphLiveSnapshot.Readiness.NOT_SYNCED) return new Status(Reason.NOT_SYNCED, true, 0);
        boolean provisional = live.readiness() != GlyphLiveSnapshot.Readiness.READY;
        LiveSpellFacts facts = live.facts(definition.spell());
        if (facts == null) facts = GlyphBehavior.facts(definition);
        if (!facts.registered()) return new Status(Reason.SPELL_MISSING, provisional, 0);
        if (!facts.enabled()) return new Status(Reason.DISABLED_UPSTREAM, provisional, facts.maxLevel());
        if (definition.level() > facts.maxLevel()) return new Status(Reason.LEVEL_UNSUPPORTED, provisional, facts.maxLevel());
        if (definition.level() < facts.minLevel()) return new Status(Reason.LEVEL_BELOW_MINIMUM, provisional, facts.minLevel());
        if (!mappings.allows(definition, facts.school()))
            return new Status(Reason.DISABLED_BY_DATAPACK, provisional, facts.maxLevel());
        return new Status(Reason.AVAILABLE, provisional, facts.maxLevel());
    }

    /**
     * The glyph's Ars tier: a datapack override, else Iron's live rarity for this level, else the
     * catalog default. Common is tier I, Uncommon and Rare tier II, Epic and Legendary tier III.
     */
    public static int tier(ImportedGlyphDefinition definition) {
        Integer override = GlyphMappingSnapshot.current().tierOverride(definition);
        if (override != null) return override;
        String rarity = GlyphBehavior.facts(definition).rarity(definition.level());
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
