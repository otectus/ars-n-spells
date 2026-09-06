package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.CompatIds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the identity of every upstream mod a profile may load, so a run cannot quietly resolve
 * a different artifact than the one the suite was written against.
 *
 * <p><b>Why this is first.</b> Every other Iron's- or addon-facing test in this package reads
 * as a statement about a specific upstream version: a CurseForge file id that silently moves,
 * or a maven range that resolves upward, turns the whole suite into a claim about software
 * nobody checked. These tests fail loudly on drift rather than letting the run continue and
 * report a green that means nothing.
 *
 * <p><b>Declared, not resolved.</b> The version asserted is the one the mod's own
 * {@code neoforge.mods.toml} declares, which is not always the artifact coordinate: Ars Nouveau
 * ships from {@code ars_nouveau-1.21.1-5.13.1.1400.jar} but declares {@code 5.13.1}. So a
 * declared version is accepted when it equals the pinned identity or is a dot-boundary prefix
 * of it - {@code 5.13.1} matches {@code 5.13.1.1400}, {@code 5.13.2} does not.
 *
 * <p>Absent mods go through {@link OptionalModGate}, so an unpinnable profile is reported as a
 * skip rather than a pass.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ProfileIdentityGameTests {

    /**
     * The upstream versions the 3.3.0 audit was carried out against. Iron's and Ars Nouveau
     * also appear in {@code gradle.properties} as dependency coordinates; the addon profiles
     * are pinned there by CurseForge file id, which carries no version in it, so the four
     * expectations are written out here where the assertion lives.
     */
    private static final String ARS_NOUVEAU = "5.13.1.1400";
    private static final String IRONS_SPELLBOOKS = "1.21.1-3.16.3";
    private static final String ARS_ELEMENTAL = "0.7.10.1";
    private static final String ARS_ELEMANCY = "1.18.3";

    private ProfileIdentityGameTests() {}

    private static void assertIdentity(GameTestHelper helper, String modId, String pinned) {
        if (OptionalModGate.skipIfAbsent(helper, modId)) {
            return;
        }
        String declared = ModList.get().getModContainerById(modId)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse(null);
        if (declared == null) {
            helper.fail(modId + " reports as loaded but has no mod container; the profile is "
                + "in a state no test below can be trusted against");
            return;
        }
        if (!declared.equals(pinned) && !pinned.startsWith(declared + ".")) {
            helper.fail(modId + " is " + declared + ", but this suite was audited against "
                + pinned + ". Re-verify the affected tests against the new version and move "
                + "the pin, rather than reading this run as a pass.");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_profile_identity")
    public static void profile_arsNouveauMatchesThePinnedIdentity(GameTestHelper helper) {
        assertIdentity(helper, CompatIds.ARS_NOUVEAU, ARS_NOUVEAU);
    }

    @GameTest(template = "platform", batch = "ans_profile_identity")
    public static void profile_ironsSpellbooksMatchesThePinnedIdentity(GameTestHelper helper) {
        assertIdentity(helper, CompatIds.IRONS_SPELLBOOKS, IRONS_SPELLBOOKS);
    }

    @GameTest(template = "platform", batch = "ans_profile_identity")
    public static void profile_arsElementalMatchesThePinnedIdentity(GameTestHelper helper) {
        assertIdentity(helper, CompatIds.ARS_ELEMENTAL, ARS_ELEMENTAL);
    }

    @GameTest(template = "platform", batch = "ans_profile_identity")
    public static void profile_arsElemancyMatchesThePinnedIdentity(GameTestHelper helper) {
        assertIdentity(helper, CompatIds.ARS_ELEMANCY, ARS_ELEMANCY);
    }
}
