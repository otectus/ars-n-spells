package com.otectus.arsnspells.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 3.3 — GameTest scaffold for the cross-cast pipeline.
 *
 * <p>Run with {@code ./gradlew runGameTestServer}. The {@code build.gradle}
 * {@code gameTestServer} run target gates on the {@code ars_n_spells}
 * namespace so unrelated mods' tests do not run.
 *
 * <p>The full 7-scenario suite from the audit's "Testing and Validation
 * Strategy" (clean Ars cast, clean Iron cast, malformed NBT rejection,
 * insufficient resources, dimension transition, separate-mode dual cost, ring
 * + cross-cast) is tracked for 2.0.1 — each scenario needs a structure NBT
 * template that this scaffold does not yet ship. The single test below
 * verifies the scaffold itself is wired correctly.
 *
 * <p>Adding a new scenario:
 * <ol>
 *   <li>Create the structure NBT in
 *       {@code src/main/resources/data/ars_n_spells/structures/&lt;name&gt;.nbt}.</li>
 *   <li>Add a {@code @GameTest(template = "&lt;name&gt;")} method here that
 *       drives the cast via {@link com.otectus.arsnspells.spell.CrossCastingHandler}'s
 *       server entry point and asserts the observed side effect via
 *       {@link GameTestHelper}.</li>
 * </ol>
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CrossCastGameTests {

    private CrossCastGameTests() {
    }

    /**
     * Sanity test: confirms the gameTestServer run target is wired and the
     * test class is discovered. Uses the empty {@code platform} template
     * provided by vanilla. Real cross-cast scenarios will follow in 2.0.1.
     */
    @GameTest(template = "platform")
    public static void scaffoldIsWired(GameTestHelper helper) {
        helper.succeed();
    }
}
