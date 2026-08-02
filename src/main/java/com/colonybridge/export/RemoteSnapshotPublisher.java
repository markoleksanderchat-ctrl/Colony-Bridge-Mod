package com.colonybridge.export;

import com.colonybridge.ColonyBridgeConstants;
import com.colonybridge.model.ColonySnapshot;
import com.colonybridge.utility.RemoteEndpointValidator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

final class RemoteSnapshotPublisher {
    static final int MAX_PAYLOAD_BYTES = 1_000_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    RemotePublishResult publish(ColonySnapshot snapshot, String endpoint, String token) throws IOException, InterruptedException {
        URI uri = RemoteEndpointValidator.requireSafeHttps(endpoint);
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Remote sync token is missing.");

        byte[] payload = SnapshotSanitizer.sanitize(snapshot, token).toString().getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Sanitized snapshot exceeds " + MAX_PAYLOAD_BYTES + " bytes.");
        }

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<Void> response = CLIENT.send(request(uri, token, payload), HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return new RemotePublishResult(status, attempt, payload.length);
                if (!isTransient(status)) throw new TerminalRemoteException("Remote sync returned HTTP " + status + ".");
                lastFailure = new IOException("Remote sync returned transient HTTP " + status + ".");
                if (attempt < MAX_ATTEMPTS) Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            } catch (TerminalRemoteException terminal) {
                throw terminal;
            } catch (IOException failure) {
                lastFailure = failure;
            }
            if (attempt < MAX_ATTEMPTS) Thread.sleep(retryDelayMillis(Optional.empty(), attempt));
        }
        throw lastFailure == null ? new IOException("Remote sync failed.") : lastFailure;
    }

    private static HttpRequest request(URI uri, String token, byte[] payload) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", "ColonyBridge/" + ColonyBridgeConstants.VERSION)
                .header("X-ColonyBridge-Protocol", ColonyBridgeConstants.PROTOCOL_ID)
                .header("X-ColonyBridge-Version", ColonyBridgeConstants.VERSION)
                .header("X-ColonyBridge-Schema", Integer.toString(ColonyBridgeConstants.SCHEMA_VERSION))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
    }

    static URI validatedEndpoint(String endpoint) {
        return RemoteEndpointValidator.requireSafeHttps(endpoint);
    }

    private static boolean isTransient(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return retryDelayMillis(response.headers().firstValue("Retry-After"), attempt);
    }

    static long retryDelayMillis(Optional<String> retryAfter, int attempt) {
        if (retryAfter.isPresent()) {
            try {
                long seconds = Long.parseLong(retryAfter.get().trim());
                if (seconds >= 0) return Math.min(5_000L, seconds * 1_000L);
            } catch (NumberFormatException ignored) {
                // HTTP-date values are uncommon here; use bounded exponential backoff instead.
            }
        }
        return Math.min(2_000L, 250L << Math.max(0, attempt - 1));
    }

    private static final class TerminalRemoteException extends IOException {
        private static final long serialVersionUID = 1L;

        private TerminalRemoteException(String message) {
            super(message);
        }
    }
}
