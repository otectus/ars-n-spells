package com.otectus.arsnspells.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real native school registry and native-container conservation, gated by runtime presence. */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class SchoolInscriptionNativeGameTests {
    @GameTest(template = "platform", batch = "ans_loom")
    public static void nativeBookBindingRestoresOriginalSpellsNameAndCapacity(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, "irons_spellbooks")) return;
        IronsLoomFixtures.assertNativeBookRoundTrip(helper);
    }
    @GameTest(template = "platform", batch = "ans_loom")
    public static void nativeSchoolResolvesRealAttributesWithoutNamespaceAlias(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, "irons_spellbooks")) return;
        IronsLoomFixtures.assertNativeSchoolBinding(helper);
    }
}
