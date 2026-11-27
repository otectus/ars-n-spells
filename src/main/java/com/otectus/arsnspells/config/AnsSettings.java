package com.otectus.arsnspells.config;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The world settings the in-game screen edits and every client is told about (3.4.1 review CONFIG-01).
 *
 * <p>One list of keys, used by the screen, the apply service, the command feedback and the sync packet,
 * so every route names a setting the same way the TOML file does. Values travel as strings and are
 * validated against the key's type before anything is written; a request is applied whole or not at all.
 */
public final class AnsSettings {
    /** A key's type and its config value. */
    public enum Kind { BOOLEAN, MODE, COOLDOWN_TICKS }

    public record Setting(String key, Kind kind) {}

    public static final String MODE = "mana_unification_mode";
    public static final List<Setting> SETTINGS = List.of(
        new Setting("enable_mana_unification", Kind.BOOLEAN),
        new Setting(MODE, Kind.MODE),
        new Setting("enable_resonance_system", Kind.BOOLEAN),
        new Setting("enable_cooldown_system", Kind.BOOLEAN),
        new Setting("enable_progression_system", Kind.BOOLEAN),
        new Setting("enable_affinity_system", Kind.BOOLEAN),
        new Setting("respect_armor_bonuses", Kind.BOOLEAN),
        new Setting("respect_enchantments", Kind.BOOLEAN),
        new Setting("enable_source_jar_synergy", Kind.BOOLEAN),
        new Setting("inscribed_ars_default_cooldown_ticks", Kind.COOLDOWN_TICKS),
        new Setting("debug_mode", Kind.BOOLEAN));
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_STRING = 64;

    private AnsSettings() {}

    public static Setting setting(String key) {
        for (Setting setting : SETTINGS) if (setting.key().equals(key)) return setting;
        return null;
    }

    /** The server-side config value behind {@code key}. */
    @SuppressWarnings("unchecked")
    static ModConfigSpec.ConfigValue<Object> value(String key) {
        Object value = switch (key) {
            case "enable_mana_unification" -> AnsConfig.ENABLE_MANA_UNIFICATION;
            case MODE -> AnsConfig.MANA_UNIFICATION_MODE;
            case "enable_resonance_system" -> AnsConfig.ENABLE_RESONANCE_SYSTEM;
            case "enable_cooldown_system" -> AnsConfig.ENABLE_COOLDOWN_SYSTEM;
            case "enable_progression_system" -> AnsConfig.ENABLE_PROGRESSION_SYSTEM;
            case "enable_affinity_system" -> AnsConfig.ENABLE_AFFINITY_SYSTEM;
            case "respect_armor_bonuses" -> AnsConfig.respectArmorBonuses;
            case "respect_enchantments" -> AnsConfig.respectEnchantments;
            case "enable_source_jar_synergy" -> AnsConfig.ENABLE_SOURCE_JAR_SYNERGY;
            case "inscribed_ars_default_cooldown_ticks" -> AnsConfig.INSCRIBED_ARS_DEFAULT_COOLDOWN_TICKS;
            case "debug_mode" -> AnsConfig.DEBUG_MODE;
            default -> null;
        };
        return (ModConfigSpec.ConfigValue<Object>) value;
    }

    /** {@code raw} as the typed value for {@code setting}, or null when it is not a valid value. */
    static Object parse(Setting setting, String raw) {
        if (raw == null || raw.length() > MAX_STRING) return null;
        return switch (setting.kind()) {
            case BOOLEAN -> "true".equals(raw) ? Boolean.TRUE : "false".equals(raw) ? Boolean.FALSE : null;
            case MODE -> {
                for (ManaUnificationMode mode : ManaUnificationMode.values())
                    if (mode.getConfigName().equals(raw.toLowerCase(Locale.ROOT))) yield mode.getConfigName();
                yield null;
            }
            case COOLDOWN_TICKS -> {
                try {
                    int ticks = Integer.parseInt(raw);
                    yield ticks >= 0 && ticks <= 12000 ? ticks : null;
                } catch (NumberFormatException invalid) {
                    yield null;
                }
            }
        };
    }

    /** What the server's settings are now, as strings, in {@link #SETTINGS} order. */
    public static Map<String, String> current() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Setting setting : SETTINGS) out.put(setting.key(), String.valueOf(value(setting.key()).get()));
        return out;
    }

    /**
     * Every SERVER setting the client's own presentation reads (audit ANS-007), as one bounded,
     * immutable value: the Hybrid bar choice, hide-with-ring, and the two ring resource toggles that
     * decide whether a ring actually replaces mana. Display-only: receiving these never makes them
     * editable from the client, and the client never writes them into its config.
     */
    public record ClientPolicy(String hybridManaBar, boolean hideManaBarWithRing, boolean lpSystem, boolean virtueAuraSystem) {
        /** The declared defaults, until a server has said otherwise. */
        public static final ClientPolicy DEFAULTS = new ClientPolicy("irons", true, true, true);

        public ClientPolicy {
            hybridManaBar = "irons".equalsIgnoreCase(hybridManaBar) ? "irons" : "ars";
        }

        /**
         * The server's values now. The ring settings belong to the Covenant of the Seven integration,
         * which has no 1.21.1 build: no ring replaces mana here, so they are sent as off.
         */
        public static ClientPolicy current() {
            return new ClientPolicy(AnsConfig.HYBRID_MANA_BAR.get(), false, false, false);
        }

        public boolean hybridShowsIrons() {
            return "irons".equals(hybridManaBar);
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(hybridManaBar, 8);
            buffer.writeBoolean(hideManaBarWithRing);
            buffer.writeBoolean(lpSystem);
            buffer.writeBoolean(virtueAuraSystem);
        }

        public static ClientPolicy read(FriendlyByteBuf buffer) {
            return new ClientPolicy(buffer.readUtf(8), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
        }
    }

    /**
     * What one client is told: every setting, the effective mode (what the routing runs after Iron's
     * presence and the master switch), whether Iron's is installed, whether this player may edit, and
     * the presentation policy the client's HUD follows (audit ANS-007).
     */
    public record Snapshot(Map<String, String> values, String effectiveMode, boolean ironsPresent, boolean canEdit,
                           String resultKey, String resultDetail, ClientPolicy policy) {
        public Snapshot {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            resultKey = resultKey == null ? "" : resultKey;
            resultDetail = resultDetail == null ? "" : resultDetail;
            policy = policy == null ? ClientPolicy.DEFAULTS : policy;
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(values.size());
            values.forEach((key, value) -> {
                buffer.writeUtf(key, MAX_STRING);
                buffer.writeUtf(value, MAX_STRING);
            });
            buffer.writeUtf(effectiveMode, MAX_STRING);
            buffer.writeBoolean(ironsPresent);
            buffer.writeBoolean(canEdit);
            buffer.writeUtf(resultKey, 128);
            buffer.writeUtf(resultDetail, 256);
            policy.write(buffer);
        }

        public static Snapshot read(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("settings snapshot lists " + size + " entries");
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) values.put(buffer.readUtf(MAX_STRING), buffer.readUtf(MAX_STRING));
            return new Snapshot(values, buffer.readUtf(MAX_STRING), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readUtf(128), buffer.readUtf(256), ClientPolicy.read(buffer));
        }
    }

    /** A requested change set, bounded on the wire. */
    public static Map<String, String> readRequest(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("settings request lists " + size + " entries");
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) out.put(buffer.readUtf(MAX_STRING), buffer.readUtf(MAX_STRING));
        return out;
    }

    public static void writeRequest(FriendlyByteBuf buffer, Map<String, String> request) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(request.entrySet());
        buffer.writeVarInt(Math.min(entries.size(), MAX_ENTRIES));
        for (int i = 0; i < Math.min(entries.size(), MAX_ENTRIES); i++) {
            buffer.writeUtf(entries.get(i).getKey(), MAX_STRING);
            buffer.writeUtf(entries.get(i).getValue(), MAX_STRING);
        }
    }
}
