package com.colonybridge.market;

import com.colonybridge.ColonyBridgeConstants;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class OnlineMarketClient {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(OnlineMarketClient.class);
    static final List<URI> ENDPOINTS = List.of(
            URI.create("https://royalexchange.net/api/market"),
            URI.create("https://royal-exchange-online.royal-exchange-market.workers.dev/api/market"));
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final OnlineMarketCache cache;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private OnlineMarketSnapshot snapshot;
    private CompletableFuture<Void> refresh;
    private long nextRefreshAt;
    private boolean closed;
    private String lastFailure;
    private URI activeEndpoint;
    private long lastSuccessfulFetchAt;
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
        if (closed || !config.enabled() || now < nextRefreshAt || refresh != null && !refresh.isDone()) return;
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
        String detail = snapshot == null ? "" : "; feed age "
                + Math.max(0, (now - snapshot.asOfEpochMillis()) / 60_000L) + "m";
        if (lastSuccessfulFetchAt > 0) detail += "; last fetch "
                + Math.max(0, (now - lastSuccessfulFetchAt) / 1000L) + "s ago";
        if (lastFailure != null) detail += "; " + lastFailure;
        if (snapshot != null && snapshot.usable(now, config)) {
            return (lastFailure == null && activeEndpoint != null
                    ? "connected via " + activeEndpoint.getHost() : "cached pricing (offline)") + detail;
        }
        return (snapshot != null ? "local pricing (cached feed stale)"
                : lastFailure == null ? "connecting" : "local pricing (offline)") + detail;
    }

    synchronized int state(long now, OnlineMarketConfig config) {
        if (!config.enabled()) return -1;
        refreshIfDue(now, config);
        if (snapshot != null && snapshot.usable(now, config))
            return lastFailure == null && activeEndpoint != null ? 2 : 3;
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
            return OnlineMarketResponse.read(body);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    synchronized void accept(OnlineMarketSnapshot next, URI endpoint) {
        if (closed || snapshot != null && next.asOfEpochMillis() < snapshot.asOfEpochMillis()) return;
        anomalyInbox.accept(snapshot == null ? List.of() : snapshot.events(), next.events());
        snapshot = next;
        activeEndpoint = endpoint;
        lastSuccessfulFetchAt = System.currentTimeMillis();
        if (lastFailure != null) LOGGER.info("Royal Exchange online feed recovered via {}.", endpoint.getHost());
        lastFailure = null;
        try {
            cache.save(next);
        } catch (IOException failure) {
            LOGGER.debug("Royal Exchange online cache could not be saved.", failure);
        }
    }

    synchronized void close() {
        closed = true;
        if (refresh != null) refresh.cancel(true);
        client.shutdownNow();
        anomalyInbox.clear();
    }

    synchronized void recordFailure(Throwable failure) {
        if (closed) return;
        Throwable cause = unwrap(failure);
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        if (!message.equals(lastFailure)) LOGGER.warn("Royal Exchange refresh failed; cached pricing is used only while fresh, otherwise local pricing applies: {}", message);
        lastFailure = message;
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof java.util.concurrent.CompletionException
                || failure instanceof java.io.UncheckedIOException) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    private void loadCache() {
        try {
            snapshot = cache.load();
        } catch (IOException | RuntimeException failure) {
            LOGGER.debug("Royal Exchange online cache could not be loaded.", failure);
        }
    }
}
