package com.colonybridge.market.trader;

import com.colonybridge.market.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class RoyalExchangeMenu extends AbstractContainerMenu {
    public static final int SELECT_ITEM_BASE = 1_000;
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
    public static final int DATA_COUNT = 19;

    private final Inventory inventory;
    private final MarketManager manager;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private MarketQuote activeQuote;
    private String resultMessage = "";
    private int contractIndex;

    public RoyalExchangeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    public RoyalExchangeMenu(int containerId, Inventory inventory, MarketManager manager, ContainerLevelAccess access) {
        this(containerId, inventory, manager, access, new SimpleContainerData(DATA_COUNT));
        data.set(1, 1);
    }

    private RoyalExchangeMenu(int containerId, Inventory inventory, MarketManager manager,
                              ContainerLevelAccess access, ContainerData data) {
        super(MarketRegistries.ROYAL_EXCHANGE_MENU.get(), containerId);
        this.inventory = inventory;
        this.manager = manager;
        this.access = access;
        this.data = data;
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (manager == null) return false;
        if (id == BUY_MODE || id == SELL_MODE || id == CONTRACTS_VIEW) {
            data.set(8, id == SELL_MODE ? 1 : 0);
            data.set(9, id == CONTRACTS_VIEW ? 1 : 0);
            data.set(2, 0);
            activeQuote = null;
            resultMessage = "";
            updateContractData();
            return true;
        }
        if (id == PREV_CONTRACT || id == NEXT_CONTRACT) {
            int total = Math.max(1, data.get(15));
            contractIndex = Math.floorMod(contractIndex + (id == NEXT_CONTRACT ? 1 : -1), total);
            data.set(16, 0);
            updateContractData();
            return true;
        }
        if (id == COMPLETE_CONTRACT && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            List<MarketContract> available = manager.contracts();
            if (available.isEmpty()) return false;
            contractIndex = Math.min(contractIndex, available.size() - 1);
            MarketManager.PurchaseResult result = manager.completeContract(serverPlayer, available.get(contractIndex).id());
            resultMessage = result.message();
            data.set(16, result.success() ? 1 : 2);
            if (result.success()) contractIndex = 0;
            updateContractData();
            return true;
        }
        if (id >= SELECT_ITEM_BASE) {
            Item selected = BuiltInRegistries.ITEM.byId(id - SELECT_ITEM_BASE);
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(selected);
            if (selected == Items.AIR || key == null || !"minecraft".equals(key.getNamespace())) return false;
            data.set(0, id - SELECT_ITEM_BASE);
            data.set(2, 0);
            activeQuote = null;
            resultMessage = "";
            return true;
        }
        if (id == DECREASE_QUANTITY || id == INCREASE_QUANTITY) {
            int quantity = data.get(1);
            data.set(1, id == INCREASE_QUANTITY ? Math.min(1024, quantity + quantityStep(quantity))
                    : Math.max(1, quantity - quantityStep(Math.max(1, quantity - 1))));
            data.set(2, 0);
            activeQuote = null;
            return true;
        }
        Item selected = selectedItem();
        if (selected == Items.AIR) return false;
        if (id == REQUEST_QUOTE && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            activeQuote = manager.requestQuote(serverPlayer, selected, data.get(1), tradeDirection());
            data.set(1, activeQuote.quantity());
            data.set(2, 1);
            updateQuoteData();
            return true;
        }
        if (id == COMPLETE_TRADE && activeQuote != null && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            MarketManager.PurchaseResult result = manager.completeTrade(serverPlayer, activeQuote.id(),
                    activeQuote.itemId(), activeQuote.quantity());
            resultMessage = result.message();
            data.set(2, result.success() ? 3 : 4);
            return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        updateQuoteData();
        updateContractData();
        if (manager != null && inventory.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            data.set(17, manager.remainingSellAllowance(serverPlayer));
            data.set(18, manager.onlineMarketState());
        }
        super.broadcastChanges();
    }

    private void updateQuoteData() {
        if (activeQuote == null) return;
        long now = System.currentTimeMillis();
        if (activeQuote.expired(now)) data.set(2, 5);
        else if (activeQuote.ready(now) && data.get(2) == 1) data.set(2, 2);
        data.set(3, activeQuote.totalDiamondCost());
        data.set(4, (int) Math.max(0, (activeQuote.expirationTime() - now + 999) / 1000));
        data.set(5, (int) Math.min(Integer.MAX_VALUE, Math.round(activeQuote.basePrice() * 1000)));
        data.set(6, (int) Math.min(Integer.MAX_VALUE, Math.round(activeQuote.currentPrice() * 1000)));
        data.set(7, activeQuote.currentPrice() > activeQuote.basePrice() * 1.08 ? 1
                : activeQuote.currentPrice() < activeQuote.basePrice() * 0.92 ? -1 : 0);
        data.set(8, activeQuote.direction() == TradeDirection.SELL ? 1 : 0);
    }

    private void updateContractData() {
        if (manager == null) return;
        List<MarketContract> available = manager.contracts();
        data.set(15, available.size());
        if (available.isEmpty()) { data.set(10, 0); return; }
        contractIndex = Math.min(contractIndex, available.size() - 1);
        MarketContract contract = available.get(contractIndex);
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(contract.itemId())).orElse(Items.AIR);
        data.set(10, BuiltInRegistries.ITEM.getId(item));
        data.set(11, contract.quantity());
        data.set(12, contract.rewardDiamonds());
        data.set(13, (int) Math.max(0, (contract.expirationTime() - System.currentTimeMillis() + 999) / 1000));
        data.set(14, contractIndex);
    }

    private Item selectedItem() { return BuiltInRegistries.ITEM.byId(data.get(0)); }
    private TradeDirection tradeDirection() { return data.get(8) == 1 ? TradeDirection.SELL : TradeDirection.BUY; }

    private static int quantityStep(int quantity) {
        return RoyalExchangePresentationModel.quantityStep(quantity);
    }

    public Item selectedItemClient() { return selectedItem(); }
    public int quantity() { return Math.max(1, data.get(1)); }
    public int quoteState() { return data.get(2); }
    public int diamondCost() { return data.get(3); }
    public int expiresInSeconds() { return data.get(4); }
    public double basePrice() { return data.get(5) / 1000.0; }
    public double currentPrice() { return data.get(6) / 1000.0; }
    public int condition() { return data.get(7); }
    public boolean sellMode() { return data.get(8) == 1; }
    public boolean contractView() { return data.get(9) == 1; }
    public Item contractItem() { return BuiltInRegistries.ITEM.byId(data.get(10)); }
    public int contractQuantity() { return data.get(11); }
    public int contractReward() { return data.get(12); }
    public int contractExpiresInSeconds() { return data.get(13); }
    public int contractIndex() { return data.get(14); }
    public int contractCount() { return data.get(15); }
    public int contractState() { return data.get(16); }
    public int sellAllowance() { return data.get(17); }
    public int onlineMarketState() { return data.get(18); }
    public Component resultMessage() { return Component.literal(resultMessage); }

    @Override
    public boolean stillValid(Player player) { return stillValid(access, player, MarketRegistries.ROYAL_EXCHANGE.get()); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
