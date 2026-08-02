package com.colonybridge.utility;

import java.text.Normalizer;
import java.util.Locale;

public final class FilenameSanitizer {
    private FilenameSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank() || normalized.equals(".") || normalized.equals("..")) {
            return "unknown";
        }
        return normalized.length() > 96 ? normalized.substring(0, 96) : normalized;
    }
}
