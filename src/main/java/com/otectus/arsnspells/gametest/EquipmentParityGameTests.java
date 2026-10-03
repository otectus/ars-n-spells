package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class EquipmentParityGameTests {
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_curioAttributeToggleChangesMirroredBonus(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        Loaded.check(helper);
        helper.succeed();
    }
    private static final class Loaded {
        static void check(GameTestHelper helper) {
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "curio_parity"));
            var id = ResourceLocation.fromNamespaceAndPath("ans_parity_fixture", "curio_max_mana");
            var modifier = new AttributeModifier(id, 137, AttributeModifier.Operation.ADD_VALUE);
            var attribute = io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA;
            var instance = player.getAttribute(attribute);
            var book = IronsTableDriver.freshBook();
            helper.assertTrue(!book.isEmpty(), "Native book fixture must exist");
            io.redspace.ironsspellbooks.api.util.Utils.setPlayerSpellbookStack(player, book);
            helper.assertTrue(!io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player).isEmpty(), "Curios book slot must be populated");
            java.util.function.Consumer<top.theillusivec4.curios.api.event.CurioAttributeModifierEvent> listener = event -> {
                if (event.getSlotContext().entity() == player) event.addModifier(attribute, modifier);
            };
            boolean previous = AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.get();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            instance.addTransientModifier(modifier);
            try {
                AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.set(true);
                double included = EquipmentIntegration.ironsGearMaxManaBonus(player);
                AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.set(false);
                double excluded = EquipmentIntegration.ironsGearMaxManaBonus(player);
                helper.assertTrue(Math.abs(included - excluded - 137) < 0.001,
                    "Curios toggle must change the mirrored native gear contribution by 137, got " + included + " / " + excluded);
                AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.set(true);
                helper.assertTrue(Math.abs(included - EquipmentIntegration.ironsGearMaxManaBonus(player)) < 0.001,
                    "Re-enabling must restore exactly one contribution");
            } finally {
                AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.set(previous);
                instance.removeModifier(id);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            }
        }
    }
}
