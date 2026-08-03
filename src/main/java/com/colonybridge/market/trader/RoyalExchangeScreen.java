package com.colonybridge.market.trader;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RoyalExchangeScreen extends AbstractContainerScreen<RoyalExchangeMenu> {
    public static final int SCREEN_WIDTH = RoyalExchangeLayout.WIDTH;
    public static final int SCREEN_HEIGHT = RoyalExchangeLayout.HEIGHT;
    private static final int HEADER = 0xff17231f;
    private static final int PANEL = 0xffe4d2af;
    private static final int PANEL_LIGHT = 0xfff7edd8;
    private static final int PANEL_DARK = 0xffc8ae7e;
    private static final int INK = 0xff16241f;
    private static final int MUTED = 0xff3d4c46;
    private static final int COPPER = 0xff286451;
    private static final int BRASS = 0xff684719;
    private static final int CARD_EDGE = 0xff9b7b48;
    private static final int VALUE_BG = 0xffd7e5da;
    private static final int VISIBLE_ROWS = RoyalExchangeLayout.VISIBLE_ROWS;
    private static final int RIGHT_TEXT_WIDTH = RoyalExchangeLayout.RIGHT_TEXT_WIDTH;

    private final List<Item> filtered = new ArrayList<>();
    private EditBox search;
    private Button decrease;
    private Button increase;
    private Button quote;
    private Button buy;
    private Button buyMode;
    private Button sellMode;
    private Button contractsMode;
    private Button contractPrev;
    private Button contractNext;
    private Button completeContract;
    private int scroll;

    public RoyalExchangeScreen(RoyalExchangeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SCREEN_WIDTH;
        imageHeight = SCREEN_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        search = addRenderableWidget(new EditBox(font, leftPos + 14, topPos + 40, 128, 20,
                Component.translatable("screen.colonybridge.royal_exchange.search")));
        search.setHint(Component.translatable("screen.colonybridge.royal_exchange.search"));
        search.setMaxLength(50);
        search.setResponder(ignored -> rebuildItems());

        decrease = addRenderableWidget(Button.builder(Component.literal("-"), ignored -> send(RoyalExchangeMenu.DECREASE_QUANTITY))
                .bounds(leftPos + 164, topPos + 97, 24, 20).build());
        increase = addRenderableWidget(Button.builder(Component.literal("+"), ignored -> send(RoyalExchangeMenu.INCREASE_QUANTITY))
                .bounds(leftPos + 270, topPos + 97, 24, 20).build());
        quote = addRenderableWidget(Button.builder(Component.literal("Request Buy Quote"),
                ignored -> send(RoyalExchangeMenu.REQUEST_QUOTE)).bounds(leftPos + 164, topPos + 120, 130, 20).build());
        buy = addRenderableWidget(Button.builder(Component.literal("Buy Goods"),
                ignored -> send(RoyalExchangeMenu.COMPLETE_TRADE)).bounds(leftPos + 164, topPos + 196, 130, 20).build());

        buyMode = addRenderableWidget(Button.builder(Component.literal("Buy"), ignored -> send(RoyalExchangeMenu.BUY_MODE))
                .bounds(leftPos + 106, topPos + 7, 48, 19).build());
        sellMode = addRenderableWidget(Button.builder(Component.literal("Sell"), ignored -> send(RoyalExchangeMenu.SELL_MODE))
                .bounds(leftPos + 158, topPos + 7, 48, 19).build());
        contractsMode = addRenderableWidget(Button.builder(Component.literal("Contracts"), ignored -> send(RoyalExchangeMenu.CONTRACTS_VIEW))
                .bounds(leftPos + 210, topPos + 7, 96, 19).build());

        contractPrev = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> send(RoyalExchangeMenu.PREV_CONTRACT))
                .bounds(leftPos + 162, topPos + 168, 20, 20).build());
        contractNext = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> send(RoyalExchangeMenu.NEXT_CONTRACT))
                .bounds(leftPos + 276, topPos + 168, 20, 20).build());
        completeContract = addRenderableWidget(Button.builder(Component.literal("Fulfill Contract"), ignored -> send(RoyalExchangeMenu.COMPLETE_CONTRACT))
                .bounds(leftPos + 164, topPos + 196, 130, 20).build());
        rebuildItems();
    }

    private void rebuildItems() {
        String needle = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).filter(item -> {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null || !"minecraft".equals(key.getNamespace())) return false;
            return needle.isEmpty() || key.getPath().contains(needle)
                    || item.getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle);
        }).sorted(Comparator.comparing(item -> item.getDefaultInstance().getHoverName().getString())).forEach(filtered::add);
        scroll = RoyalExchangePresentationModel.clampScroll(scroll, filtered.size());
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                search.setFocused(false);
                return true;
            }
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (search != null && search.isFocused()) return search.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    public int screenWidthPixels() { return width; }
    public int screenHeightPixels() { return height; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!menu.contractView() && button == 0
                && mouseX >= leftPos + RoyalExchangeLayout.ITEM_LIST_LEFT
                && mouseX < leftPos + RoyalExchangeLayout.ITEM_LIST_RIGHT
                && mouseY >= topPos + RoyalExchangeLayout.ITEM_LIST_TOP
                && mouseY < topPos + RoyalExchangeLayout.ITEM_LIST_BOTTOM) {
            int index = scroll + (int) ((mouseY - (topPos + RoyalExchangeLayout.ITEM_LIST_TOP))
                    / RoyalExchangeLayout.ITEM_ROW_HEIGHT);
            if (index >= 0 && index < filtered.size()) {
                send(RoyalExchangeMenu.SELECT_ITEM_BASE + BuiltInRegistries.ITEM.getId(filtered.get(index)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!menu.contractView() && mouseX >= leftPos + 10 && mouseX < leftPos + 146
                && mouseY >= topPos + 63 && mouseY < topPos + 207) {
            scroll = RoyalExchangePresentationModel.clampScroll(scroll - (int) Math.signum(scrollY), filtered.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        Item hovered = hoveredItem(mouseX, mouseY);
        if (hovered != Items.AIR) graphics.renderTooltip(font, new ItemStack(hovered), mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, HEADER);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, PANEL);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + 29, HEADER);
        graphics.fill(leftPos + 4, topPos + 29, leftPos + imageWidth - 4, topPos + 31, BRASS);
        graphics.fill(leftPos + 10, topPos + 35, leftPos + 146, topPos + 218, PANEL_LIGHT);
        graphics.fill(leftPos + 152, topPos + 35, leftPos + 306, topPos + 218, PANEL_DARK);

        if (menu.contractView()) {
            drawCard(graphics, 16, 41, 140, 94, PANEL);
            drawCard(graphics, 16, 101, 140, 150, PANEL);
            drawCard(graphics, 16, 157, 140, 212, PANEL);
            drawCard(graphics, 158, 41, 300, 79, PANEL_LIGHT);
            drawCard(graphics, 158, 84, 300, 111, PANEL_LIGHT);
            drawCard(graphics, 158, 116, 300, 159, PANEL_LIGHT);
            drawCard(graphics, 158, 164, 300, 193, PANEL_LIGHT);
            renderContractGuide(graphics);
            renderContract(graphics);
        } else {
            drawCard(graphics, 158, 41, 300, 77, PANEL_LIGHT);
            drawCard(graphics, 158, 82, 300, 142, PANEL_LIGHT);
            drawCard(graphics, 158, 147, 300, 193, PANEL_LIGHT);
            renderItemList(graphics);
            renderSelectedItem(graphics);
            renderQuote(graphics);
        }
        updateControls();
    }

    private void drawCard(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(leftPos + x1, topPos + y1, leftPos + x2, topPos + y2, CARD_EDGE);
        graphics.fill(leftPos + x1 + 1, topPos + y1 + 1, leftPos + x2 - 1, topPos + y2 - 1, color);
    }

    private void renderItemList(GuiGraphics graphics) {
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) break;
            Item item = filtered.get(index);
            int y = topPos + 65 + row * 20;
            boolean selected = item == menu.selectedItemClient();
            if (selected) graphics.fill(leftPos + 14, y, leftPos + 140, y + 19, 0x663c806a);
            if (row > 0) graphics.fill(leftPos + 16, y, leftPos + 138, y + 1, 0x33684e28);
            graphics.renderItem(new ItemStack(item), leftPos + 17, y + 2);
            String name = item.getDefaultInstance().getHoverName().getString();
            graphics.drawString(font, fit(name, 96), leftPos + 40, y + 6,
                    selected ? 0xff103d30 : INK, false);
        }
        int trackTop = topPos + 66;
        int trackBottom = topPos + 204;
        graphics.fill(leftPos + 142, trackTop, leftPos + 144, trackBottom, 0x55684e28);
        if (filtered.size() > VISIBLE_ROWS) {
            int thumbHeight = Math.max(18, (trackBottom - trackTop) * VISIBLE_ROWS / filtered.size());
            int travel = trackBottom - trackTop - thumbHeight;
            int thumbTop = trackTop + travel * scroll / Math.max(1, filtered.size() - VISIBLE_ROWS);
            graphics.fill(leftPos + 141, thumbTop, leftPos + 145, thumbTop + thumbHeight, COPPER);
        }
    }

    private void renderSelectedItem(GuiGraphics graphics) {
        Item selected = menu.selectedItemClient();
        graphics.drawString(font, "SELECTED GOOD", leftPos + 164, topPos + 46, BRASS, false);
        if (selected == Items.AIR) {
            graphics.drawString(font, fit("Choose a good on the left", RIGHT_TEXT_WIDTH), leftPos + 164, topPos + 61, MUTED, false);
        } else {
            graphics.renderItem(new ItemStack(selected), leftPos + 164, topPos + 57);
            String name = selected.getDefaultInstance().getHoverName().getString();
            graphics.drawString(font, fit(name, 108), leftPos + 186, topPos + 59, INK, false);
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(selected);
            graphics.drawString(font, fit(key == null ? "" : key.toString(), 108),
                    leftPos + 186, topPos + 68, MUTED, false);
        }

        graphics.drawString(font, "QUANTITY", leftPos + 164, topPos + 87, BRASS, false);
        if (menu.sellMode()) {
            String allowance = "DAILY: " + menu.sellAllowance();
            graphics.drawString(font, allowance, leftPos + 294 - font.width(allowance), topPos + 87, MUTED, false);
        }
        graphics.fill(leftPos + 194, topPos + 97, leftPos + 264, topPos + 117, COPPER);
        graphics.fill(leftPos + 195, topPos + 98, leftPos + 263, topPos + 116, VALUE_BG);
        String amount = menu.quantity() + (menu.quantity() == 1 ? " item" : " items");
        graphics.drawCenteredString(font, amount, leftPos + 229, topPos + 103, COPPER);
    }

    private void renderQuote(GuiGraphics graphics) {
        int state = menu.quoteState();
        graphics.drawString(font, "MARKET QUOTE", leftPos + 164, topPos + 152, BRASS, false);
        if (state == 0) {
            drawWrapped(graphics, "Select a good, then request its live Exchange valuation.",
                    leftPos + 164, topPos + 166, RIGHT_TEXT_WIDTH, MUTED, 3);
        } else if (state == 1) {
            graphics.drawString(font, fit("Valuation in progress", RIGHT_TEXT_WIDTH), leftPos + 164, topPos + 166, INK, false);
            drawWrapped(graphics, "Weighing supply and demand.", leftPos + 164, topPos + 177,
                    RIGHT_TEXT_WIDTH, MUTED, 2);
        } else if (state == 2) {
            String diamonds = menu.sellMode()
                    ? "Receive " + menu.diamondCost() + diamondSuffix(menu.diamondCost())
                    : "Pay " + menu.diamondCost() + diamondSuffix(menu.diamondCost());
            String condition = menu.condition() > 0 ? "Demand elevated" : menu.condition() < 0 ? "Favorable supply" : "Steady market";
            graphics.drawString(font, fit(diamonds + "  |  " + exchangeRateText(), RIGHT_TEXT_WIDTH),
                    leftPos + 164, topPos + 165, COPPER, false);
            graphics.drawString(font, fit(condition + "  |  " + menu.expiresInSeconds() + "s left", RIGHT_TEXT_WIDTH),
                    leftPos + 164, topPos + 178, INK, false);
        } else if (state == 3) {
            graphics.drawString(font, "Trade completed", leftPos + 164, topPos + 168, 0xff1e6847, false);
            graphics.drawString(font, menu.sellMode() ? "Payment issued." : "Goods delivered.", leftPos + 164, topPos + 180, MUTED, false);
        } else if (state == 4) {
            graphics.drawString(font, "Trade declined", leftPos + 164, topPos + 168, 0xff8a2f29, false);
            graphics.drawString(font, menu.sellMode() ? "Check goods or daily limit." : "Check your diamonds.", leftPos + 164, topPos + 180, MUTED, false);
        } else {
            graphics.drawString(font, "Quote expired", leftPos + 164, topPos + 168, 0xff8a2f29, false);
            graphics.drawString(font, "Request a fresh valuation.", leftPos + 164, topPos + 180, MUTED, false);
        }
        quote.active = menu.selectedItemClient() != Items.AIR && state != 1;
    }

    private String exchangeRateText() {
        int quantity = menu.quantity();
        int diamonds = Math.max(1, menu.diamondCost());
        double itemsPerDiamond = (double) quantity / diamonds;
        if (itemsPerDiamond >= 1) {
            String amount = Math.abs(itemsPerDiamond - Math.rint(itemsPerDiamond)) < 0.001
                    ? Integer.toString((int) Math.rint(itemsPerDiamond))
                    : String.format(Locale.ROOT, "%.1f", itemsPerDiamond);
            return menu.sellMode() ? "1 diamond per " + amount : "1 diamond buys " + amount;
        }
        double diamondsPerItem = (double) diamonds / Math.max(1, quantity);
        String amount = Math.abs(diamondsPerItem - Math.rint(diamondsPerItem)) < 0.001
                ? Integer.toString((int) Math.rint(diamondsPerItem))
                : String.format(Locale.ROOT, "%.1f", diamondsPerItem);
        return menu.sellMode() ? amount + " per item" : amount + " each";
    }

    private static String diamondSuffix(int amount) { return amount == 1 ? " diamond" : " diamonds"; }

    private void renderContract(GuiGraphics graphics) {
        graphics.drawString(font, "ROYAL CONTRACT", leftPos + 164, topPos + 46, BRASS, false);
        int total = menu.contractCount();
        if (total == 0 || menu.contractItem() == Items.AIR) {
            graphics.drawString(font, "No commissions posted.", leftPos + 164, topPos + 62, MUTED, false);
            return;
        }
        Item item = menu.contractItem();
        graphics.drawString(font, (menu.contractIndex() + 1) + " of " + total, leftPos + 260, topPos + 46, MUTED, false);
        graphics.renderItem(new ItemStack(item), leftPos + 164, topPos + 58);
        graphics.drawString(font, fit(item.getDefaultInstance().getHoverName().getString(), 108),
                leftPos + 186, topPos + 61, INK, false);

        graphics.drawString(font, "DELIVER", leftPos + 164, topPos + 89, BRASS, false);
        graphics.drawString(font, menu.contractQuantity() + " items", leftPos + 164, topPos + 101, INK, false);

        graphics.drawString(font, "ROYAL REWARD", leftPos + 164, topPos + 121, BRASS, false);
        graphics.drawString(font, menu.contractReward() + diamondSuffix(menu.contractReward()), leftPos + 164, topPos + 133, COPPER, false);
        graphics.drawString(font, "Premium Exchange rate.", leftPos + 164, topPos + 146, MUTED, false);

        if (menu.contractState() == 1) graphics.drawCenteredString(font, "Fulfilled", leftPos + 229, topPos + 174, 0xff1e6847);
        else if (menu.contractState() == 2) graphics.drawCenteredString(font, "Goods missing", leftPos + 229, topPos + 174, 0xff8a2f29);
        else graphics.drawCenteredString(font, compactTime(menu.contractExpiresInSeconds()), leftPos + 229, topPos + 174, INK);
    }

    private void renderContractGuide(GuiGraphics graphics) {
        graphics.drawString(font, "ROYAL COMMISSIONS", leftPos + 22, topPos + 47, BRASS, false);
        graphics.drawString(font, "Deliver listed goods", leftPos + 22, topPos + 64, INK, false);
        graphics.drawString(font, "for premium rewards.", leftPos + 22, topPos + 75, INK, false);

        graphics.drawString(font, "ACCEPTED GOODS", leftPos + 22, topPos + 107, BRASS, false);
        graphics.drawString(font, "Vanilla goods only.", leftPos + 22, topPos + 126, MUTED, false);

        graphics.drawString(font, "HOW TO FULFILL", leftPos + 22, topPos + 163, BRASS, false);
        graphics.drawString(font, "Carry the full order.", leftPos + 22, topPos + 181, MUTED, false);
        graphics.drawString(font, "Press Fulfill.", leftPos + 22, topPos + 192, MUTED, false);
    }

    private void updateControls() {
        RoyalExchangePresentationModel.Controls controls = RoyalExchangePresentationModel.controls(
                menu.contractView(), menu.sellMode(), menu.quoteState(), menu.contractCount());
        search.visible = controls.tradeControlsVisible();
        decrease.visible = controls.tradeControlsVisible();
        increase.visible = controls.tradeControlsVisible();
        quote.visible = controls.tradeControlsVisible();
        buy.visible = controls.completeTradeVisible();
        buy.active = buy.visible;
        quote.setMessage(Component.literal(controls.quoteLabel()));
        buy.setMessage(Component.literal(controls.tradeLabel()));
        buyMode.active = controls.buyModeActive();
        sellMode.active = controls.sellModeActive();
        contractsMode.active = controls.contractsModeActive();
        contractPrev.visible = controls.contractNavigationVisible();
        contractNext.visible = controls.contractNavigationVisible();
        completeContract.visible = controls.completeContractVisible();
        completeContract.active = completeContract.visible;
    }

    private static String formatTime(int seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m remaining";
        return Math.max(0, seconds / 60) + "m remaining";
    }

    private static String compactTime(int seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m left";
        return Math.max(0, seconds / 60) + "m left";
    }

    private String fit(String text, int maxWidth) {
        return RoyalExchangePresentationModel.fit(text, maxWidth, font::width,
                width -> font.plainSubstrByWidth(text, width));
    }

    private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int count = Math.min(maxLines, lines.size());
        for (int line = 0; line < count; line++) {
            graphics.drawString(font, lines.get(line), x, y + line * 10, color, false);
        }
    }

    private Item hoveredItem(int mouseX, int mouseY) {
        if (menu.contractView()) return Items.AIR;
        if (mouseX < leftPos + RoyalExchangeLayout.ITEM_LIST_LEFT
                || mouseX >= leftPos + RoyalExchangeLayout.ITEM_LIST_RIGHT
                || mouseY < topPos + RoyalExchangeLayout.ITEM_LIST_TOP
                || mouseY >= topPos + RoyalExchangeLayout.ITEM_LIST_BOTTOM) return Items.AIR;
        int index = scroll + (mouseY - (topPos + RoyalExchangeLayout.ITEM_LIST_TOP))
                / RoyalExchangeLayout.ITEM_ROW_HEIGHT;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : Items.AIR;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "ROYAL EXCHANGE", 10, 11, 0xfff8efd9, true);
        if (!menu.contractView()) graphics.drawString(font, filtered.size() + (filtered.size() == 1 ? " good" : " goods"),
                14, 222, MUTED, false);
        String status = switch (menu.onlineMarketState()) {
            case 2 -> "SITE CONNECTED";
            case 1 -> "SITE CONNECTING";
            case -1 -> "SITE DISABLED";
            default -> "LOCAL FALLBACK";
        };
        int statusColor = menu.onlineMarketState() == 2 ? 0xff1e6847 : menu.onlineMarketState() == 1 ? BRASS : 0xff8a2f29;
        graphics.drawString(font, status, 300 - font.width(status), 222, statusColor, false);
    }
}
