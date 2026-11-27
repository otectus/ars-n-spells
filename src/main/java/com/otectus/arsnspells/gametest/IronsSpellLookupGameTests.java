package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Audit ANS-014 (same name as the Forge test): a stored Iron's spell id whose spell is not registered
 * is unavailable, and Iron's none sentinel itself never counts as a spell. Validation and the final
 * cross-cast lookup use the same exact lookup and refuse before any payment; the saved entry is not
 * rewritten, so reinstalling the addon restores it.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class IronsSpellLookupGameTests {
    private IronsSpellLookupGameTests() {}

    @GameTest(template = "platform")
    public static void ironsLoaded_lookup_removedSpellIdIsUnavailableNotTheNoneSpell(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.run(helper);
        helper.succeed();
    }

    private static final class Loaded {
        static void run(GameTestHelper helper) {
            var missing = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("audit_missing", "spell");
            var heal = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "heal");
            var none = io.redspace.ironsspellbooks.api.registry.SpellRegistry.none();
            helper.assertTrue(io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(missing) == none,
                "fixture: Iron's answers its none sentinel for an unknown id");
            helper.assertTrue(com.otectus.arsnspells.spell.irons.IronsSpellLookup.lookupExact(missing) == null, "an unknown id is unavailable");
            helper.assertTrue(com.otectus.arsnspells.spell.irons.IronsSpellLookup.lookupExact(none.getSpellResource()) == null,
                "the none sentinel itself is never a cross-cast spell");
            var found = com.otectus.arsnspells.spell.irons.IronsSpellLookup.lookupExact(heal);
            helper.assertTrue(found != null && heal.equals(found.getSpellResource()), "a registered spell keeps its exact id");

            for (var id : new net.minecraft.resources.ResourceLocation[]{missing, none.getSpellResource()}) {
                var entry = entry(id);
                var result = com.otectus.arsnspells.spell.CrossCastValidator.validate(entry, 0, 1);
                helper.assertTrue(!result.ok() && "message.ars_n_spells.crosscast.invalid.iron_unresolved".equals(result.reasonKey()),
                    "validation refuses " + id + ", got " + (result.ok() ? "ok" : result.reasonKey()));
                helper.assertTrue(entry.equals(entry(id)), "the saved entry is not rewritten");
            }
            helper.assertTrue(com.otectus.arsnspells.spell.CrossCastValidator.validate(entry(heal), 0, 1).ok(), "a real spell validates");
        }

        private static com.otectus.arsnspells.spell.CrossModSpell entry(net.minecraft.resources.ResourceLocation spell) {
            return new com.otectus.arsnspells.spell.CrossModSpell(spell, 1, com.otectus.arsnspells.spell.CrossSpellType.IRONS_SPELLBOOKS.name(),
                java.util.Optional.empty(), java.util.Optional.empty(), com.otectus.arsnspells.spell.CrossModSpellComponents.NO_PROXY_POOL_ID,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), new net.minecraft.nbt.CompoundTag());
        }
    }
}
