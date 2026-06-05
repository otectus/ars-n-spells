package com.otectus.arsnspells.spell;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bootstrap-free coverage for {@link CrossCastValidator}. Uses the package-private
 * test seam with a synthetic Iron's-registry predicate so no live registry is needed,
 * and avoids the Ars decode path (which would need the spell registry) by exercising
 * the structural / Iron's branches only.
 */
class CrossCastValidatorTest {

    private static final Predicate<ResourceLocation> ALWAYS = id -> true;
    private static final Predicate<ResourceLocation> NEVER = id -> false;

    private static CrossModSpell irons(String id, int level) {
        return new CrossModSpell(ResourceLocation.parse(id), level,
            CrossSpellType.IRONS_SPELLBOOKS.name(), Optional.empty(), Optional.empty());
    }

    @Test
    void indexOutOfRangeFails() {
        CrossModSpell e = irons("irons_spellbooks:fireball", 1);
        assertFalse(CrossCastValidator.validate(e, 0, 0, ALWAYS).ok());
        assertFalse(CrossCastValidator.validate(e, -1, 1, ALWAYS).ok());
        assertFalse(CrossCastValidator.validate(e, 2, 2, ALWAYS).ok());
    }

    @Test
    void ironsResolvableSucceeds() {
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(irons("irons_spellbooks:fireball", 1), 0, 1, ALWAYS);
        assertTrue(r.ok());
        assertEquals(CrossSpellType.IRONS_SPELLBOOKS, r.resolvedType());
    }

    @Test
    void ironsUnresolvedFails() {
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(irons("irons_spellbooks:missing", 1), 0, 1, NEVER);
        assertFalse(r.ok());
        assertEquals("message.ars_n_spells.crosscast.invalid.iron_unresolved", r.reasonKey());
    }

    @Test
    void ironsBadLevelFails() {
        assertFalse(CrossCastValidator.validate(irons("irons_spellbooks:fireball", 0), 0, 1, ALWAYS).ok());
    }

    @Test
    void unknownTypeFails() {
        CrossModSpell e = new CrossModSpell(ResourceLocation.parse("foo:bar"), 1, "NONSENSE",
            Optional.empty(), Optional.empty());
        CrossCastValidator.ValidationResult r = CrossCastValidator.validate(e, 0, 1, ALWAYS);
        assertFalse(r.ok());
        assertEquals("message.ars_n_spells.crosscast.invalid.type", r.reasonKey());
    }

    @Test
    void namespaceInferenceWhenTypeBlank() {
        CrossModSpell e = new CrossModSpell(ResourceLocation.parse("irons_spellbooks:fireball"), 1, "",
            Optional.empty(), Optional.empty());
        assertTrue(CrossCastValidator.validate(e, 0, 1, ALWAYS).ok());
    }

    @Test
    void arsMissingTagFails() {
        // Empty arsSpellTag short-circuits before the Ars decode path (keeps the test Bootstrap-free).
        CrossModSpell e = new CrossModSpell(ResourceLocation.parse("ars_nouveau:spell"), 1,
            CrossSpellType.ARS_NOUVEAU.name(), Optional.empty(), Optional.empty());
        CrossCastValidator.ValidationResult r = CrossCastValidator.validate(e, 0, 1, ALWAYS);
        assertFalse(r.ok());
        assertEquals("message.ars_n_spells.crosscast.invalid.ars_missing", r.reasonKey());
    }
}
