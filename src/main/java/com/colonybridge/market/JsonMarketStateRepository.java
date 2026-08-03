package com.colonybridge.market;

import java.io.IOException;
import java.nio.file.Path;

public final class JsonMarketStateRepository implements MarketStateRepository {
    private final Path path;

    public JsonMarketStateRepository(Path path) {
        this.path = path;
    }

    @Override
    public MarketState load() throws IOException {
        return MarketPersistence.load(path);
    }

    @Override
    public void save(MarketState state) throws IOException {
        MarketPersistence.save(path, state);
    }
}
