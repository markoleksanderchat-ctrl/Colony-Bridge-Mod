package com.colonybridge.market;

public final class MarketTransactionCoordinator {
    private MarketTransactionCoordinator() {
    }

    public static Result execute(PlayerInventoryPort inventory, InventoryMutation mutation,
                                 Runnable updateMarketState, Runnable rollbackMarketState,
                                 PersistAction persist, String successMessage) {
        PlayerInventoryPort.PendingInventoryMutation pending = inventory.prepare(mutation);
        boolean stateUpdated = false;
        try {
            pending.apply();
            updateMarketState.run();
            stateUpdated = true;
            persist.save();
            pending.commit();
            return Result.success(successMessage);
        } catch (Exception failure) {
            if (stateUpdated) rollbackMarketState.run();
            pending.rollback();
            return Result.failure("Royal Exchange transaction could not be saved. No goods were exchanged.", failure);
        }
    }

    @FunctionalInterface
    public interface PersistAction {
        void save() throws Exception;
    }

    public record Result(boolean success, String message, Exception failure) {
        static Result success(String message) {
            return new Result(true, message, null);
        }

        static Result failure(String message, Exception failure) {
            return new Result(false, message, failure);
        }
    }
}
