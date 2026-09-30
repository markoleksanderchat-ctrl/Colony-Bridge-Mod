package com.colonybridge.market.trader;

import com.colonybridge.market.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RoyalExchangeMenu extends AbstractContainerMenu {
    public static final int SELECT_ITEM_BASE = 1_000;
    public static final int SET_QUANTITY_BASE = 50_000_000;
    public static final int DECREASE_QUANTITY = 1;
    public static final int INCREASE_QUANTITY = 2;
    public static final int REQUEST_QUOTE = 3;
    public static final int COMPLETE_TRADE = 4;
    public static final int BUY_MODE = 5;
    public static final int SELL_MODE = 6;
    public static final int CONTRACTS_VIEW = 7;
    public static final int PREV_CONTRACT = 8;
    public static final int NEXT_CONTRACT = 9;
    public static final int COMPLETE_CONTRACT = 10;
    public static final int ADD_TO_BASKET = 11;
    public static final int REQUEST_BASKET_QUOTE = 12;
    public static final int COMPLETE_BASKET_TRADE = 13;
    public static final int SELECT_BASKET_BASE = 2_000_000;
    public static final int REMOVE_BASKET_BASE = 3_000_000;
    public static final int BASKET_LIMIT = 9;
    // A bounded page of display data; selecting a row never executes a contract.
    public static final int CONTRACT_PAGE_SIZE = 5;
    public static final int SELECT_CONTRACT_ROW_BASE = 4_000_000;
    public static final int DATA_SELECTED_ITEM = 0;
    public static final int DATA_QUANTITY = 1;
    public static final int DATA_QUOTE_STATE = 2;
    public static final int DATA_DIAMOND_COST = 3;
    public static final int DATA_QUOTE_SECONDS = 4;
    public static final int DATA_BASE_PRICE = 5;
    public static final int DATA_CURRENT_PRICE = 6;
    public static final int DATA_CONDITION = 7;
    public static final int DATA_TRADE_DIRECTION = 8;
    public static final int DATA_CONTRACT_VIEW = 9;
    public static final int DATA_CONTRACT_ITEM = 10;
    public static final int DATA_CONTRACT_QUANTITY = 11;
    public static final int DATA_CONTRACT_REWARD = 12;
    public static final int DATA_CONTRACT_SECONDS = 13;
    public static final int DATA_CONTRACT_INDEX = 14;
    public static final int DATA_CONTRACT_COUNT = 15;
    public static final int DATA_CONTRACT_STATE = 16;
    public static final int DATA_SELL_ALLOWANCE = 17;
    public static final int DATA_ONLINE_STATE = 18;
    public static final int DATA_RESULT = 19;
    public static final int DATA_SELECTED_ITEM_HIGH = 20;
    public static final int DATA_DIAMOND_COST_HIGH = 21;
    public static final int DATA_BASE_PRICE_HIGH = 22;
    public static final int DATA_CURRENT_PRICE_HIGH = 23;
    public static final int DATA_CONTRACT_ITEM_HIGH = 24;
    public static final int DATA_CONTRACT_REWARD_HIGH = 25;
    public static final int DATA_CONTRACT_SECONDS_HIGH = 26;
    public static final int DATA_BLOCK_X = 27;
    public static final int DATA_BLOCK_X_HIGH = 28;
    public static final int DATA_BLOCK_Y = 29;
    public static final int DATA_BLOCK_Y_HIGH = 30;
    public static final int DATA_BLOCK_Z = 31;
    public static final int DATA_BLOCK_Z_HIGH = 32;
    public static final int DATA_BLOCK_READY = 33;
    public static final int DATA_BASKET_COUNT = 34;
    public static final int DATA_BASKET_STATE = 35;
    public static final int DATA_BASKET_TOTAL = 36;
    public static final int DATA_BASKET_TOTAL_HIGH = 37;
    public static final int DATA_BASKET_SECONDS = 38;
    public static final int DATA_BASKET_ERROR = 39;
    public static final int DATA_BASKET_LINES = 40;
    public static final int DATA_BASKET_REQUIRED_QUANTITIES = DATA_BASKET_LINES + BASKET_LIMIT * 5;
    public static final int DATA_CONTRACT_ROWS = DATA_BASKET_REQUIRED_QUANTITIES + BASKET_LIMIT;
    public static final int CONTRACT_ROW_DATA_SIZE = 5;
    public static final int DATA_COUNT = DATA_CONTRACT_ROWS + CONTRACT_PAGE_SIZE * CONTRACT_ROW_DATA_SIZE;

    private static final RoyalExchangeDraftCache<DraftKey, Draft> DRAFTS =
            new RoyalExchangeDraftCache<>(() -> System.nanoTime() / 1_000_000, 64);

    private final Inventory inventory;
    private final MarketManager manager;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final DraftKey draftKey;
    private MarketQuote activeQuote;
    private final List<BasketLine> basket = new ArrayList<>();
    private final Map<String, Integer> basketRequiredQuantities = new HashMap<>();
    private BasketQuote activeBasketQuote;
    private int refreshTicks;
    private int contractIndex;

    public RoyalExchangeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT), null);
    }

    public RoyalExchangeMenu(int containerId, Inventory inventory, MarketManager manager,
                             ContainerLevelAccess access, BlockPos pos) {
        this(containerId, inventory, manager, access, new SimpleContainerData(DATA_COUNT),
                new DraftKey(inventory.player.getServer(), inventory.player.level().dimension(), pos,
                        inventory.player.getUUID()));
        data.set(DATA_QUANTITY, 1);
        setWide(data, DATA_BLOCK_X, DATA_BLOCK_X_HIGH, pos.getX());
        setWide(data, DATA_BLOCK_Y, DATA_BLOCK_Y_HIGH, pos.getY());
        setWide(data, DATA_BLOCK_Z, DATA_BLOCK_Z_HIGH, pos.getZ());
        data.set(DATA_BLOCK_READY, 1);
        DRAFTS.get(draftKey).ifPresent(this::restoreDraft);
        updateContractData();
    }

    private RoyalExchangeMenu(int containerId, Inventory inventory, MarketManager manager,
                              ContainerLevelAccess access, ContainerData data, DraftKey draftKey) {
        super(MarketRegistries.ROYAL_EXCHANGE_MENU.get(), containerId);
        this.inventory = inventory;
        this.manager = manager;
        this.access = access;
        this.data = data;
        this.draftKey = draftKey;
        addDataSlots(data);
    }

    private void restoreDraft(Draft draft) {
        setWide(data, DATA_SELECTED_ITEM, DATA_SELECTED_ITEM_HIGH, draft.selectedItemId());
        data.set(DATA_QUANTITY, Math.max(1, Math.min(1024, draft.quantity())));
        data.set(DATA_TRADE_DIRECTION, draft.sellMode() ? 1 : 0);
        data.set(DATA_CONTRACT_VIEW, draft.contractView() ? 1 : 0);
        data.set(DATA_QUOTE_STATE, draft.quoteState());
        data.set(DATA_RESULT, draft.result());
        contractIndex = draft.contractIndex();
        activeQuote = draft.activeQuote();
        basket.clear();
        basket.addAll(draft.basket());
        activeBasketQuote = draft.activeBasketQuote();
        data.set(DATA_BASKET_STATE, draft.basketState());
        data.set(DATA_BASKET_ERROR, draft.basketError());
        basketRequiredQuantities.clear();
        basketRequiredQuantities.putAll(draft.basketRequiredQuantities());
        updateBasketData();
        updateQuoteData();
    }

    @Override
    public void removed(Player player) {
        if (draftKey != null) {
            DRAFTS.put(draftKey, new Draft(getWide(data, DATA_SELECTED_ITEM, DATA_SELECTED_ITEM_HIGH),
                    quantity(), sellMode(), contractView(), contractIndex, quoteState(), data.get(DATA_RESULT), activeQuote,
                    List.copyOf(basket), activeBasketQuote, data.get(DATA_BASKET_STATE), data.get(DATA_BASKET_ERROR),
                    Map.copyOf(basketRequiredQuantities)));
        }
        super.removed(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (manager == null) return false;
        if (id >= SELECT_BASKET_BASE && id < SELECT_BASKET_BASE + BASKET_LIMIT) {
            int index = id - SELECT_BASKET_BASE;
            if (index >= basket.size()) return false;
            BasketLine line = basket.get(index);
            Item item = VanillaItems.resolve(line.itemId());
            setWide(data, DATA_SELECTED_ITEM, DATA_SELECTED_ITEM_HIGH, BuiltInRegistries.ITEM.getId(item));
            data.set(DATA_QUANTITY, line.quantity());
            return true;
        }
        if (id >= REMOVE_BASKET_BASE && id < REMOVE_BASKET_BASE + BASKET_LIMIT) {
            int index = id - REMOVE_BASKET_BASE;
            if (index >= basket.size()) return false;
            basket.remove(index);
            invalidateBasketQuote();
            updateBasketData();
            return true;
        }
        if (id == ADD_TO_BASKET && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            Item item = selectedItem();
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            String itemId = key == null ? null : key.toString();
            int quantity = data.get(DATA_QUANTITY);
            if (!sellMode() || !MarketItemIds.isTradable(itemId) || quantity < 1 || quantity > 1024
                    || new ServerPlayerInventoryAdapter(serverPlayer).countPlainItems(itemId) < quantity) {
                serverPlayer.sendSystemMessage(Component.literal("Basket needs enough unmodified goods in your inventory."));
                return true;
            }
            int existing = -1;
            for (int index = 0; index < basket.size(); index++) {
                if (basket.get(index).itemId().equals(itemId)) { existing = index; break; }
            }
            if (existing < 0 && basket.size() >= BASKET_LIMIT) {
                serverPlayer.sendSystemMessage(Component.literal("The sell basket holds up to nine different goods."));
                return true;
            }
            BasketLine line = new BasketLine(itemId, quantity);
            if (existing < 0) basket.add(line);
            else basket.set(existing, line);
            invalidateBasketQuote();
            updateBasketData();
            return true;
        }
        if (id == REQUEST_BASKET_QUOTE && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (!sellMode() || basket.isEmpty()) return false;
            try {
                activeBasketQuote = manager.requestBasketQuote(serverPlayer, List.copyOf(basket));
                basketRequiredQuantities.clear();
                data.set(DATA_BASKET_STATE, 1);
                data.set(DATA_BASKET_ERROR, 0);
            } catch (IllegalArgumentException failure) {
                basketRequiredQuantities.clear();
                invalidateBasketQuote();
                data.set(DATA_BASKET_STATE, 4);
                data.set(DATA_BASKET_ERROR, basketErrorCode(failure.getMessage()));
                if (failure instanceof BasketTradeService.QuantityRequiredException quantityFailure) {
                    basketRequiredQuantities.putAll(quantityFailure.requiredQuantities());
                    for (BasketLine line : basket) {
                        Integer required = basketRequiredQuantities.get(line.itemId());
                        if (required != null) serverPlayer.sendSystemMessage(
                                quantityWarning(VanillaItems.resolve(line.itemId()), required));
                    }
                } else {
                    serverPlayer.sendSystemMessage(Component.literal(failure.getMessage()));
                }
            }
            updateBasketData();
            return true;
        }
        if (id == COMPLETE_BASKET_TRADE && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (!sellMode() || activeBasketQuote == null || basket.isEmpty() || basketState() != 2) return false;
            MarketManager.PurchaseResult result = manager.completeBasketTrade(serverPlayer, activeBasketQuote);
            serverPlayer.sendSystemMessage(Component.literal(result.message()));
            data.set(DATA_BASKET_STATE, result.success() ? 3 : 4);
            data.set(DATA_BASKET_ERROR, result.success() ? 0 : basketErrorCode(result.message()));
            if (result.success()) basket.clear();
            updateBasketData();
            return true;
        }
        if (id == BUY_MODE || id == SELL_MODE || id == CONTRACTS_VIEW) {
            data.set(DATA_TRADE_DIRECTION, id == SELL_MODE ? 1 : 0);
            data.set(DATA_CONTRACT_VIEW, id == CONTRACTS_VIEW ? 1 : 0);
            data.set(DATA_QUOTE_STATE, 0);
            activeQuote = null;
            data.set(DATA_RESULT, 0);
            data.set(DATA_CONTRACT_STATE, 0);
            updateContractData();
            return true;
        }
        if (id >= SELECT_CONTRACT_ROW_BASE && id < SELECT_CONTRACT_ROW_BASE + CONTRACT_PAGE_SIZE) {
            if (!contractView()) return false;
            int index = contractPageStart() + id - SELECT_CONTRACT_ROW_BASE;
            if (index >= manager.contracts().size()) return false;
            contractIndex = index;
            data.set(DATA_CONTRACT_STATE, 0);
            updateContractData();
            return true;
        }
        if (id == PREV_CONTRACT || id == NEXT_CONTRACT) {
            int total = Math.max(1, data.get(DATA_CONTRACT_COUNT));
            contractIndex = Math.floorMod(contractIndex + (id == NEXT_CONTRACT ? 1 : -1), total);
            data.set(DATA_CONTRACT_STATE, 0);
            updateContractData();
            return true;
        }
        if (id == COMPLETE_CONTRACT && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            List<MarketContract> available = manager.contracts();
            if (available.isEmpty()) return false;
            contractIndex = Math.min(contractIndex, available.size() - 1);
            MarketManager.PurchaseResult result = manager.completeContract(serverPlayer, available.get(contractIndex).id());
            data.set(DATA_RESULT, TradeFeedback.code(result.message()));
            serverPlayer.sendSystemMessage(Component.literal(result.message()));
            data.set(DATA_CONTRACT_STATE, result.success() ? 1 : 2);
            if (result.success()) contractIndex = 0;
            updateContractData();
            return true;
        }
        if (id > SET_QUANTITY_BASE && id <= SET_QUANTITY_BASE + 1024) {
            data.set(DATA_QUANTITY, id - SET_QUANTITY_BASE);
            if (sellMode() && basketContains(selectedItem())) invalidateBasketQuote();
            data.set(DATA_QUOTE_STATE, 0);
            data.set(DATA_RESULT, 0);
            activeQuote = null;
            return true;
        }
        if (id >= SELECT_ITEM_BASE && id < SELECT_ITEM_BASE + BuiltInRegistries.ITEM.size()) {
            Item selected = BuiltInRegistries.ITEM.byId(id - SELECT_ITEM_BASE);
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(selected);
            if (selected == Items.AIR || key == null || !MarketItemIds.isTradable(key.toString())) return false;
            setWide(data, DATA_SELECTED_ITEM, DATA_SELECTED_ITEM_HIGH, id - SELECT_ITEM_BASE);
            if (sellMode() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                data.set(DATA_QUANTITY, Math.max(1, Math.min(1024,
                        new ServerPlayerInventoryAdapter(serverPlayer).countPlainItems(key.toString()))));
            }
            data.set(DATA_QUOTE_STATE, 0);
            activeQuote = null;
            data.set(DATA_RESULT, 0);
            return true;
        }
        if (id == DECREASE_QUANTITY || id == INCREASE_QUANTITY) {
            int quantity = data.get(DATA_QUANTITY);
            data.set(DATA_QUANTITY, id == INCREASE_QUANTITY ? Math.min(1024, quantity + quantityStep(quantity))
                    : Math.max(1, quantity - quantityStep(Math.max(1, quantity - 1))));
            if (sellMode() && basketContains(selectedItem())) invalidateBasketQuote();
            data.set(DATA_QUOTE_STATE, 0);
            activeQuote = null;
            data.set(DATA_RESULT, 0);
            return true;
        }
        Item selected = selectedItem();
        if (selected == Items.AIR || !MarketItemIds.isTradable(BuiltInRegistries.ITEM.getKey(selected).toString())) return false;
        if (id == REQUEST_QUOTE && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            activeQuote = manager.requestQuote(serverPlayer, selected, data.get(DATA_QUANTITY), tradeDirection());
            data.set(DATA_QUANTITY, activeQuote.quantity());
            data.set(DATA_QUOTE_STATE, 1);
            updateQuoteData();
            return true;
        }
        if (id == COMPLETE_TRADE && activeQuote != null && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            MarketManager.PurchaseResult result = manager.completeTrade(serverPlayer, activeQuote.id(),
                    activeQuote.itemId(), activeQuote.quantity());
            data.set(DATA_RESULT, TradeFeedback.code(result.message()));
            serverPlayer.sendSystemMessage(Component.literal(result.message()));
            data.set(DATA_QUOTE_STATE, result.success() ? 3 : 4);
            return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        updateQuoteData();
        updateBasketData();
        if (refreshTicks++ % 20 == 0) updateContractData();
        if (manager != null && inventory.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            data.set(DATA_SELL_ALLOWANCE, manager.remainingSellAllowance(serverPlayer));
            data.set(DATA_ONLINE_STATE, manager.onlineMarketState());
        }
        super.broadcastChanges();
    }

    private void updateQuoteData() {
        if (activeQuote == null) return;
        long now = System.currentTimeMillis();
        data.set(DATA_QUOTE_STATE, RoyalExchangePresentationModel.quoteState(data.get(DATA_QUOTE_STATE),
                activeQuote.ready(now), activeQuote.expired(now)));
        setWide(data, DATA_DIAMOND_COST, DATA_DIAMOND_COST_HIGH, activeQuote.totalDiamondCost());
        data.set(DATA_QUOTE_SECONDS, (int) Math.max(0, (activeQuote.expirationTime() - now + 999) / 1000));
        setWide(data, DATA_BASE_PRICE, DATA_BASE_PRICE_HIGH,
                (int) Math.min(Integer.MAX_VALUE, Math.round(activeQuote.basePrice() * 1000)));
        setWide(data, DATA_CURRENT_PRICE, DATA_CURRENT_PRICE_HIGH,
                (int) Math.min(Integer.MAX_VALUE, Math.round(activeQuote.currentPrice() * 1000)));
        data.set(DATA_CONDITION, activeQuote.currentPrice() > activeQuote.basePrice() * 1.08 ? 1
                : activeQuote.currentPrice() < activeQuote.basePrice() * 0.92 ? -1 : 0);
        data.set(DATA_TRADE_DIRECTION, activeQuote.direction() == TradeDirection.SELL ? 1 : 0);
    }

    private void invalidateBasketQuote() {
        activeBasketQuote = null;
        Map<String, Integer> remaining = BasketTradeService.remainingQuantityWarnings(basket, basketRequiredQuantities);
        basketRequiredQuantities.clear();
        basketRequiredQuantities.putAll(remaining);
        data.set(DATA_BASKET_STATE, basketRequiredQuantities.isEmpty() ? 0 : 4);
        data.set(DATA_BASKET_ERROR, basketRequiredQuantities.isEmpty() ? 0 : 14);
    }

    private static int basketErrorCode(String message) {
        if (message.contains("larger sale quantity")) return 14;
        if (message.contains("Basket items changed")) return 15;
        return TradeFeedback.code(message);
    }

    private void updateBasketData() {
        data.set(DATA_BASKET_COUNT, basket.size());
        if (activeBasketQuote != null) {
            long now = System.currentTimeMillis();
            data.set(DATA_BASKET_STATE, RoyalExchangePresentationModel.quoteState(data.get(DATA_BASKET_STATE),
                    activeBasketQuote.ready(now), activeBasketQuote.expired(now)));
            boolean showQuote = data.get(DATA_BASKET_STATE) == 2 || data.get(DATA_BASKET_STATE) == 3;
            setWide(data, DATA_BASKET_TOTAL, DATA_BASKET_TOTAL_HIGH,
                    showQuote ? activeBasketQuote.totalDiamonds() : 0);
            data.set(DATA_BASKET_SECONDS, (int) Math.max(0, (activeBasketQuote.expirationTime() - now + 999) / 1000));
        } else {
            setWide(data, DATA_BASKET_TOTAL, DATA_BASKET_TOTAL_HIGH, 0);
            data.set(DATA_BASKET_SECONDS, 0);
        }
        for (int index = 0; index < BASKET_LIMIT; index++) {
            int base = DATA_BASKET_LINES + index * 5;
            data.set(DATA_BASKET_REQUIRED_QUANTITIES + index, index < basket.size()
                    ? basketRequiredQuantities.getOrDefault(basket.get(index).itemId(), 0) : 0);
            if (index < basket.size()) {
                BasketLine line = basket.get(index);
                Item item = VanillaItems.resolve(line.itemId());
                setWide(data, base, base + 1, BuiltInRegistries.ITEM.getId(item));
                data.set(base + 2, line.quantity());
                int payout = activeBasketQuote != null && data.get(DATA_BASKET_STATE) == 2
                        ? activeBasketQuote.lines().get(index).totalDiamondCost() : 0;
                setWide(data, base + 3, base + 4, payout);
            } else {
                for (int offset = 0; offset < 5; offset++) data.set(base + offset, 0);
            }
        }
    }

    private void updateContractData() {
        if (manager == null) return;
        List<MarketContract> available = manager.contracts();
        data.set(DATA_CONTRACT_COUNT, available.size());
        contractIndex = Math.max(0, Math.min(contractIndex, available.size() - 1));
        data.set(DATA_CONTRACT_INDEX, contractIndex);
        int pageStart = contractIndex / CONTRACT_PAGE_SIZE * CONTRACT_PAGE_SIZE;
        for (int row = 0; row < CONTRACT_PAGE_SIZE; row++) {
            int base = DATA_CONTRACT_ROWS + row * CONTRACT_ROW_DATA_SIZE;
            int index = pageStart + row;
            if (index < available.size()) {
                MarketContract entry = available.get(index);
                Item entryItem = VanillaItems.resolve(entry.itemId());
                setWide(data, base, base + 1, BuiltInRegistries.ITEM.getId(entryItem));
                data.set(base + 2, entry.quantity());
                setWide(data, base + 3, base + 4, entry.rewardDiamonds());
            } else {
                for (int offset = 0; offset < CONTRACT_ROW_DATA_SIZE; offset++) data.set(base + offset, 0);
            }
        }
        if (available.isEmpty()) {
            setWide(data, DATA_CONTRACT_ITEM, DATA_CONTRACT_ITEM_HIGH, 0);
            data.set(DATA_CONTRACT_QUANTITY, 0);
            setWide(data, DATA_CONTRACT_REWARD, DATA_CONTRACT_REWARD_HIGH, 0);
            setWide(data, DATA_CONTRACT_SECONDS, DATA_CONTRACT_SECONDS_HIGH, 0);
            return;
        }
        MarketContract contract = available.get(contractIndex);
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(contract.itemId())).orElse(Items.AIR);
        setWide(data, DATA_CONTRACT_ITEM, DATA_CONTRACT_ITEM_HIGH, BuiltInRegistries.ITEM.getId(item));
        data.set(DATA_CONTRACT_QUANTITY, contract.quantity());
        setWide(data, DATA_CONTRACT_REWARD, DATA_CONTRACT_REWARD_HIGH, contract.rewardDiamonds());
        setWide(data, DATA_CONTRACT_SECONDS, DATA_CONTRACT_SECONDS_HIGH,
                (int) Math.max(0, (contract.expirationTime() - System.currentTimeMillis() + 999) / 1000));
        data.set(DATA_CONTRACT_INDEX, contractIndex);
    }

    private static void setWide(ContainerData data, int lowSlot, int highSlot, int value) {
        data.set(lowSlot, MenuValueCodec.low(value));
        data.set(highSlot, MenuValueCodec.high(value));
    }

    private static int getWide(ContainerData data, int lowSlot, int highSlot) {
        return MenuValueCodec.combine(data.get(lowSlot), data.get(highSlot));
    }

    private Item selectedItem() { return BuiltInRegistries.ITEM.byId(getWide(data, DATA_SELECTED_ITEM, DATA_SELECTED_ITEM_HIGH)); }
    private TradeDirection tradeDirection() { return data.get(DATA_TRADE_DIRECTION) == 1 ? TradeDirection.SELL : TradeDirection.BUY; }

    private static int quantityStep(int quantity) {
        return RoyalExchangePresentationModel.quantityStep(quantity);
    }

    public Item selectedItemClient() { return selectedItem(); }
    public BlockPos blockPosClient() {
        return data.get(DATA_BLOCK_READY) == 0 ? null : new BlockPos(
                getWide(data, DATA_BLOCK_X, DATA_BLOCK_X_HIGH),
                getWide(data, DATA_BLOCK_Y, DATA_BLOCK_Y_HIGH),
                getWide(data, DATA_BLOCK_Z, DATA_BLOCK_Z_HIGH));
    }
    public int quantity() { return Math.max(1, data.get(DATA_QUANTITY)); }
    public int quoteState() { return data.get(DATA_QUOTE_STATE); }
    public int diamondCost() { return getWide(data, DATA_DIAMOND_COST, DATA_DIAMOND_COST_HIGH); }
    public int expiresInSeconds() { return data.get(DATA_QUOTE_SECONDS); }
    public double basePrice() { return getWide(data, DATA_BASE_PRICE, DATA_BASE_PRICE_HIGH) / 1000.0; }
    public double currentPrice() { return getWide(data, DATA_CURRENT_PRICE, DATA_CURRENT_PRICE_HIGH) / 1000.0; }
    public int condition() { return data.get(DATA_CONDITION); }
    public boolean sellMode() { return data.get(DATA_TRADE_DIRECTION) == 1; }
    public boolean contractView() { return data.get(DATA_CONTRACT_VIEW) == 1; }
    public Item contractItem() { return BuiltInRegistries.ITEM.byId(getWide(data, DATA_CONTRACT_ITEM, DATA_CONTRACT_ITEM_HIGH)); }
    public int contractQuantity() { return data.get(DATA_CONTRACT_QUANTITY); }
    public int contractReward() { return getWide(data, DATA_CONTRACT_REWARD, DATA_CONTRACT_REWARD_HIGH); }
    public int contractExpiresInSeconds() { return getWide(data, DATA_CONTRACT_SECONDS, DATA_CONTRACT_SECONDS_HIGH); }
    public int contractIndex() { return data.get(DATA_CONTRACT_INDEX); }
    public int contractCount() { return data.get(DATA_CONTRACT_COUNT); }
    public int contractPageStart() { return contractIndex() / CONTRACT_PAGE_SIZE * CONTRACT_PAGE_SIZE; }
    public Item contractRowItem(int row) {
        if (row < 0 || row >= CONTRACT_PAGE_SIZE) return Items.AIR;
        int base = DATA_CONTRACT_ROWS + row * CONTRACT_ROW_DATA_SIZE;
        return BuiltInRegistries.ITEM.byId(getWide(data, base, base + 1));
    }
    public int contractRowQuantity(int row) {
        return row < 0 || row >= CONTRACT_PAGE_SIZE ? 0
                : data.get(DATA_CONTRACT_ROWS + row * CONTRACT_ROW_DATA_SIZE + 2);
    }
    public int contractRowReward(int row) {
        if (row < 0 || row >= CONTRACT_PAGE_SIZE) return 0;
        int base = DATA_CONTRACT_ROWS + row * CONTRACT_ROW_DATA_SIZE;
        return getWide(data, base + 3, base + 4);
    }
    public int contractState() { return data.get(DATA_CONTRACT_STATE); }
    public int sellAllowance() { return data.get(DATA_SELL_ALLOWANCE); }
    public int basketCount() { return data.get(DATA_BASKET_COUNT); }
    public boolean basketContains(Item item) {
        int id = BuiltInRegistries.ITEM.getId(item);
        for (int index = 0; index < basketCount(); index++) {
            if (getWide(data, DATA_BASKET_LINES + index * 5, DATA_BASKET_LINES + index * 5 + 1) == id) return true;
        }
        return false;
    }
    public int basketState() { return data.get(DATA_BASKET_STATE); }
    public int basketError() { return data.get(DATA_BASKET_ERROR); }
    public boolean basketNeedsLargerQuantity(int index) {
        return basketState() == 4 && basketError() == 14 && index >= 0 && index < basketCount()
                && basketRequiredQuantity(index) > 0;
    }
    public int basketRequiredQuantity(int index) {
        return index >= 0 && index < basketCount() ? data.get(DATA_BASKET_REQUIRED_QUANTITIES + index) : 0;
    }
    public Component basketQuantityWarning(int index) {
        return quantityWarning(basketItem(index), basketRequiredQuantity(index));
    }
    private static Component quantityWarning(Item item, int requiredQuantity) {
        return Component.translatable("screen.colonybridge.royal_exchange.basket.item_quantity_required",
                item.getDefaultInstance().getHoverName(), requiredQuantity);
    }
    public Component basketQuantityWarning() {
        for (int index = 0; index < basketCount(); index++) {
            if (basketNeedsLargerQuantity(index)) return basketQuantityWarning(index);
        }
        return Component.empty();
    }
    public int basketQuantityWarningCount() {
        int count = 0;
        for (int index = 0; index < basketCount(); index++) {
            if (basketNeedsLargerQuantity(index)) count++;
        }
        return count;
    }
    public int basketTotal() { return getWide(data, DATA_BASKET_TOTAL, DATA_BASKET_TOTAL_HIGH); }
    public int basketSeconds() { return data.get(DATA_BASKET_SECONDS); }
    public Item basketItem(int index) { return BuiltInRegistries.ITEM.byId(getWide(data, DATA_BASKET_LINES + index * 5, DATA_BASKET_LINES + index * 5 + 1)); }
    public int basketQuantity(int index) { return data.get(DATA_BASKET_LINES + index * 5 + 2); }
    public int basketPayout(int index) { return getWide(data, DATA_BASKET_LINES + index * 5 + 3, DATA_BASKET_LINES + index * 5 + 4); }
    public int onlineMarketState() { return data.get(DATA_ONLINE_STATE); }
    public Component resultMessage() { return Component.translatable("screen.colonybridge.royal_exchange.result." + data.get(DATA_RESULT)); }

    @Override
    public boolean stillValid(Player player) { return stillValid(access, player, MarketRegistries.ROYAL_EXCHANGE.get()); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    private record DraftKey(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos, UUID player) {
    }

    private record Draft(int selectedItemId, int quantity, boolean sellMode, boolean contractView,
                         int contractIndex, int quoteState, int result, MarketQuote activeQuote,
                         List<BasketLine> basket, BasketQuote activeBasketQuote, int basketState, int basketError,
                         Map<String, Integer> basketRequiredQuantities) {
    }
}
