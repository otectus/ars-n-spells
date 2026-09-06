package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.contract.AnsModifierIds;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V07/V14 preflight - every ANS-owned modifier identity must be reachable from the
 * shared registry, or cleanup cannot find it.
 *
 * <p>The regression this pins: each site that applied an attribute modifier declared its own
 * {@code UUID.fromString(...)} literal, and cleanup removed only the literals whichever
 * cleanup method happened to know about. A modifier applied by a site the cleanup path had
 * never heard of stayed on the player forever. The contract owns the keys; this loader owns
 * exactly one map from key to {@code UUID}, and {@link AnsModifierIdentities} is it.
 */
class AnsModifierIdentitiesTest {

    /** The five identities the mod actually writes, read off the application sites. */
    private static final Set<UUID> LIVE_IDENTITIES = Set.of(
        UUID.fromString("d3e1f1d1-6b39-4ec7-9a4a-7e6d706a8b9b"),  // EquipmentIntegration max mana
        UUID.fromString("0c2c7e6a-44e8-4cc6-9b5d-5a43a0e5f23b"),  // EquipmentIntegration regen
        UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"),  // MixinArsPotionEffects max mana
        UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),  // MixinArsPotionEffects regen
        UUID.fromString("b0ba11ad-dead-beef-cafe-f00d20245678")); // ProgressionAttributes

    @Test
    void everyContractKeyMapsToAUuid() {
        for (String key : AnsModifierIds.allKeys()) {
            assertNotNull(AnsModifierIdentities.MAPPER.map(key),
                "contract key " + key + " has no UUID on the Forge loader");
        }
    }

    @Test
    void theRegistryCoversEveryIdentityTheModActuallyWrites() {
        Set<UUID> mapped = new HashSet<>();
        for (String key : AnsModifierIds.allKeys()) {
            for (String candidate : AnsModifierIds.currentAndLegacyKeysFor(key)) {
                UUID id = AnsModifierIdentities.MAPPER.map(candidate);
                if (id != null) {
                    mapped.add(id);
                }
            }
        }
        for (UUID live : LIVE_IDENTITIES) {
            assertTrue(mapped.contains(live),
                "identity " + live + " is written by the mod but unknown to the registry");
        }
    }

    @Test
    void aLegacyKeyWithNoUuidFormMapsToNull() {
        // The display-name legacy keys exist for the ResourceLocation loader. On Forge they
        // are not identities at all, and must not be guessed into one.
        assertNull(AnsModifierIdentities.MAPPER.map("Ars Gear Max Mana"));
    }

    @Test
    void everyKeyNamesAtLeastOneTargetAttribute() {
        for (String key : AnsModifierIds.allKeys()) {
            List<String> targets = AnsModifierIdentities.targetAttributesFor(key);
            assertTrue(!targets.isEmpty(), "no target attribute declared for " + key);
        }
    }

    @Test
    void theProgressionKeyNamesEverySchoolPowerAttribute() {
        List<String> targets =
            AnsModifierIdentities.targetAttributesFor(AnsModifierIds.CROSS_MOD_SCHOOL_PROGRESSION);
        // Nine non-generic schools, each with its own Iron's spell-power attribute. Cleanup
        // that walked only the school the player last cast would leave the other eight.
        assertEquals(9, targets.size());
        assertTrue(targets.contains("irons_spellbooks:fire_spell_power"));
        assertTrue(targets.contains("irons_spellbooks:eldritch_spell_power"));
    }
}
