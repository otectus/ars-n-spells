package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.contract.AnsModifierIds;
import com.otectus.arsnspells.contract.ModifierKeyMapper;
import com.otectus.arsnspells.util.SpellSchoolId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Forge 1.20.1 half of the shared modifier registry (audit V07, V14).
 *
 * <p>{@link AnsModifierIds} owns the modifier keys loader-neutrally, as strings. This class is
 * this loader's one {@link ModifierKeyMapper}: it turns a key into the {@code UUID} that
 * 1.20.1 identifies an {@code AttributeModifier} by. The NeoForge repo maps the same keys onto
 * {@code ResourceLocation}s; neither repo keeps a second literal list.
 *
 * <p>Before this existed, each application site declared its own {@code UUID.fromString(...)}
 * literal and each cleanup site removed whichever literals it happened to know about, so a
 * modifier written by a site the cleanup path had never heard of could never be removed. Every
 * identity the mod writes is reachable from here, and {@link AnsFeatureCleanup} walks all of
 * them.
 *
 * <p>Attribute targets are named as plain {@code namespace:path} strings and resolved through
 * {@code ForgeRegistries} at use time, deliberately: naming Iron's {@code AttributeRegistry}
 * here would make the registry itself unloadable on an Iron's-less server, which is exactly the
 * install where the leftover-modifier cleanup still has to run.
 */
public final class AnsModifierIdentities implements ModifierKeyMapper<UUID> {

    private static final String IRONS = "irons_spellbooks";

    /** Iron's max-mana attribute, the target of both max-mana modifiers. */
    private static final String IRONS_MAX_MANA = IRONS + ":max_mana";

    /** Iron's mana-regen attribute, the target of both regen modifiers. */
    private static final String IRONS_MANA_REGEN = IRONS + ":mana_regen";

    /** The single instance; the mapper is stateless. */
    public static final AnsModifierIdentities MAPPER = new AnsModifierIdentities();

    private static final Map<String, UUID> IDENTITIES;
    private static final Map<String, List<String>> TARGETS;

    static {
        Map<String, UUID> ids = new LinkedHashMap<>();
        ids.put(AnsModifierIds.ARS_GEAR_MAX_MANA,
            UUID.fromString("d3e1f1d1-6b39-4ec7-9a4a-7e6d706a8b9b"));
        ids.put(AnsModifierIds.ARS_GEAR_MANA_REGEN,
            UUID.fromString("0c2c7e6a-44e8-4cc6-9b5d-5a43a0e5f23b"));
        ids.put(AnsModifierIds.ARS_POTION_MAX_MANA,
            UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"));
        ids.put(AnsModifierIds.ARS_POTION_MANA_REGEN,
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
        ids.put(AnsModifierIds.CROSS_MOD_SCHOOL_PROGRESSION,
            UUID.fromString("b0ba11ad-dead-beef-cafe-f00d20245678"));
        IDENTITIES = Collections.unmodifiableMap(ids);

        Map<String, List<String>> targets = new LinkedHashMap<>();
        targets.put(AnsModifierIds.ARS_GEAR_MAX_MANA, List.of(IRONS_MAX_MANA));
        targets.put(AnsModifierIds.ARS_GEAR_MANA_REGEN, List.of(IRONS_MANA_REGEN));
        targets.put(AnsModifierIds.ARS_POTION_MAX_MANA, List.of(IRONS_MAX_MANA));
        targets.put(AnsModifierIds.ARS_POTION_MANA_REGEN, List.of(IRONS_MANA_REGEN));
        // The progression bonus is not one attribute but nine: it lands on whichever school
        // the player cast. Cleanup that walked only the last-cast school left the other eight.
        List<String> schools = new ArrayList<>();
        for (SpellSchoolId school : SpellSchoolId.values()) {
            if (!school.isGeneric()) {
                schools.add(IRONS + ":" + school.id() + "_spell_power");
            }
        }
        targets.put(AnsModifierIds.CROSS_MOD_SCHOOL_PROGRESSION, List.copyOf(schools));
        TARGETS = Collections.unmodifiableMap(targets);
    }

    private AnsModifierIdentities() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>A key that is itself a {@code UUID} in text form - which is how a legacy Forge identity
     * is recorded in the contract - maps to that {@code UUID}. A legacy key that is a display
     * name belongs to the other loader and correctly maps to {@code null} here rather than being
     * guessed into an identity.
     */
    @Override
    public UUID map(String key) {
        if (key == null) {
            return null;
        }
        UUID current = IDENTITIES.get(key);
        if (current != null) {
            return current;
        }
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /** The canonical identity for a current key, or {@code null} if the key is not one. */
    public static UUID uuid(String key) {
        return IDENTITIES.get(key);
    }

    /**
     * Every attribute this key's modifier can sit on, as {@code namespace:path} strings.
     * Cleanup walks this list; nothing else knows where a modifier could be hiding.
     */
    public static List<String> targetAttributesFor(String key) {
        List<String> targets = TARGETS.get(key);
        return targets == null ? List.of() : targets;
    }
}
