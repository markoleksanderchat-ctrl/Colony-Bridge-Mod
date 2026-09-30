package com.colonybridge.market;

public interface PlayerInventoryPort {
    String playerId();

    boolean restrictedGameMode();

    int countDiamonds();

    int countPlainItems(String itemId);

    PendingInventoryMutation prepare(InventoryMutation mutation);

    default PendingInventoryMutation prepareBasket(BasketInventoryMutation mutation) {
        throw new UnsupportedOperationException("Basket sales are not supported by this inventory.");
    }

    interface PendingInventoryMutation {
        void apply();

        /** Release rollback material only. All delivery occurs in apply(), before persistence. */
        void commit();

        void rollback();
    }
}
