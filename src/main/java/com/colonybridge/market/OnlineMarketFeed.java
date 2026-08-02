package com.colonybridge.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;

public final class OnlineMarketFeed {
    private static final Set<String> SEVERITIES = Set.of("notice", "material", "severe", "crisis");
    private static final Set<String> PHASES = Set.of("onset", "peak", "recovery");
    private OnlineMarketFeed() {
    }

    public static OnlineMarketSnapshot parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!"Royal Exchange".equals(text(root, "exchange"))
                    || !"autonomous-wall-clock".equals(text(root, "mode"))) {
                throw new IllegalArgumentException("Unexpected online market identity.");
            }
            long asOf = Instant.parse(text(root, "asOf")).toEpochMilli();
            JsonArray companies = root.getAsJsonArray("companies");
            if (companies == null) throw new IllegalArgumentException("Online market has no issuers.");
            Map<String, Double> changes = new HashMap<>();
            for (JsonElement element : companies) {
                JsonObject company = element.getAsJsonObject();
                String ticker = text(company, "ticker");
                double change = company.get("changePercent").getAsDouble();
                if (!OnlineIssuerMapper.TICKERS.contains(ticker) || !Double.isFinite(change) || Math.abs(change) > 50) {
                    throw new IllegalArgumentException("Online market contains an invalid issuer movement.");
                }
                if (changes.put(ticker, change) != null) throw new IllegalArgumentException("Online market contains a duplicate issuer.");
            }
            if (!changes.keySet().containsAll(OnlineIssuerMapper.TICKERS)) {
                throw new IllegalArgumentException("Online market is missing a required issuer.");
            }
            List<OnlineMarketEvent> events = new ArrayList<>();
            JsonArray notices = root.getAsJsonArray("events");
            if (notices != null) {
                if (notices.size() > 3) throw new IllegalArgumentException("Online market contains too many active notices.");
                for (JsonElement element : notices) events.add(parseEvent(element.getAsJsonObject()));
            }
            return new OnlineMarketSnapshot(asOf, changes, events);
        } catch (DateTimeParseException | NullPointerException | IllegalStateException | NumberFormatException failure) {
            throw new IllegalArgumentException("Online market response is invalid.", failure);
        }
    }

    private static OnlineMarketEvent parseEvent(JsonObject event) {
        String id = text(event, "id");
        String title = text(event, "title");
        String description = text(event, "description");
        String severity = text(event, "severity");
        String phase = text(event, "phase");
        double impact = event.get("impactPercent").getAsDouble();
        long startedAt = Instant.parse(text(event, "startedAt")).toEpochMilli();
        long endsAt = Instant.parse(text(event, "endsAt")).toEpochMilli();
        if (!id.matches("rxa-[a-z0-9-]{3,64}") || title.isBlank() || title.length() > 96
                || description.isBlank() || description.length() > 240 || !SEVERITIES.contains(severity)
                || !PHASES.contains(phase) || !Double.isFinite(impact) || Math.abs(impact) > 35
                || endsAt <= startedAt) {
            throw new IllegalArgumentException("Online market contains an invalid anomaly notice.");
        }
        JsonArray affected = event.getAsJsonArray("affectedTickers");
        if (affected == null || affected.isEmpty() || affected.size() > OnlineIssuerMapper.TICKERS.size()) {
            throw new IllegalArgumentException("Online market anomaly has invalid issuers.");
        }
        List<String> tickers = new ArrayList<>();
        for (JsonElement ticker : affected) {
            String value = ticker.getAsString();
            if (!OnlineIssuerMapper.TICKERS.contains(value) || tickers.contains(value)) {
                throw new IllegalArgumentException("Online market anomaly has an invalid issuer.");
            }
            tickers.add(value);
        }
        return new OnlineMarketEvent(id, title, description, severity, phase, tickers, impact, startedAt, endsAt);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Online market is missing " + key + ".");
        return value.getAsString();
    }
}
