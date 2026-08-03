package com.colonybridge.market;

import java.io.IOException;

public interface OnlineMarketCache {
    OnlineMarketSnapshot load() throws IOException;

    void save(OnlineMarketSnapshot snapshot) throws IOException;
}
