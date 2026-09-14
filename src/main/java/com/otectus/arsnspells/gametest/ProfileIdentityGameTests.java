package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the identity of the profile the rest of the suite is asserting against.
 *
 * <p><b>Why.</b> Every cross-mod expectation below is written against one specific build of one
 * specific mod — "Iron's writes the modern container", "Ars Elemental declares its schools in
 * metadata". Swap the jar and those tests keep reporting green while measuring something else.
 * A version drift must fail here, loudly and once, rather than turn a downstream scenario into a
 * mystery. Absent mods are not asserted: they go through {@link OptionalModGate}, which records
 * the skip so {@link ScenarioReport} can say so.
 *
 * <p>The pins are the versions the 3.3.0 audit ran against. Bump them deliberately, together with
 * the coordinates in {@code gradle.properties}, never to make a failing run pass.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ProfileIdentityGameTests {

    private static final String ARS_NOUVEAU = "ars_nouveau";
    private static final String ARS_ELEMENTAL = "ars_elemental";

    private static final String ARS_NOUVEAU_PINNED = "4.12.7";
    private static final String IRONS_PINNED = "1.20.1-3.15.0";
    private static final String ARS_ELEMENTAL_PINNED = "0.6.8.0";

    private ProfileIdentityGameTests() {}

    @GameTest(template = "platform", batch = "ans_profile_identity")
    public static void arsNouveau_isThePinnedAuditVersion(GameTestHelper helper) {
        // This required dependency is never an optional pass/skip.
        ScenarioReport.executed("ProfileIdentityGameTests#arsNouveau_isThePinnedAuditVersion");
        assertVersion(helper, ARS_NOUVEAU, ARS_NOUVEAU_PINNED);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_profile_identity")
    public static void requestedProfile_matchesRuntimeMods(GameTestHelper helper) {
        for (String modId : new String[] {IronsCompat.MODID, ARS_ELEMENTAL, "ars_zero", "toomanyglyphs", "covenant_of_the_seven"}) {
            String expectedValue = System.getProperty("ans.gametest.expected." + modId);
            if (!"true".equals(expectedValue) && !"false".equals(expectedValue)) {
                helper.fail("Missing expected profile property for " + modId
                    + "; run the Gradle runGameTestServer target so absent jars cannot silently pass");
            }
            boolean expected = Boolean.parseBoolean(expectedValue);
            boolean actual = ModList.get().isLoaded(modId);
            if (actual != expected) {
                helper.fail("Profile requires " + modId + (expected ? " present" : " absent")
                    + ", but runtime reports " + loadedVersion(modId));
            }
            ArsNSpells.LOGGER.info("ANS-PROFILE mod={} expected={} version={}", modId,
                expected ? "present" : "absent", loadedVersion(modId));
        }
        String covenantMods = System.getProperty("ans.gametest.covenant.mods", "");
        if (!covenantMods.isEmpty()) {
            for (String modId : covenantMods.split(",")) {
                assertVersion(helper, modId, System.getProperty("ans.gametest.covenant.version." + modId, "<unresolved>"));
                if (!"covenant_of_the_seven".equals(modId)) ArsNSpells.LOGGER.info(
                    "ANS-PROFILE mod={} expected=present version={}", modId, loadedVersion(modId));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_profile_identity")
    public static void ironsSpellbooks_isThePinnedAuditVersion(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        assertVersion(helper, IronsCompat.MODID, IRONS_PINNED);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_profile_identity")
    public static void arsElemental_isThePinnedAuditVersion(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, ARS_ELEMENTAL)) {
            return;
        }
        assertVersion(helper, ARS_ELEMENTAL, ARS_ELEMENTAL_PINNED);
        helper.succeed();
    }

    /** Fail unless the loaded {@code modId} reports exactly {@code pinned}. */
    private static void assertVersion(GameTestHelper helper, String modId, String pinned) {
        String actual = loadedVersion(modId);
        if (!pinned.equals(actual)) {
            helper.fail("this suite is written against " + modId + " " + pinned + ", but the run "
                + "classpath carries " + actual + "; the cross-mod assertions below are no longer "
                + "known to be measuring what they claim");
        }
    }

    /** The version string of {@code modId}, or {@code "<absent>"}. */
    private static String loadedVersion(String modId) {
        for (IModInfo info : ModList.get().getMods()) {
            if (modId.equals(info.getModId())) {
                return info.getVersion().toString();
            }
        }
        return "<absent>";
    }
}
