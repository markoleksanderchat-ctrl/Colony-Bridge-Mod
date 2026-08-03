package com.colonybridge.market;

import com.colonybridge.export.AtomicFileWriter;
import com.colonybridge.utility.JsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonOnlineMarketCache implements OnlineMarketCache {
    private final Path path;

    public JsonOnlineMarketCache(Path path) {
        this.path = path;
    }

    @Override
    public OnlineMarketSnapshot load() throws IOException {
        if (!Files.isRegularFile(path)) return null;
        try {
            OnlineMarketSnapshot cached = JsonSupport.gson(false)
                    .fromJson(Files.readString(path, StandardCharsets.UTF_8), OnlineMarketSnapshot.class);
            if (cached == null || cached.changes() == null
                    || !cached.changes().keySet().containsAll(OnlineIssuerMapper.TICKERS)) {
                throw new IOException("Online market cache is incomplete.");
            }
            return cached;
        } catch (RuntimeException failure) {
            throw new IOException("Online market cache is invalid.", failure);
        }
    }

    @Override
    public void save(OnlineMarketSnapshot snapshot) throws IOException {
        AtomicFileWriter.writeUtf8(path, JsonSupport.toJson(snapshot, true));
    }
}
