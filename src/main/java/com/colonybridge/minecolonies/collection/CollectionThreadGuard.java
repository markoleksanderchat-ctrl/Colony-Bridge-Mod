package com.colonybridge.minecolonies.collection;

public final class CollectionThreadGuard {
    private final Thread owner;

    public CollectionThreadGuard(Thread owner) {
        this.owner = owner;
    }

    public static CollectionThreadGuard captureCurrent() {
        return new CollectionThreadGuard(Thread.currentThread());
    }

    public void requireOwnerThread() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("MineColonies collection must remain on the captured server thread.");
        }
    }
}
