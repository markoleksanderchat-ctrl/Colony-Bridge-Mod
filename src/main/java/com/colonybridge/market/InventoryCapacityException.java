package com.colonybridge.market;

public final class InventoryCapacityException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    public InventoryCapacityException() { super("Make room in your inventory for the full trade."); }
}
