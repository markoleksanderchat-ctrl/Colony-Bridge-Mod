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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

public final class MarketManager {
    private static final Map<MinecraftServer, MarketManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Path persistencePath;
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
        this.onlineMarket = new OnlineMarketClient(persistencePath.resolveSibling("online-market-cache.json"));
        load();
        onlineMarket.refreshIfDue(System.currentTimeMillis(), ColonyBridgeConfig.onlineMarketValues());
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
        int quantity = Math.max(1, Math.min(1024, requestedQuantity));
        long now = System.currentTimeMillis();
        MarketConfig config = ColonyBridgeConfig.marketValues();
        Valuation value = value(item, now, config);
        double ratio = direction == TradeDirection.SELL ? config.sellPriceRatio() : 1.0;
        double tradeUnit = value.currentUnit() * ratio;
        if (tradeUnit < 1) quantity = Math.max(quantity, PriceCalculator.practicalItemsPerDiamond(tradeUnit));
        double bulk = PriceCalculator.bulkModifier(quantity, value.classified().input().wholesaleSuitability());
        double baseTotal = value.baseUnit() * ratio * quantity * bulk;
        double currentTotal = tradeUnit * quantity * bulk;
        int diamonds = Math.max(1, direction == TradeDirection.SELL ? (int) Math.floor(currentTotal) : (int) Math.round(currentTotal));
        String condition = value.currentUnit() > value.baseUnit() * 1.08 ? "Demand is elevated"
                : value.currentUnit() < value.baseUnit() * 0.92 ? "Supply is favorable" : "Market is steady";
        String eventText = activeEvents.stream().filter(event -> event.affects(value.itemId(), value.classified().tags()) && event.strengthAt(now) > 0)
                .map(MarketEvent::title).findFirst().map(title -> " Active influence: " + title + ".").orElse("");
        String explanation = "Primary increase: " + value.classified().strongestIncrease() + "; primary restraint: "
                + value.classified().strongestDecrease() + "." + eventText;
        if (value.onlineInfluence() != null) {
            explanation += " Online Exchange: " + value.onlineInfluence().summary() + ".";
        }
        long ready = now + config.quoteDelaySeconds() * 1000L;
        double validityFactor = 1.25 - value.classified().volatility() * 0.5;
        long expires = ready + Math.max(15_000L, Math.round(config.quoteValiditySeconds() * 1000L * validityFactor));
        MarketQuote quote = new MarketQuote(UUID.randomUUID().toString(), player.getUUID().toString(), value.itemId(),
                quantity, direction, baseTotal, currentTotal, diamonds, condition, explanation, now, ready, expires, false);
        quotes.put(quote.id(), quote);
        discardExpired(now);
        save();
        return quote;
    }

    public synchronized PurchaseResult completePurchase(ServerPlayer player, String quoteId, String itemId, int quantity) {
        return completeTrade(player, quoteId, itemId, quantity);
    }

    public synchronized PurchaseResult completeTrade(ServerPlayer player, String quoteId, String itemId, int quantity) {
        long now = System.currentTimeMillis();
        if (player.isCreative() || player.isSpectator()) {
            return PurchaseResult.failure("Royal Exchange trades require Survival or Adventure mode.");
        }
        MarketQuote quote = quotes.get(quoteId);
        if (completedQuoteIds.contains(quoteId) || quote != null && quote.completed()) return PurchaseResult.failure("That quote has already been completed.");
        if (!QuoteManager.canComplete(quote, player.getUUID().toString(), itemId, quantity, now)) {
            return PurchaseResult.failure(quote != null && quote.expired(now) ? "That quote has expired." : "That quote is no longer valid.");
        }
        MarketConfig config = ColonyBridgeConfig.marketValues();
        Item item = resolveVanilla(itemId);
        if (item == Items.AIR) return PurchaseResult.failure("That item is not available.");
        if (quote.direction() == TradeDirection.BUY) {
            if (!config.buyingEnabled()) return PurchaseResult.failure("Royal Exchange buying is disabled.");
            if (!QuoteManager.canAfford(countDiamonds(player), quote.totalDiamondCost())) return PurchaseResult.failure("You do not have enough diamonds.");
            removeDiamonds(player, quote.totalDiamondCost());
            giveItems(player, item, quantity);
        } else {
            if (!config.sellingEnabled()) return PurchaseResult.failure("Royal Exchange selling is disabled.");
            int remaining = remainingSellAllowance(player, config);
            if (quote.totalDiamondCost() > remaining) return PurchaseResult.failure("Your daily Exchange limit has " + remaining + " diamonds remaining.");
            if (countPlainItems(player, item) < quantity) return PurchaseResult.failure("You do not have enough unmodified items.");
            removePlainItems(player, item, quantity);
            giveItems(player, Items.DIAMOND, quote.totalDiamondCost());
            recordSale(player, quote.totalDiamondCost());
        }
        quotes.put(quoteId, quote.completedCopy());
        completedQuoteIds.add(quoteId);
        records.computeIfPresent(itemId, (ignored, record) -> MarketDynamics.afterTrade(record, quote.direction(), quantity, now));
        save();
        return PurchaseResult.success(quote.direction() == TradeDirection.BUY
                ? "Purchased for " + quote.totalDiamondCost() + " diamonds."
                : "Sold for " + quote.totalDiamondCost() + " diamonds.");
    }

    public synchronized List<MarketContract> contracts() {
        ensureContracts(System.currentTimeMillis());
        return contracts.values().stream().sorted(Comparator.comparing(MarketContract::id)).toList();
    }

    public synchronized PurchaseResult completeContract(ServerPlayer player, String contractId) {
        if (player.isCreative() || player.isSpectator()) {
            return PurchaseResult.failure("Royal contracts require Survival or Adventure mode.");
        }
        ensureContracts(System.currentTimeMillis());
        MarketContract contract = contracts.get(contractId);
        if (contract == null || completedContractIds.contains(contractId) || contract.expired(System.currentTimeMillis())) {
            return PurchaseResult.failure("That contract is no longer available.");
        }
        Item item = resolveVanilla(contract.itemId());
        if (item == Items.AIR || countPlainItems(player, item) < contract.quantity()) {
            return PurchaseResult.failure("You do not have the required unmodified goods.");
        }
        removePlainItems(player, item, contract.quantity());
        giveItems(player, Items.DIAMOND, contract.rewardDiamonds());
        completedContractIds.add(contract.id());
        contracts.remove(contract.id());
        records.computeIfPresent(contract.itemId(), (ignored, record) -> MarketDynamics.afterTrade(record,
                TradeDirection.SELL, contract.quantity(), System.currentTimeMillis()));
        save();
        return PurchaseResult.success("Contract fulfilled for " + contract.rewardDiamonds() + " diamonds.");
    }

    public synchronized int remainingSellAllowance(ServerPlayer player) {
        return remainingSellAllowance(player, ColonyBridgeConfig.marketValues());
    }

    public synchronized List<MarketEvent> events() {
        activeEvents = MarketEventManager.advance(activeEvents, seed, System.currentTimeMillis(),
                ColonyBridgeConfig.marketValues().eventFrequencyMinutes());
        return List.copyOf(activeEvents);
    }

    public synchronized MarketEvent forceEvent(String templateId) {
        MarketConfig config = ColonyBridgeConfig.marketValues();
        MarketEvent event = MarketEventManager.force(templateId, seed, System.currentTimeMillis(), config.eventFrequencyMinutes() * 2);
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
        onlineMarket.refreshIfDue(System.currentTimeMillis(), config);
        return onlineMarket.status(System.currentTimeMillis(), config);
    }

    public synchronized int onlineMarketState() {
        OnlineMarketConfig config = ColonyBridgeConfig.onlineMarketValues();
        return onlineMarket.state(System.currentTimeMillis(), config);
    }

    public synchronized void tickOnlineMarket() {
        OnlineMarketConfig config = ColonyBridgeConfig.onlineMarketValues();
        onlineMarket.refreshIfDue(System.currentTimeMillis(), config);
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
            MarketState state = MarketPersistence.load(persistencePath);
            if (state == null) { seed = new SecureRandom().nextLong(); save(); return; }
            seed = state.seed();
            if (state.records() != null) records.putAll(state.records());
            if (state.quotes() != null) quotes.putAll(state.quotes());
            if (state.completedQuoteIds() != null) completedQuoteIds.addAll(state.completedQuoteIds());
            if (state.contracts() != null) contracts.putAll(state.contracts());
            if (state.completedContractIds() != null) completedContractIds.addAll(state.completedContractIds());
            if (state.dailySellVolumes() != null) dailySellVolumes.putAll(state.dailySellVolumes());
            activeEvents = state.activeEvents() == null ? List.of() : List.copyOf(state.activeEvents());
            discardExpired(System.currentTimeMillis());
        } catch (IOException failure) {
            ColonyBridge.LOGGER.warn("Royal Exchange market data could not be loaded; a fresh market was created.", failure);
            seed = new SecureRandom().nextLong(); save();
        }
    }

    public synchronized void save() {
        try {
            MarketPersistence.save(persistencePath, new MarketState(MarketState.CURRENT_VERSION, seed,
                    Map.copyOf(records), List.copyOf(activeEvents), Map.copyOf(quotes), Set.copyOf(completedQuoteIds),
                    ColonyBridgeConfig.marketValues(), Map.copyOf(contracts), Set.copyOf(completedContractIds),
                    Map.copyOf(dailySellVolumes)));
        } catch (IOException failure) {
            ColonyBridge.LOGGER.error("Royal Exchange market data could not be saved.", failure);
        }
    }

    private void discardExpired(long now) {
        quotes.entrySet().removeIf(entry -> entry.getValue().expired(now - 86_400_000L));
        if (completedQuoteIds.size() > 4096) completedQuoteIds.clear();
        if (completedContractIds.size() > 4096) completedContractIds.clear();
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        dailySellVolumes.entrySet().removeIf(entry -> entry.getValue().epochDay() < today - 1);
    }

    private int remainingSellAllowance(ServerPlayer player, MarketConfig config) {
        DailySellVolume volume = dailySellVolumes.get(player.getUUID().toString());
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        int used = volume != null && volume.epochDay() == today ? volume.diamondsPaid() : 0;
        return Math.max(0, config.dailySellDiamondLimit() - used);
    }

    private void recordSale(ServerPlayer player, int diamonds) {
        String id = player.getUUID().toString();
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        DailySellVolume old = dailySellVolumes.get(id);
        int used = old != null && old.epochDay() == today ? old.diamondsPaid() : 0;
        dailySellVolumes.put(id, new DailySellVolume(today, used + diamonds));
    }

    private static Item resolveVanilla(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !"minecraft".equals(key.getNamespace())) return Items.AIR;
        return BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
    }

    private static int countDiamonds(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.DIAMOND)) count += stack.getCount();
        }
        return count;
    }

    private static int countPlainItems(ServerPlayer player, Item item) {
        int count = 0;
        ItemStack sample = new ItemStack(item);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, sample)) count += stack.getCount();
        }
        return count;
    }

    private static void removeDiamonds(ServerPlayer player, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.DIAMOND)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.getInventory().setChanged();
    }

    private static void removePlainItems(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        ItemStack sample = new ItemStack(item);
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, sample)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.getInventory().setChanged();
    }

    private static void giveItems(ServerPlayer player, Item item, int quantity) {
        int remaining = quantity;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(item, Math.min(remaining, item.getDefaultMaxStackSize()));
            remaining -= stack.getCount();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    private record Valuation(ClassifiedItem classified, String itemId, double baseUnit, double currentUnit,
                             OnlineMarketInfluence onlineInfluence) {}

    public record PurchaseResult(boolean success, String message) {
        static PurchaseResult success(String message) { return new PurchaseResult(true, message); }
        static PurchaseResult failure(String message) { return new PurchaseResult(false, message); }
    }
}
