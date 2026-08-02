package com.colonybridge.market;

import com.colonybridge.ColonyBridge;
import com.colonybridge.ColonyBridgeConstants;
import com.colonybridge.export.AtomicFileWriter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Queue;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

final class OnlineMarketClient {
    static final List<URI> ENDPOINTS = List.of(
            URI.create("https://royalexchange.net/api/market"),
            URI.create("https://royal-exchange-online.royal-exchange-market.workers.dev/api/market"));
    static final int MAX_RESPONSE_BYTES = 512_000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final Path cachePath;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private OnlineMarketSnapshot snapshot;
    private CompletableFuture<Void> refresh;
    private long nextRefreshAt;
    private String lastFailure;
    private URI activeEndpoint;
    private final Queue<OnlineMarketEvent> pendingEvents = new ArrayDeque<>();

    OnlineMarketClient(Path cachePath) {
        this.cachePath = cachePath;
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
        refresh = fetch(0)
                .thenAccept(next -> accept(next.snapshot(), next.endpoint()))
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
        List<OnlineMarketEvent> events = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    private CompletableFuture<FetchedMarket> fetch(int endpointIndex) {
        URI endpoint = ENDPOINTS.get(endpointIndex);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "ColonyBridge/" + ColonyBridgeConstants.VERSION)
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(OnlineMarketClient::responseBody)
                .thenApply(OnlineMarketFeed::parse)
                .thenApply(snapshot -> new FetchedMarket(endpoint, snapshot))
                .handle((result, failure) -> {
                    if (failure == null) return CompletableFuture.completedFuture(result);
                    if (endpointIndex + 1 < ENDPOINTS.size()) return fetch(endpointIndex + 1);
                    return CompletableFuture.<FetchedMarket>failedFuture(unwrap(failure));
                }).thenCompose(Function.identity());
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
        Set<String> previousIds = new HashSet<>();
        if (snapshot != null) snapshot.events().forEach(event -> previousIds.add(event.id()));
        for (OnlineMarketEvent event : next.events()) {
            if (!previousIds.contains(event.id()) && pendingEvents.stream().noneMatch(pending -> pending.id().equals(event.id()))) {
                pendingEvents.add(event);
            }
        }
        snapshot = next;
        activeEndpoint = endpoint;
        lastFailure = null;
        try {
            String json = com.colonybridge.utility.JsonSupport.toJson(next, true);
            AtomicFileWriter.writeUtf8(cachePath, json);
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
        if (!Files.isRegularFile(cachePath)) return;
        try {
            String json = Files.readString(cachePath, StandardCharsets.UTF_8);
            OnlineMarketSnapshot cached = com.colonybridge.utility.JsonSupport.gson(false)
                    .fromJson(json, OnlineMarketSnapshot.class);
            if (cached != null && cached.changes() != null && cached.changes().keySet().containsAll(OnlineIssuerMapper.TICKERS)) {
                snapshot = cached;
            }
        } catch (IOException | RuntimeException failure) {
            ColonyBridge.LOGGER.debug("Royal Exchange online cache could not be loaded.", failure);
        }
    }

    private record FetchedMarket(URI endpoint, OnlineMarketSnapshot snapshot) {}
}
