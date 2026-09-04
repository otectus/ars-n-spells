package com.otectus.arsnspells.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.compat.ModPresence;
import com.otectus.arsnspells.compat.irons_spells.SchoolIndex;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
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

        // Resonance
        double resonance = ResonanceManager.getResonance(target);
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.ans.info.resonance", String.format("%.3f", resonance)),
            false
        );

        // Affinity tracks (2.5.0 dynamic schools) — only the player's non-zero tracks,
        // sorted by school id so addon schools show under their full registry id.
        AffinityData affinity = target.getData(AttachmentTypes.AFFINITY.get());
        var tracks = affinity.getAllLevels().entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue() > 0)
            .sorted(java.util.Map.Entry.comparingByKey())
            .toList();
        if (tracks.isEmpty()) {
            context.getSource().sendSuccess(
                () -> Component.translatable("commands.ans.info.affinity.none"), false);
        } else {
            context.getSource().sendSuccess(
                () -> Component.translatable("commands.ans.info.affinity.header").withStyle(ChatFormatting.AQUA),
                false);
            for (var entry : tracks) {
                final String display = AffinityType.displayName(entry.getKey());
                final String key = entry.getKey();
                final int level = entry.getValue();
                context.getSource().sendSuccess(
                    () -> Component.translatable("commands.ans.info.affinity.line", display, key, level), false);
            }
        }

        // Registered Iron's school roster (diagnostic). Guarded so SchoolIndex,
        // which imports Iron's types, never classloads on an Iron's-absent server.
        if (ModPresence.isLoaded(CompatIds.IRONS_SPELLBOOKS)) {
            final int schoolCount = SchoolIndex.allSchools().size();
            context.getSource().sendSuccess(
                () -> Component.translatable("commands.ans.info.schools", schoolCount), false);
        }

        // Iron's raw mana: what Iron's natively sees, before any ANS adjustment. This is the
        // number to look at for "the spell silently does not cast" - if it is below the spell
        // cost, Iron's refuses at canBeCastedBy with cast_error_mana, before any of this mod's
        // event handlers get a say.
        //
        // Reflection because this command class is loaded on Iron's-less servers too; a direct
        // import would stop the whole class from loading.
        if (ModPresence.isLoaded(CompatIds.IRONS_SPELLBOOKS)) {
            try {
                Class<?> magicDataClass =
                    Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
                Object md = magicDataClass
                    .getMethod("getPlayerMagicData", net.minecraft.world.entity.LivingEntity.class)
                    .invoke(null, target);
                final float rawMana = (Float) magicDataClass.getMethod("getMana").invoke(md);
                context.getSource().sendSuccess(
                    () -> Component.translatable("commands.ans.info.irons_raw_mana",
                        String.format("%.1f", rawMana)).withStyle(ChatFormatting.GRAY), false);
            } catch (Throwable t) {
                context.getSource().sendSuccess(
                    () -> Component.translatable("commands.ans.info.irons_unavailable",
                        t.getClass().getSimpleName()).withStyle(ChatFormatting.RED), false);
            }
        }

        return 1;
    }

    /**
     * Exports the Ars spell held in the player's main hand onto a real Iron's
     * scroll carrier and places it in their inventory. Admin/test counterpart to
     * the Spell Loom. Routes through ANS util classes so this file stays free of
     * Iron's imports (it loads on Iron's-less servers too).
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

        net.minecraft.world.item.ItemStack source = player.getMainHandItem();
        java.util.Optional<com.hollingsworth.arsnouveau.api.spell.Spell> spell =
            com.otectus.arsnspells.spell.ArsSpellExportUtil.extractArsSpell(source);
        if (spell.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.export.no_ars_spell"));
            return 0;
        }
        java.util.List<String> blacklisted =
            com.otectus.arsnspells.util.ArsSpellIntegrity.blacklistedGlyphIds(spell.get());
        if (!blacklisted.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.export.blacklisted_glyphs",
                com.otectus.arsnspells.util.ArsSpellIntegrity.describeMissing(blacklisted)));
            return 0;
        }

        net.minecraft.world.item.ItemStack carrier =
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
     * each hand, either order). The scroll's Ars spell — with its Spell Loom
     * display metadata, if any — is appended to the book's cross-cast sidecar,
     * mirrored into Iron's native wheel, and one scroll is consumed.
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

        // The ritual honours this at SpellbookBindingRitual.onEnd(); this command did not, so
        // allow_ars_spells_in_irons_spellbooks=false stopped survival players while leaving the
        // op path wide open -- the opposite of what the config comment promises. Checked before
        // the hand contents for the same reason the ritual checks it first: the feature being
        // switched off is not a "you are holding the wrong things" problem.
        if (!AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.get()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.bind.disabled"));
            return 0;
        }

        net.minecraft.world.item.ItemStack main = player.getMainHandItem();
        net.minecraft.world.item.ItemStack off = player.getOffhandItem();
        net.minecraft.world.item.ItemStack scroll;
        net.minecraft.world.item.ItemStack book;
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

        java.util.Optional<com.otectus.arsnspells.spell.CrossModSpell> entry =
            com.otectus.arsnspells.spell.IronsBookBindingUtil.extractSingleEntry(scroll);
        if (entry.isEmpty() || entry.get().arsSpellTag().isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.ans.bind.scroll_not_carrier"));
            return 0;
        }

        // 3.0.3: refuse an unreadable payload before the scroll is consumed - same gate the
        // binding ritual applies. See IronsBookBindingUtil.isCastableArsPayload.
        if (!com.otectus.arsnspells.spell.IronsBookBindingUtil
                .isCastableArsPayload(entry.get().arsSpellTag().get())) {
            context.getSource().sendFailure(Component.translatable("commands.ans.bind.uncastable"));
            return 0;
        }

        int maxCap = AnsConfig.MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK.get();
        com.otectus.arsnspells.spell.IronsBookBindingUtil.AppendResult result =
            com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(
                book, entry.get().arsSpellTag().get(),
                entry.get().customName().orElse(null),
                entry.get().nature().orElse(null),
                entry.get().iconSymbol().orElse(null),
                maxCap);
        switch (result) {
            case ADDED:
                break;
            case DUPLICATE:
                context.getSource().sendFailure(Component.translatable("commands.ans.bind.duplicate"));
                return 0;
            case BOOK_FULL:
                // 3.0.3: distinct from FAILED. "the book is full" is actionable; "it failed"
                // sends the player looking for a bug that is not there.
                context.getSource().sendFailure(Component.translatable("commands.ans.bind.book_full",
                    com.otectus.arsnspells.spell.IronsBookBindingUtil.effectiveProxyCeiling(maxCap)));
                return 0;
            case FAILED:
            default:
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
