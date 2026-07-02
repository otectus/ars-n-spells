package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, ArsNSpells.MODID);

    public static final RegistryObject<MenuType<SpellLoomMenu>> SPELL_LOOM =
        MENUS.register("spell_loom",
            () -> IForgeMenuType.create(SpellLoomMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
