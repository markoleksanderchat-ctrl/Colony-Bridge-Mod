package com.colonybridge.market.trader;

final class MenuValueCodec {
    private MenuValueCodec() {
    }

    static int low(int value) {
        return value & 0xffff;
    }

    static int high(int value) {
        return value >>> 16;
    }

    static int combine(int low, int high) {
        return ((high & 0xffff) << 16) | (low & 0xffff);
    }
}
