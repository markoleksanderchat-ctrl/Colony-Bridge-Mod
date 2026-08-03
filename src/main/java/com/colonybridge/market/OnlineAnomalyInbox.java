package com.colonybridge.market;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class OnlineAnomalyInbox {
    private final Queue<OnlineMarketEvent> pending = new ArrayDeque<>();

    public void accept(List<OnlineMarketEvent> previous, List<OnlineMarketEvent> next) {
        Set<String> previousIds = new HashSet<>();
        previous.forEach(event -> previousIds.add(event.id()));
        for (OnlineMarketEvent event : next) {
            if (!previousIds.contains(event.id())
                    && pending.stream().noneMatch(existing -> existing.id().equals(event.id()))) {
                pending.add(event);
            }
        }
    }

    public List<OnlineMarketEvent> drain() {
        List<OnlineMarketEvent> events = new ArrayList<>(pending);
        pending.clear();
        return List.copyOf(events);
    }
}
