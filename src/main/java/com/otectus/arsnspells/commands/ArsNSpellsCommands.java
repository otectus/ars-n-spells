package com.otectus.arsnspells.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.combat.CombatDebugState;
import com.otectus.arsnspells.combat.IronsAttributeReport;
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
                    .then(Commands.literal("combat")
                        .executes(ArsNSpellsCommands::debugCombat)
                    )
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

    /**
     * Print everything that decides a cross-mod damage number for the executing player: the
     * config that governs the two bridges, both mods' spell-power attributes, and the last hit
     * each bridge actually touched.
     *
     * <p>This is the answer to "armour does nothing" and its whole family of look-alikes. The
     * fault can be the attribute (zero on the player), the school (the glyph resolved to one
     * the caster has no power in), the config (a toggle off, or a policy that discards the
     * school that mattered) or the formula (the cap swallowed the product), and all four look
     * identical from inside the game. Every one of them is a line below.
     *
     * <p>Chat feedback, never the log: an op running this wants the numbers in front of them,
     * and the damage path itself is forbidden from logging per event.
     *
     * <p>No Iron's type is named here. The Iron's attribute block goes through
     * {@link IronsAttributeReport}, called only inside the presence gate, because this class
     * loads on Iron's-less servers too.
     */
    private static int debugCombat(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            context.getSource().sendFailure(Component.translatable("commands.ans.export.not_player"));
            return 0;
        }
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.header")
            .withStyle(ChatFormatting.GOLD), false);

        // Said first and said plainly: with debug off nothing is recorded, so the two snapshot
        // sections below read "none recorded" even while the bridges are working perfectly.
        // Without this line that empty report looks exactly like the bug being hunted.
        if (!AnsConfig.debugEnabled()) {
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.debug_off")
                .withStyle(ChatFormatting.RED), false);
        }

        final String manaMode = AnsConfig.getManaMode().getConfigName();
        final String policy = AnsConfig.getMultiSchoolPowerPolicy().name();
        final String cap = String.format("%.2f", AnsConfig.SPELL_POWER_CAP.get());
        source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.config",
            manaMode, policy, cap).withStyle(ChatFormatting.GRAY), false);

        final boolean crossStats = AnsConfig.flag(AnsConfig.ENABLE_CROSS_MOD_COMBAT_STATS, true);
        final boolean ironsForArs = AnsConfig.flag(AnsConfig.ENABLE_IRONS_POWER_FOR_ARS_DAMAGE, true);
        final boolean arsForIrons = AnsConfig.flag(AnsConfig.ENABLE_ARS_DAMAGE_FOR_IRONS_DAMAGE, true);
        source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.toggles",
            crossStats, ironsForArs, arsForIrons).withStyle(ChatFormatting.GRAY), false);

        final String damageBonus =
            String.format("%.2f", arsAttribute(player, PerkAttributes.SPELL_DAMAGE_BONUS));
        final String maxMana = String.format("%.2f", arsAttribute(player, PerkAttributes.MAX_MANA));
        final String manaRegen =
            String.format("%.2f", arsAttribute(player, PerkAttributes.MANA_REGEN_BONUS));
        source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.ars_attributes",
            damageBonus, maxMana, manaRegen).withStyle(ChatFormatting.AQUA), false);

        // Gated exactly like the Iron's school roster in /ans info: the helper imports Iron's,
        // so it must not classload on a server without it.
        if (ModPresence.isLoaded(CompatIds.IRONS_SPELLBOOKS)) {
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.irons_attributes")
                .withStyle(ChatFormatting.AQUA), false);
            for (java.util.Map.Entry<String, Double> entry : IronsAttributeReport.read(player).entrySet()) {
                final String name = entry.getKey();
                final double raw = entry.getValue();
                // NaN is IronsAttributeReport's "could not read" sentinel; rendering it as a
                // number would misreport an unreadable attribute as a real zero.
                final String value = Double.isNaN(raw)
                    ? Component.translatable("commands.ans.debug.combat.attribute_unavailable").getString()
                    : String.format("%.2f", raw);
                source.sendSuccess(() -> Component.translatable(
                    "commands.ans.debug.combat.attribute_line", name, value), false);
            }
        } else {
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.irons_absent")
                .withStyle(ChatFormatting.GRAY), false);
        }

        CombatDebugState.ArsSnapshot ars = CombatDebugState.lastArs(player.getUUID());
        if (ars == null) {
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.ars_none")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.ars_header")
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.ars_spell",
                ars.spellId(), ars.spellName(), ars.crossCast()), false);
            // The whole school set, not only the winner: "matched" being the wrong school is the
            // symptom, and the set is what says whether the resolution or the policy chose it.
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.ars_schools",
                String.join(", ", ars.schools()),
                ars.breakdown().matchedSchool() == null ? "-" : ars.breakdown().matchedSchool()), false);
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.ars_factors",
                String.format("%.2f", ars.breakdown().globalPower()),
                String.format("%.2f", ars.breakdown().schoolPower()),
                String.format("%.2f", ars.breakdown().affinity()),
                String.format("%.2f", ars.breakdown().resonance())), false);
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.ars_result",
                String.format("%.2f", ars.rawDamage()),
                String.format("%.2f", ars.finalDamage()),
                String.format("%.3f", ars.breakdown().multiplier()),
                String.format("%.3f", ars.breakdown().uncapped())), false);
        }

        CombatDebugState.IronsSnapshot irons = CombatDebugState.lastIrons(player.getUUID());
        if (irons == null) {
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.irons_none")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.irons_header")
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.irons_spell",
                irons.spellId() == null ? "-" : irons.spellId(),
                irons.school() == null ? "-" : irons.school()), false);
            source.sendSuccess(() -> Component.translatable("commands.ans.debug.combat.irons_result",
                String.format("%.2f", irons.nativeAmount()),
                String.format("%.2f", irons.finalAmount()),
                String.format("%.2f", irons.arsBonus())), false);
        }

        return 1;
    }

    /** Ars perk attribute read that reports 0 rather than throwing in front of a diagnostic. */
    private static double arsAttribute(ServerPlayer player, net.minecraft.core.Holder<
            net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        try {
            return player.getAttributeValue(attribute);
        } catch (Throwable t) {
            return 0.0;
        }
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

        // Validation and mutation both live in the shared binder, so this command, the
        // Spellbook Binding ritual and Iron's Inscription Table cannot drift apart on what a
        // bind is allowed to do. The command keeps its own wording, its own permission check
        // and the consumption.
        int maxCap = AnsConfig.MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK.get();
        com.otectus.arsnspells.spell.IronsSpellbookBinder.BindResult result =
            com.otectus.arsnspells.spell.IronsSpellbookBinder.bind(player, scroll, book,
                com.otectus.arsnspells.spell.IronsSpellbookBinder.Caller.COMMAND);
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
            case DISABLED:
                context.getSource().sendFailure(Component.translatable("commands.ans.bind.disabled"));
                return 0;
            case NO_BOOK:
                context.getSource().sendFailure(
                    Component.translatable("commands.ans.bind.need_scroll_and_book"));
                return 0;
            case UNCASTABLE:
                // 3.0.3: refused before the scroll is consumed, rather than binding a wheel
                // entry that silently does nothing when selected.
                context.getSource().sendFailure(Component.translatable("commands.ans.bind.uncastable"));
                return 0;
            case NOT_A_CARRIER:
            case INVALID_CARRIER:
                context.getSource().sendFailure(
                    Component.translatable("commands.ans.bind.scroll_not_carrier"));
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
