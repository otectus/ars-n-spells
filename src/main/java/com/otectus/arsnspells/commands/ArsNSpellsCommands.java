package com.otectus.arsnspells.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
                .then(Commands.literal("aura")
                    .executes(ArsNSpellsCommands::showOwnAura)
                )
                .then(Commands.literal("export_to_irons_scroll")
                    .requires(source -> source.hasPermission(2))
                    .executes(ArsNSpellsCommands::exportToIronsScroll)
                )
                .then(Commands.literal("bind_scroll_to_irons_book")
                    .requires(source -> source.hasPermission(2))
                    .executes(ArsNSpellsCommands::bindScrollToIronsBook)
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

        // Aura info — Covenant of the Seven owns aura state; we read it via reflection
        // bridge. Max-aura is not always exposed by Covenant's API, so display "?" if
        // the bridge could not resolve it.
        int aura = SanctifiedLegacyCompat.getCovenantAura(target);
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.info.aura", aura, "?"),
            false
        );

        // Resonance
        double resonance = ResonanceManager.getResonance(target);
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.info.resonance", String.format("%.3f", resonance)),
            false
        );

        // Ring status
        boolean cursed = SanctifiedLegacyCompat.isAvailable() && SanctifiedLegacyCompat.isWearingCursedRing(target);
        boolean virtue = SanctifiedLegacyCompat.isAvailable() && SanctifiedLegacyCompat.isWearingVirtueRing(target);
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.info.cursed_ring", cursed ? "Active" : "Inactive")
                .withStyle(cursed ? ChatFormatting.RED : ChatFormatting.GRAY),
            false
        );
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.info.virtue_ring", virtue ? "Active" : "Inactive")
                .withStyle(virtue ? ChatFormatting.AQUA : ChatFormatting.GRAY),
            false
        );

        // Iron's diagnostics: raw mana value (what Iron's natively sees) and whether the
        // ring-bypass redirect would inflate it. Useful for diagnosing "spell silently
        // doesn't cast" — if rawIronsMana < spellCost AND ringBypass is false, Iron's
        // would reject the cast at canBeCastedBy with cast_error_mana.
        //
        // Reflection because this command file is loaded on Iron's-less servers too —
        // a direct import would prevent the command class from loading at all.
        if (com.otectus.arsnspells.compat.IronsCompat.isLoaded()) {
            try {
                Class<?> magicDataClass = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
                Object md = magicDataClass
                    .getMethod("getPlayerMagicData", net.minecraft.world.entity.LivingEntity.class)
                    .invoke(null, target);
                float rawMana = (Float) magicDataClass.getMethod("getMana").invoke(md);
                boolean bypassActive = (cursed && AnsConfig.ENABLE_LP_SYSTEM.get())
                    || virtue;
                context.getSource().sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal(String.format(
                        "Iron's: rawMana=%.1f, ringBypass=%s", rawMana, bypassActive))
                        .withStyle(ChatFormatting.GRAY),
                    false);
            } catch (Throwable t) {
                context.getSource().sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal(
                        "Iron's: <inspection failed: " + t.getClass().getSimpleName() + ">")
                        .withStyle(ChatFormatting.RED),
                    false);
            }
        }

        return 1;
    }

    private static int showOwnAura(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer self = context.getSource().getPlayerOrException();
            // Covenant of the Seven owns the aura state; we delegate via reflection.
            int aura = SanctifiedLegacyCompat.getCovenantAura(self);
            context.getSource().sendSuccess(
                () -> Component.translatable("commands.ans.info.aura", aura, "?")
                    .withStyle(ChatFormatting.AQUA),
                false
            );
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
    }

    /**
     * Exports the Ars spell held in the player's main hand onto a real Iron's
     * scroll carrier and places it in their inventory. Admin/test counterpart to
     * the transcription ritual. Routes through ANS util classes so this file
     * stays free of Iron's imports (it loads on Iron's-less servers too).
     */
    private static int exportToIronsScroll(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            context.getSource().sendFailure(Component.translatable("commands.ans.export.not_player"));
            return 0;
        }
        if (!com.otectus.arsnspells.compat.IronsCompat.isLoaded()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.irons_required"));
            return 0;
        }

        ItemStack source = player.getMainHandItem();
        java.util.Optional<com.hollingsworth.arsnouveau.api.spell.Spell> spell =
            com.otectus.arsnspells.spell.ArsSpellExportUtil.extractArsSpell(source);
        if (spell.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.export.no_ars_spell"));
            return 0;
        }

        ItemStack carrier =
            com.otectus.arsnspells.spell.ArsSpellExportUtil.createIronsScrollCarrier(spell.get());
        if (carrier.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.export.scroll_unavailable"));
            return 0;
        }

        player.getInventory().placeItemBackInInventory(carrier);
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.export.success").withStyle(ChatFormatting.GREEN),
            true);
        return 1;
    }

    /**
     * Binds the carrier scroll and Iron's spellbook the player is holding (one in
     * each hand, either order). The scroll's Ars spell is appended to the book's
     * cross-cast sidecar and one scroll is consumed.
     */
    private static int bindScrollToIronsBook(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            context.getSource().sendFailure(Component.translatable("commands.ans.export.not_player"));
            return 0;
        }
        if (!com.otectus.arsnspells.compat.IronsCompat.isLoaded()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.irons_required"));
            return 0;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        ItemStack scroll;
        ItemStack book;
        if (com.otectus.arsnspells.spell.IronsBookBindingUtil.isIronsScroll(main)
            && com.otectus.arsnspells.spell.IronsBookBindingUtil.isIronsSpellBook(off)) {
            scroll = main;
            book = off;
        } else if (com.otectus.arsnspells.spell.IronsBookBindingUtil.isIronsScroll(off)
            && com.otectus.arsnspells.spell.IronsBookBindingUtil.isIronsSpellBook(main)) {
            scroll = off;
            book = main;
        } else {
            context.getSource().sendFailure(Component.translatable("commands.ans.bind.need_scroll_and_book"));
            return 0;
        }

        java.util.Optional<net.minecraft.nbt.CompoundTag> arsTag =
            com.otectus.arsnspells.spell.IronsBookBindingUtil.extractSingleArsEntry(scroll);
        if (arsTag.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.bind.scroll_not_carrier"));
            return 0;
        }
        if (com.otectus.arsnspells.spell.IronsBookBindingUtil.containsEquivalentArsSpell(book, arsTag.get())) {
            context.getSource().sendFailure(Component.translatable("commands.ans.bind.duplicate"));
            return 0;
        }

        if (!com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(book, arsTag.get())) {
            context.getSource().sendFailure(Component.translatable("commands.ans.bind.failed"));
            return 0;
        }
        scroll.shrink(1);
        com.otectus.arsnspells.util.AdvancementUtil.grant(player, "bind_spell");
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.bind.success").withStyle(ChatFormatting.GREEN),
            true);
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
        // Apply live: re-read the config and re-select bridges (server thread — command
        // handlers run there). refreshMode() may downgrade the effective mode when a
        // mode needs Iron's and it is absent (e.g. ISS_PRIMARY -> ARS_PRIMARY), so we
        // echo both the requested and the now-active mode.
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
