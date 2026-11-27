package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Audit ANS-014: a stored Iron's spell id whose spell is not registered is unavailable, never
 * Iron's none sentinel standing in for it. Validation, descriptor resolution and the final
 * cross-cast lookup all use the same exact lookup, refuse before any payment, and leave the saved
 * entry untouched so reinstalling the addon restores it.
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
            var missing = new net.minecraft.resources.ResourceLocation("audit_missing", "spell");
            var heal = new net.minecraft.resources.ResourceLocation("irons_spellbooks", "heal");
            var none = io.redspace.ironsspellbooks.api.registry.SpellRegistry.none();
            helper.assertTrue(io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(missing) == none,
                "fixture: Iron's answers its none sentinel for an unknown id");
            helper.assertTrue(com.otectus.arsnspells.spell.irons.IronsSpellLookup.lookupExact(missing) == null, "an unknown id is unavailable");
            helper.assertTrue(com.otectus.arsnspells.spell.irons.IronsSpellLookup.lookupExact(none.getSpellResource()) == null,
                "the none sentinel itself is never a cross-cast spell");
            var found = com.otectus.arsnspells.spell.irons.IronsSpellLookup.lookupExact(heal);
            helper.assertTrue(found != null && heal.equals(found.getSpellResource()), "a registered spell keeps its exact id");

            // Validation and the descriptor refuse with the localized unavailable reason.
            var entry = entry(missing);
            var result = com.otectus.arsnspells.spell.CrossCastValidator.validate(null, entry, 0, 1);
            helper.assertTrue(!result.ok() && "arsnspells.crosscast.invalid.iron_unresolved".equals(result.reasonKey()),
                "validation refuses the removed spell, got " + (result.ok() ? "ok" : result.reasonKey()));
            helper.assertTrue(com.otectus.arsnspells.spell.CrossCastValidator.validate(null, entry(heal), 0, 1).ok(), "a real spell validates");
            var descriptor = new com.otectus.arsnspells.spell.IronsRegistrySpellDescriptor(missing, 1, "");
            helper.assertTrue(!descriptor.validate(1, null).ok(), "the descriptor is unavailable");
            helper.assertTrue(descriptor.resolve() == null, "and resolves to nothing, not the none sentinel");
            helper.assertTrue(new com.otectus.arsnspells.spell.IronsRegistrySpellDescriptor(heal, 1, "").resolve() == found,
                "a real descriptor resolves to its own spell");

            // The cast itself: refused before anything is spent, the entry untouched.
            var player = CrossCastGameTests.scenarioPlayer(helper, "lookup_missing");
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            IronsProxyCastDriver.setIronsMana(player, 100.0f);
            TouchRegressionGameTests.fundArs(player);
            var carrier = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
            var list = new net.minecraft.nbt.ListTag();
            list.add(entry.copy());
            carrier.getOrCreateTag().put(com.otectus.arsnspells.spell.CrossCastNbt.TAG_CROSS_MOD_SPELLS, list);
            var saved = carrier.getTag().copy();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, carrier);
            float ironsBefore = IronsProxyCastDriver.ironsMana(player);
            float arsBefore = com.otectus.arsnspells.bridge.BridgeManager.getNativeArsBridge().getMana(player);
            boolean attempted = com.otectus.arsnspells.spell.CrossCastingHandler.serverHandleCast(player, carrier,
                net.minecraft.world.InteractionHand.MAIN_HAND, com.otectus.arsnspells.network.CrossCastRequestPacket.Action.CAST,
                java.util.UUID.randomUUID());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            helper.assertTrue(!attempted, "the removed spell's entry is not cast");
            helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == ironsBefore
                && com.otectus.arsnspells.bridge.BridgeManager.getNativeArsBridge().getMana(player) == arsBefore, "nothing is spent");
            helper.assertTrue(saved.equals(carrier.getTag()), "the saved id and payload are not rewritten, so reinstalling restores it");
        }

        private static net.minecraft.nbt.CompoundTag entry(net.minecraft.resources.ResourceLocation spell) {
            var entry = new net.minecraft.nbt.CompoundTag();
            entry.putString(com.otectus.arsnspells.spell.CrossCastNbt.TAG_SPELL_TYPE, com.otectus.arsnspells.spell.CrossSpellType.IRONS_SPELLBOOKS.name());
            entry.putString(com.otectus.arsnspells.spell.CrossCastNbt.TAG_SPELL_ID, spell.toString());
            entry.putInt(com.otectus.arsnspells.spell.CrossCastNbt.TAG_SPELL_LEVEL, 1);
            return entry;
        }
    }
}
