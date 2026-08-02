package com.colonybridge.config;

import com.colonybridge.market.MarketConfig;
import com.colonybridge.market.OnlineMarketConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ColonyBridgeConfig {
    public static final ModConfigSpec SERVER_SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue EXPORT_ON_STARTUP;
    private static final ModConfigSpec.BooleanValue EXPORT_ON_PLAYER_JOIN;
    private static final ModConfigSpec.BooleanValue EXPORT_ON_SHUTDOWN;
    private static final ModConfigSpec.BooleanValue EXPORT_ON_LAST_PLAYER_DISCONNECT;
    private static final ModConfigSpec.BooleanValue PERIODIC_EXPORT_ENABLED;
    private static final ModConfigSpec.IntValue PERIODIC_EXPORT_SECONDS;
    private static final ModConfigSpec.IntValue RETAIN_SNAPSHOTS;
    private static final ModConfigSpec.BooleanValue PRETTY_PRINT_JSON;
    private static final ModConfigSpec.BooleanValue SHOW_EXPORT_PROGRESS;
    private static final ModConfigSpec.BooleanValue INCLUDE_CITIZEN_POSITIONS;
    private static final ModConfigSpec.BooleanValue INCLUDE_OWNER_UUID;
    private static final ModConfigSpec.BooleanValue DAY_COUNTER_ENABLED;
    private static final ModConfigSpec.BooleanValue REMOTE_SYNC_ENABLED;
    private static final ModConfigSpec.ConfigValue<String> REMOTE_ENDPOINT;
    private static final ModConfigSpec.ConfigValue<String> REMOTE_TOKEN;
    private static final ModConfigSpec.IntValue MARKET_QUOTE_DELAY;
    private static final ModConfigSpec.IntValue MARKET_QUOTE_VALIDITY;
    private static final ModConfigSpec.IntValue MARKET_EVENT_FREQUENCY;
    private static final ModConfigSpec.DoubleValue MARKET_VOLATILITY;
    private static final ModConfigSpec.DoubleValue MARKET_MINIMUM_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue MARKET_MAXIMUM_MULTIPLIER;
    private static final ModConfigSpec.BooleanValue MARKET_BUYING_ENABLED;
    private static final ModConfigSpec.BooleanValue MARKET_OPERATOR_CONTROLS;
    private static final ModConfigSpec.BooleanValue MARKET_SELLING_ENABLED;
    private static final ModConfigSpec.DoubleValue MARKET_SELL_PRICE_RATIO;
    private static final ModConfigSpec.IntValue MARKET_DAILY_SELL_LIMIT;
    private static final ModConfigSpec.IntValue MARKET_ACTIVE_CONTRACTS;
    private static final ModConfigSpec.IntValue MARKET_CONTRACT_DURATION;
    private static final ModConfigSpec.DoubleValue MARKET_CONTRACT_PREMIUM;
    private static final ModConfigSpec.BooleanValue ONLINE_MARKET_ENABLED;
    private static final ModConfigSpec.IntValue ONLINE_MARKET_REFRESH;
    private static final ModConfigSpec.IntValue ONLINE_MARKET_MAXIMUM_AGE;
    private static final ModConfigSpec.DoubleValue ONLINE_MARKET_INFLUENCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("export");
        ENABLED = builder.define("enabled", true);
        EXPORT_ON_STARTUP = builder.define("exportOnStartup", true);
        EXPORT_ON_PLAYER_JOIN = builder.define("exportOnPlayerJoin", true);
        EXPORT_ON_SHUTDOWN = builder.define("exportOnShutdown", true);
        EXPORT_ON_LAST_PLAYER_DISCONNECT = builder.define("exportOnLastPlayerDisconnect", true);
        PERIODIC_EXPORT_ENABLED = builder.define("periodicExportEnabled", true);
        PERIODIC_EXPORT_SECONDS = builder.defineInRange("periodicExportSeconds", BridgeConfigValues.DEFAULT_PERIODIC_SECONDS,
                BridgeConfigValues.MIN_PERIODIC_SECONDS, BridgeConfigValues.MAX_PERIODIC_SECONDS);
        RETAIN_SNAPSHOTS = builder.defineInRange("retainSnapshots", 50, 1, BridgeConfigValues.MAX_RETAINED_SNAPSHOTS);
        PRETTY_PRINT_JSON = builder.define("prettyPrintJson", true);
        SHOW_EXPORT_PROGRESS = builder.define("showExportProgress", false);
        INCLUDE_CITIZEN_POSITIONS = builder.define("includeCitizenPositions", false);
        INCLUDE_OWNER_UUID = builder.define("includeOwnerUuid", false);
        builder.pop();
        builder.push("remoteSync");
        REMOTE_SYNC_ENABLED = builder
                .comment("Uploads a sanitized snapshot to Kingdom Chronicle after each successful local export.")
                .define("enabled", false);
        REMOTE_ENDPOINT = builder.comment("HTTPS endpoint that accepts sanitized schema 2 snapshots.").define("endpoint", "");
        REMOTE_TOKEN = builder.comment("Private bearer token used only for snapshot upload.").define("token", "");
        builder.pop();
        builder.push("notifications");
        DAY_COUNTER_ENABLED = builder
                .comment("Shows a gold colony-day title and celebration sound when MineColonies advances to a new colony day while a player is inside that colony.")
                .define("dayCounterEnabled", true);
        builder.pop();
        builder.push("royalExchange");
        MARKET_QUOTE_DELAY = builder.comment("Lore-friendly delay before a requested quote is revealed, in seconds.")
                .defineInRange("quoteDelaySeconds", 3, 0, 30);
        MARKET_QUOTE_VALIDITY = builder.defineInRange("quoteValiditySeconds", 120, 15, 3600);
        MARKET_EVENT_FREQUENCY = builder.defineInRange("eventFrequencyMinutes", 90, 5, 1440);
        MARKET_VOLATILITY = builder.defineInRange("volatilityStrength", 0.16, 0.0, 0.75);
        MARKET_MINIMUM_MULTIPLIER = builder.defineInRange("minimumPriceMultiplier", 0.60, 0.10, 1.0);
        MARKET_MAXIMUM_MULTIPLIER = builder.defineInRange("maximumPriceMultiplier", 1.80, 1.0, 5.0);
        MARKET_BUYING_ENABLED = builder.define("buyingEnabled", true);
        MARKET_OPERATOR_CONTROLS = builder.define("operatorEventControls", true);
        MARKET_SELLING_ENABLED = builder.define("sellingEnabled", true);
        MARKET_SELL_PRICE_RATIO = builder.comment("Share of the live buy valuation paid when players sell goods.")
                .defineInRange("sellPriceRatio", 0.72, 0.10, 0.95);
        MARKET_DAILY_SELL_LIMIT = builder.comment("Maximum diamonds each player may receive from ordinary sales per UTC day.")
                .defineInRange("dailySellDiamondLimit", 64, 1, 4096);
        MARKET_ACTIVE_CONTRACTS = builder.defineInRange("activeContractCount", 3, 1, 5);
        MARKET_CONTRACT_DURATION = builder.defineInRange("contractDurationMinutes", 360, 30, 10080);
        MARKET_CONTRACT_PREMIUM = builder.defineInRange("contractRewardPremium", 1.25, 1.0, 3.0);
        ONLINE_MARKET_ENABLED = builder.comment("Reads the public Royal Exchange feed to influence Minecraft prices. Minecraft never sends market data back.")
                .define("onlinePricesEnabled", true);
        ONLINE_MARKET_REFRESH = builder.comment("Minimum seconds between background online market refreshes.")
                .defineInRange("onlineRefreshSeconds", 60, 30, 900);
        ONLINE_MARKET_MAXIMUM_AGE = builder.comment("Maximum age of a cached online market tick before local-only pricing resumes.")
                .defineInRange("onlineMaximumAgeMinutes", 360, 5, 1440);
        ONLINE_MARKET_INFLUENCE = builder.comment("Strength applied to each online issuer's percentage movement.")
                .defineInRange("onlineInfluenceStrength", 1.0, 0.0, 3.0);
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private ColonyBridgeConfig() {
    }

    public static BridgeConfigValues values() {
        return new BridgeConfigValues(
                ENABLED.get(),
                EXPORT_ON_STARTUP.get(),
                EXPORT_ON_PLAYER_JOIN.get(),
                EXPORT_ON_SHUTDOWN.get(),
                EXPORT_ON_LAST_PLAYER_DISCONNECT.get(),
                PERIODIC_EXPORT_ENABLED.get(),
                PERIODIC_EXPORT_SECONDS.get(),
                RETAIN_SNAPSHOTS.get(),
                PRETTY_PRINT_JSON.get(),
                SHOW_EXPORT_PROGRESS.get(),
                INCLUDE_CITIZEN_POSITIONS.get(),
                INCLUDE_OWNER_UUID.get(),
                DAY_COUNTER_ENABLED.get(),
                REMOTE_SYNC_ENABLED.get(),
                REMOTE_ENDPOINT.get(),
                REMOTE_TOKEN.get()
        ).validated();
    }

    public static MarketConfig marketValues() {
        return new MarketConfig(MARKET_QUOTE_DELAY.get(), MARKET_QUOTE_VALIDITY.get(),
                MARKET_EVENT_FREQUENCY.get(), MARKET_VOLATILITY.get(), MARKET_MINIMUM_MULTIPLIER.get(),
                MARKET_MAXIMUM_MULTIPLIER.get(), MARKET_BUYING_ENABLED.get(), MARKET_OPERATOR_CONTROLS.get(),
                MARKET_SELLING_ENABLED.get(), MARKET_SELL_PRICE_RATIO.get(), MARKET_DAILY_SELL_LIMIT.get(),
                MARKET_ACTIVE_CONTRACTS.get(), MARKET_CONTRACT_DURATION.get(), MARKET_CONTRACT_PREMIUM.get()).validated();
    }

    public static OnlineMarketConfig onlineMarketValues() {
        return new OnlineMarketConfig(ONLINE_MARKET_ENABLED.get(), ONLINE_MARKET_REFRESH.get(),
                ONLINE_MARKET_MAXIMUM_AGE.get(), ONLINE_MARKET_INFLUENCE.get()).validated();
    }
}
