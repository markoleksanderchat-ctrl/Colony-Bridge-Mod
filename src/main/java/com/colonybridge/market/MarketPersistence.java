package com.colonybridge.market;

import com.colonybridge.export.AtomicFileWriter;
import com.colonybridge.utility.JsonSupport;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public final class MarketPersistence {
    private MarketPersistence() {
    }

    public static MarketState load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return null;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            MarketState state = JsonSupport.gson(false).fromJson(json, MarketState.class);
            if (state == null) throw new IOException("Empty market data");
            if (state.version() == 1) {
                return new MarketState(MarketState.CURRENT_VERSION, state.seed(), state.records(), state.activeEvents(),
                        java.util.Map.of(), state.completedQuoteIds(), MarketConfig.defaults(), java.util.Map.of(),
                        java.util.Set.of(), java.util.Map.of());
            }
            if (state.version() != MarketState.CURRENT_VERSION) throw new IOException("Unsupported market data version");
            return state;
        } catch (RuntimeException | IOException malformed) {
            Path preserved = path.resolveSibling("market-corrupt-" + Instant.now().toEpochMilli() + ".json");
            Files.move(path, preserved, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("Market data was corrupt and has been preserved as " + preserved.getFileName(), malformed);
        }
    }

    public static void save(Path path, MarketState state) throws IOException {
        AtomicFileWriter.writeUtf8(path, JsonSupport.toJson(state, true));
    }
}
