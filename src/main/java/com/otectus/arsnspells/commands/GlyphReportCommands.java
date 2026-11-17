package com.otectus.arsnspells.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.data.ImportedGlyphReloadListener;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.GlyphSessions;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.LiveSpellFacts;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 3.4.1 diagnostics under {@code /ans glyph}, extending the existing glyph commands:
 * <ul>
 *   <li>{@code report} (op): every spell in the loaded native registry, addon spells included,
 *       accounted for as supported, unsupported or retired (with its catalog source, registered
 *       levels, live maximum and adapter), excluded (ANS proxies, {@code none}), or not catalogued
 *       (no identity: needs a catalog and a restart). The full report goes to
 *       {@code ars_n_spells/reports/glyph-catalog-report.txt} in the game directory;</li>
 *   <li>{@code explain <spell> [level]} (op): catalog defaults, Iron's effective values, the ANS
 *       datapack override, the resulting tier and Press terms, readiness and generation;</li>
 *   <li>{@code last} (any player, about themselves): the levels, payment and stage of their last
 *       imported-glyph outcomes;</li>
 *   <li>{@code validate} (op): reads every mapping file from the server's current data packs and
 *       reports what a reload would accept or reject, without publishing anything;</li>
 *   <li>{@code inventory export} (op): writes the loaded native registry in the inventory format
 *       {@code tools/generate_iron_glyph_catalog.py pack} consumes.</li>
 * </ul>
 * Values are labelled "effective": Iron's getters apply its own precedence (per-spell files,
 * datapack overrides, the global config, configuration events) and do not say which one decided.
 */
public final class GlyphReportCommands {
    private static final int LISTED_LINES = 20;

    private GlyphReportCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> attach(LiteralArgumentBuilder<CommandSourceStack> glyph) {
        return glyph
            .then(Commands.literal("report").requires(source -> source.hasPermission(2)).executes(GlyphReportCommands::report))
            .then(Commands.literal("explain").requires(source -> source.hasPermission(2))
                .then(Commands.argument("spell", ResourceLocationArgument.id())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ImportedGlyphCatalog.get().spells(), builder))
                    .executes(ctx -> explain(ctx, ResourceLocationArgument.getId(ctx, "spell"), 0))
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 99))
                        .executes(ctx -> explain(ctx, ResourceLocationArgument.getId(ctx, "spell"), IntegerArgumentType.getInteger(ctx, "level"))))))
            .then(Commands.literal("last").executes(GlyphReportCommands::last))
            .then(Commands.literal("validate").requires(source -> source.hasPermission(2)).executes(GlyphReportCommands::validate))
            .then(Commands.literal("inventory").requires(source -> source.hasPermission(2))
                .then(Commands.literal("export").executes(GlyphReportCommands::exportInventory)));
    }

    /** How one native spell is accounted for. */
    public record Row(String id, String provider, String status, String detail) {}

    public static List<Row> rows() {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        List<Row> rows = new ArrayList<>();
        java.util.Set<ResourceLocation> listed = new java.util.HashSet<>();
        for (IronGlyphBridge.NativeSpell spell : IronGlyphBridges.get().nativeSpells()) {
            listed.add(spell.id());
            String provider = spell.provider() + " " + spell.providerVersion();
            if (spell.none()) { rows.add(new Row(spell.id().toString(), provider, "excluded", "Iron's empty placeholder")); continue; }
            if (spell.proxy()) { rows.add(new Row(spell.id().toString(), provider, "excluded", "Ars 'n' Spells proxy for an Ars spell")); continue; }
            List<ImportedGlyphDefinition> levels = catalog.levelsOf(spell.id());
            if (levels.isEmpty()) {
                rows.add(new Row(spell.id().toString(), provider, "not_catalogued",
                    "no identity; generate a catalog for " + spell.provider() + " (tools/generate_iron_glyph_catalog.py pack) and restart"));
                continue;
            }
            ImportedGlyphDefinition first = levels.get(0);
            String status = first.status().id();
            int registered = first.registeredMax();
            String range = "registered " + first.minLevel() + "-" + registered + ", default max " + first.maxLevel() + ", live max " + spell.maxLevel()
                + (spell.maxLevel() > registered ? " (levels " + (registered + 1) + "-" + spell.maxLevel() + " need a catalog update)" : "");
            rows.add(new Row(spell.id().toString(), provider, status, range + ", adapter " + first.family().adapterId() + " v" + first.adapterVersion()
                + ", source " + first.source() + (spell.enabled() ? "" : ", disabled in Iron's")));
        }
        for (ResourceLocation spell : catalog.spells()) {
            if (listed.contains(spell)) continue;
            ImportedGlyphDefinition first = catalog.levelsOf(spell).get(0);
            rows.add(new Row(spell.toString(), "-", first.status().id(), "catalogued (registered " + first.minLevel() + "-" + catalog.registeredMax(spell)
                + ", source " + first.source() + ") but not in the loaded registry: dormant"));
        }
        return rows;
    }

    private static int report(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<Row> rows = rows();
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Row row : rows) counts.merge(row.status(), 1, Integer::sum);
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        StringBuilder text = new StringBuilder("# Ars 'n' Spells registry-to-catalog report\n");
        text.append("catalog ").append(catalog.catalogVersion()).append(", identity ").append(catalog.identityDigest()).append('\n');
        for (ImportedGlyphCatalog.SourceReport report : catalog.sources())
            text.append("source ").append(report.name()).append(": ").append(report.spells()).append(" spells, ").append(report.glyphs())
                .append(" glyphs").append(report.errors().isEmpty() ? "" : " " + report.errors()).append('\n');
        List<String> missing = com.otectus.arsnspells.glyph.GlyphIdentityRecord.lastMissing();
        text.append("identities this world used that are not registered now: ").append(missing.size())
            .append(missing.isEmpty() ? "" : " (first " + missing.get(0) + ")").append('\n');
        text.append("live readiness ").append(GlyphBehavior.live().readiness()).append(", generation ").append(GlyphBehavior.generation()).append('\n');
        counts.forEach((status, count) -> text.append(status).append(": ").append(count).append('\n'));
        text.append('\n');
        for (Row row : rows) text.append(row.id()).append(" | ").append(row.provider()).append(" | ").append(row.status()).append(" | ").append(row.detail()).append('\n');
        Path file = source.getServer().getServerDirectory().toPath().resolve("ars_n_spells/reports/glyph-catalog-report.txt");
        String written;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
            written = file.toString();
        } catch (java.io.IOException failure) {
            written = "not written: " + failure.getMessage();
        }
        String path = written;
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.report.summary", rows.size(), counts.toString(), path), false);
        rows.stream().filter(row -> !row.status().equals("supported") && !row.status().equals("excluded")).limit(LISTED_LINES)
            .forEach(row -> source.sendSuccess(() -> Component.literal("  " + row.id() + ": " + row.status() + " - " + row.detail()), false));
        return rows.size();
    }

    private static int explain(CommandContext<CommandSourceStack> context, ResourceLocation spell, int level) {
        CommandSourceStack source = context.getSource();
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        List<ImportedGlyphDefinition> levels = catalog.levelsOf(spell);
        if (levels.isEmpty()) {
            source.sendFailure(Component.translatable("commands.ans.glyph.coverage.unknown", spell.toString()));
            return 0;
        }
        ImportedGlyphDefinition first = levels.get(0);
        LiveSpellFacts facts = GlyphBehavior.facts(first);
        GlyphMappingSnapshot mappings = GlyphMappingSnapshot.current();
        line(source, "catalog: " + first.status().id() + ", registered 1-" + levels.size() + ", default max " + first.maxLevel()
            + ", school " + first.school() + ", learning " + first.requiresLearning() + ", adapter " + first.family().adapterId()
            + ", source " + first.source());
        line(source, "effective (Iron's, " + GlyphBehavior.live().readiness().name().toLowerCase(Locale.ROOT) + "): registered "
            + facts.registered() + ", enabled " + facts.enabled() + ", max " + facts.maxLevel() + ", school " + facts.school()
            + ", learning " + facts.requiresLearning());
        line(source, "datapack: " + (mappings.selfOnly(spell) ? "Self only; " : "") + "generation " + mappings.generation()
            + ", behaviour generation " + GlyphBehavior.generation() + ", digest " + GlyphBehavior.digest().substring(0, 16));
        for (ImportedGlyphDefinition definition : levels) {
            if (level > 0 && definition.level() != level) continue;
            GlyphAvailability.Status status = GlyphAvailability.status(definition);
            int tier = GlyphAvailability.tier(definition);
            GlyphMappingSnapshot.PressTerms terms = mappings.pressTerms(definition, tier);
            String refusal = mappings.refusal(definition, facts.school());
            line(source, "  level " + definition.level() + (definition.reserved() ? " (reserved)" : "") + ": " + (status.available()
                ? "available" : status.reason().name().toLowerCase(Locale.ROOT)) + (status.provisional() ? " (provisional)" : "")
                + ", tier " + tier + (mappings.tierOverride(definition) != null ? " (datapack)" : "") + ", Press " + terms.sourceCost()
                + " Source, " + terms.catalystCount() + " x " + terms.catalystTag() + ", " + terms.durationTicks() + " ticks"
                + (refusal == null ? "" : ", datapack: " + refusal));
        }
        if (level > levels.size()) line(source, "  level " + level + " is not registered: a catalog that reserves it, and a restart, are needed");
        return levels.size();
    }

    private static int last(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<GlyphSessions.CastRecord> records = GlyphSessions.records(player.getUUID());
        if (records.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.ans.glyph.last.none"), false);
            return 0;
        }
        for (GlyphSessions.CastRecord record : records)
            line(context.getSource(), record.stage() + " " + record.spell() + ": base " + record.base() + ", augmented " + record.augmented()
                + ", native " + (record.nativeLevel() < 0 ? "-" : record.nativeLevel()) + ", cast " + (record.finalLevel() < 0 ? "-" : record.finalLevel())
                + ", paid " + record.paid() + ", " + record.outcome());
        return records.size();
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<GlyphMappingSnapshot.Source> sources = ImportedGlyphReloadListener.readSources(source.getServer().getResourceManager());
        GlyphMappingSnapshot candidate = GlyphMappingSnapshot.build(sources, ImportedGlyphCatalog.get(), -1);
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.validate.summary", sources.size(),
            candidate.winners().size(), candidate.errors().size(), candidate.failedClosed().size()), false);
        candidate.errors().stream().limit(LISTED_LINES).forEach(error -> line(source, "  " + error));
        return candidate.errors().isEmpty() ? 1 : 0;
    }

    private static int exportInventory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!com.otectus.arsnspells.compat.IronsCompat.isLoaded()) {
            source.sendFailure(Component.translatable("message.ars_n_spells.glyph.dormant.irons_absent", "-"));
            return 0;
        }
        Path file = source.getServer().getServerDirectory().toPath().resolve("ars_n_spells/reports/glyph-inventory.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, com.otectus.arsnspells.compat.irons.glyph.IronsSpellInventory.toJson(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            source.sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.ans.glyph.inventory.written", file.toString()), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
