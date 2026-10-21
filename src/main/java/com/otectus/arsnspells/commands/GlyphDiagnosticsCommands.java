package com.otectus.arsnspells.commands;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.glyph.GlyphCompositionValidator;
import com.otectus.arsnspells.glyph.ImportedGlyphItem;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /ans glyph}: read-only diagnostics for imported Iron's glyphs.
 * <ul>
 *   <li>{@code inspect}: the held imported glyph, or every imported glyph in the held spell
 *       caster's current spell, with its identity, adapter, target, tier, Press terms, dormancy
 *       and augments, and the composition errors the spell would get;</li>
 *   <li>{@code coverage} (operators): catalog identity, coverage by adapter and status, dormancy
 *       counts, data-pack mapping winners and errors;</li>
 *   <li>{@code sessions [player]}: open imported-glyph casts and the latest outcomes (another
 *       player's for operators only).</li>
 * </ul>
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class GlyphDiagnosticsCommands {
    private GlyphDiagnosticsCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ans")
            .then(Commands.literal("glyph")
                .then(Commands.literal("inspect").executes(GlyphDiagnosticsCommands::inspect))
                .then(Commands.literal("coverage").requires(source -> source.hasPermission(2))
                    .executes(GlyphDiagnosticsCommands::coverage))
                .then(Commands.literal("sessions").executes(context -> sessions(context, context.getSource().getPlayerOrException()))
                    .then(Commands.argument("player", EntityArgument.player()).requires(source -> source.hasPermission(2))
                        .executes(context -> sessions(context, EntityArgument.getPlayer(context, "player")))))));
    }

    private static void line(CommandSourceStack source, String text, ChatFormatting style) {
        source.sendSuccess(() -> Component.literal(text).withStyle(style), false);
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof ImportedGlyphItem glyph) {
            describe(source, glyph.effect());
            return 1;
        }
        Spell spell = held.isEmpty() ? null : SpellCasterRegistry.from(held) == null ? null : SpellCasterRegistry.from(held).getSpell();
        if (spell == null || spell.isEmpty()) {
            source.sendFailure(Component.translatable("ars_n_spells.diagnostics.glyph.nothing_held"));
            return 0;
        }
        int found = 0;
        List<AbstractSpellPart> recipe = spell.unsafeList();
        for (int i = 0; i < recipe.size(); i++) {
            if (recipe.get(i) instanceof ImportedIronEffect effect) {
                line(source, "#" + i + ":", ChatFormatting.GRAY);
                describe(source, effect);
                found++;
            }
        }
        if (found == 0) {
            source.sendSuccess(() -> Component.translatable("ars_n_spells.diagnostics.glyph.none_in_spell"), false);
            return 1;
        }
        List<SpellValidationError> errors = GlyphCompositionValidator.INSTANCE.validate(recipe);
        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("ars_n_spells.diagnostics.glyph.composition_ok").withStyle(ChatFormatting.GREEN), false);
        }
        for (SpellValidationError error : errors) {
            source.sendSuccess(() -> error.makeTextComponentExisting().copy().withStyle(ChatFormatting.RED), false);
        }
        return found;
    }

    private static void describe(CommandSourceStack source, ImportedIronEffect effect) {
        CatalogEntry entry = effect.entry();
        VariantBehavior behavior = GlyphBehavior.current().variant(effect.key());
        line(source, effect.getRegistryName() + "  (" + entry.spellId() + " level " + effect.key().baseLevel()
            + " of " + entry.minLevel() + ".." + entry.maxLevel() + ")", ChatFormatting.LIGHT_PURPLE);
        line(source, "  adapter " + entry.adapter() + ", target " + entry.target().name().toLowerCase(Locale.ROOT)
            + ", cast " + entry.castType().toLowerCase(Locale.ROOT) + (entry.requiresLearning() ? ", requires Iron's learning" : "")
            + (GlyphBehavior.current().selfOnly(entry.spellId()) ? ", restricted to Self by a pack" : ""), ChatFormatting.GRAY);
        line(source, "  tier " + behavior.tier() + "; Press: " + behavior.pressGems() + " x #" + behavior.catalyst() + ", "
            + behavior.pressSource() + " Source, " + behavior.pressTicks() + " ticks", ChatFormatting.GRAY);
        line(source, "  augments " + effect.compatibleAugmentList().stream().map(a -> a.getRegistryName().getPath()).toList()
            + ", Iron's mana cost " + IronGlyphBridges.get().manaCost(entry.spellId(), effect.key().baseLevel()), ChatFormatting.GRAY);
        Dormancy dormancy = behavior.dormancy();
        source.sendSuccess(() -> Component.literal("  ").append(Component.translatable(dormancy.translationKey()))
            .withStyle(dormancy.available() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
    }

    private static int coverage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        line(source, "catalog " + catalog.hash().substring(0, Math.min(12, catalog.hash().length())) + ": " + catalog.entries().size()
            + " spells, " + catalog.variantCount() + " glyph variants; behaviour generation " + snapshot.generation()
            + " digest " + snapshot.digest().substring(0, 12), ChatFormatting.GOLD);
        Map<String, Integer> byAdapter = new TreeMap<>();
        Map<String, Integer> byStatus = new TreeMap<>();
        for (CatalogEntry entry : catalog.entries()) {
            byAdapter.merge(entry.adapter().getPath(), 1, Integer::sum);
            byStatus.merge(entry.status(), 1, Integer::sum);
        }
        line(source, "adapters " + byAdapter, ChatFormatting.GRAY);
        line(source, "status " + byStatus, ChatFormatting.GRAY);
        line(source, "dormancy " + snapshot.dormancyCounts(), ChatFormatting.GRAY);
        line(source, "Iron's " + (IronGlyphBridges.get().available() ? "loaded" : "absent") + "; glyphs "
            + (snapshot.settings().glyphsEnabled() ? "enabled" : "disabled") + "; Press "
            + (snapshot.settings().pressEnabled() ? "enabled" : "disabled"), ChatFormatting.GRAY);
        for (Map.Entry<ResourceLocation, String> winner : snapshot.mappingWinners().entrySet()) {
            line(source, "mapping " + winner.getKey() + " <- " + winner.getValue(), ChatFormatting.GRAY);
        }
        List<String> errors = snapshot.mappingErrors();
        for (int i = 0; i < Math.min(10, errors.size()); i++) line(source, "mapping error: " + errors.get(i), ChatFormatting.RED);
        if (errors.size() > 10) line(source, "... " + (errors.size() - 10) + " more in the server log", ChatFormatting.RED);
        for (String rejected : GlyphBehavior.rejectedReload()) line(source, "rejected reload: " + rejected, ChatFormatting.RED);
        return 1;
    }

    private static int sessions(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        CommandSourceStack source = context.getSource();
        List<String> open = GlyphSessions.describe(target.getUUID());
        line(source, target.getGameProfile().getName() + ": " + open.size() + " open imported-glyph cast(s)", ChatFormatting.GOLD);
        open.forEach(text -> line(source, "  " + text, ChatFormatting.GRAY));
        var outcomes = GlyphSessions.outcomes(target.getUUID());
        for (int i = Math.max(0, outcomes.size() - 10); i < outcomes.size(); i++) {
            var outcome = outcomes.get(i);
            line(source, "  " + outcome.spell() + " " + outcome.level() + " " + outcome.outcome() + " (" + outcome.detail() + ")",
                outcome.outcome() == com.otectus.arsnspells.glyph.runtime.GlyphOutcome.REJECTED ? ChatFormatting.RED : ChatFormatting.GRAY);
        }
        return 1;
    }
}
