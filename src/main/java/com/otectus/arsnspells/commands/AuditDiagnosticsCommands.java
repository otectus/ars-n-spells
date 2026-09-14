package com.otectus.arsnspells.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.network.CarrierFingerprint;
import com.otectus.arsnspells.progression.ProgressionAttributes;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.util.SchoolMappings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** Read-only self-service diagnostics; never prints payload NBT or modifies saved data. */
@EventBusSubscriber(modid = "ars_n_spells")
public final class AuditDiagnosticsCommands {
    private AuditDiagnosticsCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ans")
            .then(Commands.literal("diagnose").executes(AuditDiagnosticsCommands::diagnose))
            .then(Commands.literal("inspect").executes(AuditDiagnosticsCommands::inspect))
            .then(Commands.literal("schools").executes(AuditDiagnosticsCommands::journal))
            .then(Commands.literal("journal").executes(AuditDiagnosticsCommands::journal)
                .then(Commands.literal("view").executes(AuditDiagnosticsCommands::journalView)))
            .then(Commands.literal("removal_report").executes(AuditDiagnosticsCommands::removalReport)));
    }
    private static void say(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable("ars_n_spells.diagnostics." + key, args), false);
    }
    private static int diagnose(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        for (String id : List.of("ars_n_spells", "minecraft", "forge", "neoforge", "ars_nouveau",
                "irons_spellbooks", "ars_elemental", "ars_elemancy", "ars_zero", "toomanyglyphs",
                "covenant_of_the_seven", "enigmaticlegacy", "curios", "jei", "emi")) {
            String version = ModList.get().getModContainerById(id)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("absent");
            say(source, "mod", id, version);
        }
        var routing = BridgeManager.getRoutingSnapshot();
        say(source, "routing", routing.requestedMode(), routing.effectiveMode(), routing.authoritativeUnit(), routing.generation());
        say(source, "adapters", routing.nativeArsAdapterId(), routing.nativeIronsAdapterId().orElse("absent"));
        say(source, "rates", AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get(), AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get());
        say(source, "mapping", SchoolMappings.get().glyphMappingCount(), SchoolMappings.get().digest());
        if (source.getEntity() instanceof ServerPlayer player) {
            say(source, "carrier", CarrierFingerprint.of(player.getMainHandItem()));
        }
        say(source, "evidence");
        return 1;
    }
    private static int journal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Map<String, Integer> affinity = affinity(player);
        Map<String, Integer> counts = counts(player);
        SortedSet<String> keys = new TreeSet<>(affinity.keySet());
        keys.addAll(counts.keySet());
        say(source, "mapping", SchoolMappings.get().glyphMappingCount(), SchoolMappings.get().digest());
        say(source, "caps", AnsConfig.PROGRESSION_BONUS_PER_CAST.get(), AnsConfig.PROGRESSION_BONUS_CAP.get());
        if (keys.isEmpty()) say(source, "empty_journal");
        keys.stream().limit(64).forEach(key -> say(source, "school", key, affinity.getOrDefault(key, 0),
            counts.getOrDefault(key, 0), String.format(Locale.ROOT, "%.4f", appliedBonus(player, key)), binding(key)));
        if (keys.size() > 64) say(source, "truncated", keys.size() - 64);
        return keys.size();
    }
    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        var inspected = HeldSpellInspection.inspect(player, player.getMainHandItem());
        say(source, "carrier", CarrierFingerprint.of(player.getMainHandItem()));
        if (!inspected.valid()) { source.sendFailure(Component.translatable(inspected.failureKey())); return 0; }
        Component selection = inspected.crossCast()
            ? Component.translatable("ars_n_spells.diagnostics.inspect_ans_selection", inspected.selection().substring("ans_entry_".length()))
            : Component.translatable("ars_n_spells.diagnostics.inspect_" + inspected.selection());
        say(source, "inspect_identity", selection, inspected.spellId(), inspected.school());
        var rules = com.otectus.arsnspells.casting.QuoteService.currentRules();
        var policy = inspected.crossCast() ? com.otectus.arsnspells.contract.CarrierPolicy.REUSABLE_BOOK_SEMANTICS
            : com.otectus.arsnspells.contract.CarrierPolicy.NATIVE_ONLY;
        com.otectus.arsnspells.contract.CostQuote quote;
        try {
            quote = com.otectus.arsnspells.casting.QuoteService.quote(player, inspected.origin(), inspected.baseCost(), rules, policy);
        } catch (IllegalArgumentException invalidQuote) {
            source.sendFailure(Component.translatable("ars_n_spells.diagnostics.inspect_quote_unavailable"));
            return 0;
        }
        say(source, "inspect_basis", inspected.baseCost(), inspected.origin(), quote.rulesGeneration(), SchoolMappings.get().digest());
        for (var leg : quote.legs()) {
            var bridge = BridgeManager.getNativeBridge(leg.unit());
            say(source, "inspect_leg", leg.unit(), leg.amount(), bridge == null ? "unavailable" : bridge.getMana(player));
        }
        say(source, "inspect_scope");
        return 1;
    }

    private static int journalView(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Map<String, Integer> affinity = affinity(player), counts = counts(player);
        SortedSet<String> keys = new TreeSet<>(affinity.keySet()); keys.addAll(counts.keySet());
        var rows = keys.stream().filter(key -> key.length() <= 256).limit(com.otectus.arsnspells.network.SchoolJournalSnapshot.MAX_ROWS)
            .map(key -> new com.otectus.arsnspells.network.SchoolJournalSnapshot.Row(key,
                affinity.getOrDefault(key, 0), counts.getOrDefault(key, 0), appliedBonus(player, key), binding(key))).toList();
        var snapshot = new com.otectus.arsnspells.network.SchoolJournalSnapshot(SchoolMappings.get().digest(),
            AnsConfig.PROGRESSION_BONUS_PER_CAST.get(), AnsConfig.PROGRESSION_BONUS_CAP.get(), keys.size(), rows);
        com.otectus.arsnspells.network.PacketHandler.sendToClient(new com.otectus.arsnspells.network.JournalSnapshotPayload(snapshot), player);
        return rows.size();
    }

    private static Map<String, Integer> affinity(ServerPlayer player) {
        return player.getData(com.otectus.arsnspells.data.AttachmentTypes.AFFINITY.get()).getAllLevels();
    }
    private static Map<String, Integer> counts(ServerPlayer player) {
        return player.getData(com.otectus.arsnspells.data.AttachmentTypes.PROGRESSION.get()).getAllCastCounts();
    }
    private static String binding(String key) {
        if (!IronsCompat.isLoaded()) return "absent";
        var attribute = com.otectus.arsnspells.compat.IronsSchoolAttributes.power(key);
        return attribute == null ? "unresolved" : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()));
    }
    private static double appliedBonus(ServerPlayer player, String key) {
        if (!IronsCompat.isLoaded()) return 0;
        var attribute = com.otectus.arsnspells.compat.IronsSchoolAttributes.power(key);
        var instance = attribute == null ? null : player.getAttribute(attribute);
        var modifier = instance == null ? null : instance.getModifier(ProgressionAttributes.MODIFIER_ID);
        return modifier == null ? 0 : modifier.amount();
    }
    private static int removalReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!CrossModSpellComponents.has(stack)) continue;
            say(source, "inventory", slot, CrossModSpellComponents.get(stack).size(), CarrierFingerprint.of(stack));
            count++;
        }
        var dimensions = player.level().registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).keySet().stream()
            .filter(id -> !"minecraft".equals(id.getNamespace())).sorted().map(Object::toString).toList();
        say(source, "removal", count, String.join(", ", dimensions));
        return count;
    }
}
