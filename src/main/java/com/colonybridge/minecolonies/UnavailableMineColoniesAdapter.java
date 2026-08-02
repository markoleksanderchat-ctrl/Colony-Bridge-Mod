package com.colonybridge.minecolonies;

import com.colonybridge.api.AdapterStatus;
import com.colonybridge.api.ExportTrigger;
import com.colonybridge.api.MineColoniesAdapter;
import com.colonybridge.api.ServerLevelContext;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.model.ColonySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class UnavailableMineColoniesAdapter implements MineColoniesAdapter {
    private final AdapterStatus status;

    public UnavailableMineColoniesAdapter(String reason) {
        this.status = AdapterStatus.unavailable(reason);
    }

    @Override
    public AdapterStatus status() {
        return status;
    }

    @Override
    public List<ColonySnapshot> collectAll(ServerLevelContext context, BridgeConfigValues config, ExportTrigger trigger, Instant generatedAt) {
        return List.of();
    }

    @Override
    public Optional<ColonySnapshot> collectById(ServerLevelContext context, BridgeConfigValues config, int colonyId, ExportTrigger trigger, Instant generatedAt) {
        return Optional.empty();
    }
}
