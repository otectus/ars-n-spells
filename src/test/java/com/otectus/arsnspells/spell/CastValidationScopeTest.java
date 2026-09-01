package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural proof that {@link CastValidationScope} reproduces the transform of the
 * {@code @Redirect} it replaced, and that a leaked scope cannot persist.
 *
 * <p>The redirect that used to live in {@code MixinIronsCastValidation} applied, in order:
 * ARS_PRIMARY divides the value by the Iron's-to-Ars conversion rate; otherwise the value
 * passes through. The transform acted on the value the mana read would otherwise have
 * produced. These tests pin every branch of that table numerically, so the equivalence claim
 * is checked rather than asserted in a comment.
 *
 * <p>The 1.20.1 original had a third branch - a Cursed/Virtue ring bypass returning
 * {@code Float.MAX_VALUE}, which won over the conversion. Covenant of the Seven has no 1.21.1
 * release, so that branch has no producer here and is not tested.
 *
 * <p>The scope keys on the {@code MagicData} instance by identity, which is why a plain
 * {@link Object} stands in for it here - no Minecraft runtime is needed.
 */
class CastValidationScopeTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @AfterEach
    void tearDown() {
        CastValidationScope.clear();
    }

    @Test
    void noScope_passesValueThrough() {
        Object magicData = new Object();
        assertEquals(42.0f, CastValidationScope.apply(magicData, 42.0f),
            "with no scope open the mana value must be untouched");
        assertFalse(CastValidationScope.isActive(magicData));
    }

    @Test
    void arsPrimaryRate_dividesValue() {
        Object magicData = new Object();
        CastValidationScope.push(magicData, PLAYER, 4.0);
        assertEquals(25.0f, CastValidationScope.apply(magicData, 100.0f),
            "ARS_PRIMARY must rescale the Iron's-side value by the conversion rate");
        assertTrue(CastValidationScope.isActive(magicData));
    }

    @Test
    void nonPositiveRate_passesValueThrough() {
        Object magicData = new Object();
        CastValidationScope.push(magicData, PLAYER, 0.0);
        assertEquals(7.5f, CastValidationScope.apply(magicData, 7.5f),
            "a zero/absent conversion rate must not scale the value");
    }

    @Test
    void differentMagicDataInstance_isIgnored() {
        Object mine = new Object();
        Object theirs = new Object();
        CastValidationScope.push(mine, PLAYER, 4.0);
        assertEquals(5.0f, CastValidationScope.apply(theirs, 5.0f),
            "a scope must never adjust a different player's MagicData");
        assertFalse(CastValidationScope.isActive(theirs));
    }

    @Test
    void clear_endsTheScope() {
        Object magicData = new Object();
        CastValidationScope.push(magicData, PLAYER, 4.0);
        CastValidationScope.clear();
        assertEquals(5.0f, CastValidationScope.apply(magicData, 5.0f));
        // clear() is called once per RETURN instruction, so it must tolerate repeats.
        CastValidationScope.clear();
        assertEquals(5.0f, CastValidationScope.apply(magicData, 5.0f));
    }

    @Test
    void expiredScope_passesThroughAndSelfEvicts() {
        // canBeCastedBy exiting by exception never reaches the RETURN hook, so a scope can
        // outlive its call. It must go inert on its own rather than leaving the player
        // reading permanently rescaled mana.
        Object magicData = new Object();
        long stale = System.nanoTime() - (CastValidationScope.MAX_AGE_NANOS * 2);
        CastValidationScope.push(magicData, PLAYER, 4.0, stale);

        assertEquals(9.0f, CastValidationScope.apply(magicData, 9.0f),
            "an expired scope must not adjust anything");
        assertFalse(CastValidationScope.isActive(magicData),
            "an expired scope must evict itself on first read");
    }

    @Test
    void pushReplacesAnyStaleScope() {
        Object first = new Object();
        Object second = new Object();
        CastValidationScope.push(first, PLAYER, 4.0);
        // No clear() in between - this is the "previous call threw" case.
        CastValidationScope.push(second, PLAYER, 2.0);

        assertEquals(50.0f, CastValidationScope.apply(second, 100.0f));
        assertFalse(CastValidationScope.isActive(first),
            "opening a new scope must retire the previous one");
    }

    @Test
    void nullMagicData_clearsRatherThanOpening() {
        Object magicData = new Object();
        CastValidationScope.push(magicData, PLAYER, 4.0);
        CastValidationScope.push(null, PLAYER, 4.0);
        assertEquals(1.0f, CastValidationScope.apply(magicData, 1.0f));
        assertEquals(1.0f, CastValidationScope.apply(null, 1.0f));
    }

    @Test
    void scopeIsPerThread() throws Exception {
        Object magicData = new Object();
        CastValidationScope.push(magicData, PLAYER, 4.0);

        AtomicReference<Float> seenOnOtherThread = new AtomicReference<>();
        Thread other = new Thread(
            () -> seenOnOtherThread.set(CastValidationScope.apply(magicData, 8.0f)));
        other.start();
        other.join();

        assertEquals(8.0f, seenOnOtherThread.get(), "the scope must not leak across threads");
        assertEquals(2.0f, CastValidationScope.apply(magicData, 8.0f),
            "and must still be live on the thread that opened it");
    }
}
