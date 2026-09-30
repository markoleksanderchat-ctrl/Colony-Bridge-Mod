package com.colonybridge.market;

import java.util.*;

public final class OnlineAnomalyInbox {
    static final int MAX_SEEN = 4096;
    static final int MAX_PENDING = 32;
    private final Map<String, OnlineMarketEvent> pending = new LinkedHashMap<>();
    private final Map<String, Long> seen = new LinkedHashMap<>();

    public void accept(List<OnlineMarketEvent> previous, List<OnlineMarketEvent> next) {
        for (OnlineMarketEvent event : previous) seen.put(event.id(), event.endsAtEpochMillis());
        Set<String> active = new HashSet<>();
        for (OnlineMarketEvent event : next) {
            active.add(event.id());
            if (!seen.containsKey(event.id())) pending.put(event.id(), event);
            else if (pending.containsKey(event.id())) pending.put(event.id(), event);
            seen.put(event.id(), event.endsAtEpochMillis());
        }
        pending.keySet().retainAll(active);
        trim(pending, MAX_PENDING);
        trim(seen, MAX_SEEN);
    }

    public List<OnlineMarketEvent> drain() { return drain(System.currentTimeMillis()); }

    public List<OnlineMarketEvent> drain(long now) {
        List<OnlineMarketEvent> result = pending.values().stream()
                .filter(event -> event.startedAtEpochMillis() <= now && event.endsAtEpochMillis() > now).toList();
        pending.clear();
        seen.entrySet().removeIf(entry -> entry.getValue() < now - 86_400_000L);
        return result;
    }

    public void clear() { pending.clear(); seen.clear(); }

    private static void trim(Map<String, ?> values, int maximum) {
        while (values.size() > maximum) values.remove(values.keySet().iterator().next());
    }
}
