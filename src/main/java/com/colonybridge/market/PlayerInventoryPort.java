package com.colonybridge.market;

public interface PlayerInventoryPort {
    String playerId();

    boolean restrictedGameMode();

    int countDiamonds();

    int countPlainItems(String itemId);

    PendingInventoryMutation prepare(InventoryMutation mutation);

    interface PendingInventoryMutation {
        void apply();

        void commit();

        void rollback();
    }
}
