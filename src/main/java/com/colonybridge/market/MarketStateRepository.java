package com.colonybridge.market;

import java.io.IOException;

public interface MarketStateRepository {
    MarketState load() throws IOException;

    void save(MarketState state) throws IOException;
}
