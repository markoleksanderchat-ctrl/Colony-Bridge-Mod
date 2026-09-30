package com.colonybridge.market.trader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RoyalExchangeScrollbarTests {
    @Test void trackClicksAndDraggingReachBothEndsAndStayWithinBounds() {
        var bar = RoyalExchangePresentationModel.scrollbar(1328, 0);
        double grab = bar.thumbHeight() / 2.0;
        assertTrue(bar.scrollable());
        assertEquals(0, bar.scrollAt(bar.trackTop() + grab, grab));
        assertEquals(184, bar.scrollAt(bar.trackBottom() - bar.thumbHeight() + grab, grab));
        assertEquals(0, bar.scrollAt(-1000, grab));
        assertEquals(184, bar.scrollAt(1000, grab));
        assertEquals(92, bar.scrollAt((bar.trackTop() + bar.trackBottom()) / 2.0, grab));
    }

    @Test void grabbingAnyPartOfTheThumbKeepsTheCurrentScroll() {
        for (int count : new int[] {43, 84, 350, 1328}) {
            for (int scroll = 0; scroll <= RoyalExchangePresentationModel.catalogRows(count) - RoyalExchangeLayout.VISIBLE_ROWS; scroll++) {
                var bar = RoyalExchangePresentationModel.scrollbar(count, scroll);
                for (double offset : new double[] {0, bar.thumbHeight() / 2.0, bar.thumbHeight() - 0.5}) {
                    double pointer = bar.thumbTop() + offset;
                    double grab = bar.grabOffsetAt(pointer);
                    assertEquals(scroll, bar.scrollAt(pointer, grab), "Thumb grab must not jump rows");
                    assertTrue(bar.scrollAt(pointer + 10, grab) >= scroll);
                    assertTrue(bar.scrollAt(pointer - 10, grab) <= scroll);
                }
            }
        }
    }

    @Test void emptyAndShortListsHaveNoDraggableThumbOrInvalidScroll() {
        for (int count = 0; count <= RoyalExchangeLayout.VISIBLE_ITEMS; count++) {
            var bar = RoyalExchangePresentationModel.scrollbar(count, 100);
            assertFalse(bar.scrollable());
            assertEquals(0, bar.scroll());
            assertEquals(bar.trackTop(), bar.thumbTop());
            assertEquals(bar.trackBottom() - bar.trackTop(), bar.thumbHeight());
            assertEquals(0, bar.scrollAt(1000, 5));
        }
        assertEquals(0, RoyalExchangePresentationModel.scrollbar(100, -100).scroll());
        assertEquals(9, RoyalExchangePresentationModel.scrollbar(100, 1000).scroll());
    }

    @Test void everyCatalogEntryRemainsReachableIncludingThePartialLastRow() {
        for (int count : new int[] {1, 7, 41, 42, 43, 48, 49, 1328}) {
            var reachable = new java.util.HashSet<Integer>();
            int lastScroll = RoyalExchangePresentationModel.clampScroll(Integer.MAX_VALUE, count);
            for (int scroll = 0; scroll <= lastScroll; scroll++) {
                for (int cell = 0; cell < RoyalExchangeLayout.VISIBLE_ITEMS; cell++) {
                    int index = RoyalExchangePresentationModel.catalogIndex(scroll, cell);
                    if (index < count) reachable.add(index);
                }
            }
            assertEquals(count, reachable.size());
            assertTrue(reachable.contains(count - 1));
        }
    }

    @Test void gridHitboxesMatchCellsAndExcludeTheFooterAndScrollbar() {
        assertEquals(0, RoyalExchangePresentationModel.catalogIndexAt(0, 14, 65));
        assertEquals(6, RoyalExchangePresentationModel.catalogIndexAt(0, 139.9, 65));
        assertEquals(41, RoyalExchangePresentationModel.catalogIndexAt(0, 139.9, 172.9));
        assertEquals(48, RoyalExchangePresentationModel.catalogIndexAt(1, 139.9, 172.9));
        assertEquals(-1, RoyalExchangePresentationModel.catalogIndexAt(0, 13.9, 65));
        assertEquals(-1, RoyalExchangePresentationModel.catalogIndexAt(0, 140, 65));
        assertEquals(-1, RoyalExchangePresentationModel.catalogIndexAt(0, 14, 173));
        assertEquals(-1, RoyalExchangePresentationModel.catalogIndexAt(0, 14, 64.9));
    }
}
