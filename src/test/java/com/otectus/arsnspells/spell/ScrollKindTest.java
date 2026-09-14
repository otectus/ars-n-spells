package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classloading contract for the two classes every bind route goes through.
 *
 * <p>{@link ScrollKind} and {@link IronsSpellbookBinder} are read by the Spell Loom, the
 * tooltip handler, the ritual and the command — all of which run with Iron's Spellbooks
 * absent. They must therefore have no hard link to {@code io.redspace}, reaching Iron's only
 * through gated, fully-qualified calls. Iron's is a {@code compileOnly} dependency, so the
 * unit-test runtime classpath has no Iron's jar on it at all: initializing these classes here
 * is the same JVM operation that happens on an Iron's-less install, and the first test proves
 * that the absence is real rather than assumed.
 *
 * <p><b>What is not covered here.</b> {@code ScrollKind.classify} needs an {@link
 * net.minecraft.world.item.ItemStack}, and this suite deliberately runs Bootstrap-free (as
 * {@code IronsBookBindingPlanTest} does), so the classification matrix itself is asserted
 * against real items in {@code gametest/IronsInscriptionTableGameTests} and
 * {@code gametest/SpellLoomInscriptionGameTests} instead. Everything asserted below is
 * reachable without a registry.
 */
class ScrollKindTest {

    @Test
    void ironsIsGenuinelyAbsentFromTheUnitTestClasspath() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("io.redspace.ironsspellbooks.api.spells.ISpellContainer"),
            "Iron's must be absent from the unit-test runtime classpath, or the loadability "
                + "assertions below prove nothing");
    }

    @Test
    void scrollKindAndBinderInitializeWithIronsAbsent() {
        assertDoesNotThrow(() -> Class.forName(ScrollKind.class.getName(), true,
                ScrollKindTest.class.getClassLoader()),
            "ScrollKind must load and initialize with no Iron's jar present");
        assertDoesNotThrow(() -> Class.forName(IronsSpellbookBinder.class.getName(), true,
                ScrollKindTest.class.getClassLoader()),
            "IronsSpellbookBinder must load and initialize with no Iron's jar present");
        assertDoesNotThrow(() -> Class.forName(IronsBookBindingUtil.class.getName(), true,
                ScrollKindTest.class.getClassLoader()),
            "IronsBookBindingUtil must load and initialize with no Iron's jar present");
    }

    @Test
    void everyScrollKindIsDistinctAndNamed() {
        assertEquals(5, ScrollKind.values().length,
            "a new ScrollKind needs a decision in the table router, the loom and the binder");
        assertEquals(ScrollKind.ANS_CARRIER, ScrollKind.valueOf("ANS_CARRIER"));
        assertEquals(ScrollKind.ANS_BLANK, ScrollKind.valueOf("ANS_BLANK"));
    }

    /** Only ADDED counts as a mutation, and every refusal has its own translation key. */
    @Test
    void bindResultsHaveDistinctMessageKeysAndOneSuccess() {
        Set<String> keys = new HashSet<>();
        int added = 0;
        for (IronsSpellbookBinder.BindResult result : IronsSpellbookBinder.BindResult.values()) {
            assertTrue(result.messageKey().startsWith("message.ars_n_spells.bind."),
                result + " must live under the shared bind message namespace, got "
                    + result.messageKey());
            assertTrue(keys.add(result.messageKey()),
                "two BindResults share the message key " + result.messageKey()
                    + ", so a player cannot tell them apart");
            if (result.wasAdded()) {
                added++;
            }
        }
        assertEquals(1, added, "exactly one BindResult may report a mutated book");
        assertTrue(IronsSpellbookBinder.BindResult.ADDED.wasAdded());
        assertFalse(IronsSpellbookBinder.BindResult.DUPLICATE.wasAdded());
    }
}
