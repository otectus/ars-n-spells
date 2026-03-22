package com.otectus.arsnspells.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin commands for Ars 'n' Spells.
 * All commands require operator permission level 2.
 */
public class ArsNSpellsCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsNSpellsCommands.class);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("ans")
                .then(Commands.literal("mana")
                    .then(Commands.literal("setdefault")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 100000.0))
                            .executes(ArsNSpellsCommands::setDefaultMana)
                        )
                    )
                    .then(Commands.literal("getdefault")
                        .executes(ArsNSpellsCommands::getDefaultMana)
                    )
                )
        );

        LOGGER.debug("Ars 'n' Spells commands registered");
    }

    private static int setDefaultMana(CommandContext<CommandSourceStack> context) {
        double value = DoubleArgumentType.getDouble(context, "value");
        AnsConfig.DEFAULT_MAX_MANA.set(value);
        AnsConfig.safeSave();

        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.mana.setdefault.success", String.format("%.1f", value)),
            true
        );

        LOGGER.info("Default max mana set to {} by {}", value,
            context.getSource().getTextName());
        return 1;
    }

    private static int getDefaultMana(CommandContext<CommandSourceStack> context) {
        double current = AnsConfig.DEFAULT_MAX_MANA.get();
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.mana.getdefault", String.format("%.1f", current)),
            false
        );
        return 1;
    }
}
