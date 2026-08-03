package com.colonybridge.config;

import com.colonybridge.market.MarketConfig;
import com.colonybridge.market.OnlineMarketConfig;

import java.util.Objects;

public record BridgeSettings(
        boolean enabled,
        ExportSettings export,
        PrivacySettings privacy,
        NotificationSettings notifications,
        RemoteSyncSettings remoteSync,
        MarketSettings market
) {
    public BridgeSettings {
        Objects.requireNonNull(export, "export");
        Objects.requireNonNull(privacy, "privacy");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(remoteSync, "remoteSync");
        Objects.requireNonNull(market, "market");
    }

    public BridgeConfigValues bridgeValues() {
        return new BridgeConfigValues(enabled, export.onStartup(), export.onPlayerJoin(), export.onShutdown(),
                export.onLastPlayerDisconnect(), export.periodicEnabled(), export.periodicSeconds(),
                export.retainSnapshots(), export.prettyPrintJson(), export.showProgress(),
                privacy.includeCitizenPositions(), privacy.includeOwnerUuid(), notifications.dayCounterEnabled(),
                remoteSync.enabled(), remoteSync.endpoint(), remoteSync.token()).validated();
    }

    public static BridgeSettings defaults() {
        return from(BridgeConfigValues.defaults(), MarketConfig.defaults(), OnlineMarketConfig.defaults());
    }

    public static BridgeSettings from(BridgeConfigValues bridge, MarketConfig market, OnlineMarketConfig online) {
        BridgeConfigValues values = Objects.requireNonNull(bridge, "bridge").validated();
        return new BridgeSettings(values.enabled(), new ExportSettings(values.exportOnStartup(),
                values.exportOnPlayerJoin(), values.exportOnShutdown(), values.exportOnLastPlayerDisconnect(),
                values.periodicExportEnabled(), values.periodicExportSeconds(), values.retainSnapshots(),
                values.prettyPrintJson(), values.showExportProgress()),
                new PrivacySettings(values.includeCitizenPositions(), values.includeOwnerUuid()),
                new NotificationSettings(values.dayCounterEnabled()),
                new RemoteSyncSettings(values.remoteSyncEnabled(), values.remoteEndpoint(), values.remoteToken()),
                new MarketSettings(Objects.requireNonNull(market, "market").validated(),
                        Objects.requireNonNull(online, "online").validated()));
    }
}
