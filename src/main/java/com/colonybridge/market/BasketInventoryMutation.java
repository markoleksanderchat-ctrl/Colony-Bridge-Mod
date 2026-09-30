package com.colonybridge.market;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record BasketInventoryMutation(List<BasketLine> lines, int diamonds) {
    public BasketInventoryMutation {
        lines = List.copyOf(lines);
        if (lines.isEmpty() || lines.size() > 9 || diamonds < 1) {
            throw new IllegalArgumentException("A basket sale needs 1–9 lines and a positive payout.");
        }
        Set<String> items = new HashSet<>();
        for (BasketLine line : lines) {
            if (!items.add(line.itemId())) throw new IllegalArgumentException("Duplicate basket item.");
        }
    }
}
