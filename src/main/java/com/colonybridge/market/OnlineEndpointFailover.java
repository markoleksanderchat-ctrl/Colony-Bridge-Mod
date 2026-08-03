package com.colonybridge.market;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class OnlineEndpointFailover {
    private OnlineEndpointFailover() {
    }

    public static <T> CompletableFuture<EndpointResult<T>> fetch(List<URI> endpoints,
                                                                 Function<URI, CompletableFuture<T>> fetcher) {
        if (endpoints.isEmpty()) return CompletableFuture.failedFuture(
                new IllegalArgumentException("At least one online market endpoint is required."));
        return fetch(endpoints, fetcher, 0);
    }

    private static <T> CompletableFuture<EndpointResult<T>> fetch(List<URI> endpoints,
                                                                  Function<URI, CompletableFuture<T>> fetcher,
                                                                  int index) {
        URI endpoint = endpoints.get(index);
        return fetcher.apply(endpoint).thenApply(value -> new EndpointResult<>(endpoint, value))
                .handle((result, failure) -> {
                    if (failure == null) return CompletableFuture.completedFuture(result);
                    if (index + 1 < endpoints.size()) return fetch(endpoints, fetcher, index + 1);
                    return CompletableFuture.<EndpointResult<T>>failedFuture(unwrap(failure));
                }).thenCompose(Function.identity());
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    public record EndpointResult<T>(URI endpoint, T value) {
    }
}
