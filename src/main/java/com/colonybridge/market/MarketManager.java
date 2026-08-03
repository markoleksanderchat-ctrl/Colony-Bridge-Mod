package com.colonybridge.market;

import com.colonybridge.ColonyBridge;
import com.colonybridge.config.ColonyBridgeConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;

public final class MarketManager {
    private static final Map<MinecraftServer, MarketManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Path persistencePath;
    private final MarketClock clock;
    private final MarketStateRepository repository;
    private final OnlineMarketClient onlineMarket;
    private final VanillaItemClassifier classifier = new VanillaItemClassifier();
    private long seed;
    private final Map<String, MarketRecord> records = new HashMap<>();
    private final Map<String, MarketQuote> quotes = new HashMap<>();
    private final Set<String> completedQuoteIds = new HashSet<>();
    private final Map<String, MarketContract> contracts = new HashMap<>();
    private final Set<String> completedContractIds = new HashSet<>();
    private final Map<String, DailySellVolume> dailySellVolumes = new HashMap<>();
    private List<MarketEvent> activeEvents = List.of();

    private MarketManager(MinecraftServer server) {
        this.server = server;
        this.persistencePath = server.getWorldPath(LevelResource.ROOT).resolve("colonybridge").resolve("market.json");
        this.clock = SystemMarketClock.INSTANCE;
        this.repository = new JsonMarketStateRepository(persistencePath);
        this.onlineMarket = new OnlineMarketClient(persistencePath.resolveSibling("online-market-cache.json"));
        load();
        onlineMarket.refreshIfDue(clock.nowMillis(), ColonyBridgeConfig.onlineMarketValues());
    }

    public static synchronized MarketManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, MarketManager::new);
    }

    public static synchronized void close(MinecraftServer server) {
        MarketManager manager = INSTANCES.remove(server);
        if (manager != null) manager.save();
    }

    public synchronized MarketQuote requestQuote(ServerPlayer player, Item item, int requestedQuantity) {
        return requestQuote(player, item, requestedQuantity, TradeDirection.BUY);
    }

    public synchronized MarketQuote requestQuote(ServerPlayer player, Item item, int requestedQuantity, TradeDirection direction) {
        long now = clock.nowMillis();
        MarketConfig config = ColonyBridgeConfig.marketValues();
        Valuation value = value(item, now, config);
        MarketQuote quote = QuoteLifecycleService.create(UUID.randomUUID().toString(), player.getUUID().toString(),
                value.classified(), value.itemId(), requestedQuantity, direction, value.baseUnit(), value.currentUnit(),
                value.onlineInfluence(), activeEvents, now, config);
        quotes.put(quote.id(), quote);
        discardExpired(now);
        save();
        return quote;
    }

    public synchronized PurchaseResult completePurchase(ServerPlayer player, String quoteId, String itemId, int quantity) {
        return completeTrade(player, quoteId, itemId, quantity);
    }

    public synchronized PurchaseResult completeTrade(ServerPlayer player, String quoteId, String itemId, int quantity) {
        long now = clock.nowMillis();
        PlayerInventoryPort inventory = new ServerPlayerInventoryAdapter(player);
        MarketQuote quote = quotes.get(quoteId);
        MarketConfig config = ColonyBridgeConfig.marketValues();
        Item item = resolveVanilla(itemId);
        int remaining = remainingSellAllowance(inventory.playerId(), config);
        String validation = TradeExecutionService.validate(inventory, quote, completedQuoteIds.contains(quoteId),
                itemId, quantity, now, config, item != Items.AIR, remaining);
        if (validation != null) return PurchaseResult.failure(validation);
        MarketState before = snapshotState();
        MarketTransactionCoordinator.Result result = MarketTransactionCoordinator.execute(inventory,
                TradeExecutionService.mutation(quote), () -> {
                    quotes.put(quoteId, quote.completedCopy());
                    completedQuoteIds.add(quoteId);
                    records.computeIfPresent(itemId, (ignored, record) ->
                            MarketDynamics.afterTrade(record, quote.direction(), quantity, now));
                    if (quote.direction() == TradeDirection.SELL) recordSale(inventory.playerId(), quote.totalDiamondCost());
                }, () -> restoreState(before), this::persistState,
                quote.direction() == TradeDirection.BUY
                        ? "Purchased for " + quote.totalDiamondCost() + " diamonds."
                        : "Sold for " + quote.totalDiamondCost() + " diamonds.");
        if (result.failure() != null) ColonyBridge.LOGGER.error("Royal Exchange trade was rolled back.", result.failure());
        return new PurchaseResult(result.success(), result.message());
    }

    public synchronized List<MarketContract> contracts() {
        ensureContracts(clock.nowMillis());
        return contracts.values().stream().sorted(Comparator.comparing(MarketContract::id)).toList();
    }

    public synchronized PurchaseResult completeContract(ServerPlayer player, String contractId) {
        long now = clock.nowMillis();
        ensureContracts(now);
        MarketContract contract = contracts.get(contractId);
        PlayerInventoryPort inventory = new ServerPlayerInventoryAdapter(player);
        Item item = contract == null ? Items.AIR : resolveVanilla(contract.itemId());
        String validation = ContractExecutionService.validate(inventory, contract,
                completedContractIds.contains(contractId), now, item != Items.AIR);
        if (validation != null) return PurchaseResult.failure(validation);
        MarketState before = snapshotState();
        MarketTransactionCoordinator.Result result = MarketTransactionCoordinator.execute(inventory,
                ContractExecutionService.mutation(contract), () -> {
                    completedContractIds.add(contract.id());
                    contracts.remove(contract.id());
                    records.computeIfPresent(contract.itemId(), (ignored, record) ->
                            MarketDynamics.afterTrade(record, TradeDirection.SELL, contract.quantity(), now));
                }, () -> restoreState(before), this::persistState,
                "Contract fulfilled for " + contract.rewardDiamonds() + " diamonds.");
        if (result.failure() != null) ColonyBridge.LOGGER.error("Royal Exchange contract was rolled back.", result.failure());
        return new PurchaseResult(result.success(), result.message());
    }

    public synchronized int remainingSellAllowance(ServerPlayer player) {
        return remainingSellAllowance(player.getUUID().toString(), ColonyBridgeConfig.marketValues());
    }

    public synchronized List<MarketEvent> events() {
        activeEvents = MarketEventManager.advance(activeEvents, seed, clock.nowMillis(),
                ColonyBridgeConfig.marketValues().eventFrequencyMinutes());
        return List.copyOf(activeEvents);
    }

    public synchronized MarketEvent forceEvent(String templateId) {
        MarketConfig config = ColonyBridgeConfig.marketValues();
        MarketEvent event = MarketEventManager.force(templateId, seed, clock.nowMillis(), config.eventFrequencyMinutes() * 2);
        List<MarketEvent> next = new ArrayList<>(activeEvents);
        next.add(event);
        activeEvents = List.copyOf(next);
        save();
        return event;
    }

    public synchronized void reset() {
        seed = new SecureRandom().nextLong();
        records.clear(); quotes.clear(); completedQuoteIds.clear(); activeEvents = List.of();
        contracts.clear(); completedContractIds.clear(); dailySellVolumes.clear();
        save();
    }

    public synchronized int recordCount() { return records.size(); }
    public synchronized int quoteCount() { return quotes.size(); }
    public long seed() { return seed; }

    public synchronized String onlineMarketStatus() {
        OnlineMarketConfig config = ColonyBridgeConfig.onlineMarketValues();
        long now = clock.nowMillis();
        onlineMarket.refreshIfDue(now, config);
        return onlineMarket.status(now, config);
    }

    public synchronized int onlineMarketState() {
        OnlineMarketConfig config = ColonyBridgeConfig.onlineMarketValues();
        return onlineMarket.state(clock.nowMillis(), config);
    }

    public synchronized void tickOnlineMarket() {
        OnlineMarketConfig config = ColonyBridgeConfig.onlineMarketValues();
        onlineMarket.refreshIfDue(clock.nowMillis(), config);
        if (!config.enabled() || server.getPlayerList().getPlayerCount() == 0) return;
        for (OnlineMarketEvent event : onlineMarket.pollNewEvents()) {
            ChatFormatting severityColor = switch (event.severity()) {
                case "crisis" -> ChatFormatting.DARK_RED;
                case "severe" -> ChatFormatting.RED;
                case "material" -> ChatFormatting.GOLD;
                default -> ChatFormatting.YELLOW;
            };
            String impact = String.format(Locale.ROOT, "%+.1f%%", event.impactPercent());
            Component notice = Component.literal("[Royal Exchange] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(event.severity().toUpperCase(Locale.ROOT) + " — " + event.title())
                            .withStyle(severityColor, ChatFormatting.BOLD))
                    .append(Component.literal("\n" + event.description() + " "
                            + String.join("/", event.affectedTickers()) + " " + impact).withStyle(ChatFormatting.GRAY));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(notice);
            ColonyBridge.LOGGER.info("Royal Exchange anomaly announced: {} ({}, {})", event.title(), event.severity(), impact);
        }
    }

    private Valuation value(Item item, long now, MarketConfig config) {
        ClassifiedItem classified = classifier.classify(server, item, 1);
        String itemId = classified.input().itemId();
        activeEvents = MarketEventManager.advance(activeEvents, seed, now, config.eventFrequencyMinutes());
        double calculatedBase = PriceCalculator.calculate(classified.input().withQuantity(1)).rawDiamondValue();
        double baseUnit = classified.fixedDiamondValue() == null ? calculatedBase : classified.fixedDiamondValue();
        MarketRecord source = records.computeIfAbsent(itemId,
                ignored -> new MarketRecord(itemId, baseUnit, classified.volatility(), 0, now, List.of(baseUnit)));
        double eventModifier = MarketEventManager.modifier(activeEvents, itemId, classified.tags(), now);
        MarketRecord advanced = MarketDynamics.advance(source, seed, now, config.volatilityStrength(), eventModifier,
                config.minimumPriceMultiplier(), config.maximumPriceMultiplier());
        records.put(itemId, advanced);
        OnlineMarketInfluence onlineInfluence = onlineMarket.influence(itemId, classified.tags(), now,
                ColonyBridgeConfig.onlineMarketValues()).orElse(null);
        double onlineMultiplier = onlineInfluence == null ? 1 : onlineInfluence.multiplier();
        double currentUnit = advanced.priceHistory().get(advanced.priceHistory().size() - 1) * onlineMultiplier;
        return new Valuation(classified, itemId, baseUnit, currentUnit, onlineInfluence);
    }

    private void ensureContracts(long now) {
        MarketConfig config = ColonyBridgeConfig.marketValues();
        boolean changed = contracts.entrySet().removeIf(entry -> entry.getValue().expired(now));
        long duration = config.contractDurationMinutes() * 60_000L;
        long bucket = Math.floorDiv(now, duration);
        int attempt = 0;
        while (contracts.size() < config.activeContractCount() && attempt < 100) {
            String id = "contract-" + bucket + "-" + attempt;
            RoyalContractManager.ContractTemplate template = RoyalContractManager.template(seed, bucket, attempt++);
            if (contracts.containsKey(id) || completedContractIds.contains(id)
                    || contracts.values().stream().anyMatch(contract -> contract.itemId().equals(template.itemId()))) continue;
            Item item = resolveVanilla(template.itemId());
            if (item == Items.AIR) continue;
            Valuation valuation = value(item, now, config);
            double bulk = PriceCalculator.bulkModifier(template.quantity(), valuation.classified().input().wholesaleSuitability());
            int reward = Math.max(1, (int) Math.round(valuation.currentUnit() * template.quantity() * bulk
                    * config.sellPriceRatio() * config.contractRewardPremium()));
            contracts.put(id, new MarketContract(id, template.itemId(), template.quantity(), reward,
                    template.title(), template.description(), now, now + duration));
            changed = true;
        }
        if (changed) save();
    }

    private void load() {
        try {
            MarketState state = repository.load();
            if (state == null) { seed = new SecureRandom().nextLong(); save(); return; }
            seed = state.seed();
            if (state.records() != null) records.putAll(state.records());
            if (state.quotes() != null) quotes.putAll(state.quotes());
            if (state.completedQuoteIds() != null) completedQuoteIds.addAll(state.completedQuoteIds());
            if (state.contracts() != null) contracts.putAll(state.contracts());
            if (state.completedContractIds() != null) completedContractIds.addAll(state.completedContractIds());
            if (state.dailySellVolumes() != null) dailySellVolumes.putAll(state.dailySellVolumes());
            activeEvents = state.activeEvents() == null ? List.of() : List.copyOf(state.activeEvents());
            discardExpired(clock.nowMillis());
        } catch (IOException failure) {
            ColonyBridge.LOGGER.warn("Royal Exchange market data could not be loaded; a fresh market was created.", failure);
            seed = new SecureRandom().nextLong(); save();
        }
    }

    public synchronized void save() {
        try {
            persistState();
        } catch (IOException failure) {
            ColonyBridge.LOGGER.error("Royal Exchange market data could not be saved.", failure);
        }
    }

    private void discardExpired(long now) {
        quotes.entrySet().removeIf(entry -> entry.getValue().expired(now - 86_400_000L));
        if (completedQuoteIds.size() > 4096) completedQuoteIds.clear();
        if (completedContractIds.size() > 4096) completedContractIds.clear();
        long today = clock.utcEpochDay();
        dailySellVolumes.entrySet().removeIf(entry -> entry.getValue().epochDay() < today - 1);
    }

    private int remainingSellAllowance(String playerId, MarketConfig config) {
        return MarketDomainOperations.remainingSellAllowance(dailySellVolumes.get(playerId),
                clock.utcEpochDay(), config.dailySellDiamondLimit());
    }

    private void recordSale(String playerId, int diamonds) {
        dailySellVolumes.put(playerId, MarketDomainOperations.recordSale(dailySellVolumes.get(playerId),
                clock.utcEpochDay(), diamonds));
    }

    private static Item resolveVanilla(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !"minecraft".equals(key.getNamespace())) return Items.AIR;
        return BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
    }

    private MarketState snapshotState() {
        return new MarketState(MarketState.CURRENT_VERSION, seed, Map.copyOf(records), List.copyOf(activeEvents),
                Map.copyOf(quotes), Set.copyOf(completedQuoteIds), ColonyBridgeConfig.marketValues(),
                Map.copyOf(contracts), Set.copyOf(completedContractIds), Map.copyOf(dailySellVolumes));
    }

    private void persistState() throws IOException {
        repository.save(snapshotState());
    }

    private void restoreState(MarketState state) {
        seed = state.seed();
        records.clear(); records.putAll(state.records());
        activeEvents = List.copyOf(state.activeEvents());
        quotes.clear(); quotes.putAll(state.quotes());
        completedQuoteIds.clear(); completedQuoteIds.addAll(state.completedQuoteIds());
        contracts.clear(); contracts.putAll(state.contracts());
        completedContractIds.clear(); completedContractIds.addAll(state.completedContractIds());
        dailySellVolumes.clear(); dailySellVolumes.putAll(state.dailySellVolumes());
    }

    private record Valuation(ClassifiedItem classified, String itemId, double baseUnit, double currentUnit,
                             OnlineMarketInfluence onlineInfluence) {}

    public record PurchaseResult(boolean success, String message) {
        static PurchaseResult success(String message) { return new PurchaseResult(true, message); }
        static PurchaseResult failure(String message) { return new PurchaseResult(false, message); }
    }
}
