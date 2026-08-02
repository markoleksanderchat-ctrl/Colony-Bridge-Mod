package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.BridgeMessage;

import java.util.List;

public final class CollectionIssues {
    private CollectionIssues() {
    }

    public static BridgeMessage error(String scope, String entityId, String code, Exception exception) {
        String message = exception.getMessage();
        return new BridgeMessage(scope, entityId, code,
                exception.getClass().getSimpleName() + (message == null ? "" : ": " + message));
    }

    public static <T> List<T> safeList(String scope, String entityId, String code, List<BridgeMessage> errors,
                                       ThrowingSupplier<List<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception exception) {
            errors.add(error(scope, entityId, code, exception));
            return List.of();
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
