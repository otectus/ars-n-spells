package com.otectus.arsnspells.commands;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.GlyphMappingReloadListener;
import com.otectus.arsnspells.glyph.behavior.GlyphMappings;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.catalog.SpellInventoryEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 3.4.1 diagnostics under {@code /ans glyph}, extending the existing glyph commands (operators):
 * <ul>
 *   <li>{@code report}: every spell in the loaded native registry, addon spells included, accounted
 *       for as supported, unsupported or retired (catalog source, registered levels, live maximum,
 *       adapter), excluded (ANS proxies, {@code none}), or not catalogued (no identity: a catalog and
 *       a restart are needed). The full report is written to
 *       {@code ars_n_spells/reports/glyph-catalog-report.txt} in the game directory;</li>
 *   <li>{@code explain <spell> [level]}: catalog defaults, Iron's effective values, the data-pack
 *       override, the resulting tier and Press terms, readiness and generation;</li>
 *   <li>{@code validate}: what a reload of the server's current data packs would accept or reject,
 *       without applying anything;</li>
 *   <li>{@code inventory export}: the loaded native registry in the inventory format
 *       {@code tools/generate_iron_glyph_catalog.py} consumes.</li>
 * </ul>
 * Values are labelled "effective": Iron's getters apply its own precedence and do not say which
 * source decided.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class GlyphReportCommands {
    private static final int LISTED = 20;

    private GlyphReportCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ans").then(Commands.literal("glyph")
            .then(Commands.literal("report").requires(source -> source.hasPermission(2)).executes(GlyphReportCommands::report))
            .then(Commands.literal("explain").requires(source -> source.hasPermission(2))
                .then(Commands.argument("spell", ResourceLocationArgument.id())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                        ImportedGlyphCatalog.get().entries().stream().map(CatalogEntry::spellId), builder))
                    .executes(ctx -> explain(ctx, ResourceLocationArgument.getId(ctx, "spell"), 0))
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, ImportedGlyphKey.MAX_LEVEL))
                        .executes(ctx -> explain(ctx, ResourceLocationArgument.getId(ctx, "spell"), IntegerArgumentType.getInteger(ctx, "level"))))))
            .then(Commands.literal("validate").requires(source -> source.hasPermission(2)).executes(GlyphReportCommands::validate))
            .then(Commands.literal("inventory").requires(source -> source.hasPermission(2))
                .then(Commands.literal("export").executes(GlyphReportCommands::exportInventory)))));
    }

    /** How one native spell is accounted for. */
    public record Row(String id, String provider, String status, String detail) {}

    public static List<Row> rows() {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        List<Row> rows = new ArrayList<>();
        Set<ResourceLocation> listed = new HashSet<>();
        for (SpellInventoryEntry spell : IronGlyphBridges.get().inventory()) {
            ResourceLocation id = ResourceLocation.parse(spell.spellId());
            listed.add(id);
            String provider = id.getNamespace() + " " + ModList.get().getModContainerById(id.getNamespace())
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
            if (spell.none()) { rows.add(new Row(spell.spellId(), provider, "excluded", "Iron's empty placeholder")); continue; }
            if (spell.ansProxy()) { rows.add(new Row(spell.spellId(), provider, "excluded", "Ars 'n' Spells proxy for an Ars spell")); continue; }
            CatalogEntry entry = catalog.entry(id);
            if (entry == null) {
                rows.add(new Row(spell.spellId(), provider, "not_catalogued",
                    "no identity; generate a catalog for " + id.getNamespace() + " (tools/generate_iron_glyph_catalog.py pack) and restart"));
                continue;
            }
            int liveMax = snapshot.liveMax(entry);
            rows.add(new Row(spell.spellId(), provider, entry.status(), "registered " + entry.minLevel() + "-" + entry.registeredMax()
                + ", default max " + entry.maxLevel() + ", live max " + liveMax
                + (liveMax > entry.registeredMax() ? " (levels " + (entry.registeredMax() + 1) + "-" + liveMax + " need a catalog update)" : "")
                + ", adapter " + entry.adapter() + " v" + entry.adapterVersion() + ", source " + entry.sourceCatalog()
                + (spell.enabled() ? "" : ", disabled in Iron's")));
        }
        for (CatalogEntry entry : catalog.entries()) {
            if (listed.contains(entry.spellId())) continue;
            rows.add(new Row(entry.spellId().toString(), "-", entry.status(), "catalogued (registered " + entry.minLevel() + "-"
                + entry.registeredMax() + ", source " + entry.sourceCatalog() + ") but not in the loaded registry: dormant"));
        }
        return rows;
    }

    private static int report(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<Row> rows = rows();
        Map<String, Integer> counts = new TreeMap<>();
        for (Row row : rows) counts.merge(row.status(), 1, Integer::sum);
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        StringBuilder text = new StringBuilder("# Ars 'n' Spells registry-to-catalog report\n");
        text.append("catalog hash ").append(catalog.hash()).append(", ").append(catalog.variantCount()).append(" glyph variants\n");
        for (String error : catalog.errors()) text.append("catalog error: ").append(error).append('\n');
        text.append("behaviour generation ").append(GlyphBehavior.current().generation()).append(", Iron's configuration ")
            .append(GlyphBehavior.current().ironsConfigPending() ? "pending" : "ready").append('\n');
        List<String> missing = com.otectus.arsnspells.glyph.GlyphIdentityRecord.lastMissing();
        text.append("identities this world used that are not registered now: ").append(missing.size())
            .append(missing.isEmpty() ? "" : " (first " + missing.get(0) + ")").append('\n');
        counts.forEach((status, count) -> text.append(status).append(": ").append(count).append('\n'));
        text.append('\n');
        for (Row row : rows) text.append(row.id()).append(" | ").append(row.provider()).append(" | ").append(row.status())
            .append(" | ").append(row.detail()).append('\n');
        Path file = source.getServer().getServerDirectory().resolve("ars_n_spells/reports/glyph-catalog-report.txt");
        String written;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
            written = file.toString();
        } catch (java.io.IOException failure) {
            written = "not written: " + failure.getMessage();
        }
        String path = written;
        source.sendSuccess(() -> Component.literal(rows.size() + " native spells accounted for: " + counts + ". Full report: " + path)
            .withStyle(ChatFormatting.GOLD), false);
        rows.stream().filter(row -> !row.status().equals("supported") && !row.status().equals("excluded")).limit(LISTED)
            .forEach(row -> source.sendSuccess(() -> Component.literal("  " + row.id() + ": " + row.status() + " - " + row.detail()), false));
        return rows.size();
    }

    private static int explain(CommandContext<CommandSourceStack> context, ResourceLocation spell, int level) {
        CommandSourceStack source = context.getSource();
        CatalogEntry entry = ImportedGlyphCatalog.get().entry(spell);
        if (entry == null) {
            source.sendFailure(Component.literal(spell + " is not in the imported-glyph catalog"));
            return 0;
        }
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        GlyphBehaviorSnapshot.SpellFacts facts = snapshot.spell(spell);
        line(source, "catalog: " + entry.status() + ", registered " + entry.minLevel() + "-" + entry.registeredMax() + ", default max "
            + entry.maxLevel() + ", school " + entry.school() + ", learning " + entry.requiresLearning() + ", adapter " + entry.adapter()
            + ", source " + entry.sourceCatalog());
        line(source, "effective (Iron's, " + (snapshot.ironsConfigPending() ? "pending" : "ready") + "): max "
            + (facts == null ? "?" : facts.liveMax()) + ", school " + (facts == null ? "?" : facts.school()) + ", learning "
            + (facts != null && facts.requiresLearning()));
        GlyphMappings.SpellMapping mapping = GlyphBehavior.mappings().mappings().get(spell);
        line(source, "data packs: " + (mapping == null ? "no mapping" : "mapped by " + snapshot.mappingWinners().get(spell))
            + (snapshot.selfOnly(spell) ? ", Self only" : "") + "; generation " + snapshot.generation() + ", digest "
            + snapshot.digest().substring(0, 12));
        for (ImportedGlyphKey key : entry.keys()) {
            if (level > 0 && key.baseLevel() != level) continue;
            VariantBehavior variant = snapshot.variant(key);
            line(source, "  level " + key.baseLevel() + (entry.reserved(key.baseLevel()) ? " (reserved)" : "") + ": "
                + variant.dormancy().name().toLowerCase(Locale.ROOT) + ", tier " + variant.tier() + ", Press " + variant.pressSource()
                + " Source, " + variant.pressGems() + " x #" + variant.catalyst() + ", " + variant.pressTicks() + " ticks");
        }
        if (level > entry.registeredMax())
            line(source, "  level " + level + " is not registered: a catalog that reserves it, and a restart, are needed");
        return 1;
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        GlyphMappings.Result candidate = GlyphMappingReloadListener.read(source.getServer().getResourceManager());
        source.sendSuccess(() -> Component.literal(candidate.mappings().size() + " spell mappings, " + candidate.tiers().size()
            + " press tiers, " + candidate.errors().size() + " errors, " + candidate.failedClosed().size()
            + " spells would fail closed. Nothing was applied.").withStyle(candidate.valid() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        candidate.errors().stream().limit(LISTED).forEach(error -> line(source, "  " + error));
        return candidate.valid() ? 1 : 0;
    }

    private static int exportInventory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!IronGlyphBridges.get().available()) {
            source.sendFailure(Component.translatable("ars_n_spells.glyph.dormant.irons_absent"));
            return 0;
        }
        JsonArray spells = new JsonArray();
        IronGlyphBridges.get().inventory().forEach(entry -> spells.add(entry.toJson()));
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("mod_id", "irons_spellbooks");
        root.addProperty("mod_version", ModList.get().getModContainerById("irons_spellbooks")
            .map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
        root.add("spells", spells);
        Path file = source.getServer().getServerDirectory().resolve("ars_n_spells/reports/glyph-inventory.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            source.sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Native spell inventory written to " + file), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
