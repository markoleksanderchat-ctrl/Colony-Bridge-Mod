package com.colonybridge.market.trader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RoyalExchangeMenuDataTests {
    @Test
    void preservesWideValuesAcrossShortDataSlots() {
        int[] values = {Integer.MIN_VALUE, -30_000_000, -1, 0, 32_767, 32_768, 48_000, 604_800,
                30_000_000, Integer.MAX_VALUE};

        for (int value : values) {
            short clientLow = (short) MenuValueCodec.low(value);
            short clientHigh = (short) MenuValueCodec.high(value);
            assertEquals(value, MenuValueCodec.combine(clientLow, clientHigh));
        }
    }
}
