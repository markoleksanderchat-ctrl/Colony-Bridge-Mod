package com.colonybridge.market;

import java.util.function.Supplier;

public final class MarketTransactionCoordinator {
    private MarketTransactionCoordinator() { }

    public static Result execute(PlayerInventoryPort inventory, InventoryMutation mutation,
                                 Runnable updateMarketState, Runnable rollbackMarketState,
                                 PersistAction persist, String successMessage) {
        return execute(() -> inventory.prepare(mutation), updateMarketState, rollbackMarketState, persist, successMessage);
    }

    public static Result execute(PlayerInventoryPort inventory, BasketInventoryMutation mutation,
                                 Runnable updateMarketState, Runnable rollbackMarketState,
                                 PersistAction persist, String successMessage) {
        return execute(() -> inventory.prepareBasket(mutation), updateMarketState, rollbackMarketState, persist, successMessage);
    }

    private static Result execute(Supplier<PlayerInventoryPort.PendingInventoryMutation> prepare,
                                  Runnable updateMarketState, Runnable rollbackMarketState,
                                  PersistAction persist, String successMessage) {
        PlayerInventoryPort.PendingInventoryMutation pending = null;
        boolean stateUpdated = false;
        boolean durable = false;
        try {
            pending = prepare.get();
            pending.apply();
            stateUpdated = true;
            updateMarketState.run();
            persist.save();
            durable = true;
            // Commit only releases rollback material; delivery must finish in apply().
            pending.commit();
            return Result.success(successMessage);
        } catch (Exception failure) {
            // A durable transaction must never be undone only in memory.
            if (durable) return new Result(true, successMessage, failure);
            boolean restored = true;
            if (stateUpdated) {
                try { rollbackMarketState.run(); }
                catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); restored = false; }
            }
            if (pending != null) {
                try { pending.rollback(); }
                catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); restored = false; }
            }
            String message = !restored ? "Transaction recovery failed. Contact the server operator."
                    : failure instanceof InventoryCapacityException ? failure.getMessage()
                    : "Royal Exchange transaction could not be saved. No goods were exchanged.";
            return Result.failure(message, failure);
        }
    }

    @FunctionalInterface
    public interface PersistAction { void save() throws Exception; }

    public record Result(boolean success, String message, Exception failure) {
        static Result success(String message) { return new Result(true, message, null); }
        static Result failure(String message, Exception failure) { return new Result(false, message, failure); }
    }
}
