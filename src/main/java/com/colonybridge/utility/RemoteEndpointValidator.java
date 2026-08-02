package com.colonybridge.utility;

import java.net.URI;

public final class RemoteEndpointValidator {
    private RemoteEndpointValidator() {
    }

    public static URI requireSafeHttps(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Remote sync endpoint is missing.");
        }

        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Remote sync endpoint is not a valid URL.", malformed);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Remote sync endpoint must be an HTTPS URL without embedded credentials.");
        }
        return uri;
    }

    public static boolean isSafeHttps(String endpoint) {
        try {
            requireSafeHttps(endpoint);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
