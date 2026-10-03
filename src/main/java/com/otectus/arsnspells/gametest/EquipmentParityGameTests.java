package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * {@code read_curio_attribute_modifiers} must add or remove a worn curio's contribution to the
 * mirrored Iron's gear bonus exactly once. The Forge 1.20.1 counterpart of the NeoForge suite of
 * the same name, driven through the real Curios attribute event.
 */
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
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "curio_parity"));
            var id = java.util.UUID.nameUUIDFromBytes(
                "ans_parity_fixture:curio_max_mana".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var modifier = new AttributeModifier(id, "ans_parity_fixture", 137,
                AttributeModifier.Operation.ADDITION);
            var attribute = io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get();
            var instance = player.getAttribute(attribute);
            var book = IronsTableDriver.findNativeInscribableBook();
            helper.assertTrue(!book.isEmpty(), "Native book fixture must exist");
            io.redspace.ironsspellbooks.api.util.Utils.setPlayerSpellbookStack(player, book);
            helper.assertTrue(!io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player).isEmpty(),
                "Curios book slot must be populated");
            java.util.function.Consumer<top.theillusivec4.curios.api.event.CurioAttributeModifierEvent> listener = event -> {
                if (event.getSlotContext().entity() == player) event.addModifier(attribute, modifier);
            };
            boolean previous = AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.get();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
            instance.addTransientModifier(modifier);
            try {
                AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.set(true);
                EquipmentIntegration.clearCache(player);
                double included = EquipmentIntegration.getIronManaBonuses(player).maxMana;
                AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.set(false);
                EquipmentIntegration.clearCache(player);
                double excluded = EquipmentIntegration.getIronManaBonuses(player).maxMana;
                helper.assertTrue(Math.abs(included - excluded - 137) < 0.001,
                    "Curios toggle must change the mirrored native gear contribution by 137, got "
                        + included + " / " + excluded);
                AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.set(true);
                EquipmentIntegration.clearCache(player);
                helper.assertTrue(Math.abs(included - EquipmentIntegration.getIronManaBonuses(player).maxMana) < 0.001,
                    "Re-enabling must restore exactly one contribution");
            } finally {
                AnsConfig.READ_CURIO_ATTRIBUTE_MODIFIERS.set(previous);
                instance.removeModifier(id);
                EquipmentIntegration.clearCache(player);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            }
        }
    }
}
