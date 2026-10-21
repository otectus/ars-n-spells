package com.otectus.arsnspells.commands;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.util.CasterUtil;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.data.ImportedGlyphReloadListener;
import com.otectus.arsnspells.glyph.GlyphAdmission;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.GlyphFamily;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphItem;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /ans glyph ...}: read-only diagnostics for imported Iron's glyphs.
 * <ul>
 *   <li>{@code inspect}: the held glyph, the imported glyphs in the held spell, or what the
 *       Glyph Press would make of a held scroll (the Press's own scroll reader);</li>
 *   <li>{@code catalog} (op): catalog identity, behaviour generation, availability by reason;</li>
 *   <li>{@code coverage [spell]} (op): per-adapter coverage, or one spell level by level;</li>
 *   <li>{@code mappings} (op): the winning data file and pack for each override, and the errors
 *       of the last rejected load;</li>
 *   <li>{@code press <pos>} (op): one Glyph Press's state, job and Source.</li>
 * </ul>
 * Nothing here converts, grants or changes anything.
 */
public final class GlyphDiagnosticsCommands {
    private static final int LISTED_LINES = 20;

    private GlyphDiagnosticsCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("glyph")
            .then(Commands.literal("inspect").executes(GlyphDiagnosticsCommands::inspect))
            .then(Commands.literal("catalog").requires(source -> source.hasPermission(2)).executes(GlyphDiagnosticsCommands::catalog))
            .then(Commands.literal("coverage").requires(source -> source.hasPermission(2))
                .executes(GlyphDiagnosticsCommands::coverage)
                .then(Commands.argument("spell", ResourceLocationArgument.id())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ImportedGlyphCatalog.get().spells(), builder))
                    .executes(ctx -> coverageOf(ctx, ResourceLocationArgument.getId(ctx, "spell")))))
            .then(Commands.literal("mappings").requires(source -> source.hasPermission(2)).executes(GlyphDiagnosticsCommands::mappings))
            .then(Commands.literal("press").requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(GlyphDiagnosticsCommands::press)));
    }

    // ---- inspect ------------------------------------------------------------------------------

    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        CommandSourceStack source = context.getSource();
        if (held.getItem() instanceof ImportedGlyphItem glyph) {
            describe(source, player, glyph.definition());
            return 1;
        }
        Spell spell = heldSpell(held);
        if (spell != null) {
            List<GlyphAdmission.Found> found = GlyphAdmission.importedIn(spell);
            if (found.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inspect.none_in_spell"), false);
                return 0;
            }
            for (GlyphAdmission.Found entry : found) {
                ImportedGlyphDefinition definition = entry.effect().definition();
                int level = GlyphAdmission.augmentedLevel(spell, entry.index(), definition, player);
                source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inspect.spell_entry", entry.index() + 1,
                    definition.glyphId().toString(), level, status(definition)), false);
            }
            return found.size();
        }
        IronGlyphBridge bridge = IronGlyphBridges.get();
        if (bridge.present()) {
            IronGlyphBridge.ScrollReading reading = bridge.readScroll(held);
            if (reading.kind() != IronGlyphBridge.ScrollReading.Kind.NOT_A_SCROLL) {
                if (reading.kind() != IronGlyphBridge.ScrollReading.Kind.OK) {
                    source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inspect.scroll_refused",
                        reading.kind().name().toLowerCase(Locale.ROOT)), false);
                    return 0;
                }
                var definition = ImportedGlyphCatalog.get().byKey(new ImportedGlyphKey(reading.spell(), reading.level()));
                if (definition.isEmpty()) {
                    source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inspect.scroll_uncatalogued",
                        reading.spell().toString(), reading.level()), false);
                    return 0;
                }
                source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inspect.scroll_makes",
                    definition.get().glyphId().toString()), false);
                describe(source, player, definition.get());
                return 1;
            }
        }
        source.sendFailure(Component.translatable("commands.ans.glyph.inspect.nothing"));
        return 0;
    }

    private static Spell heldSpell(ItemStack held) {
        if (held.isEmpty()) return null;
        try {
            var caster = CasterUtil.getCaster(held);
            Spell spell = caster == null ? null : caster.getSpell();
            return spell == null || spell.recipe == null || spell.recipe.isEmpty() ? null : spell;
        } catch (RuntimeException notACaster) {
            return null;
        }
    }

    private static void describe(CommandSourceStack source, ServerPlayer player, ImportedGlyphDefinition definition) {
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(player).orElse(null);
        boolean known = cap != null && com.otectus.arsnspells.glyph.ImportedGlyphRegistrar.part(definition.key())
            .map(cap::knowsGlyph).orElse(false);
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inspect.glyph", definition.glyphId().toString(),
            definition.spell().toString(), definition.level(), definition.maxLevel()), false);
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inspect.glyph_detail",
            definition.family().adapterId(), forms(definition.family()), GlyphAvailability.tier(definition),
            definition.school().toString(), definition.castType()), false);
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inspect.glyph_status", status(definition),
            Component.translatable(known ? "commands.ans.glyph.known" : "commands.ans.glyph.unknown")), false);
    }

    private static Component status(ImportedGlyphDefinition definition) {
        GlyphAvailability.Status status = GlyphAvailability.status(definition);
        return status.available() ? Component.translatable("commands.ans.glyph.available") : status.message(definition);
    }

    private static String forms(GlyphFamily family) {
        return family.forms().stream().map(id -> id.getPath().replace("glyph_", "")).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }

    // ---- catalog / coverage / mappings / press -----------------------------------------------

    private static int catalog(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.current();
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.catalog.identity", catalog.catalogVersion(),
            catalog.spells().size(), catalog.size(), catalog.identityDigest().substring(0, 16),
            String.join(", ", catalog.ironsBuilds())), false);
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.catalog.behaviour", snapshot.generation(),
            snapshot.digest().substring(0, 16), snapshot.failedClosed().size(), ImportedGlyphReloadListener.lastRejected().size()), false);
        Map<GlyphAvailability.Reason, Integer> byReason = new EnumMap<>(GlyphAvailability.Reason.class);
        for (ImportedGlyphDefinition definition : catalog.definitions())
            byReason.merge(GlyphAvailability.status(definition).reason(), 1, Integer::sum);
        byReason.forEach((reason, count) -> source.sendSuccess(() -> Component.translatable("commands.ans.glyph.catalog.reason",
            reason.name().toLowerCase(Locale.ROOT), count), false));
        return catalog.size();
    }

    private static int coverage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<GlyphFamily, int[]> byFamily = new EnumMap<>(GlyphFamily.class);
        Map<ResourceLocation, GlyphAvailability.Reason> unavailable = new TreeMap<>();
        for (ResourceLocation spell : ImportedGlyphCatalog.get().spells()) {
            List<ImportedGlyphDefinition> levels = ImportedGlyphCatalog.get().levelsOf(spell);
            int[] counts = byFamily.computeIfAbsent(levels.get(0).family(), family -> new int[2]);
            counts[0]++;
            counts[1] += levels.size();
            GlyphAvailability.Status status = GlyphAvailability.status(levels.get(0));
            if (!status.available()) unavailable.put(spell, status.reason());
        }
        byFamily.forEach((family, counts) -> source.sendSuccess(() -> Component.translatable("commands.ans.glyph.coverage.family",
            family.adapterId(), counts[0], counts[1], forms(family)), false));
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.coverage.unavailable", unavailable.size()), false);
        unavailable.entrySet().stream().limit(LISTED_LINES).forEach(entry -> source.sendSuccess(() -> Component.literal(
            "  " + entry.getKey() + ": " + entry.getValue().name().toLowerCase(Locale.ROOT)), false));
        return byFamily.size();
    }

    private static int coverageOf(CommandContext<CommandSourceStack> context, ResourceLocation spell) {
        CommandSourceStack source = context.getSource();
        List<ImportedGlyphDefinition> levels = ImportedGlyphCatalog.get().levelsOf(spell);
        if (levels.isEmpty()) {
            source.sendFailure(Component.translatable("commands.ans.glyph.coverage.unknown", spell.toString()));
            return 0;
        }
        ImportedGlyphDefinition first = levels.get(0);
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.coverage.spell", spell.toString(),
            first.family().adapterId(), forms(first.family()), first.castType(), first.requiresLearning()), false);
        for (ImportedGlyphDefinition definition : levels) {
            GlyphMappingSnapshot.PressTerms terms = GlyphMappingSnapshot.current().pressTerms(definition, GlyphAvailability.tier(definition));
            source.sendSuccess(() -> Component.translatable("commands.ans.glyph.coverage.level", definition.level(),
                GlyphAvailability.tier(definition), terms.sourceCost(), terms.catalystCount(), status(definition)), false);
        }
        return levels.size();
    }

    private static int mappings(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.current();
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.mappings.header", snapshot.generation(),
            snapshot.winners().size(), snapshot.errors().size()), false);
        snapshot.winners().stream().limit(LISTED_LINES).forEach(winner ->
            source.sendSuccess(() -> Component.literal("  " + winner), false));
        List<String> rejected = ImportedGlyphReloadListener.lastRejected();
        if (!rejected.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ans.glyph.mappings.rejected", rejected.size()), false);
            rejected.stream().limit(LISTED_LINES).forEach(error -> source.sendSuccess(() -> Component.literal("  " + error), false));
        }
        return snapshot.winners().size();
    }

    private static int press(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!(context.getSource().getLevel().getBlockEntity(pos) instanceof GlyphPressBlockEntity press)) {
            context.getSource().sendFailure(Component.translatable("commands.ans.glyph.press.none"));
            return 0;
        }
        String description = press.describe();
        context.getSource().sendSuccess(() -> Component.literal(description), false);
        return 1;
    }
}
