package com.colonybridge.market.trader;

import com.colonybridge.market.TradeFeedback;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RoyalExchangeJeiPropertiesTests {
    @Test void ignoresScreensBeforeInitialization() {
        assertNull(RoyalExchangeJeiPlugin.FullScreenProperties.create(0, 0));
        assertNull(RoyalExchangeJeiPlugin.FullScreenProperties.create(640, 0));
        assertNull(RoyalExchangeJeiPlugin.FullScreenProperties.create(-1, 480));
    }

    @Test void snapshotsDimensionsForResizeDetection() {
        var before = RoyalExchangeJeiPlugin.FullScreenProperties.create(640, 480);
        var after = RoyalExchangeJeiPlugin.FullScreenProperties.create(960, 540);
        assertNotEquals(before, after);
        assertEquals(before, RoyalExchangeJeiPlugin.FullScreenProperties.create(640, 480));
        assertEquals(640, before.guiXSize());
        assertEquals(480, before.guiYSize());
        assertEquals(960, after.screenWidth());
        assertEquals(540, after.screenHeight());
        assertEquals(0, after.guiLeft());
        assertEquals(0, after.guiTop());
    }

    @Test void usesSingularCurrencyForOneDiamond() {
        assertEquals("1 diamond", TradeFeedback.diamondAmount(1));
        assertEquals("2 diamonds", TradeFeedback.diamondAmount(2));
    }
}
