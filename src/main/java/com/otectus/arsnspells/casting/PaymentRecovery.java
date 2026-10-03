package com.otectus.arsnspells.casting;

import com.google.gson.*;
import com.otectus.arsnspells.contract.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Server-owned recovery journal. Only measured remaining credits are eligible for retry. */
@EventBusSubscriber(modid = "ars_n_spells")
public final class PaymentRecovery {
    private PaymentRecovery() {}
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PaymentRecovery.class);
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean readable = true;
    public static boolean available() { return readable; }
    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/ars_n_spells-payment-recovery.json");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        CastLedger.ledger().clear(); readable = true;
        Path file = path(event.getServer());
        if (!Files.exists(file)) return;
        try {
            if (Files.size(file) > 4 * 1024 * 1024) throw new IOException("Recovery journal exceeds size limit");
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IOException("Unsupported recovery version");
            JsonArray rows = root.getAsJsonArray("obligations");
            if (rows.size() > 4096) throw new IOException("Too many recovery obligations");
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                UUID id = UUID.fromString(row.get("attempt").getAsString()), player = UUID.fromString(row.get("player").getAsString());
                List<ResourceAmount> remaining = new ArrayList<>();
                for (JsonElement value : row.getAsJsonArray("remaining")) {
                    JsonObject leg = value.getAsJsonObject();
                    remaining.add(new ResourceAmount(ResourceUnit.valueOf(leg.get("unit").getAsString()), leg.get("amount").getAsDouble()));
                }
                Set<ResourceUnit> unknown = EnumSet.noneOf(ResourceUnit.class);
                for (JsonElement value : row.getAsJsonArray("unknown")) unknown.add(ResourceUnit.valueOf(value.getAsString()));
                CastLedger.ledger().restore(id, player, remaining, unknown);
            }
            LOG.info("[CastPayment] restored {} unresolved payment obligations", rows.size());
        } catch (IOException | RuntimeException error) {
            CastLedger.ledger().clear(); // Do not retry a partially parsed journal.
            readable = false;
            LOG.error("[CastPayment] Cannot read recovery journal; new ANS payments are refused and the original journal is preserved", error);
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        if (!readable) return;
        AlternativePayment.releaseAll();
        var ledger = CastLedger.ledger();
        for (CastAttempt attempt : ledger.allOpen()) {
            if (attempt.state() == AttemptState.COMMITTED) ledger.complete(attempt);
            else ledger.cancel(attempt, CastLedger.forServer(event.getServer()));
        }
        JsonObject root = new JsonObject(); root.addProperty("version", 1);
        JsonArray rows = new JsonArray();
        for (CastAttempt attempt : ledger.allOpen()) {
            JsonObject row = new JsonObject();
            row.addProperty("attempt", attempt.attemptId().toString()); row.addProperty("player", attempt.playerId().toString());
            row.add("remaining", JSON.toJsonTree(attempt.remainingRefunds())); row.add("unknown", JSON.toJsonTree(attempt.unknownUnits()));
            rows.add(row);
        }
        for (var leg : AlternativePayment.allOpen()) {
            JsonObject row = new JsonObject(); row.addProperty("attempt", leg.attemptId().toString()); row.addProperty("player", leg.playerId().toString());
            row.add("remaining", JSON.toJsonTree(List.of(new ResourceAmount(leg.unit(), leg.remaining()))));
            row.add("unknown", JSON.toJsonTree(Set.of(leg.unit()))); rows.add(row);
        }
        root.add("obligations", rows);
        Path file = path(event.getServer()), staging = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(staging, JSON.toJson(root));
            try { Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING); }
            if (!rows.isEmpty()) LOG.warn("[CastPayment] saved {} unresolved obligations; unknown movements require review", rows.size());
        } catch (IOException error) { LOG.error("[CastPayment] Unable to save remaining obligations: {}", root, error); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CastLedger.ledger().clear(); AlternativePayment.clearAfterSave(); readable = true; }
}
