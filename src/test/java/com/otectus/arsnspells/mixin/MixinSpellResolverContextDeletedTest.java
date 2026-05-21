package com.otectus.arsnspells.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-003 — verifies that {@code MixinSpellResolverContext} and
 * {@code util/CasterContext} have been deleted, and the mixin config no longer
 * references them.
 *
 * <p>The deleted classes set a static ThreadLocal that wasn't cleared on
 * exception, leaking the previous caster's spell into the next caster's
 * cost-calc on the main server thread.
 */
class MixinSpellResolverContextDeletedTest {

    @Test
    void mixinSpellResolverContext_isDeleted() {
        Path p = Paths.get("src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverContext.java");
        assertFalse(Files.exists(p),
            "MixinSpellResolverContext.java must be deleted (ANS-HIGH-003)");
    }

    @Test
    void casterContext_isDeleted() {
        Path p = Paths.get("src/main/java/com/otectus/arsnspells/util/CasterContext.java");
        assertFalse(Files.exists(p),
            "util/CasterContext.java must be deleted (ANS-HIGH-003)");
    }

    @Test
    void mixinsJson_noLongerListsSpellResolverContext() throws IOException {
        String json = Files.readString(Paths.get(
            "src/main/resources/ars_n_spells.mixins.json"));
        assertFalse(json.contains("MixinSpellResolverContext"),
            "ars_n_spells.mixins.json must not reference MixinSpellResolverContext "
                + "after ANS-HIGH-003 deletion");
    }

    @Test
    void cursedRingHandler_noLongerImportsCasterContext() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java"));
        assertFalse(src.contains("import com.otectus.arsnspells.util.CasterContext"),
            "CursedRingHandler must no longer import CasterContext");
        assertTrue(src.contains("event.context") && src.contains(".getSpell()"),
            "CursedRingHandler must read spell directly from event.context.getSpell()");
    }

    @Test
    void virtueRingHandler_noLongerImportsCasterContext() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java"));
        assertFalse(src.contains("import com.otectus.arsnspells.util.CasterContext"),
            "VirtueRingHandler must no longer import CasterContext");
    }

    @Test
    void curioDiscountHandler_noLongerImportsCasterContext() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/CurioDiscountHandler.java"));
        assertFalse(src.contains("import com.otectus.arsnspells.util.CasterContext"),
            "CurioDiscountHandler must no longer import CasterContext");
    }
}
