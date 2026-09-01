package com.otectus.arsnspells.mixinplugin;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ANS-HIGH-003 - {@code MixinSpellResolverContext} and {@code util/CasterContext} must stay
 * deleted.
 *
 * <p>They set a static {@code ThreadLocal} at {@code canCast} HEAD and cleared it at RETURN.
 * {@code @At("RETURN")} does not run when the method exits by throwing, and it is also skipped
 * when another mixin's cancelling HEAD callback returns first - so the previous caster's player
 * and spell leaked into the next cast on that server thread.
 *
 * <p>The port had reintroduced both classes, and by then nothing read them at all: two mixins
 * wrote the ThreadLocal and no code anywhere consumed it. The 1.20.1 readers were Covenant's
 * ring handlers, which are not part of this build. So it was a leak with no upside.
 */
class CasterContextDeletedTest {

    @Test
    void bothClassesAreGone() {
        assertFalse(Files.exists(TestPaths.of(
                "src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverContext.java")),
            "MixinSpellResolverContext must stay deleted (ANS-HIGH-003)");
        assertFalse(Files.exists(TestPaths.of(
                "src/main/java/com/otectus/arsnspells/util/CasterContext.java")),
            "util/CasterContext must stay deleted (ANS-HIGH-003)");
    }

    @Test
    void noMixinConfigListsIt() throws IOException {
        for (String config : new String[] {
                "src/main/resources/ars_n_spells.mixins.json",
                "src/main/resources/ars_n_spells.compat.mixins.json"}) {
            assertFalse(Files.readString(TestPaths.of(config)).contains("MixinSpellResolverContext"),
                config + " must not reference the deleted mixin - a listed but missing class "
                    + "throws at mixin PREPARE");
        }
    }

    @Test
    void nothingWritesTheThreadLocalAnyMore() throws IOException {
        String preCast = Files.readString(TestPaths.of(
            "src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverPreCast.java"));
        assertFalse(preCast.contains("CasterContext"),
            "MixinSpellResolverPreCast also wrote the leaked ThreadLocal and must not "
                + "reintroduce it");
    }
}
