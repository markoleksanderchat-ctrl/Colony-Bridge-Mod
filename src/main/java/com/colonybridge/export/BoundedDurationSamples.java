package com.colonybridge.export;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

final class BoundedDurationSamples {
    private final int capacity;
    private final Deque<Long> samples = new ArrayDeque<>();

    BoundedDurationSamples(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    synchronized void clear() { samples.clear(); }

    synchronized void add(long milliseconds) {
        if (milliseconds < 0) throw new IllegalArgumentException("duration must not be negative");
        if (samples.size() == capacity) samples.removeFirst();
        samples.addLast(milliseconds);
    }

    synchronized MainThreadTimingSummary summary() {
        if (samples.isEmpty()) return MainThreadTimingSummary.EMPTY;
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.naturalOrder());
        return new MainThreadTimingSummary(sorted.size(), percentile(sorted, 0.50), percentile(sorted, 0.95),
                sorted.getLast());
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
