package com.colonybridge.market;

import com.colonybridge.ColonyBridge;
import com.colonybridge.ColonyBridgeConstants;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class OnlineMarketClient {
    static final List<URI> ENDPOINTS = List.of(
            URI.create("https://royalexchange.net/api/market"),
            URI.create("https://royal-exchange-online.royal-exchange-market.workers.dev/api/market"));
    static final int MAX_RESPONSE_BYTES = 512_000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final OnlineMarketCache cache;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private OnlineMarketSnapshot snapshot;
    private CompletableFuture<Void> refresh;
    private long nextRefreshAt;
    private String lastFailure;
    private URI activeEndpoint;
    private final OnlineAnomalyInbox anomalyInbox = new OnlineAnomalyInbox();

    OnlineMarketClient(Path cachePath) {
        this(new JsonOnlineMarketCache(cachePath));
    }

    OnlineMarketClient(OnlineMarketCache cache) {
        this.cache = cache;
        loadCache();
    }

    synchronized Optional<OnlineMarketInfluence> influence(String itemId, Set<String> tags,
                                                            long now, OnlineMarketConfig config) {
        refreshIfDue(now, config);
        return snapshot == null ? Optional.empty() : snapshot.influence(itemId, tags, now, config);
    }

    synchronized void refreshIfDue(long now, OnlineMarketConfig config) {
        if (!config.enabled() || now < nextRefreshAt || refresh != null && !refresh.isDone()) return;
        nextRefreshAt = now + config.refreshSeconds() * 1000L;
        refresh = OnlineEndpointFailover.fetch(ENDPOINTS, this::fetch)
                .thenAccept(next -> accept(next.value(), next.endpoint()))
                .exceptionally(failure -> {
                    recordFailure(failure);
                    return null;
                });
    }

    synchronized String status(long now, OnlineMarketConfig config) {
        if (!config.enabled()) return "disabled";
        if (snapshot != null && snapshot.usable(now, config)) {
            long ageMinutes = Math.max(0, (now - snapshot.asOfEpochMillis()) / 60_000L);
            String host = activeEndpoint == null ? "cached feed" : activeEndpoint.getHost();
            return "connected via " + host + " (market age " + ageMinutes + "m)";
        }
        if (snapshot != null) return "offline (cached market is stale)";
        return lastFailure == null ? "connecting" : "offline (local pricing active)";
    }

    synchronized int state(long now, OnlineMarketConfig config) {
        if (!config.enabled()) return -1;
        refreshIfDue(now, config);
        if (snapshot != null && snapshot.usable(now, config)) return 2;
        return snapshot == null && lastFailure == null ? 1 : 0;
    }

    synchronized List<OnlineMarketEvent> pollNewEvents() {
        return anomalyInbox.drain();
    }

    private CompletableFuture<OnlineMarketSnapshot> fetch(URI endpoint) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "ColonyBridge/" + ColonyBridgeConstants.VERSION)
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(OnlineMarketClient::responseBody)
                .thenApply(OnlineMarketFeed::parse);
    }

    private static String responseBody(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Online market returned HTTP " + response.statusCode() + ".");
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("Online market response is too large.");
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private synchronized void accept(OnlineMarketSnapshot next, URI endpoint) {
        anomalyInbox.accept(snapshot == null ? List.of() : snapshot.events(), next.events());
        snapshot = next;
        activeEndpoint = endpoint;
        lastFailure = null;
        try {
            cache.save(next);
        } catch (IOException failure) {
            ColonyBridge.LOGGER.debug("Royal Exchange online cache could not be saved.", failure);
        }
    }

    private synchronized void recordFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        if (!message.equals(lastFailure)) ColonyBridge.LOGGER.warn("Royal Exchange online prices are unavailable; local pricing remains active: {}", message);
        lastFailure = message;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    private void loadCache() {
        try {
            snapshot = cache.load();
        } catch (IOException | RuntimeException failure) {
            ColonyBridge.LOGGER.debug("Royal Exchange online cache could not be loaded.", failure);
        }
    }
}
