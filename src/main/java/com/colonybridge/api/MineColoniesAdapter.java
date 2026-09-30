package com.colonybridge.api;

import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.model.ColonySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MineColoniesAdapter {
    AdapterStatus status();

    default void resetSession() { }

    default CollectionProfile lastCollectionProfile() {
        return CollectionProfile.EMPTY;
    }

    List<ColonySnapshot> collectAll(ServerLevelContext context, BridgeConfigValues config, ExportTrigger trigger, Instant generatedAt);

    Optional<ColonySnapshot> collectById(ServerLevelContext context, BridgeConfigValues config, int colonyId, ExportTrigger trigger, Instant generatedAt);
}
