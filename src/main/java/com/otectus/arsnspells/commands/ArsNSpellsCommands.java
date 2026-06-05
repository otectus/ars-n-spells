package com.otectus.arsnspells.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin commands for Ars 'n' Spells.
 */
public class ArsNSpellsCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsNSpellsCommands.class);

    /** Lowercase config names of every mana mode — drives {@code /ans mode set} tab-completion and validation. */
    private static final String[] MODE_NAMES = buildModeNames();

    private static String[] buildModeNames() {
        ManaUnificationMode[] modes = ManaUnificationMode.values();
        String[] names = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            names[i] = modes[i].getConfigName();
        }
        return names;
    }

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
                .then(Commands.literal("debug")
                    .requires(source -> source.hasPermission(2))
                    .executes(ArsNSpellsCommands::toggleDebug)
                )
                .then(Commands.literal("info")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ArsNSpellsCommands::showPlayerInfo)
                    )
                )
                .then(Commands.literal("mode")
                    .executes(ArsNSpellsCommands::showMode)
                    .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(MODE_NAMES, builder))
                            .executes(ArsNSpellsCommands::setMode)
                        )
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

    private static int toggleDebug(CommandContext<CommandSourceStack> context) {
        boolean current = AnsConfig.DEBUG_MODE.get();
        AnsConfig.DEBUG_MODE.set(!current);
        AnsConfig.safeSave();

        context.getSource().sendSuccess(
            () -> Component.translatable(current ? "commands.ans.debug.disabled" : "commands.ans.debug.enabled")
                .withStyle(current ? ChatFormatting.RED : ChatFormatting.GREEN),
            true
        );
        return 1;
    }

    private static int showPlayerInfo(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");

        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.info.header", target.getName().getString())
                .withStyle(ChatFormatting.GOLD),
            false
        );

        // Mana info
        float mana = BridgeManager.getBridge() != null ? BridgeManager.getBridge().getMana(target) : 0;
        float maxMana = BridgeManager.getBridge() != null ? BridgeManager.getBridge().getMaxMana(target) : 0;
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.info.mana",
                String.format("%.1f", mana), String.format("%.1f", maxMana)),
            false
        );

        // Resonance
        double resonance = ResonanceManager.getResonance(target);
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.info.resonance", String.format("%.3f", resonance)),
            false
        );

        return 1;
    }

    private static int showMode(CommandContext<CommandSourceStack> context) {
        ManaUnificationMode mode = AnsConfig.getManaMode();
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.mode.current", mode.name())
                .withStyle(ChatFormatting.YELLOW),
            false
        );
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> context) {
        String requested = StringArgumentType.getString(context, "mode");

        // Strict match against the known config names. Unlike ManaUnificationMode.fromString,
        // we do NOT silently fall back to ISS_PRIMARY — a typo must be reported so the op
        // knows the mode did not change.
        ManaUnificationMode parsed = null;
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            if (mode.getConfigName().equalsIgnoreCase(requested)) {
                parsed = mode;
                break;
            }
        }
        if (parsed == null) {
            context.getSource().sendFailure(
                Component.translatable("commands.ans.mode.set.invalid", requested)
            );
            return 0;
        }

        AnsConfig.MANA_UNIFICATION_MODE.set(parsed.getConfigName());
        AnsConfig.safeSave();
        // Apply live: re-read the config and re-select bridges (command handlers run on
        // the server thread). refreshMode() may downgrade the effective mode when a mode
        // needs Iron's and it is absent (e.g. ISS_PRIMARY -> ARS_PRIMARY), so we echo both
        // the requested and the now-active mode.
        BridgeManager.refreshMode();

        final String requestedName = parsed.getConfigName();
        final String effectiveName = BridgeManager.getCurrentMode().getConfigName();
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.mode.set.success", requestedName, effectiveName)
                .withStyle(ChatFormatting.GREEN),
            true
        );
        LOGGER.info("Mana unification mode set to {} (effective {}) by {}",
            requestedName, effectiveName, context.getSource().getTextName());
        return 1;
    }
}
