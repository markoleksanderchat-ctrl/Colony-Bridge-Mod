package com.colonybridge.market;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Retains a hard bound while allowing the current public market response. */
final class OnlineMarketResponse {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private OnlineMarketResponse() { }
    static String read(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Online market response exceeds the 2 MiB limit.");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
